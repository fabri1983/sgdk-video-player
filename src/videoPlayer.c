#include <genesis.h>
#include "generated/movie_data.h"
#include "movieHVInterrupts.h"
#include "videoPlayer.h"
#include "dma_1elem.h"
#include "compressionTypesTracker.h"
#include "decomp/rlew.h"
#include "stopwatch.h"

static u32* unpackedTilesetChunk = NULL;
static u16* unpackedTilemap = NULL;
static u16* unpackedPalsRender = NULL;
static u16* unpackedPalsBuffer = NULL;

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
	if (subtick == 0)
		return;

	u32 i = subtick;
	while (i--) {
		u32 tmp;
		// next code seems to loops 7 times to simulate a tick
		// TODO: use cycle accurate wait loop in asm (about 100 cycles for 1 subtick)
		ASM_STATEMENT __volatile__ (
			"   moveq  #7, %0\n"
			"1:\n"
			"   dbra   %0, 1b\n"   // dbf/dbra: test if not zero, then decrement register dN and branch back (b) to label 1
			: "=d" (tmp)
			:
			: "cc"
		);
	}
}

/// @brief See original Z80_setBusProtection() method.
/// NOTE: This implementation doesn't disable interrupts because at the moment it's called no interrupt is expected to occur.
/// NOTE: This implementation assumes the Z80 bus was not already requested, and requests it immediatelly.
/// @param value TRUE enables protection, FALSE disables it.
static FORCE_INLINE void setBusProtection_Z80 (bool value) {
    Z80_requestBus(FALSE);
	u16 busProtectSignalAddress = (Z80_DRV_PARAMS + 0x0D) & 0xFFFF; // point to Z80 PROTECT parameter
    vu8* pb = (u8*) (Z80_RAM + busProtectSignalAddress); // See Z80_useBusProtection() reference in z80_ctrl.c
    *pb = value?1:0;
	Z80_releaseBus();
}

/// @brief This implementation differs from DMA_flushQueue() in which:
/// - it doesn't check if transfer size exceeds capacity because we know before hand the max capacity.
/// - it assumes Z80 bus wasn't requested and hence request it.
static FORCE_INLINE void fast_DMA_flushQueue () {
	#ifdef DEBUG_VIDEO_PLAYER
	if (DMA_getQueueTransferSize() > DMA_getMaxTransferSize())
		KLog("[VIDEOPLAYER] WARNING: DMA transfer size limit raised. Modify the capacity in your DMA_initEx() call.");
	#endif
    // u8 autoInc = VDP_getAutoInc(); // save autoInc
	// Z80_requestBus(FALSE);
	flushDMA_1elem();
	// Z80_releaseBus();
    DMA_clearQueue();
    // VDP_setAutoInc(autoInc); // restore autoInc
}

static void NO_INLINE waitVInt_AND_flushDMA () {
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

	// casting to u8* allows us to use cmp.b instead of cmp.l, by using vtimerPtr+3 which is the first byte of vtimer
	u8* vtimerPtr = (u8*)&vtimer + 3;
	ASM_STATEMENT __volatile__ (
        "1:\n"
        "    cmp.b   (%1), %0\n"    // cmp: %0 - (%1) => dN - (aN)
        "    beq.s   1b\n"          // loop back if equal
        :
        : "d" (*vtimerPtr), "a" (vtimerPtr)
        : "cc"
    );

	// AT THIS POINT THE VInt callback WAS ALREADY CALLED. Check sega.s to see when vtimer is updated and what other callbacks are called.

	//VDP_setEnable(FALSE);
	*(vu16*) VDP_CTRL_PORT = 0x8100 | (116 & ~0x40);

	setBusProtection_Z80(TRUE);
	waitSubTick_(10); // Z80 delay --> wait a bit (10 ticks) to improve PCM playback (test on SOR2)

	fast_DMA_flushQueue();

	setBusProtection_Z80(FALSE);

	// This needed for DMA setup used in HInt, and likely needed for the CPU HInt too.
	// *((vu16*) VDP_CTRL_PORT) = 0x8F00 | 2; // instead of VDP_setAutoInc(2) due to additionals read and write from/to internal regValues[]

	//VDP_setEnable(TRUE);
	*(vu16*) VDP_CTRL_PORT = 0x8100 | (116 | 0x40);

	// We can perform the previous two operations in one call to VDP control port using sending u32 data
	// u32 twoWrites = (0x8F00 | 2) | ((0x8100 | (116 | 0x40)) << 16);
	// *(vu32*) VDP_CTRL_PORT = twoWrites;
}

