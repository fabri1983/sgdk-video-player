#include <genesis.h>
#include "generated/movie_data.h"
#include "movieHVInterrupts.h"
#include "videoPlayer.h"

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
/// NOTE: This implementation doesn't disable interrupts because at the moment it's called no interrupt is expected to occur.
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
static void fastDMA_flushQueue () {
	#ifdef DEBUG_VIDEO_PLAYER
	if (DMA_getQueueTransferSize() > DMA_getMaxTransferSize())
		KLog("WARNING: DMA transfer size limit raised. Modify the capacity in your DMA_initEx() call.");
	#endif
    // u8 autoInc = VDP_getAutoInc(); // save autoInc
	Z80_requestBus(FALSE);
	flushQueue(1); // We know DMA_getQueueSize() is 1 because we only do one DMA_QUEUE before each DMA flush.
	Z80_releaseBus();
    DMA_clearQueue();
    // VDP_setAutoInc(autoInc); // restore autoInc
}

static void NO_INLINE waitVInt_AND_flushDMA (u16* palsForRender, bool resetPalsPtrsForHInt) {
	// TODO PALS_1: uncomment when unpacking/load happens in the current active display loop
	// We have to enqueue the first 2 strips' pals on every active display period so when on Blank period the data is DMAed into CRAM
	//PAL_setColors(0, (const u16*) palsForRender, 64, DMA_QUEUE); // First strip palettes at [PAL0,PAL1], second at [PAL2,PAL3]

	// From Discord:
	// ctr001: It would be better to just turn display off in vblank and then turn it on later, since then the z80 vblank interrupt will be better synchronized.
	//         But doing so may cause sprite glitches on the first line of the re-enabled display.
	// Stef: Better to blank top lines just for that reason.
	//       Just turn display off at the start of vblank, then turn back on when all DMA tasks are done.
	//       Better to use the V counter to re-enable display at a fixed line (otherwise it may vary depending the DMA load)
	//       You can even use the h-int to re-enable display without a passive wait.

	u32 t = vtimer; // initial frame counter
	while (vtimer == t) {;} // wait for next VInt

	// AT THIS POINT THE VInt WAS ALREADY CALLED.

	*(vu16*) VDP_CTRL_PORT = 0x8100 | (116 & ~0x40);//VDP_setEnable(FALSE);

	// Reset the pals pointers used by HInt so they now point to the new unpacked pals
	if (resetPalsPtrsForHInt)
		setPalsPointer(palsForRender);

	setBusProtection_Z80(TRUE);
	waitSubTick_(0); // Z80 delay --> wait a bit (10 ticks) to improve PCM playback (test on SOR2)

	fastDMA_flushQueue();

	setBusProtection_Z80(FALSE);

	// This needed for DMA setup used in HInt, and likely needed for the CPU HInt too.
	// *((vu16*) VDP_CTRL_PORT) = 0x8F00 | 2; // instead of VDP_setAutoInc(2) due to additionals read and write from/to internal regValues[]

	*(vu16*) VDP_CTRL_PORT = 0x8100 | (116 | 0x40);//VDP_setEnable(TRUE);

	// We can perform the previous two operations in one call to VDP control port using sending u32 data
	// u32 twoWrites = (0x8F00 | 2) | ((0x8100 | (116 | 0x40)) << 16);
	// *(vu32*) VDP_CTRL_PORT = twoWrites;
}

static FORCE_INLINE void waitVInt () {
	u32 t = vtimer; // initial frame counter
	while (vtimer == t) {;} // wait for next VInt
}

/*
 * Clearing FONT TILES from VRAM gives us additional VRAM space.
 * This is 53 tiles more per image and since we have 2 images loaded it accounts for 106 more tiles in VRAM.
 */
static void clearFontTiles () {
	VDP_fillTileData(0, TILE_FONT_INDEX, FONT_LEN, TRUE);
}

static u32* unpackedTilesetChunk;

static void allocateTilesetBuffer () {
	unpackedTilesetChunk = (u32*) MEM_alloc((VIDEO_FRAME_MAX_TILESET_TOTAL_SIZE / 2) * 32);
	memsetU16((u16*) unpackedTilesetChunk, 0x0, (VIDEO_FRAME_MAX_TILESET_TOTAL_SIZE / 2) * 16); // zero all the buffer
}

static FORCE_INLINE void unpackFrameTileset (TileSet* src) {
	const u16 size = src->numTile * 32;
	if (src->compression != COMPRESSION_NONE)
		lz4w_unpack((u8*) FAR_SAFE(src->tiles, size), (u8*) unpackedTilesetChunk);
	else
        memcpy((u8*) unpackedTilesetChunk, FAR_SAFE(src->tiles, size), size);
}

static void freeTilesetBuffer () {
	MEM_free((void*) unpackedTilesetChunk);
    unpackedTilesetChunk = NULL;
}

