#include <genesis.h>
#include "generated/movie_data.h"
#include "movieHVInterrupts.h"
#include "videoPlayer.h"
#include "palsBuffersSwapper.h"

// #define DEBUG_VIDEO_PLAYER
// #define DEBUG_FIXED_FRAME 196 // Always use an even frame number due to the static map base tile index statically set on each frame by our custom rescomp extension
// #define LOG_DIFF_BETWEEN_VIDEO_FRAMES

#define VIDEO_FRAME_RATE 15
#define FORCE_NO_MISSING_FRAMES FALSE
#define VIDEO_FRAME_MAX_TILESET_NUM 716 // this value got experimentally from rescomp output (biggest resulting numTile). It has to be an even number.
#define VIDEO_FRAME_MAX_TILESET_CHUNK_SIZE 370 // this value got experimentally from rescomp output (biggest numTile from one of two halves). It has to be an even number.
#define VIDEO_FRAME_MAX_TILEMAP_NUM MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES * MOVIE_FRAME_HEIGHT_IN_TILES
#define VIDEO_FRAME_MAX_TILEMAP_NUM_HALF_1 MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES * (MOVIE_FRAME_HEIGHT_IN_TILES/2)
#define VIDEO_FRAME_MAX_TILEMAP_NUM_HALF_2 MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES * ((MOVIE_FRAME_HEIGHT_IN_TILES/2) + (MOVIE_FRAME_HEIGHT_IN_TILES - 2*(MOVIE_FRAME_HEIGHT_IN_TILES/2)))

#define HINT_USE_DMA TRUE // TRUE: DMA. FALSE: CPU

/// SGDK reserves 16 tiles starting at address 0. That's the purpose of using SGDK's TILE_USER_INDEX.
/// Tile address 0 holds a black tile and it shouldn't be overriden since is what an empty tilemap in VRAM points to. Also other internal effects use it.
/// Remaining 15 tiles are OK to override for re use. So we start using tiles at index 1.
#define TILE_USER_INDEX_CUSTOM 1

/// Number of Tiles to be transferred by DMA_flushQueue() with off/on VDP setting to speed up the transfer. 
/// NOTE: this has to be enough to support VIDEO_FRAME_MAX_TILESET_NUM / 2 which is the buffer size that holds the unpack of half a tileset.
/// 320 tiles * 32 bytes = 10240 as maxTransferPerFrame. 
/// 368 tiles * 32 bytes = 11776 as maxTransferPerFrame. 
/// 384 tiles * 32 bytes = 12282 as maxTransferPerFrame. 
/// Hoping that disabling VDP before DMA_flushQueue() helps to effectively increase the max transfer limit.
/// If number is bigger then you will notice some flickering on top of image meaning the transfer size consumes more time than Vertical retrace.
/// The flickering still exists but is not noticeable due to lower image Y position in plane. 
/// Using bigger image height or locating it at upper Y values will reveal the flickering.
#define TILES_PER_DMA_TRANSFER 368 // NOT USED ANYMORE since we have splitted a frame's tileset in 2 chunks.

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
			"\t  dbra %0,1b"   // decrement register dx by 1 and if not zero then loop to label 1 (don't know why the b)
			: "=d" (tmp)
			:
			: "cc" // Clobbers: condition codes
		);
	}
}

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

/// @brief See original Z80_setBusProtection() method.
/// NOTE: This implementation doesn't disable interrupts since at the moment is called no interrupt is expected to occur.
/// NOTE: This implementation assumes the Z80 bus was not already requested, and requests it immediatelly.
/// @param value TRUE enables protection, FALSE disables it.
static void setBusProtection_Z80 (bool value) {
    Z80_requestBus(FALSE);
	u16 busProtectSignalAddress = (Z80_DRV_PARAMS + 0x0D) & 0xFFFF; // point to Z80 PROTECT parameter
    vu8* pb = (u8*) (Z80_RAM + busProtectSignalAddress); // See Z80_useBusProtection() reference in z80_ctrl.c
    *pb = value;
	Z80_releaseBus();
}

extern void flushQueue(u16 num);

/// @brief NOTE: this implementation doesn't check if transfer size exceeds capacity, and assumes it has to request Z80 bus.
static void flushDMAQueue () {
	#ifdef DEBUG_VIDEO_PLAYER
	if (DMA_getQueueTransferSize() > DMA_getMaxTransferSize())
		KLog("WARNING: DMA transfer size limit raised. Modify the capacity in your DMA_initEx() call.");
	#endif
    u8 autoInc = VDP_getAutoInc(); // save autoInc
	Z80_requestBus(FALSE);
	flushQueue(DMA_getQueueSize());
	Z80_releaseBus();
    DMA_clearQueue();
    VDP_setAutoInc(autoInc); // restore autoInc
}

