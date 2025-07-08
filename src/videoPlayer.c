#include <types.h>
#include <sys.h>
#include <z80_ctrl.h>
#include <vdp.h>
#include <vdp_bg.h>
#include <vdp_tile.h>
#include <memory.h>
#include <tools.h>
#include <mapper.h>
#include <snd/sound.h>
#include <snd/xgm2.h>
#include "generated/movie_data.h"
#include "generated/movie_cache_consts.h"
#include "movieHVInts.h"
#include "videoPlayer.h"
#include "dma_elems.h"
#include "compressionTypesTracker.h"
#include "decomp/rlew.h"
#include "utils.h"
#include "memcpy.h"

static u32* unpackedTilesetChunk = NULL;
static u16* unpackedPalsRender = NULL;
static u16* unpackedPalsBuffer = NULL;

/// @brief See original Z80_setBusProtection() method.
/// NOTE: This implementation doesn't disable interrupts because at the moment it's called no interrupt is expected to occur.
/// NOTE: This implementation assumes the Z80 bus was not already requested, and requests it immediatelly.
/// @param value TRUE enables protection, FALSE disables it.
static FORCE_INLINE void Z80_setBusProtection_fast (bool value)
{
    Z80_requestBus(FALSE);
	u16 busProtectSignalAddress = (Z80_DRV_PARAMS + 0x0D) & 0xFFFF; // point to Z80 PROTECT parameter
    vu8* pb = (u8*) (Z80_RAM + busProtectSignalAddress); // See Z80_useBusProtection() reference in z80_ctrl.c
    *pb = value?1:0;
	Z80_releaseBus();
}

/// @brief This implementation differs from DMA_flushQueue() in which:
/// - it doesn't check if transfer size exceeds capacity because we know before hand the max capacity.
/// - it assumes Z80 bus wasn't requested and hence request it.
static FORCE_INLINE void DMA_flushQueue_fast ()
{
	#if VIDEO_PLAYER_DEBIG_MODE
	if (DMA_getQueueTransferSize() > DMA_getMaxTransferSize())
		KLog("[VIDEOPLAYER] WARNING: DMA transfer size limit raised. Modify the capacity in your DMA_initEx() call.");
	#endif

    // u8 autoInc = VDP_getAutoInc(); // save autoInc
	// Z80_requestBus(FALSE);

    DMA_ELEMS_flush();

	// Z80_releaseBus();
    // VDP_setAutoInc(autoInc); // restore autoInc
}

static FORCE_INLINE void waitVInt ()
{
	// Casting to u8* allows to use cmp.b instead of cmp.l, by using vtimerPtr+3 which is the first byte of vtimer
    const u8* vtimerPtr = (u8*)&vtimer + 3;
    // Loops while vtimer keeps unchanged. Exits loop when it changes, meaning we are in VBlank.
    u8 currVal;
	__asm volatile (
        "move.b  (%1), %0\n\t"
        "1:\n\t"
        "cmp.b   (%1), %0\n\t" // cmp: %0 - (%1) => dN - (aN)
        "beq.s   1b"           // loop back if equal
        : "=d" (currVal)
        : "a" (vtimerPtr)
        : "cc"
    );

	// AT THIS POINT THE _VInt INTERRUPT CALLBACK HAS BEEN CALLED. Check sega.s to see when vtimer is updated and what other callbacks are called.
}

static FORCE_INLINE void waitVInt_AND_flushDMA ()
{
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

    // if (IS_PAL_SYSTEM)
    //     waitVCounterReg(MOVIE_HINT_COLORS_SWAP_END_SCANLINE_PAL + 2*HINT_COUNTER_FOR_COLORS_UPDATE);
    // else
    //     waitVCounterReg(MOVIE_HINT_COLORS_SWAP_END_SCANLINE_NTSC + 2*HINT_COUNTER_FOR_COLORS_UPDATE);

	waitVInt();

	// AT THIS POINT THE _VInt INTERRUPT CALLBACK HAS BEEN CALLED. Check sega.s to see when vtimer is updated and what other callbacks are called.

    turnOffVDP(0x74);

	Z80_setBusProtection_fast(TRUE);
	waitSubTick_(10); // Z80 delay --> wait a bit (10 ticks) to improve PCM playback (test on SOR2)

	DMA_flushQueue_fast();

	Z80_setBusProtection_fast(FALSE);

	turnOnVDP(0x74);

    // TODO: update joy here like in raycasting project
}