static FORCE_INLINE void waitVInt () {
	// casting to u8* allows us to use cmp.b instead of cmp.l, by using vtimerPtr+3 which is the first byte of vtimer
	u8* vtimerPtr = (u8*)&vtimer + 3;
	ASM_STATEMENT __volatile__ (
        "1:\n"
        "    cmp.b   (%1), %0\n"    // cmp: %0 - (%1) => dN - (aN)
        "    beq.s   1b\n"          // loop back if equal
        :
        : "d" (*vtimerPtr), "a" (vtimerPtr)
        : "cc"
    );

	// AT THIS POINT THE VInt callback WAS ALREADY CALLED. Check sega.s to see when vtimer is updated and what other callbacks are called.
}

static void loadTilesCache () {
#ifdef DEBUG_TILES_CACHE
	//KLog_U1("tilesCache_movie1.numTile ", tilesCache_movie1.numTile);
	if (tilesCache_movie1.numTile > 0) {
		// In order to print the cache tiles values you need to set compression to NONE in the res file
		// u32* dptr = tilesCache_movie1.tiles;
		// for (u16 i=0; i < tilesCache_movie1.numTile; ++i) {
		// 	kprintf("%d,%d,%d,%d,%d,%d,%d,%d", *(dptr+0),*(dptr+1),*(dptr+2),*(dptr+3),*(dptr+4),*(dptr+5),*(dptr+6),*(dptr+7));
		// 	dptr += 8;
		// }

		PAL_setPalette(PAL0, palette_grey, CPU);
		// Fill all VRAM targeted for cached tiles with 0x66, which points to the 7th color for whatever palette is loaded in CRAM.
		// This way we can see in the VRAM Debugger the are occupied by the cached tiles.
		//VDP_fillTileData(0x66, MOVIE_TILES_CACHE_START_INDEX, tilesCache_movie1.numTile, TRUE);

		// By loading the cached tiles we can see in the VRAM Debugger the tiles with more details.
		VDP_loadTileSet((TileSet* const) &tilesCache_movie1, MOVIE_TILES_CACHE_START_INDEX, DMA);
		VDP_waitDMACompletion();
		// Set palette buffer with white color 0xEEE only for strips using PAL0
		u16* rendPtr = unpackedPalsRender;
		for (u16 i=0; i < MOVIE_FRAME_STRIPS; ++i) {
			memsetU16(rendPtr + 1, 0xEEE, 15);
			rendPtr += MOVIE_FRAME_COLORS_PER_STRIP;
		}
		//while (1) waitVInt();
	}
#else
	if (tilesCache_movie1.numTile > 0) {
		VDP_loadTileSet((TileSet* const) &tilesCache_movie1, MOVIE_TILES_CACHE_START_INDEX, DMA);
		VDP_waitDMACompletion();
	}
#endif
}

static void allocateTilesetBuffer () {
	unpackedTilesetChunk = (u32*) MEM_alloc(VIDEO_FRAME_TILESET_CHUNK_SIZE * 32);
	memsetU16((u16*) unpackedTilesetChunk, 0x0, VIDEO_FRAME_TILESET_CHUNK_SIZE * 16); // zero all the buffer
}

static FORCE_INLINE void unpackFrameTileset (TileSet* src) {
	const u16 size = src->numTile * 32;
	if (src->compression != COMPRESSION_NONE) {
		lz4w_unpack((u8*) FAR_SAFE(src->tiles, size), (u8*) unpackedTilesetChunk);
		//rlew_decomp_B_asm(0, (u8*) FAR_SAFE(src->tiles, size), (u8*) unpackedTilesetChunk);
	}
	else
        memcpy((u8*) unpackedTilesetChunk, FAR_SAFE(src->tiles, size), size);
}

static void freeTilesetBuffer () {
	MEM_free((void*) unpackedTilesetChunk);
}

