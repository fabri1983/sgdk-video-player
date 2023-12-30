#include <genesis.h>
#include "generated/movie_data.h"
#include "movieHVInterrupts.h"
#include "videoPlayer.h"

// #define DEBUG_VIDEO_PLAYER
// #define DEBUG_FIXED_FRAME 196 // Always use an even frame number due to the static map base tile index statically set on each frame by our custom rescomp extension
// #define LOG_DIFF_BETWEEN_VIDEO_FRAMES

#define VIDEO_FRAME_RATE (15-1) // Minus 1 so it delays enough to be in sync with audio. IT'S A TEMPORARY HACK BUT WORKS FLAWLESSLY!
#define FORCE_NO_MISSING_FRAMES FALSE
#define VIDEO_FRAME_MAX_TILESET_NUM 716 // this value got experimentally from rescomp output (biggest resulting numTile1 + numTile2). It has to be an even number.
#define VIDEO_FRAME_MAX_TILESET_CHUNK_NUM 370 // this value got experimentally from rescomp output (biggest numTile from one of two halves). It has to be an even number.
#define VIDEO_FRAME_MAX_TILEMAP_NUM MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES * MOVIE_FRAME_HEIGHT_IN_TILES
#define VIDEO_FRAME_MAX_TILEMAP_NUM_HALF_1 MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES * (MOVIE_FRAME_HEIGHT_IN_TILES/2)
#define VIDEO_FRAME_MAX_TILEMAP_NUM_HALF_2 MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES * ((MOVIE_FRAME_HEIGHT_IN_TILES/2) + (MOVIE_FRAME_HEIGHT_IN_TILES - 2*(MOVIE_FRAME_HEIGHT_IN_TILES/2)))

#define HINT_USE_DMA TRUE // TRUE: DMA. FALSE: CPU

/// SGDK reserves 16 tiles starting at address 0. That's the purpose of using SGDK's TILE_USER_INDEX.
/// Tile address 0 holds a black tile and it shouldn't be overriden since is what an empty tilemap in VRAM points to. Also other internal effects use it.
/// Remaining 15 tiles are OK to override for re use. So we start using tiles at index 1.
#define TILE_USER_INDEX_CUSTOM 1

/// Number of Tiles to be transferred by DMA_flushQueue() with mandatory off/on VDP setting to speed up the transfer (otherwise it glitches up).
/// NOTE: this has to be enough to support VIDEO_FRAME_MAX_TILESET_NUM / 2 which is the buffer size that holds the unpack of half a tileset.
/// 320 tiles * 32 bytes = 10240 as maxTransferPerFrame. 
/// 368 tiles * 32 bytes = 11776 as maxTransferPerFrame. 
/// 384 tiles * 32 bytes = 12282 as maxTransferPerFrame. 
/// Disabling VDP before DMA_flushQueue() helps to effectively increase the max transfer limit.
/// If number is bigger then you will notice some flickering on top of image meaning the transfer takes more time than Vertical retrace.
/// The flickering still exists but is not noticeable due to lower image Y position in plane. 
/// Using bigger image height or locating it at upper Y values will reveal the flickering.
#define TILES_PER_DMA_TRANSFER 368 // NOT USED ANYMORE since we now have splitted every frame's tileset in 2 chunks and using VIDEO_FRAME_MAX_TILESET_CHUNK_NUM instead.

/// @brief Waits for a certain amount of millisecond (~3.33 ms based timer when wait is >= 100ms). 
/// Lightweight implementation without calling SYS_doVBlankProcess().
/// This method CAN NOT be called from V-Int callback or when V-Int is disabled.
/// @param ms >= 100ms, otherwise use waitMs() from timer.h
static void waitMs_ (u32 ms) {
    u32 tick = (ms * TICKPERSECOND) / 1000;
    u32 start = getTick();
    u32 max = start + tick;

    // need to check for overflow
    if (max < start) max = 0xFFFFFFFF;

    // wait until we reached subtick
    while (getTick() < max) {;}
}