static void loadTilesCache ()
{
	if (tilesCache_movie1.numTile == 0)
        return;

    #if VIDEO_PLAYER_DEBUG_TILES_CACHE
    //KLog_U1("tilesCache_movie1.numTile ", tilesCache_movie1.numTile);
    // In order to print the cache tiles values you need to set compression to NONE in the res file
    // u32* dptr = tilesCache_movie1.tiles;
    // for (u16 i=0; i < tilesCache_movie1.numTile; ++i) {
    // 	kprintf("%d,%d,%d,%d,%d,%d,%d,%d", *(dptr+0),*(dptr+1),*(dptr+2),*(dptr+3),*(dptr+4),*(dptr+5),*(dptr+6),*(dptr+7));
    // 	dptr += 8;
    // }

    PAL_setPalette(PAL0, palette_grey, CPU);
    // Fill all VRAM targeted for cached tiles with 0x66, which points to the 7th color for whatever palette is loaded in CRAM.
    // This way we can see in the VRAM Debugger the are occupied by the cached tiles.
    // for (u16 i=0; i < MOVIE_TILES_CACHE_RANGES_NUM; ++i) {
    //     RangeFixedVRAM range = cacheRangesInVRAM_fixed[i];
    //     VDP_fillTileData(0x66, range.start, range.length, TRUE);
    // }
    // VDP_fillTileData(0x66, MOVIE_TILES_CACHE_START_INDEX_VAR, MOVIE_TILES_CACHE_TILES_NUM_VAR, TRUE);
    #endif

    TileSet* t = unpackTileSet((TileSet* const) &tilesCache_movie1, NULL);
    u16 tilesPlacedAccumCounter = 0;
    // First, place tiles at fixed VRAM location
    u32* cacheTiles_ptr = t->tiles;
    // Iterate over all the fixed ranges
    for (u16 i=0; i < MOVIE_TILES_CACHE_RANGES_NUM; ++i) {
        // Make sure we don't exceed the total tiles (just in case)
        if (tilesPlacedAccumCounter < tilesCache_movie1.numTile) {
            RangeFixedVRAM range = cacheRangesInVRAM_fixed[i];
            VDP_loadTileData(cacheTiles_ptr, range.start, range.length, DMA); // DMA or DMA_QUEUE_COPY
            tilesPlacedAccumCounter += range.length;
            cacheTiles_ptr += 8*range.length;
        }
    }

    // Finally place remaining tiles at variable VRAM location
    if (tilesCache_movie1.numTile > tilesPlacedAccumCounter) {
        VDP_loadTileData(cacheTiles_ptr, MOVIE_TILES_CACHE_START_INDEX_VAR, MOVIE_TILES_CACHE_TILES_NUM_VAR, DMA); // DMA or DMA_QUEUE_COPY
    }
    MEM_free(t);

    #if VIDEO_PLAYER_DEBUG_TILES_CACHE
    // Set palette buffer with white color 0xEEE only for strips using PAL0
    u16* rendPtr = unpackedPalsRender;
    for (u16 i=0; i < MOVIE_FRAME_STRIPS; ++i) {
        memsetU16(rendPtr + 1, 0xEEE, 15);
        rendPtr += MOVIE_FRAME_COLORS_PER_STRIP;
    }
    //while (1) waitVInt();
    #endif
}

static void allocateTilesetBuffer ()
{
	unpackedTilesetChunk = (u32*) MEM_alloc(VIDEO_FRAME_TILESET_CHUNK_SIZE * 32);
	memsetU16((u16*) unpackedTilesetChunk, 0x0, (VIDEO_FRAME_TILESET_CHUNK_SIZE * 32) /2); // zero all the buffer
}

static FORCE_INLINE void unpackFrameTileset (TileSet* src)
{
    // We need to check due to empty tilesets
	if (src->numTile == 0)
		return;
	const u16 size = src->numTile * 32;
	if (src->compression != COMPRESSION_NONE)
		lz4w_unpack((u8*) FAR_SAFE(src->tiles, size), (u8*) unpackedTilesetChunk);
	else
        memcpy_asm(size, FAR_SAFE(src->tiles, size), (u8*) unpackedTilesetChunk);
}

static void freeTilesetBuffer ()
{
	MEM_free((void*) unpackedTilesetChunk);
}