static void allocateTilemapBuffer () {
	const u16 len = MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES * MOVIE_FRAME_HEIGHT_IN_TILES;
	unpackedTilemap = (u16*) MEM_alloc(len * 2);
	memsetU16(unpackedTilemap, TILE_SYSTEM_INDEX, len); // set TILE_SYSTEM_INDEX (black tile) all over the buffer
}

static FORCE_INLINE void unpackFrameTilemap (TileMapCustomCompField* src) {
#if ALL_TILEMAPS_COMPRESSED
	// NOTE: if NOT using RLEW enable next
	// const u16 size = MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES * MOVIE_FRAME_HEIGHT_IN_TILES * 2;
	// lz4w_unpack((u8*) FAR_SAFE(src->tilemap, size), (u8*) unpackedTilemap);
	// NOTE: if using RLEW enable next
	const u8 jumpGap = 2 * (MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES - MOVIE_FRAME_WIDTH_IN_TILES);
	const u16 size = MOVIE_FRAME_WIDTH_IN_TILES * MOVIE_FRAME_HEIGHT_IN_TILES * 2;
	rlew_decomp_B_asm(jumpGap, (u8*) FAR_SAFE(src->tilemap, size), (u8*) unpackedTilemap);
#elif ALL_TILEMAPS_NOT_COMPRESSED
	// NOTE: if using extended width in the data generated by rescomp then enable next
	const u16 size = MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES * MOVIE_FRAME_HEIGHT_IN_TILES * 2;
	memcpy((u8*) (unpackedTilemap), FAR_SAFE(src->tilemap, size), size);
	// NOTE: if NOT using extended width in the data generated by rescomp then enable next
	// u16* srcPtr = src->tilemap;
	// u16* unpackedPtr = unpackedTilemap;
	// const u16 size = MOVIE_FRAME_WIDTH_IN_TILES * 2;
	// for (u8 i=MOVIE_FRAME_HEIGHT_IN_TILES; i--;) {
	// 	memcpy((u8*) unpackedPtr, FAR_SAFE(srcPtr, size), size);
	// 	srcPtr += MOVIE_FRAME_WIDTH_IN_TILES;
	// 	unpackedPtr += MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES;
	// }
#else
	if (src->compression != COMPRESSION_NONE) {
		// NOTE: if NOT using RLEW enable next
		const u16 size = MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES * MOVIE_FRAME_HEIGHT_IN_TILES * 2;
		lz4w_unpack((u8*) FAR_SAFE(src->tilemap, size), (u8*) unpackedTilemap);
		// NOTE: if using RLEW enable next
		// const u8 jumpGap = 2 * (MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES - MOVIE_FRAME_WIDTH_IN_TILES);
		// const u16 size = MOVIE_FRAME_WIDTH_IN_TILES * MOVIE_FRAME_HEIGHT_IN_TILES * 2;
		// rlew_decomp_B_asm(jumpGap, (u8*) FAR_SAFE(src->tilemap, size), (u8*) unpackedTilemap);
	}
	else {
		// NOTE: if using extended width in the data generated by rescomp then enable next
		const u16 size = MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES * MOVIE_FRAME_HEIGHT_IN_TILES * 2;
		memcpy((u8*) (unpackedTilemap), FAR_SAFE(src->tilemap, size), size);
		// NOTE: if NOT using extended width in the data generated by rescomp then enable next
		// u16* srcPtr = src->tilemap;
		// u16* unpackedPtr = unpackedTilemap;
		// const u16 size = MOVIE_FRAME_WIDTH_IN_TILES * 2;
		// for (u8 i=MOVIE_FRAME_HEIGHT_IN_TILES; i--;) {
		// 	memcpy((u8*) unpackedPtr, FAR_SAFE(srcPtr, size), size);
		// 	srcPtr += MOVIE_FRAME_WIDTH_IN_TILES;
		// 	unpackedPtr += MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES;
		// }
	}
#endif
}

static void freeTilemapBuffer () {
	MEM_free((void*) unpackedTilemap);
}

static void allocatePalettesBuffer () {
	unpackedPalsRender = (u16*) MEM_alloc(VIDEO_FRAME_PALS_NUM * 2);
	unpackedPalsBuffer = (u16*) MEM_alloc(VIDEO_FRAME_PALS_NUM * 2);
	memsetU16(unpackedPalsRender, 0x0, VIDEO_FRAME_PALS_NUM); // black all the buffer
	memsetU16(unpackedPalsBuffer, 0x0, VIDEO_FRAME_PALS_NUM); // black all the buffer
}