/// Wait for a certain amount of subtick. ONLY values < 150.
static void waitSubTick_ (u32 subtick) {
	u32 i = subtick;
	while (i--) {
		u32 tmp;
		// next code seems to loops 7 times to simulate a tick
		// TODO: use cycle accurate wait loop in asm (about 100 cycles for 1 subtick)
		ASM_STATEMENT volatile (
			"moveq #7,%0\n"
			"1:\n"
			"\t  dbra %0,1b"   // decrement register dx by 1 and branch if not zero
			: "=d" (tmp)
			:
			: "cc" // Clobbers: condition codes
		);
	}
}

/// @brief See original Z80_setBusProtection() method.
/// NOTE: This implementation doesn't disable interrupts since at the moment is called no interrupt is expected to occur.
/// NOTE: This implementation assumes the Z80 bus was not already requested, and requests it immediatelly.
/// @param value TRUE enables protection, FALSE disables it.
static void setBusProtection_Z80 (bool value) {
    Z80_requestBus(FALSE);
	u16 busProtectSignalAddress = (Z80_DRV_PARAMS + 0x0D) & 0xFFFF; // point to Z80 PROTECT parameter
    vu8* pb = (u8*) (Z80_RAM + busProtectSignalAddress); // See Z80_useBusProtection() reference in z80_ctrl.c
    *pb = value?1:0;
	Z80_releaseBus();
}

extern void flushQueue(u16 num);

/// @brief This implementation differs from DMA_flushQueue() in that:
/// - it doesn't check if transfer size exceeds capacity because we know before hand the max capacity.
/// - it assumes Z80 bus wasn't requested and hence request it.
static void flushQueue_DMA () {
	#ifdef DEBUG_VIDEO_PLAYER
	if (DMA_getQueueTransferSize() > DMA_getMaxTransferSize())
		KLog("WARNING: DMA transfer size limit raised. Modify the capacity in your DMA_initEx() call.");
	#endif
    // u8 autoInc = VDP_getAutoInc(); // save autoInc
	Z80_requestBus(FALSE);
	flushQueue(DMA_getQueueSize());
	Z80_releaseBus();
    DMA_clearQueue();
    // VDP_setAutoInc(autoInc); // restore autoInc
}

static void NO_INLINE waitVInt_AND_flushDMA (u16* palsForRender) {
	// From Discord:
	// ctr001: It would be better to just turn display off in vblank and then turn it on later, since then the z80 vblank interrupt will be better synchronized.
	//         But doing so may cause sprite glitches on the first line of the re-enabled display.
	// Stef: Better to blank top lines just for that reason.
	//       Just turn display off at the start of vblank, then turn back on when all DMA tasks are done.
	//       Better to use the V counter to re-enable display at a fixed line (otherwise it may vary depending the DMA load)
	//       You can even use the h-int to re-enable display without a passive wait.

	#ifdef DEBUG_VIDEO_PLAYER
	// already in VInt?
    if (SYS_isInVInt()) {
		// Likely this will cause some glitches if I don't understand when exactly this case occurs. 
		// Also it introduces delays since the DMA Queue now needs to wait to next VInt retrace.
		KLog("Already in VInt! Missed opportunity for DMA flush!");
		return;
	};
	#endif

	u32 t = vtimer; // initial frame counter
	while (vtimer == t) {;} // wait for next VInt
	// vu16 *pw = (u16 *) VDP_CTRL_PORT;
    // while (!(*pw & VDP_VBLANK_FLAG)) {;} // wait end of active period (vactive)

	// AT THIS POINT THE VInt WAS ALREADY CALLED. And if we have enqueued the pals we have to update the pointers used in HInt.
    if (palsForRender) {
        setPalsPointers(palsForRender + 64); // Points to 3rd strip's palette
    }

	*(vu16*) VDP_CTRL_PORT = 0x8100 | (116 & ~0x40);//VDP_setEnable(FALSE);

	setBusProtection_Z80(TRUE);
	waitSubTick_(0); // Z80 delay --> wait a bit (10 ticks) to improve PCM playback (test on SOR2)

	flushQueue_DMA();

	setBusProtection_Z80(FALSE);

	*(vu16*) VDP_CTRL_PORT = 0x8100 | (116 | 0x40);//VDP_setEnable(TRUE);

	// This needed for DMA setup used in HInt, and likely needed for the CPU HInt too.
	*((vu16*) VDP_CTRL_PORT) = 0x8F00 | 2; // instead of VDP_setAutoInc(2) due to additionals read and write from/to internal regValues[]
}

