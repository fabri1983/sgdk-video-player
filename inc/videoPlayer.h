#ifndef _MOVIE_PLAYER_H
#define _MOVIE_PLAYER_H

// #define DEBUG_VIDEO_PLAYER
// #define DEBUG_FIXED_FRAME 196 // Always use an even frame number due to the static map base tile index statically set on each frame by our custom rescomp extension
// #define LOG_DIFF_BETWEEN_VIDEO_FRAMES

#define VIDEO_FRAME_RATE (15-1) // Minus 1 so it delays enough to be in sync with audio. IT'S A TEMPORARY HACK BUT WORKS FLAWLESSLY!
#define EXIT_PLAYER_WITH_JOY_START FALSE
#define FORCE_NO_MISSING_FRAMES FALSE
// IMPL 0: Use normal division formula when calculating current frame
// IMPL 1: Use reciprocal magic numbers approximation for 1/50 and 1/60
// IMPL 2: Use the delta between vtimer (system's internal frame counter) and current video frame
#define VIDEO_FRAME_ADVANCE_STRATEGY 0

#define VIDEO_FRAME_MAX_TILESET_NUM 716 // this value got experimentally from rescomp output (biggest resulting numTile1 + numTile2). It has to be an even number.
#define VIDEO_FRAME_MAX_TILESET_CHUNK_NUM 370 // this value got experimentally from rescomp output (biggest numTile from one of two halves). It has to be an even number.
#define VIDEO_FRAME_MAX_TILEMAP_NUM MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES * MOVIE_FRAME_HEIGHT_IN_TILES
#define VIDEO_FRAME_MAX_TILEMAP_NUM_HALF_1 MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES * (MOVIE_FRAME_HEIGHT_IN_TILES/2)
#define VIDEO_FRAME_MAX_TILEMAP_NUM_HALF_2 MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES * ((MOVIE_FRAME_HEIGHT_IN_TILES/2) + (MOVIE_FRAME_HEIGHT_IN_TILES - 2*(MOVIE_FRAME_HEIGHT_IN_TILES/2)))

#define HINT_USE_DMA FALSE // TRUE: DMA. FALSE: CPU

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

#define FADE_TO_BLACK_STEP_FREQ 4 // Every N frames we apply one fade to black step

#define STRINGIFY(x) #x
#define STOPWATCH_START(n) \
			u16 lineStart_##n = GET_VCOUNTER;
#define STOPWATCH_STOP(n) \
			u16 lineEnd_##n = GET_VCOUNTER;\
			u16 frameTime_##n;\
			if (lineEnd_##n < lineStart_##n) {\
				frameTime_##n = 261 - lineStart_##n;\
				frameTime_##n += lineEnd_##n;\
			} else {\
				frameTime_##n = lineEnd_##n - lineStart_##n;\
			}\
			{\
				char str[] = "frameTime_"STRINGIFY(n)"     ";\
				*(str + 14) = '0' + (frameTime_##n / 100);\
				*(str + 15) = '0' + ((frameTime_##n / 10) % 10);\
				*(str + 16) = '0' + (frameTime_##n % 10);\
				*(str + 17) = '\0';\
				KLog(str);\
			}\

void playMovie ();

#endif