static FORCE_INLINE void unpackFramePalettes (u16* data, u16 len, u16 offset) {
#if ALL_PALETTES_COMPRESSED
	// No need to use FAR_SAFE() macro here because palette data is always stored at NEAR region
	lz4w_unpack((u8*)data, (u8*) (unpackedPalsBuffer + offset));
#else
	// Copy the palette data. No FAR_SAFE() needed here because palette data is always stored at NEAR region.
	const u16 size = len * 2;
	memcpy((u8*) (unpackedPalsBuffer + offset), data, size);
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
}

static FORCE_INLINE void enqueueTilesetData (u16 startTileIndex, u16 length) {
	// This was the previous way
	// VDP_loadTileData(unpackedTilesetChunk, startTileIndex, length, DMA_QUEUE);
	// Now we use custom DMA_queueDmaFast() because the data is in RAM, so no 128KB bank boundary check is needed
	enqueueDMA_1elem((void*) unpackedTilesetChunk, startTileIndex * 32, length * 16);
}

static FORCE_INLINE void enqueueTilemapData (u16 tilemapAddrInPlane) {
	const u16 len = MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES * MOVIE_FRAME_HEIGHT_IN_TILES;
	// This was the previous way which benefits from tilemap width being 64 tiles
	// VDP_setTileMapData(tilemapAddrInPlane, unpackedTilemap, 0, len, 2, DMA_QUEUE);
	// Now we use custom DMA_queueDmaFast() because the data is in RAM, so no 128KB bank boundary check is needed
	enqueueDMA_1elem((void*) unpackedTilemap, tilemapAddrInPlane + (0 * 2), len);
}

#if VIDEO_FRAME_ADVANCE_STRATEGY == 4
static u16* createFramerateDivLUT () {
	u16 sysFrameRate = IS_PAL_SYSTEM ? 50 : 60;
	u16 lutSize = MOVIE_FRAME_COUNT * (sysFrameRate / MOVIE_FRAME_RATE);
	u16* lut = MEM_alloc(lutSize * 2);
	u16* lutPtr = lut;
	for (u16 hwFrameCntr = 0; hwFrameCntr < lutSize; ++hwFrameCntr) {
        *lutPtr++ = divu(hwFrameCntr * MOVIE_FRAME_RATE, sysFrameRate);
    }
	return lut;
}
#endif

static u16 calculatePlaneAddress () {
	// u16 xp = (screenWidth - MOVIE_FRAME_WIDTH_IN_TILES*8 + 7)/8/2; // centered in X axis (in tiles)
	// u16 yp = (screenHeight - MOVIE_FRAME_HEIGHT_IN_TILES*8 + 7)/8/2; // centered in Y axis (in tiles)
	// yp = max(yp, MOVIE_MIN_TILE_Y_POS_AVOID_DMA_FLICKER); // offsets the Y axis plane position to avoid the flickering due to DMA transfer leaking into active display area
	// u16 tilemapAddrInPlane = VDP_getPlaneAddress(BG_B, xp, yp);
	// return tilemapAddrInPlane;
	// For a frame size of 34x22 tiles the plane address for BG_B is 0xE186
	return 0xE186;
}

static void fadeToBlack () {
	// Last frame's palettes is still pointed by unpackedPalsRender

	// Always multiple of FADE_TO_BLACK_STEPS since a uniform fade to black effect lasts FADE_TO_BLACK_STEPS steps.
	s16 loopFrames = FADE_TO_BLACK_STEPS * FADE_TO_BLACK_STEP_FREQ;

	while (loopFrames >= 0) {
		if ((loopFrames-- % FADE_TO_BLACK_STEP_FREQ) == 0) {
			u16* palsPtr = unpackedPalsRender;
			for (u16 i=MOVIE_FRAME_STRIPS * MOVIE_FRAME_COLORS_PER_STRIP; i--;) {
                u16 d = *palsPtr - 0x222; // decrement 1 unit on every component
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
            }
		}
		// TODO PALS_1: uncomment when unpacking/load happens in the current active display loop
		//waitVInt_AND_flushDMA();
		waitVInt();
	}
}