static void NO_INLINE waitVInt () {
	// already in VInt?
    if (SYS_isInVInt()) {
		return;
	};

	u32 t = vtimer; // initial frame counter
	while (vtimer == t) {;} // wait for next VInt
	// vu16 *pw = (u16 *) VDP_CTRL_PORT;
    // while (!(*pw & VDP_VBLANK_FLAG)) {;} // wait end of active period (vactive)
}

/*
 * Clearing FONT TILES from VRAM gives us additional VRAM space.
 * This is 53 tiles more per image and since we have 2 images loaded it accounts for 106 more tiles in VRAM.
 */
static void clearFontTiles () {
	VDP_fillTileData(0, TILE_FONT_INDEX, FONT_LEN, TRUE);
}

static u32* unpackedTilesetHalf;

static void allocateTilesetBuffer () {
	unpackedTilesetHalf = (u32*) MEM_alloc((VIDEO_FRAME_MAX_TILESET_NUM / 2) * 32);
	memsetU16((u16*) unpackedTilesetHalf, 0x0, (VIDEO_FRAME_MAX_TILESET_NUM / 2) * 16); // zero all the buffer
}

static void unpackFrameTileset (TileSet* src) {
	const u16 size = src->numTile * 32;
	if (src->compression != COMPRESSION_NONE)
		lz4w_unpack((u8*) FAR_SAFE(src->tiles, size), (u8*) unpackedTilesetHalf);
	else
        memcpy((u8*) unpackedTilesetHalf, FAR_SAFE(src->tiles, size), size);
}

static void freeTilesetBuffer () {
	MEM_free((void*) unpackedTilesetHalf);
    unpackedTilesetHalf = NULL;
}

static u16* unpackedTilemap;

static void allocateTilemapBuffer () {
	unpackedTilemap = (u16*) MEM_alloc(VIDEO_FRAME_MAX_TILEMAP_NUM * 2);
	memsetU16(unpackedTilemap, TILE_SYSTEM_INDEX, VIDEO_FRAME_MAX_TILEMAP_NUM); // set TILE_SYSTEM_INDEX (black tile) all the buffer
}

static void unpackFrameTilemap (TileMap* src, u16 len, u16 offset) {
	const u16 size = len * 2;
	if (src->compression != COMPRESSION_NONE)
		lz4w_unpack((u8*) FAR_SAFE(src->tilemap, size), (u8*) (unpackedTilemap + offset));
	else
        memcpy((u8*) (unpackedTilemap + offset), FAR_SAFE(src->tilemap, size), size);
}

static void freeTilemapBuffer () {
	MEM_free((void*) unpackedTilemap);
    unpackedTilemap = NULL;
}

static u16* unpackedPalsRender;
static u16* unpackedPalsBuffer;