static void allocateTilemapBuffer ()
{
	u16 lenInWords = MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES * MOVIE_FRAME_HEIGHT_IN_TILES;
    u16* unpackedTilemap = (u16*) RAM_FIXED_MOVIE_FRAME_UNPACKED_TILEMAP_ADDRESS;
	memsetU16(unpackedTilemap, TILE_SYSTEM_INDEX, lenInWords); // set TILE_SYSTEM_INDEX (black tile) all over the buffer
}

static FORCE_INLINE void unpackFrameTilemap (TileMapCustomCompField* src)
{
    u16* unpackedTilemap = (u16*) RAM_FIXED_MOVIE_FRAME_UNPACKED_TILEMAP_ADDRESS;
    const u16 size = MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES * MOVIE_FRAME_HEIGHT_IN_TILES * 2; // bytes

#if ALL_TILEMAPS_COMPRESSED
	// NOTE: if using LZ4W enable next lines
	lz4w_unpack((u8*) FAR_SAFE(src->tilemap, size), (u8*) unpackedTilemap);
	// NOTE: if using RLEW enable next lines
    // #if (MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES == MOVIE_FRAME_WIDTH_IN_TILES)
    // const u8 jumpGap = 0;
    // #else
    // const u8 jumpGap = 2 * (MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES - MOVIE_FRAME_WIDTH_IN_TILES);
    // #endif
    // rlew_decomp_A_asm(jumpGap, (u8*) FAR_SAFE(src->tilemap, size), (u8*) unpackedTilemap);
#elif ALL_TILEMAPS_UNCOMPRESSED
    #if (MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES == MOVIE_FRAME_WIDTH_IN_TILES)
    // if NOT using extended width in the data generated by rescomp we do the copy in one go
    memcpy_tilemap_asm(FAR_SAFE(src->tilemap, size), (u8*) unpackedTilemap);
    #else
    // if using extended width in the data generated by rescomp then we can skip the extended data
    memcpy_tilemap_extended_asm(FAR_SAFE(src->tilemap, size), (u8*) unpackedTilemap);
    #endif
#else
	if (src->compression != COMPRESSION_NONE) {
		// NOTE: if using LZ4W enable next lines
		lz4w_unpack((u8*) FAR_SAFE(src->tilemap, size), (u8*) unpackedTilemap);
		// NOTE: if using RLEW enable next lines
        // #if (MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES == MOVIE_FRAME_WIDTH_IN_TILES)
		// const u8 jumpGap = 0;
        // #else
        // const u8 jumpGap = 2 * (MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES - MOVIE_FRAME_WIDTH_IN_TILES);
        // #endif
		// rlew_decomp_A_asm(jumpGap, (u8*) FAR_SAFE(src->tilemap, size), (u8*) unpackedTilemap);
	}
	else {
        #if (MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES == MOVIE_FRAME_WIDTH_IN_TILES)
		// if NOT using extended width in the data generated by rescomp we do the copy in one go
        memcpy_tilemap_asm(FAR_SAFE(src->tilemap, size), (u8*) unpackedTilemap);
        #else
		// if using extended width in the data generated by rescomp then we can skip the extended data
        memcpy_tilemap_extended_asm(FAR_SAFE(src->tilemap, size), (u8*) unpackedTilemap);
        #endif
	}
#endif
}

static void freeTilemapBuffer ()
{
    u16 lenInWords = MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES * MOVIE_FRAME_HEIGHT_IN_TILES;
    u16* unpackedTilemap = (u16*) RAM_FIXED_MOVIE_FRAME_UNPACKED_TILEMAP_ADDRESS;
    memsetU16(unpackedTilemap, 0, lenInWords); // set 0 all over the buffer
}

static void allocatePalettesBuffer ()
{
	unpackedPalsRender = (u16*) MEM_alloc(VIDEO_FRAME_PALS_NUM * 2);
	unpackedPalsBuffer = (u16*) MEM_alloc(VIDEO_FRAME_PALS_NUM * 2);
	memsetU16(unpackedPalsRender, 0x0, VIDEO_FRAME_PALS_NUM); // black all the buffer
	memsetU16(unpackedPalsBuffer, 0x0, VIDEO_FRAME_PALS_NUM); // black all the buffer
}