void playMovie () {

	VDP_resetScreen();

	// Blacks out everything in screen while first frame is being loaded
	PAL_setColors(0, palette_black, 64, DMA);
	VDP_waitDMACompletion();

	if (IS_PAL_SYSTEM) VDP_setScreenHeight240();

	// Only Plane size 64x32 due to internal VRAM setup made by SGDK and the use of fixed tilemap width of 64 tiles by the custom rescomp_ext plugin.
    VDP_setPlaneSize(64, 32, TRUE);

	// If we want to use up to 1791 tiles (remember we keep the reserved tile at address 0) then we need to move BG_B 
	// and Window planes into BG_A so we can use the space otherwise used by BG_B by default. So:
	// - 0xE000 is where BG_A plane (its tilemap) address starts.
	// - Move BG_B and Window planes addresses into BG_A plane address, achieving up to 1791 tiles.
	VDP_setBGBAddress(VDP_getBGAAddress());
    VDP_setWindowAddress(VDP_getBGAAddress());

	// Clear all tileset VRAM until BG_B plane address (we can use the address as a counter because VRAM tileset starts at address 0)
	u16 numToClear = VDP_getBGBAddress();
	VDP_fillTileData(0, 1, numToClear, TRUE);

	allocateTilesetBuffer();
	allocateTilemapBuffer();
	allocatePalettesBuffer();
	loadTilesCache();
	MEM_pack();

	#if VIDEO_FRAME_ADVANCE_STRATEGY == 4
	u16* framerateDivLUT = createFramerateDivLUT();
	#endif

	// Get more RAM space
	// DMA_initEx(DMA_QUEUE_SIZE_MIN, (VIDEO_FRAME_TILESET_CHUNK_SIZE * 32), DMA_BUFFER_SIZE_MIN);
	// MEM_free(dmaQueues); // free up DMA_QUEUE_SIZE_MIN * sizeof(DMAOpInfo) bytes
	// dmaQueues = MEM_alloc(1 * sizeof(DMAOpInfo));
	// MEM_pack();

	u16 tilemapAddrInPlane = calculatePlaneAddress();

	// Load the appropriate driver
	// Z80_loadDriver(Z80_DRIVER_PCM, TRUE);
    Z80_loadDriver(Z80_DRIVER_XGM2, TRUE);
	// Z80_loadDriver(Z80_DRIVER_XGM, TRUE);

// KLog_U1("Free Mem: ", MEM_getFree()); // 33500 bytes.

    // Loop the entire video
	for (;;) // Better than while (TRUE) for infinite loops
    {
		#if VIDEO_FRAME_ADVANCE_STRATEGY == 1
		u16 sysFrameRate = IS_PAL_SYSTEM ? 50 : 60;
		#elif VIDEO_FRAME_ADVANCE_STRATEGY == 2
		u16 sysFrameRateReciprocal = IS_PAL_SYSTEM ? MOVIE_FRAME_RATE * 0x051E : MOVIE_FRAME_RATE * 0x0444;
		#elif VIDEO_FRAME_ADVANCE_STRATEGY == 3
		bool isPal = IS_PAL_SYSTEM;
		#endif

		// Let the HInt use the right pals before setting the VInt and HInt callbacks, otherwise it glitches out by one frame
		setMoviePalsPointerBeforeInterrupts(unpackedPalsRender); // Palettes are all black at this point

		SYS_disableInts();
			// SYS_setVIntCallback(VIntPlayerCallback);
			SYS_setVIntCallback(VIntMovieCallback);
			VDP_setHIntCounter(HINT_COUNTER_FOR_COLORS_UPDATE - 1);
			VDP_setHInterrupt(TRUE);
			#if HINT_USE_DMA
				if (IS_PAL_SYSTEM) SYS_setHIntCallback(HIntCallback_DMA_PAL);
				else SYS_setHIntCallback(HIntCallback_DMA_NTSC);
			#else
				if (IS_PAL_SYSTEM) SYS_setHIntCallback(HIntCallback_CPU_PAL);
				else SYS_setHIntCallback(HIntCallback_CPU_NTSC);
			#endif
		SYS_enableInts();

		// Wait for VInt so the logic can start at the beginning of the active display period
		waitVInt();

		// Start sound
		// SND_PCM_startPlay(sound_wav, sizeof(sound_wav), SOUND_PCM_RATE_22050, SOUND_PAN_CENTER, FALSE);
		XGM2_playPCMEx(sound_wav, sizeof(sound_wav), SOUND_PCM_CH_AUTO, 1, FALSE, FALSE);
		// XGM_setPCM(1, sound_wav, sizeof(sound_wav));
		// XGM_startPlayPCM(1, 1, SOUND_PCM_CH_AUTO);
		// XGM_setLoopNumber(0);

		SYS_disableInts();
		#ifdef DEBUG_FIXED_FRAME
		u16 vFrame = DEBUG_FIXED_FRAME;
		vtimer = ((IS_PAL_SYSTEM ? 50 : 60) / MOVIE_FRAME_RATE) * DEBUG_FIXED_FRAME;
		#else
		// NOTE: somehow the content of VRAM is erased when vFrame is 0, removing the cached tiles, so use next even number.
		u16 vFrame = 2;
		vtimer = ((IS_PAL_SYSTEM ? 50 : 60) / MOVIE_FRAME_RATE) * vFrame; // reset vtimer so we can use it as our hardware frame counter
		#endif
		SYS_enableInts();

		// As frames are indexed in a 0 based access layout, we know that even indexes hold frames with base tile index TILE_USER_INDEX_CUSTOM, 
		// and odd indexes hold frames with base tile index TILE_USER_INDEX_CUSTOM + VIDEO_FRAME_TILESET_TOTAL_SIZE.
		// We know vFrame always starts with a even value.
		u16 baseTileIndex = TILE_USER_INDEX_CUSTOM;

		bool exitPlayer = FALSE;

		while (vFrame < MOVIE_FRAME_COUNT)
		{
			unpackFrameTileset(data[vFrame]->tileset1);
			u16 numTile1 = data[vFrame]->tileset1->numTile;
			if (baseTileIndex == TILE_USER_INDEX_CUSTOM)
				enqueueTilesetData(TILE_USER_INDEX_CUSTOM, numTile1);
			else
				enqueueTilesetData(TILE_USER_INDEX_CUSTOM + VIDEO_FRAME_TILESET_TOTAL_SIZE, numTile1);
			waitVInt_AND_flushDMA();

			unpackFrameTileset(data[vFrame]->tileset2);
			u16 numTile2 = data[vFrame]->tileset2->numTile;
			if (baseTileIndex == TILE_USER_INDEX_CUSTOM)
				enqueueTilesetData(numTile1 + TILE_USER_INDEX_CUSTOM, numTile2);
			else
				enqueueTilesetData(numTile1 + TILE_USER_INDEX_CUSTOM + VIDEO_FRAME_TILESET_TOTAL_SIZE, numTile2);
			waitVInt_AND_flushDMA();

			unpackFrameTileset(data[vFrame]->tileset3);
			u16 numTile3 = data[vFrame]->tileset3->numTile;
			if (baseTileIndex == TILE_USER_INDEX_CUSTOM)
				enqueueTilesetData(numTile1 + numTile2 + TILE_USER_INDEX_CUSTOM, numTile3);
			else
				enqueueTilesetData(numTile1 + numTile2 + TILE_USER_INDEX_CUSTOM + VIDEO_FRAME_TILESET_TOTAL_SIZE, numTile3);
			waitVInt_AND_flushDMA();

			// Toggles between TILE_USER_INDEX_CUSTOM (initial mandatory value) and TILE_USER_INDEX_CUSTOM + VIDEO_FRAME_TILESET_TOTAL_SIZE
			baseTileIndex ^= (TILE_USER_INDEX_CUSTOM ^ (TILE_USER_INDEX_CUSTOM + VIDEO_FRAME_TILESET_TOTAL_SIZE)); // a ^= (x1 ^ x2)

			unpackFramePalettes(pals_data[vFrame]->data, VIDEO_FRAME_PALS_NUM, 0);

			// Swaps buffers pals
			swapPalsBuffers();

			unpackFrameTilemap(data[vFrame]->tilemap1);

			// Enqueue tilemap for DMA
			enqueueTilemapData(tilemapAddrInPlane);

			// NOTE: first 2 strips' palettes (previously unpacked) will be enqueued in waitVInt_AND_flushDMA()
			// NOTE2: not true until TODO PALS_1 is done

			#ifndef DEBUG_FIXED_FRAME
			u16 prevFrame = vFrame;
			#endif
			u16 hwFrameCntr = (u16)vtimer;
			#if VIDEO_FRAME_ADVANCE_STRATEGY == 1 /* Takes 202~235 cycles with a peak of 657. HInt disabled. */
			vFrame = divu(hwFrameCntr * MOVIE_FRAME_RATE, sysFrameRate);
			#elif VIDEO_FRAME_ADVANCE_STRATEGY == 2 /* Takes 370~406 cycles with a peak of 865. HInt disabled. */
			vFrame = (hwFrameCntr * sysFrameRateReciprocal) >> 16;
			#elif VIDEO_FRAME_ADVANCE_STRATEGY == 3 /* Takes 204~235 cycles with a peak of 687. HInt disabled. */
			u16 deltaFrames = isPal ? divu(hwFrameCntr, 50/MOVIE_FRAME_RATE) : divu(hwFrameCntr, 60/MOVIE_FRAME_RATE);
			vFrame += deltaFrames - vFrame;
			#elif VIDEO_FRAME_ADVANCE_STRATEGY == 4 /* Takes 71~93 cycles with a peak of 534. HInt disabled. */
			vFrame = framerateDivLUT[hwFrameCntr];
			#endif

			setMoviePalsPointer(unpackedPalsRender);
			waitVInt_AND_flushDMA();

			#ifdef DEBUG_FIXED_FRAME
			vFrame = DEBUG_FIXED_FRAME;
			#else
			#if FORCE_NO_MISSING_FRAMES
			// A frame is missed when the overal unpacking and loading is eating more than 60/15=4 NTSC (50/15=3.33 PAL) active display periods (for MOVIE_FRAME_RATE = 15)
			vFrame = prevFrame + 1;
			#else
			// IMPORTANT: next frame must be counter parity from previous frame. If same parity (both even or both odd) then we will mistakenly 
			// override the tileset VRAM region currently is being used for display the recently loaded frame.
			if (!((prevFrame ^ vFrame) & 1))
			   ++vFrame; // move into next frame so parity is not shared with previous frame
			#endif
			#endif

			#ifdef LOG_DIFF_BETWEEN_VIDEO_FRAMES
			u16 frmCntr = (u16) vtimer;
			KLog_U1("", frmCntr - prevFrame); // this tells how many system frames are spent for unpack, load, etc, per video frame
			#endif

			#if EXIT_PLAYER_WITH_JOY_START
			JOY_update();
			if (JOY_readJoypad(JOY_1) & BUTTON_START) {
				exitPlayer = TRUE;
				break;
			}
			#endif
		}

		// Fade out to black last frame's palettes. Only if we deliberatly wanted to exit from the video
		if (exitPlayer) {
			// Stop sound only if we are more than N (duration of the fade effect) frames before the last frame
			if (vFrame >= MOVIE_FRAME_COUNT - (FADE_TO_BLACK_STEPS * FADE_TO_BLACK_STEP_FREQ)) {
				// SND_PCM_stopPlay();
				XGM2_stopPCM(SOUND_PCM_CH1);
				// XGM_stopPlayPCM(SOUND_PCM_CH1);
			}
			// Fading effect
			fadeToBlack();
		}

		// Stop sound
		// SND_PCM_stopPlay();
		XGM2_stopPCM(SOUND_PCM_CH1);
		// XGM_stopPlayPCM(SOUND_PCM_CH1);

		SYS_disableInts();
			SYS_setVIntCallback(NULL);
			VDP_setHInterrupt(FALSE);
			SYS_setHIntCallback(NULL);
		SYS_enableInts();

		// Stop the video
		if (exitPlayer)
			break;
		// Loop the video
		else {
			// Clears all tilemap VRAM region for BG_A
			VDP_clearTileMap(VDP_BG_B, 0, 1 << (planeWidthSft + planeHeightSft), TRUE); // See VDP_clearPlane() for details
			waitMs_(1000);
		}
    }

	freeTilesetBuffer();
	freeTilemapBuffer();
	freePalettesBuffer();
	#if VIDEO_FRAME_ADVANCE_STRATEGY == 4
	MEM_free((void*) framerateDivLUT);
	#endif

	VDP_resetScreen();
	VDP_setPlaneSize(64, 32, TRUE);

	Z80_unloadDriver();
}