static void allocatePalettesBuffer () {
	unpackedPalsRender = (u16*) MEM_alloc(MOVIE_FRAME_STRIPS * MOVIE_DATA_COLORS_PER_STRIP * 2);
	unpackedPalsBuffer = (u16*) MEM_alloc(MOVIE_FRAME_STRIPS * MOVIE_DATA_COLORS_PER_STRIP * 2);
	memsetU16(unpackedPalsRender, 0x0, MOVIE_FRAME_STRIPS * MOVIE_DATA_COLORS_PER_STRIP); // black all the buffer
	memsetU16(unpackedPalsBuffer, 0x0, MOVIE_FRAME_STRIPS * MOVIE_DATA_COLORS_PER_STRIP); // black all the buffer
}

static void unpackFramePalettes (const Palette32AllStrips* pals32) {
    if (pals32->compression != COMPRESSION_NONE) {
        // No need to use FAR_SAFE() macro here because palette data is always stored near
        lz4w_unpack((u8*) pals32->data, (u8*) unpackedPalsBuffer);
    }
    else {
		// Copy the palette data. No FAR_SAFE() needed here because palette data is always stored at near region.
		const u16 size = (MOVIE_FRAME_STRIPS * MOVIE_DATA_COLORS_PER_STRIP) * 2;
        memcpy((u8*) unpackedPalsBuffer, pals32->data, size);
    }
}

static void freePalettesBuffer () {
    MEM_free((void*) unpackedPalsRender);
	MEM_free((void*) unpackedPalsBuffer);
    unpackedPalsRender = NULL;
	unpackedPalsBuffer = NULL;
}

void swapBuffersForPals () {
	u16* tmp = unpackedPalsRender;
	unpackedPalsRender = unpackedPalsBuffer;
	unpackedPalsBuffer = tmp;
}

#define FADE_TO_BLACK_STEP_FREQ 4 // Every N frames we apply one fade to black step

static void fadeToBlack () {
	// Last frame's palettes is still pointed by unpackedPalsRender

	// Always multiple of 7 since a uniform fade to black effect lasts 7 steps.
	s16 loopFrames = 7 * FADE_TO_BLACK_STEP_FREQ;

	while (loopFrames >= 0) {
		if ((loopFrames-- % FADE_TO_BLACK_STEP_FREQ) == 0) {
			u16* palsPtr = unpackedPalsRender;
			for (u16 i=MOVIE_FRAME_STRIPS * MOVIE_DATA_COLORS_PER_STRIP; i > 0; --i) {
                // IMPL A:
                u16 d = *palsPtr - 0x222; // decrement 1 unit in every component
                switch (d & 0b1000100010000) {
                       case 0b0000000010000: d &= ~0b0000000011110; break; // red overflows? then zero it
                       case 0b0000100010000: d &= ~0b0000111111110; break; // red and green overflow? then zero them
                       case 0b0000100000000: d &= ~0b0000111100000; break; // green overflows? then zero it
                       case 0b1000000010000: d &= ~0b1111000011110; break; // red and blue overflow? then zero them
                       case 0b1000000000000: d &= ~0b1111000000000; break; // blue overflows? then zero it
                       case 0b1000100000000: d &= ~0b1111111100000; break; // green and blue overflow? then zero them
                       case 0b1000100010000: d = 0; break; // all colors overflow, then zero them
                       default: break;
                }
                *palsPtr++ = d;

                // IMPL B:
                // u16 d = *palsPtr - 0x222; // decrement 1 unit in every component
                // if (d & 0b0000000010000) d &= ~0b0000000011110; // red overflows? then zero it
                // if (d & 0b0000100000000) d &= ~0b0000111100000; // green overflows? then zero it
                // if (d & 0b1000000000000) d &= ~0b1111000000000; // blue overflows? then zero it
                // *palsPtr++ = d;

				// IMPL C: decay faster
				// u16 d = *palsPtr - 0x222; // decrement 1 unit in every component
                // if (d & 0b1000100010000) d = 0; // if only one color overflows then zero them all
                // *palsPtr++ = d;
            }
		}
		waitVInt();
	}
}