static void NO_INLINE waitVInt_AND_flushDMA () {
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
    // while (!(*pw & VDP_VBLANK_FLAG)) {;}

	// AT THIS POINT THE VInt WAS ALREADY CALLED

	*(vu16*) VDP_CTRL_PORT = 0x8100 | (116 & ~0x40);//VDP_setEnable(FALSE);

	//SYS_getAndSetInterruptMaskLevel(4); // only disables HInt

	setBusProtection_Z80(TRUE);
	waitSubTick_(0); // Z80 delay --> wait a bit (10 ticks) to improve PCM playback (test on SOR2)

	flushDMAQueue();

	setBusProtection_Z80(FALSE);

	*(vu16*) VDP_CTRL_PORT = 0x8100 | (116 | 0x40);//VDP_setEnable(TRUE);

	// This needed for DMA setup used in HInt, and likely needed for the CPU HInt too.
	VDP_setAutoInc(2);
	//*((vu16*) VDP_CTRL_PORT) = 0x8F00 | 2; // instead of VDP_setAutoInc(2) due to additionals read and write from/to internal regValues[]

	//SYS_setInterruptMaskLevel(3); // interrupt saved state is by default 3 (if not nested nor other particular setup)
}

static void NO_INLINE waitVInt () {
	// already in VInt?
    if (SYS_isInVInt()) {
		return;
	};

	u32 t = vtimer; // initial frame counter
	while (vtimer == t) {;} // wait for next VInt
	// vu16 *pw = (u16 *) VDP_CTRL_PORT;
    // while (!(*pw & VDP_VBLANK_FLAG)) {;}
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

static void unpackFrameTilemap_1 (TileMap* src) {
	const u16 size = VIDEO_FRAME_MAX_TILEMAP_NUM_HALF_1 * 2;
	if (src->compression != COMPRESSION_NONE)
		lz4w_unpack((u8*) FAR_SAFE(src->tilemap, size), (u8*) unpackedTilemap);
	else
        memcpy((u8*) unpackedTilemap, FAR_SAFE(src->tilemap, size), size);
}

static void unpackFrameTilemap_2 (TileMap* src) {
	const u16 size = VIDEO_FRAME_MAX_TILEMAP_NUM_HALF_2 * 2;
	if (src->compression != COMPRESSION_NONE)
		lz4w_unpack((u8*) FAR_SAFE(src->tilemap, size), (u8*) (unpackedTilemap + VIDEO_FRAME_MAX_TILEMAP_NUM_HALF_1));
	else
        memcpy((u8*) (unpackedTilemap + VIDEO_FRAME_MAX_TILEMAP_NUM_HALF_1), FAR_SAFE(src->tilemap, size), size);
}

static void freeTilemapBuffer () {
	MEM_free((void*) unpackedTilemap);
    unpackedTilemap = NULL;
}

static void allocatePalettesBuffer () {
	u16 *unpackedPalsBufferA = (u16*) MEM_alloc(MOVIE_FRAME_STRIPS * MOVIE_DATA_COLORS_PER_STRIP * 2);
	u16* unpackedPalsBufferB = (u16*) MEM_alloc(MOVIE_FRAME_STRIPS * MOVIE_DATA_COLORS_PER_STRIP * 2);
	memsetU16(unpackedPalsBufferA, 0x0, MOVIE_FRAME_STRIPS * MOVIE_DATA_COLORS_PER_STRIP); // black all the buffer
	memsetU16(unpackedPalsBufferB, 0x0, MOVIE_FRAME_STRIPS * MOVIE_DATA_COLORS_PER_STRIP); // black all the buffer
	setPalsRender(unpackedPalsBufferA);
	setPalsUnpacked(unpackedPalsBufferB);
}

static void unpackFramePalettes (Palette32AllStrips* pals32) {
    if (pals32->compression != COMPRESSION_NONE) {
        // No need to use FAR_SAFE() macro here because palette data is always stored near
        lz4w_unpack((u8*) pals32->data, (u8*) getPalsUnpacked());
    }
    else {
		// Copy the palette data. No FAR_SAFE() needed here because palette data is always stored at near region.
		const u16 size = (MOVIE_FRAME_STRIPS * MOVIE_DATA_COLORS_PER_STRIP) * 2;
        memcpy((u8*) getPalsUnpacked(), pals32->data, size);
    }
}

static void freePalettesBuffer () {
    MEM_free((void*) getPalsRender());
	MEM_free((void*) getPalsUnpacked());
    setPalsRender(NULL);
	setPalsUnpacked(NULL);
}

static void loadTileSets (u16 tileIndex, u16 len, TransferMethod tm) {
	VDP_loadTileData(unpackedTilesetHalf, tileIndex, len, tm);
}

// static void loadTileMaps (u16 xp, u16 yp, TransferMethod tm) {
// 	u16 wt = MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES; // Every strip is N tiles width, but extended to 64 to speed up DMA transfer
// 	u16 ht = MOVIE_FRAME_HEIGHT_IN_TILES; // Every strip is M tiles height
// 	u16 wm = MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES;
// 	VDP_setTileMapDataRect(BG_B, unpackedTilemap, xp, yp, wt, ht, wm, tm);
// }

static void loadTileMaps (u16 addrInPlane, TransferMethod tm) {
	VDP_setTileMapData(addrInPlane, unpackedTilemap, 0, VIDEO_FRAME_MAX_TILEMAP_NUM, 2, tm);
}

void playMovie () {

    // size: min queue size is 20.
	// capacity: experimentally we won't have more than VIDEO_FRAME_MAX_TILESET_CHUNK_SIZE * 32 = 11840 bytes of data to transfer per display loop. 
	// bufferSize: we won't use temporary allocation, so set it at its min size.
	DMA_initEx(20, VIDEO_FRAME_MAX_TILESET_CHUNK_SIZE * 32, DMA_BUFFER_SIZE_MIN);

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
		// Instruct to do the pals buffers swap on the VInt so the HInt starts using the right pals
		doSwapPalsBuffers();

		bool exitPlayer = FALSE;
		//u16 sysFrameRate = IS_PAL_SYSTEM ? 50 : 60;
		u16 sysFrameRateReciprocal = IS_PAL_SYSTEM ? VIDEO_FRAME_RATE * 0x051E : VIDEO_FRAME_RATE * 0x0444;

		// Start sound
		//SND_startPlay_2ADPCM(sound_wav, sizeof(sound_wav), SOUND_PCM_CH1, FALSE);

		SYS_disableInts();
			SYS_setVIntCallback(VIntCallback);
			VDP_setHIntCounter(HINT_COUNTER_FOR_COLORS_UPDATE - 1);
			VDP_setHInterrupt(TRUE);
			#if HINT_USE_DMA
				if (IS_PAL_SYSTEM) SYS_setHIntCallback(HIntCallback_DMA_PAL);
				else SYS_setHIntCallback(HIntCallback_DMA_NTSC);
			#else
				if (IS_PAL_SYSTEM) SYS_setHIntCallback(HIntCallback_CPU_PAL);
				else SYS_setHIntCallback(HIntCallback_CPU_NTSC);
			#endif
			vtimer = 0; // reset vTimer so we can use it as our frame counter
			#ifdef DEBUG_FIXED_FRAME
			vtimer = DEBUG_FIXED_FRAME;
			#endif
		SYS_enableInts();

		// Force setup of vars used in the HInt
		waitVInt();

		u16 vFrame = 0;
		#ifdef DEBUG_FIXED_FRAME
		vFrame = DEBUG_FIXED_FRAME;
		#endif
		ImageNoPalsTilesetSplit2** dataPtr = (ImageNoPalsTilesetSplit2**) data;
		dataPtr += vFrame;
		Palette32AllStrips** palsDataPtr = (Palette32AllStrips**) pals_data; 
		palsDataPtr += vFrame;

		// As frames are indexed in a 0 based access layout, we know that even indexes hold frames with base tile index TILE_USER_INDEX_CUSTOM, 
		// and odd frames hold frames with base tile index TILE_USER_INDEX_CUSTOM + VIDEO_FRAME_MAX_TILESET_NUM.
		// We know vFrame always starts with a even value.
		u16 baseTileIndex = TILE_USER_INDEX_CUSTOM;

		while (vFrame < MOVIE_FRAME_COUNT)
		{
			u16 numTile1 = (*dataPtr)->tileset1->numTile;
			unpackFrameTileset((*dataPtr)->tileset1);
			loadTileSets(baseTileIndex, numTile1, DMA_QUEUE);
			waitVInt_AND_flushDMA();

			if ((*dataPtr)->tileset2 != NULL) {
				u16 numTile2 = (*dataPtr)->tileset2->numTile;
				unpackFrameTileset((*dataPtr)->tileset2);
				loadTileSets(baseTileIndex + numTile1, numTile2, DMA_QUEUE);
				waitVInt_AND_flushDMA();
			}

			unpackFrameTilemap_1((*dataPtr)->tilemap1);
			unpackFrameTilemap_2((*dataPtr)->tilemap2);

			unpackFramePalettes(*palsDataPtr);

			if (JOY_readJoypad(JOY_1) & BUTTON_START) {
				exitPlayer = TRUE;
				break;
			}

			// Loops until time consumes the VIDEO_FRAME_RATE before moving into next frame
			u16 prevFrame = vFrame;
			for (;;) {
				//SYS_getAndSetInterruptMaskLevel(6); // only disables VInt
				u16 frameCount = vtimer;
				//SYS_setInterruptMaskLevel(3); // interrupt saved state is by default 3 (if not nested nor other particular setup)
				//vFrame = frameCount * VIDEO_FRAME_RATE / sysFrameRate;
				vFrame = (frameCount * sysFrameRateReciprocal) >> 16;

				if (vFrame != prevFrame) {
					break;
				} else {
					waitVInt();
					if (JOY_readJoypad(JOY_1) & BUTTON_START) {
						exitPlayer = TRUE;
						break;
					}
				}
			}

			if (exitPlayer) break;

			// At this moment the tileset for the new frame is fully loaded into VRAM, and the tilemap is unpacked but not loaded into VRAM yet.
			// Now is time to load tilemap and palettes into VRAM, all within the time of the current active display loop.
			// NOTE: loading the tilemap and palettes into VRAM must only be done at this stage since they immediatelly affects whats the VDP draws on screen

			#ifdef DEBUG_FIXED_FRAME
			// Only draw target frame
			if (prevFrame == DEBUG_FIXED_FRAME) {
			#endif

			// Enqueue tilemap into VRAM
			loadTileMaps(addrInPlane, DMA_QUEUE);

			// Enqueue first 2 strips' palettes which were previously unpacked
			PAL_setColors(0, (const u16*) getPalsUnpacked(), 64, DMA_QUEUE); // First strip palettes at [PAL0,PAL1], second at [PAL2,PAL3]

			// Instruct to do the pals buffers swap on the VInt so the HInt starts using the right pals
			doSwapPalsBuffers();

			#ifdef DEBUG_FIXED_FRAME
			}
			#endif

			waitVInt_AND_flushDMA();

			#ifdef LOG_DIFF_BETWEEN_VIDEO_FRAMES
			KLog_U1("", vtimer - prevFrame); // this tells how many system frames are spent for unpack, load, etc, per video frame
			#endif

			#if FORCE_NO_MISSING_FRAMES
				// Any frame missed? If yes then it means loadTileSets() is eating 60/12=5 NTSC (50/12=4.16 PAL) or more display loops (if VIDEO_FRAME_RATE = 12)
				#ifdef DEBUG_VIDEO_PLAYER
				if (deltaFrames > 1) KLog_U1("Frame/s missed: ", deltaFrames);
				#endif
				vFrame = prevFrame + 1;
				++dataPtr;
				++palsDataPtr;
			#else
				// IMPORTANT: next frame must be the opposite parity of current frame. If same parity (both even or both odd) then we will mistakenly 
				// override the VRAM region currently is being used for display the loaded frame.
				if (!((prevFrame ^ vFrame) & 1))
					++vFrame; // move into next frame so parity is not sharedwith previous frame
				u16 jumpFrames = vFrame - prevFrame;
				dataPtr += jumpFrames;
				palsDataPtr += jumpFrames;
			#endif


			#ifdef DEBUG_FIXED_FRAME
			// Once we already draw the target frame and let the next one load its data but not drawn, we set back target frame
			if (prevFrame != DEBUG_FIXED_FRAME) {
				u16 backFrames = vFrame - DEBUG_FIXED_FRAME;
				dataPtr -= backFrames;
				palsDataPtr -= backFrames;
				vFrame = DEBUG_FIXED_FRAME;
			}
			#endif

			// Toggles between TILE_USER_INDEX_CUSTOM (initial mandatory value) and TILE_USER_INDEX_CUSTOM + VIDEO_FRAME_MAX_TILESET_NUM
			baseTileIndex ^= VIDEO_FRAME_MAX_TILESET_NUM;
		}

		// Stop sound
		//SND_stopPlay_2ADPCM(SOUND_PCM_CH1);

		// Wait for next VInt in order to disable all interrupt handlers
		waitVInt();
		SYS_disableInts();
			SYS_setVIntCallback(NULL);
			VDP_setHInterrupt(FALSE);
			SYS_setHIntCallback(NULL);
		SYS_enableInts();

		PAL_setColors(PAL0, palette_black, 64, DMA);
		VDP_fillTileMap(BG_B, 0, TILE_SYSTEM_INDEX, TILE_MAX_NUM); // SGDK's tile address 0 is a black tile.

		if (exitPlayer) {
			break;
		}

		waitMs_(500);
    }

	freeTilesetBuffer();
	freeTilemapBuffer();
	freePalettesBuffer();

	// TODO: clear mem used for sound?
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