static FORCE_INLINE void unpackFramePalettes (u16* data, u16 len, u16 offset)
{
#if ALL_PALETTES_COMPRESSED
	// No need to use FAR_SAFE() macro here because palette data is always stored at NEAR region
	lz4w_unpack((u8*)data, (u8*) (unpackedPalsBuffer + offset));
#else
	// Copy the palette data. No FAR_SAFE() needed here because palette data is always stored at NEAR region.
	const u16 size = len * 2;
	//memcpy((u8*) (unpackedPalsBuffer + offset), data, size);
    memcpy_asm(size, (u8*) (unpackedPalsBuffer + offset), data);
#endif
}

static FORCE_INLINE void swapPalsBuffers ()
{
	u16* tmp = unpackedPalsRender;
	unpackedPalsRender = unpackedPalsBuffer;
	unpackedPalsBuffer = tmp;
}

static void freePalettesBuffer ()
{
    MEM_free((void*) unpackedPalsRender);
	MEM_free((void*) unpackedPalsBuffer);
}

static FORCE_INLINE void queueTilesetData (u16 startTileIndex, u16 length)
{
    // We need to check due to empty tilesets
	if (length == 0)
		return;
	// This was the previous one
	// VDP_loadTileData(unpackedTilesetChunk, startTileIndex, length, DMA_QUEUE);
	// Now we use custom DMA_queueDmaFast() because the data is in RAM, so no 128KB bank boundary check is needed
    const u16 lenInWords = length * 16;
	DMA_ELEMS_queue((u32) unpackedTilesetChunk, startTileIndex * 32, lenInWords, VIDEO_PLAYER_DMA_ELEM_TYPE_TILESET);
}