static u16* unpackedTilemap;

static void allocateTilemapBuffer () {
	unpackedTilemap = (u16*) MEM_alloc(VIDEO_FRAME_MAX_TILEMAP_NUM * 2);
	memsetU16(unpackedTilemap, TILE_SYSTEM_INDEX, VIDEO_FRAME_MAX_TILEMAP_NUM); // set TILE_SYSTEM_INDEX (black tile) all over the buffer
}

static FORCE_INLINE void unpackFrameTilemap (TileMap* src, u16 len, u16 offset) {
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
	unpackedPalsRender = (u16*) MEM_alloc(MOVIE_FRAME_STRIPS * MOVIE_FRAME_COLORS_PER_STRIP * 2);
	unpackedPalsBuffer = (u16*) MEM_alloc(MOVIE_FRAME_STRIPS * MOVIE_FRAME_COLORS_PER_STRIP * 2);
	memsetU16(unpackedPalsRender, 0x0, MOVIE_FRAME_STRIPS * MOVIE_FRAME_COLORS_PER_STRIP); // black all the buffer
	memsetU16(unpackedPalsBuffer, 0x0, MOVIE_FRAME_STRIPS * MOVIE_FRAME_COLORS_PER_STRIP); // black all the buffer
}

static FORCE_INLINE void unpackFramePalettes (const Palette32AllStrips* pals32) {
	#if ALL_PALETTES_ARE_COMPRESSED
	// No need to use FAR_SAFE() macro here because palette data is always stored near
	lz4w_unpack((u8*) pals32->data, (u8*) unpackedPalsBuffer);
	#else
	if (pals32->compression != COMPRESSION_NONE) {
		// No need to use FAR_SAFE() macro here because palette data is always stored near
		lz4w_unpack((u8*) pals32->data, (u8*) unpackedPalsBuffer);
	}
	else {
		// Copy the palette data. No FAR_SAFE() needed here because palette data is always stored at near region.
		const u16 size = (MOVIE_FRAME_STRIPS * MOVIE_FRAME_COLORS_PER_STRIP) * 2;
		memcpy((u8*) unpackedPalsBuffer, pals32->data, size);
    }
	#endif
}

static FORCE_INLINE void swapPalsBuffers () {
	u16* tmp = unpackedPalsRender;
	unpackedPalsRender = unpackedPalsBuffer;
	unpackedPalsBuffer = tmp;
}

static void freePalettesBuffer () {
    MEM_free((void*) unpackedPalsRender);
	MEM_free((void*) unpackedPalsBuffer);
    unpackedPalsRender = NULL;
	unpackedPalsBuffer = NULL;
}