void playMovie () {

    // size: min queue size is 20.
	// capacity: experimentally we won't have more than VIDEO_FRAME_MAX_TILESET_CHUNK_NUM * 32 = 11840 bytes of data to transfer per display loop. 
	// bufferSize: we won't use temporary allocation, so set it at its min size.
	DMA_initEx(20, VIDEO_FRAME_MAX_TILESET_CHUNK_NUM * 32, DMA_BUFFER_SIZE_MIN);

	if (IS_PAL_SYSTEM) VDP_setScreenHeight240();

    VDP_setPlaneSize(MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES, 32, TRUE);

	// Hides first frame whilst is being loaded
	PAL_setColors(0, palette_black, 64, DMA);

	// Clear font tiles from VRAM. We will need that space, and it has to be cleared
	clearFontTiles();

	allocateTilesetBuffer();
	allocateTilemapBuffer();
	allocatePalettesBuffer();
	MEM_pack();
//KLog_U1("Free Mem: ", MEM_getFree()); // 36784 bytes

	// Position in screen (in tiles)
	u16 xp = (screenWidth - MOVIE_FRAME_WIDTH_IN_TILES*8 + 7)/8/2; // centered in X axis
	u16 yp = (screenHeight - MOVIE_FRAME_HEIGHT_IN_TILES*8 + 7)/8/2; // centered in Y axis
	yp = max(yp, MOVIE_MIN_TILE_Y_POS_AVOID_DMA_FLICKER); // yp >= 3 to avoid that DMA exceeding VInt's time causes some flicker at the top rendering section
	u16 addrInPlane = VDP_getPlaneAddress(BG_B, xp, yp);

    // Loop the entire video
	for (;;) // Better than while (TRUE) for infinite loops
    {
		// Let the HInt starts using the right pals
		setPalsPointers(unpackedPalsRender + 64); // Points to 3rd strip's palette

		bool isPal = IS_PAL_SYSTEM;
		// IMPL A: use normal division formula when calculating current frame
		// u16 sysFrameRate = isPal ? 50 : 60;
		// IMPL B: Reciprocal approximation magic numbers for 1/50 and 1/60
		u16 sysFrameRateReciprocal = isPal ? VIDEO_FRAME_RATE * 0x051E : VIDEO_FRAME_RATE * 0x0444;

		// Start sound
		SND_startPlay_2ADPCM(sound_wav, sizeof(sound_wav), SOUND_PCM_CH1, FALSE);

		SYS_disableInts();
			SYS_setVIntCallback(VIntCallback);
			VDP_setHIntCounter(HINT_COUNTER_FOR_COLORS_UPDATE - 1);
			VDP_setHInterrupt(TRUE);
			#if HINT_USE_DMA
				if (isPal) SYS_setHIntCallback(HIntCallback_DMA_PAL);
				else SYS_setHIntCallback(HIntCallback_DMA_NTSC);
			#else
				if (isPal) SYS_setHIntCallback(HIntCallback_CPU_PAL);
				else SYS_setHIntCallback(HIntCallback_CPU_NTSC);
			#endif
		SYS_enableInts();

		// Force setup of vars used in the HInt
		waitVInt();

		#ifdef DEBUG_FIXED_FRAME
		vtimer = DEBUG_FIXED_FRAME;
		#else
		vtimer = 0; // reset vTimer so we can use it as our frame counter
		#endif

		#ifdef DEBUG_FIXED_FRAME
		u16 vFrame = DEBUG_FIXED_FRAME;
		#else
		u16 vFrame = 0;
		#endif

		// As frames are indexed in a 0 based access layout, we know that even indexes hold frames with base tile index TILE_USER_INDEX_CUSTOM, 
		// and odd indexes hold frames with base tile index TILE_USER_INDEX_CUSTOM + VIDEO_FRAME_MAX_TILESET_NUM.
		// We know vFrame always starts with a even value.
		u16 baseTileIndex = TILE_USER_INDEX_CUSTOM;

		bool exitPlayer = FALSE;

		while (vFrame < MOVIE_FRAME_COUNT)
		{
			u16 numTile1 = data[vFrame]->tileset1->numTile;
			unpackFrameTileset(data[vFrame]->tileset1);
			VDP_loadTileData(unpackedTilesetHalf, baseTileIndex, numTile1, DMA_QUEUE);
			waitVInt_AND_flushDMA(NULL);

			if (data[vFrame]->tileset2 != NULL) {
				u16 numTile2 = data[vFrame]->tileset2->numTile;
				unpackFrameTileset(data[vFrame]->tileset2);
				VDP_loadTileData(unpackedTilesetHalf, baseTileIndex + numTile1, numTile2, DMA_QUEUE);
				waitVInt_AND_flushDMA(NULL);
			}

			// Toggles between TILE_USER_INDEX_CUSTOM (initial mandatory value) and TILE_USER_INDEX_CUSTOM + VIDEO_FRAME_MAX_TILESET_NUM
			baseTileIndex ^= VIDEO_FRAME_MAX_TILESET_NUM;

			unpackFrameTilemap(data[vFrame]->tilemap1, VIDEO_FRAME_MAX_TILEMAP_NUM_HALF_1, 0);
			unpackFrameTilemap(data[vFrame]->tilemap2, VIDEO_FRAME_MAX_TILEMAP_NUM_HALF_2, VIDEO_FRAME_MAX_TILEMAP_NUM_HALF_1);

			unpackFramePalettes(pals_data[vFrame]);
			swapBuffersForPals();

			// Loops until time consumes the VIDEO_FRAME_RATE before moving into next frame
			u16 prevFrame = vFrame;
			for (;;) {
				u16 hwFrameCntr = vtimer;

				// IMPL A:
				// vFrame = divu(hwFrameCntr * VIDEO_FRAME_RATE, sysFrameRate);
				// IMPL B:
				vFrame = (hwFrameCntr * sysFrameRateReciprocal) >> 16;
				// IMPL A/B:
				if (vFrame != prevFrame) {
					break;
				} else {
					waitVInt();
					JOY_update();
					if (JOY_readJoypad(JOY_1) & BUTTON_START) {
						exitPlayer = TRUE;
						break;
					}
				}

				// IMPL C: if VIDEO_FRAME_RATE = 15 then 50/15=3 PAL and 60/15=4 NTSC.
				// u16 deltaFrames = isPal ? divu(hwFrameCntr, 3) : hwFrameCntr / 4;
				// deltaFrames -= vFrame;
				// if (deltaFrames >= 1) {
				// 	vFrame += deltaFrames;
				// 	break;
				// } else {
				// 	waitVInt();
				// 	JOY_update();
				// 	if (JOY_readJoypad(JOY_1) & BUTTON_START) {
				// 		exitPlayer = TRUE;
				// 		break;
				// 	}
				// }
			}

			if (exitPlayer) break;

			// At this moment the tileset for the new frame is fully loaded into VRAM, and the tilemap is unpacked but not loaded into VRAM yet.
			// Now is time to load tilemap and palettes into VRAM, all within the time of the current active display loop.
			// NOTE: loading the tilemap and palettes into VRAM must only be done at this stage since they immediatelly affects whats the VDP draws on screen

			#ifdef DEBUG_FIXED_FRAME
			// If previous frame is same than fixed frame, it means this current frame must be displayed
			if (prevFrame == DEBUG_FIXED_FRAME) {
			#endif

			// Enqueue tilemap into VRAM
			VDP_setTileMapData(addrInPlane, unpackedTilemap, 0, VIDEO_FRAME_MAX_TILEMAP_NUM, 2, DMA_QUEUE);

			// Enqueue first 2 strips' palettes which were previously unpacked
			PAL_setColors(0, (const u16*) unpackedPalsRender, 64, DMA_QUEUE); // First strip palettes at [PAL0,PAL1], second at [PAL2,PAL3]

			#ifdef DEBUG_FIXED_FRAME
			}
			#endif

			#ifdef DEBUG_FIXED_FRAME
			// If previous frame wasn't the fixed frame, we don't need to modify the pointers the HInt is using so it continues showing the fixed frame
			if (prevFrame != DEBUG_FIXED_FRAME)
				waitVInt_AND_flushDMA(NULL);
			else
				waitVInt_AND_flushDMA(unpackedPalsRender);
			#else
			waitVInt_AND_flushDMA(unpackedPalsRender);
			#endif

			#ifdef LOG_DIFF_BETWEEN_VIDEO_FRAMES
			u16 frmCntr = vtimer;
			KLog_U1("", frmCntr - prevFrame); // this tells how many system frames are spent for unpack, load, etc, per video frame
			#endif

			#if FORCE_NO_MISSING_FRAMES
				// A frame is missed when the overal unpacking and loading is eating more than 60/15=4 NTSC (50/15=3.33 PAL) display loops (for VIDEO_FRAME_RATE = 15)
				vFrame = prevFrame + 1;
			#else
				// IMPORTANT: next frame must be the opposite parity of current frame. If same parity (both even or both odd) then we will mistakenly 
				// override the VRAM region currently is being used for display the loaded frame.
				if (!((prevFrame ^ vFrame) & 1))
					++vFrame; // move into next frame so parity is not sharedwith previous frame
			#endif

			#ifdef DEBUG_FIXED_FRAME
			// Once we already draw the target frame and let the next one load its data but not drawn => we set back to fixed frame
			if (prevFrame != DEBUG_FIXED_FRAME)
				vFrame = DEBUG_FIXED_FRAME;
			#endif

			JOY_update();
			if (JOY_readJoypad(JOY_1) & BUTTON_START) {
				exitPlayer = TRUE;
				break;
			}
		}

		// Fade out to black last frame's palettes. Only if we deliberatly wanted to exit from the video
		if (exitPlayer) {
			fadeToBlack();
		}

		// Stop sound
		SND_stopPlay_2ADPCM(SOUND_PCM_CH1);

		SYS_disableInts();
			SYS_setVIntCallback(NULL);
			VDP_setHInterrupt(FALSE);
			SYS_setHIntCallback(NULL);
		SYS_enableInts();

		// Stop the video
		if (exitPlayer) {
			VDP_fillTileMap(BG_B, 0, TILE_SYSTEM_INDEX, TILE_MAX_NUM); // SGDK's tile address 0 is a black tile.
			waitMs_(400);
			break;
		}
		// Loop the video
		else {
			// Clears all BG_B Plane's VRAM since tile index 1 leaving the tile 0 (system black tile)
			VDP_clearTileMap(VDP_BG_B, 1, 1 << (planeWidthSft + planeHeightSft), TRUE); // See VDP_clearPlane() for details
			waitMs_(400);
		}
    }

	freeTilesetBuffer();
	freeTilemapBuffer();
	freePalettesBuffer();
	// Clear plane after the MEM_free() calls otherwise buffers' pointers would point to empty memory
	VDP_clearPlane(BG_B, TRUE); // It uses SGDK's tile address 0 which is a black tile.

	MEM_pack();

	// Restore system tiles (16 plain tile)
    u16 i = 16;
    while(i--) VDP_fillTileData(i | (i << 4), TILE_SYSTEM_INDEX + i, 1, TRUE);

	// Restore system default palettes
    PAL_setPalette(PAL0, palette_grey, CPU);
    PAL_setPalette(PAL1, palette_red, CPU);
    PAL_setPalette(PAL2, palette_green, CPU);
    PAL_setPalette(PAL3, palette_blue, CPU);

	// Load font tiles again
	VDP_loadFont(&font_default, DMA);
}