static FORCE_INLINE void queueTilemapData (u16 tilemapAddrInPlane)
{
	const u16 lenInWords = MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES * MOVIE_FRAME_HEIGHT_IN_TILES;
	// This was the previous one, which benefits from tilemap width being 64 tiles
	// VDP_setTileMapData(tilemapAddrInPlane, unpackedTilemap, 0 & TILE_INDEX_MASK, lenInWords, 2, DMA_QUEUE);
	// Now we use custom DMA_queueDmaFast() because the data is in RAM, so no 128KB bank boundary check is needed
	DMA_ELEMS_queue(RAM_FIXED_MOVIE_FRAME_UNPACKED_TILEMAP_ADDRESS, tilemapAddrInPlane + ((0 & TILE_INDEX_MASK) * 2), lenInWords, VIDEO_PLAYER_DMA_ELEM_TYPE_TILEMAP);
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

static bool checkCorrectPlaneAddress ()
{
	u16 xp = (screenWidth/2 - (MOVIE_FRAME_WIDTH_IN_TILES/2)*8 + 7)/8;
	u16 yp = (screenHeight/2 - (MOVIE_FRAME_HEIGHT_IN_TILES/2)*8 + 7)/8;
	yp = max(yp, MOVIE_MIN_TILE_Y_POS_AVOID_DMA_FLICKER); // offsets the Y axis plane position to avoid the flickering due to DMA transfer leaking into active display area
	u16 tilemapAddrInPlane = VDP_getPlaneAddress(BG_B, xp, yp);
	return tilemapAddrInPlane == VIDEO_FRAME_PLANE_ADDRESS;
}

static void fadeToBlack ()
{
	// Last frame's palettes is still pointed by unpackedPalsRender

	// Always multiple of FADE_TO_BLACK_STEPS since a uniform fade to black effect lasts FADE_TO_BLACK_STEPS steps.
	s16 loopFrames = FADE_TO_BLACK_STEPS * FADE_TO_BLACK_STEP_FREQ;

	while (loopFrames >= 0) {
		if ((loopFrames-- % FADE_TO_BLACK_STEP_FREQ) == 0) {
			u16* pals_ptr = unpackedPalsRender;
			for (u16 i=MOVIE_FRAME_STRIPS * MOVIE_FRAME_COLORS_PER_STRIP; i--;) {
                u16 d = *pals_ptr - 0x222; // decrement 1 unit on every component
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
                *pals_ptr++ = d;
            }
		}
		// TODO PALS_1: uncomment when unpacking/load happens in the current active display loop
		//waitVInt_AND_flushDMA();
		waitVInt();
	}
}

static void unloadSoundDriver ()
{
    Z80_unloadDriver();
}

static void loadSoundDriver ()
{
	// PCM_loadDriver(TRUE);
	// XGM_loadDriver(TRUE);
    XGM2_loadDriver(TRUE);
}

static void playSound ()
{
    // SND_PCM_startPlay(sound_wav, sizeof(sound_wav), SOUND_PCM_RATE_22050, SOUND_PAN_CENTER, FALSE);
    // XGM_setPCM(1, sound_wav, sizeof(sound_wav));
    // XGM_startPlayPCM(1, 1, SOUND_PCM_CH_AUTO);
    // XGM_setLoopNumber(0);
    XGM2_playPCMEx(sound_wav, sizeof(sound_wav), SOUND_PCM_CH_AUTO, 1, FALSE, FALSE);
}

static void stopSound ()
{
    // SND_PCM_stopPlay();
    // XGM_stopPlayPCM(SOUND_PCM_CH1);
    XGM2_stopPCM(SOUND_PCM_CH1);
}

void playMovie ()
{
	// Blacks out everything in screen while first frame is being loaded
	PAL_setColors(0, palette_black, 64, CPU);

	// Only Plane size 64x32 due to internal VRAM setup made by SGDK and the use of fixed tilemap width of 64 tiles by the custom rescomp_ext plugin.
    VDP_setPlaneSize(VIDEO_PLANE_COLUMNS, 32, TRUE);

    // If we need more free RAM then:
    // DMA_initEx(DMA_QUEUE_SIZE_MIN, -1, DMA_BUFFER_SIZE_MIN);
	// MEM_free(dmaQueues); // free upto DMA_QUEUE_SIZE_MIN * sizeof(DMAOpInfo) bytes
	// MEM_pack();

	// If we want to use up to 1791 tiles (remember we keep the reserved tile at address 0) then we need to move BG_B 
	// and Window planes into BG_A so we can use the space otherwise used by BG_B by default. So:
	// - 0xE000 is where BG_A plane (its tilemap) address starts.
	// - Move BG_B and Window planes addresses into BG_A plane address, achieving up to 1791 tiles.
    // - Move the SAT (Sprite Allocation Table) where the HScroll table is located: 0xF000. This allows us to setup next thing.
    // - Now SAT and HScroll table are at 0xF000 and it seems only first 32 bytes (0x20) have an effect in the image, 
    //   hence we have additional free VRAM from 0xF020 to 0xFFFF -> 4064 bytes = 127 tiles.
    VDP_setBGBAddress(VDP_getBGAAddress());
    VDP_setWindowAddress(VDP_getBGAAddress());
    VDP_setWindowOff();
    VDP_setSpriteListAddress(VDP_getHScrollTableAddress());

    // Ensure the constant used for the target Plane is correct. Do it here after all the plane setup
    if (checkCorrectPlaneAddress() == FALSE) {
        return;
    }

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

	// Load the appropriate driver
    loadSoundDriver();

    // 32422 bytes (39848 bytes if SGDK's DMA buffers are cleared)
	// KLog_U1("Free Mem: ", MEM_getFree() - (MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES * MOVIE_FRAME_HEIGHT_IN_TILES * 2));

    // Loop the entire video
	for (;;) // Better than while (TRUE) for infinite loops
    {
		#if VIDEO_FRAME_ADVANCE_STRATEGY == 1
		u16 sysFrameRate = IS_PAL_SYSTEM ? 50 : 60;
		#elif VIDEO_FRAME_ADVANCE_STRATEGY == 2
		u16 sysFrameRateReciprocal = IS_PAL_SYSTEM ? MOVIE_FRAME_RATE * 0x051E : MOVIE_FRAME_RATE * 0x0444;
		#endif

		// Let the HInt use the right pals before setting the VInt and HInt callbacks, otherwise it glitches out by one frame
		setMoviePalsPointerBeforeInterrupts(unpackedPalsRender); // Palettes are all black at this point

        // Start sound
        playSound();

        // Wait for VInt so the logic can start at the beginning of the active display period
		waitVInt();

		SYS_disableInts();

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

        #if VIDEO_PLAYER_DEBUG_FIXED_VFRAME
        u16 vFrame = VIDEO_PLAYER_DEBUG_FIXED_VFRAME;
        vtimer = ((IS_PAL_SYSTEM ? 50 : 60) / MOVIE_FRAME_RATE) * VIDEO_PLAYER_DEBUG_FIXED_VFRAME;
        #else
        u16 vFrame = 0;
        vtimer = 0; // reset vtimer so we can use it as our hardware frame counter
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
			queueTilesetData(baseTileIndex, numTile1);

            waitVInt_AND_flushDMA();

			unpackFrameTileset(data[vFrame]->tileset2);
			u16 numTile2 = data[vFrame]->tileset2->numTile;
			queueTilesetData(numTile1 + baseTileIndex, numTile2);

            waitVInt_AND_flushDMA();

			unpackFrameTileset(data[vFrame]->tileset3);
			u16 numTile3 = data[vFrame]->tileset3->numTile;
			queueTilesetData(numTile1 + numTile2 + baseTileIndex, numTile3);

            waitVInt_AND_flushDMA();

			// Toggles between TILE_USER_INDEX_CUSTOM (initial mandatory value) and TILE_USER_INDEX_CUSTOM + VIDEO_FRAME_TILESET_TOTAL_SIZE
			baseTileIndex ^= (TILE_USER_INDEX_CUSTOM ^ (TILE_USER_INDEX_CUSTOM + VIDEO_FRAME_TILESET_TOTAL_SIZE)); // a ^= (x1 ^ x2)

			unpackFramePalettes(pals_data[vFrame]->data, VIDEO_FRAME_PALS_NUM, 0);

			swapPalsBuffers();

			unpackFrameTilemap(data[vFrame]->tilemap1);

			queueTilemapData(VIDEO_FRAME_PLANE_ADDRESS);

			// NOTE: first 2 strips' palettes (previously unpacked) will be enqueued in waitVInt_AND_flushDMA()
			// NOTE2: not true until TODO PALS_1 is done

			#if VIDEO_PLAYER_DEBUG_FIXED_VFRAME == FALSE
			u16 prevFrame = vFrame;
			#endif
			u16 hwFrameCntr = (u16)vtimer;
			#if VIDEO_FRAME_ADVANCE_STRATEGY == 1 /* Takes 202~235 cycles with a peak of 657 (?). HInt disabled. */
			vFrame = divu(hwFrameCntr * MOVIE_FRAME_RATE, sysFrameRate);
			#elif VIDEO_FRAME_ADVANCE_STRATEGY == 2 /* Takes 370~406 cycles with a peak of 865 (?). HInt disabled. */
			vFrame = (hwFrameCntr * sysFrameRateReciprocal) >> 16;
			#elif VIDEO_FRAME_ADVANCE_STRATEGY == 3 /* Takes 204~235 cycles with a peak of 687 (?). HInt disabled. */
			u16 deltaFrames = IS_PAL_SYSTEM ? divu(hwFrameCntr, 50/MOVIE_FRAME_RATE) : divu(hwFrameCntr, 60/MOVIE_FRAME_RATE);
			vFrame += deltaFrames - vFrame;
			#elif VIDEO_FRAME_ADVANCE_STRATEGY == 4 /* Takes 71~93 cycles with a peak of 534 (?). HInt disabled. */
			vFrame = framerateDivLUT[hwFrameCntr];
			#endif

			setMoviePalsPointer(unpackedPalsRender);

			waitVInt_AND_flushDMA();

			#if VIDEO_PLAYER_DEBUG_FIXED_VFRAME
			vFrame = VIDEO_PLAYER_DEBUG_FIXED_VFRAME;
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

			#if VIDEO_PLAYER_DEBUG_LOG_DIFF_BETWEEN_VIDEO_FRAMES
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
			// Stop sound only if we are more than N frames (duration of the fade effect) before the last frame
			if (vFrame >= (MOVIE_FRAME_COUNT - (FADE_TO_BLACK_STEPS * FADE_TO_BLACK_STEP_FREQ))) {
                stopSound();
			}
			// Fading effect
			fadeToBlack();
		}

		SYS_disableInts();
			SYS_setVIntCallback(NULL);
			VDP_setHInterrupt(FALSE);
			SYS_setHIntCallback(NULL);
		SYS_enableInts();

        // Stop sound
        stopSound();

		// Stop the video?
		if (exitPlayer) {
			break;
		}
		// Loop the video
        else {
			// Clears all tilemap VRAM region for BG_A
			VDP_clearTileMap(VDP_BG_B, 0, VIDEO_PLANE_COLUMNS * 32, TRUE); // See VDP_clearPlane() for details
			waitMs_(1000);
		}
    }

	freeTilesetBuffer();
	freeTilemapBuffer();
	freePalettesBuffer();
	#if VIDEO_FRAME_ADVANCE_STRATEGY == 4
	MEM_free(framerateDivLUT);
	#endif
    
    unloadSoundDriver();
}