static void fadeToBlack () {
	// Last frame's palettes is still pointed by unpackedPalsRender

	// Always multiple of 7 since a uniform fade to black effect lasts 7 steps.
	s16 loopFrames = 7 * FADE_TO_BLACK_STEP_FREQ;

	while (loopFrames >= 0) {
		if ((loopFrames-- % FADE_TO_BLACK_STEP_FREQ) == 0) {
			u16* palsPtr = unpackedPalsRender;
			for (u16 i=MOVIE_FRAME_STRIPS * MOVIE_FRAME_COLORS_PER_STRIP; --i;) {
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
		// TODO PALS_1: uncomment when unpacking/load happens in the current active display loop
		//waitVInt_AND_flushDMA(unpackedPalsRender, FALSE);
		waitVInt();
	}
}

void playMovie () {

    // size: min queue size is 20.
	// capacity: experimentally we won't have more than (VIDEO_FRAME_MAX_TILESET_CHUNK_SIZE * 32) = 11840 bytes of data to transfer in VBlank period.
	// bufferSize: we won't use temporary allocation, so set it at its min size.
	DMA_initEx(20, (VIDEO_FRAME_MAX_TILESET_CHUNK_SIZE * 32), DMA_BUFFER_SIZE_MIN);

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
// KLog_U1("Free Mem: ", MEM_getFree()); // 36778 bytes

	// Position in screen (in tiles)
	u16 xp = (screenWidth - MOVIE_FRAME_WIDTH_IN_TILES*8 + 7)/8/2; // centered in X axis
	u16 yp = (screenHeight - MOVIE_FRAME_HEIGHT_IN_TILES*8 + 7)/8/2; // centered in Y axis
	yp = max(yp, MOVIE_MIN_TILE_Y_POS_AVOID_DMA_FLICKER); // offsets the Y axis plane position to avoid the flickering due to DMA transfer leaking into active display area
	u16 tilemapAddrInPlane = VDP_getPlaneAddress(BG_B, xp, yp);

    // Loop the entire video
	for (;;) // Better than while (TRUE) for infinite loops
    {
		bool isPal = IS_PAL_SYSTEM;
		#if (VIDEO_FRAME_ADVANCE_STRATEGY == 0)
		u16 sysFrameRate = isPal ? 50 : 60;
		#elif (VIDEO_FRAME_ADVANCE_STRATEGY == 1)
		u16 sysFrameRateReciprocal = isPal ? MOVIE_FRAME_RATE * 0x051E : MOVIE_FRAME_RATE * 0x0444;
		#endif

		// Let the HInt usie the right pals right before setting the VInt and HInt callbacks
		setPalsPointer(unpackedPalsRender); // Palettes are all black at this point

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

		// As frames are indexed in a 0 based access layout, we know that even indexes hold frames with base tile index TILE_USER_INDEX_CUSTOM, 
		// and odd indexes hold frames with base tile index TILE_USER_INDEX_CUSTOM + VIDEO_FRAME_MAX_TILESET_TOTAL_SIZE.
		// We know vFrame always starts with a even value.
		u16 baseTileIndex = TILE_USER_INDEX_CUSTOM;

		bool exitPlayer = FALSE;

		// Start sound
		SND_startPlay_PCM(sound_wav, sizeof(sound_wav), SOUND_RATE_32000, SOUND_PAN_CENTER, FALSE);
		// XGM_setPCM(1, sound_wav, sizeof(sound_wav));
		// XGM_startPlayPCM(1, 1, SOUND_PCM_CH1);
		// XGM_setLoopNumber(0);

		// Wait for VInt so the logic can start at the beginning of the active display period
		waitVInt();

		#ifdef DEBUG_FIXED_FRAME
		vtimer = DEBUG_FIXED_FRAME;
		u16 vFrame = DEBUG_FIXED_FRAME;
		#else
		vtimer = 0; // reset vTimer so we can use it as our hardware frame counter
		u16 vFrame = 0;
		#endif

		ImageNoPalsTilesetSplit3** dataPtr = (ImageNoPalsTilesetSplit3**)data + vFrame;

		while (vFrame < MOVIE_FRAME_COUNT)
		{
			u16 numTile1 = (*dataPtr)->tileset1->numTile;
			unpackFrameTileset((*dataPtr)->tileset1);
			VDP_loadTileData(unpackedTilesetChunk, baseTileIndex, numTile1, DMA_QUEUE);
			waitVInt_AND_flushDMA(unpackedPalsRender, FALSE);

			u16 numTile2 = (*dataPtr)->tileset2->numTile;
			unpackFrameTileset((*dataPtr)->tileset2);
			VDP_loadTileData(unpackedTilesetChunk, baseTileIndex + numTile1, numTile2, DMA_QUEUE);
			waitVInt_AND_flushDMA(unpackedPalsRender, FALSE);

			u16 numTile3 = (*dataPtr)->tileset3->numTile;
			unpackFrameTileset((*dataPtr)->tileset3);
			VDP_loadTileData(unpackedTilesetChunk, baseTileIndex + numTile1 + numTile2, numTile3, DMA_QUEUE);
			waitVInt_AND_flushDMA(unpackedPalsRender, FALSE);

			// Toggles between TILE_USER_INDEX_CUSTOM (initial mandatory value) and TILE_USER_INDEX_CUSTOM + VIDEO_FRAME_MAX_TILESET_TOTAL_SIZE
			baseTileIndex ^= VIDEO_FRAME_MAX_TILESET_TOTAL_SIZE;

			unpackFrameTilemap((*dataPtr)->tilemap1, VIDEO_FRAME_MAX_TILEMAP_NUM_CHUNK, 0);
			unpackFrameTilemap((*dataPtr)->tilemap2, VIDEO_FRAME_MAX_TILEMAP_NUM_CHUNK, VIDEO_FRAME_MAX_TILEMAP_NUM_CHUNK);
			unpackFrameTilemap((*dataPtr)->tilemap3, VIDEO_FRAME_MAX_TILEMAP_NUM_CHUNK_LAST, 2*VIDEO_FRAME_MAX_TILEMAP_NUM_CHUNK);
			unpackFramePalettes(pals_data[vFrame]);

			#if EXIT_PLAYER_WITH_JOY_START
			JOY_update();
			if (JOY_readJoypad(JOY_1) & BUTTON_START) {
				exitPlayer = TRUE;
				break;
			}
			#endif

			// Loops until time consumes the MOVIE_FRAME_RATE before moving into next frame
			u16 prevFrame = vFrame;
			for (;;) {
				u16 hwFrameCntr = vtimer;

				#if (VIDEO_FRAME_ADVANCE_STRATEGY == 0)
				vFrame = divu(hwFrameCntr * MOVIE_FRAME_RATE, sysFrameRate);
				#elif (VIDEO_FRAME_ADVANCE_STRATEGY == 1)
				vFrame = (hwFrameCntr * sysFrameRateReciprocal) >> 16;
				#endif
				#if (VIDEO_FRAME_ADVANCE_STRATEGY < 2)
				if (vFrame != prevFrame) {
					break;
				} else {
					// TODO PALS_1: uncomment when unpacking/load happens in the current active display loop
					//waitVInt_AND_flushDMA(unpackedPalsRender, FALSE);
					waitVInt();
				}
				#endif

				#if (VIDEO_FRAME_ADVANCE_STRATEGY == 2)
				u16 deltaFrames = isPal ? divu(hwFrameCntr, 50/MOVIE_FRAME_RATE) : divu(hwFrameCntr, 60/MOVIE_FRAME_RATE);
				deltaFrames -= vFrame;
				if (deltaFrames >= 1) {
					vFrame += deltaFrames;
					break;
				} else {
					// TODO PALS_1: uncomment when unpacking/load happens in the current active display loop
					//waitVInt_AND_flushDMA(unpackedPalsRender, FALSE);
					waitVInt();
				}
				#endif
			}

			if (exitPlayer) break;

			#ifdef DEBUG_FIXED_FRAME
			// If previous frame is same than fixed frame, it means this current frame must be displayed
			if (prevFrame == DEBUG_FIXED_FRAME) {
			#endif

			// Enqueue tilemap into VRAM
			VDP_setTileMapData(tilemapAddrInPlane, unpackedTilemap, 0, VIDEO_FRAME_MAX_TILEMAP_NUM, 2, DMA_QUEUE);

			// Swaps buffers pals so now the pals render buffer points to the unpacked pals
			swapPalsBuffers();

			// NOTE: first 2 strips' palettes which were previously unpacked will be enqueued in waitVInt_AND_flushDMA()
			// NOTE2: not true until TODO PALS_1 is done

			#ifdef DEBUG_FIXED_FRAME
			}
			#endif

			#ifdef DEBUG_FIXED_FRAME
			// If previous frame wasn't the fixed frame, we don't need to modify the pointers the HInt is using so it continues showing the fixed frame
			if (prevFrame != DEBUG_FIXED_FRAME)
				waitVInt_AND_flushDMA(unpackedPalsRender, FALSE);
			else
				waitVInt_AND_flushDMA(unpackedPalsRender, TRUE);
			#else
			waitVInt_AND_flushDMA(unpackedPalsRender, TRUE);
			#endif

			#ifdef LOG_DIFF_BETWEEN_VIDEO_FRAMES
			u16 frmCntr = vtimer;
			KLog_U1("", frmCntr - prevFrame); // this tells how many system frames are spent for unpack, load, etc, per video frame
			#endif

			#ifdef DEBUG_FIXED_FRAME
			// Once we already draw the target frame and let the next one load its data but not drawn => we go back to fixed frame
			if (prevFrame != DEBUG_FIXED_FRAME) {
				vFrame = DEBUG_FIXED_FRAME;
				--dataPtr;
			} else {
				++dataPtr;
			}
			#else
			#if FORCE_NO_MISSING_FRAMES
			// A frame is missed when the overal unpacking and loading is eating more than 60/15=4 NTSC (50/15=3.33 PAL) active display periods (for MOVIE_FRAME_RATE = 15)
			vFrame = prevFrame + 1;
			#else
			// IMPORTANT: next frame must be of the opposite parity of previous frame. If same parity (both even or both odd) then we will mistakenly 
			// override the VRAM region currently is being used for display the recently loaded frame.
			if (!((prevFrame ^ vFrame) & 1))
				++vFrame; // move into next frame so parity is not shared with previous frame
			#endif
			dataPtr += vFrame - prevFrame;
			#endif

			#if EXIT_PLAYER_WITH_START
			JOY_update();
			if (JOY_readJoypad(JOY_1) & BUTTON_START) {
				exitPlayer = TRUE;
				break;
			}
			#endif
		}

		// Stop sound
		SND_stopPlay_PCM();
		// XGM_stopPlayPCM(SOUND_PCM_CH1);

		// Fade out to black last frame's palettes. Only if we deliberatly wanted to exit from the video
		if (exitPlayer) {
			fadeToBlack();
		}

		SYS_disableInts();
			SYS_setVIntCallback(NULL);
			VDP_setHInterrupt(FALSE);
			SYS_setHIntCallback(NULL);
		SYS_enableInts();

		// Stop the video
		if (exitPlayer) {
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
	// Clears all BG_B Plane's VRAM since tile index 1 leaving the tile 0 (system black tile)
	VDP_clearTileMap(VDP_BG_B, 1, 1 << (planeWidthSft + planeHeightSft), TRUE); // See VDP_clearPlane() for details

	MEM_pack();

	// Restore system tiles (16 plain tiles)
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