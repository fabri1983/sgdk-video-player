#ifndef _MOVIE_PLAYER_H
#define _MOVIE_PLAYER_H

#include "generated/movie_data_consts.h"
#include "compatibilities.h"

// #define DEBUG_VIDEO_PLAYER
// #define DEBUG_TILES_CACHE
// #define DEBUG_FIXED_FRAME 196 // Always use an even frame number due to the static map base tile index statically set on each frame by our custom rescomp extension
// #define LOG_DIFF_BETWEEN_VIDEO_FRAMES

/// If TRUE then it will add Joy plling logic and user can exit the video playback at any time. FALSE otherwise.
/// The JOY polling logic is a bit heavy and it adds some overhead. So test it.
#define EXIT_PLAYER_WITH_JOY_START FALSE

/// If TRUE then it will display every video frame. Might cause audio desync if video frames takes more than 60/MOVIE_FRAME_RATE for NTSC or 50/MOVIE_FRAME_RATE for PAL.
#define FORCE_NO_MISSING_FRAMES FALSE

// IMPL 0: Use normal division formula when calculating current frame
// IMPL 1: Use reciprocal magic numbers approximation for 1/50 and 1/60
// IMPL 2: Use the delta between vtimer (system's internal frame counter) and current video frame
#define VIDEO_FRAME_ADVANCE_STRATEGY 0

// Enables HInt callbakc implementatio using DMA (TRUE) or pure CPU (FALSE)
#define HINT_USE_DMA TRUE

/// If you are 100% sure ALL the tilemaps were compressed by rescomp tool (by looking at the console output) then set this to TRUE.
/// If you are 100% sure ALL the tilemaps were NOT compressed by rescomp tool (by looking at the console output) then set this to FALSE.
#define ALL_TILEMAPS_COMPRESSED TRUE

/// If you are 100% sure ALL the palettes were compressed by rescomp tool (by looking at the console output) then set this to TRUE.
/// If you are 100% sure ALL the palettes were NOT compressed by rescomp tool (by looking at the console output) then set this to FALSE.
#define ALL_PALETTES_COMPRESSED TRUE

/// SGDK reserves 16 tiles starting at address 0. That's the purpose of using SGDK's TILE_USER_INDEX so you don't its tiles.
/// Tile address 0 holds a black tile and it shouldn't be overriden since is what an empty tilemap in VRAM points to. Also other internal effects use it.
/// Remaining 15 tiles are OK to override for re use. So we can start using tiles at index 1.
#define TILE_USER_INDEX_CUSTOM 1

#define VIDEO_FRAME_TILESET_CHUNK_SIZE 272 // Got experimentally from rescomp output (biggest summation of numTile). If odd then use next even number.
#define VIDEO_FRAME_TILESET_TOTAL_SIZE 716 // Got experimentally from rescomp output (biggest operand from numTile1 + numTile2 + ...). If odd then use next even number.
#define VIDEO_FRAME_TILEMAP_NUM (MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES * MOVIE_FRAME_HEIGHT_IN_TILES)
#define VIDEO_FRAME_TILEMAP_NUM_CHUNK (MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES * (MOVIE_FRAME_HEIGHT_IN_TILES / 1))
#define VIDEO_FRAME_TILEMAP_NUM_CHUNK_LAST (MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES * ((MOVIE_FRAME_HEIGHT_IN_TILES / 1) + (MOVIE_FRAME_HEIGHT_IN_TILES % 1)))
#define VIDEO_FRAME_PALS_TOTAL_SIZE (MOVIE_FRAME_STRIPS * MOVIE_FRAME_COLORS_PER_STRIP)
#define VIDEO_FRAME_PALS_CHUNK_SIZE (VIDEO_FRAME_PALS_TOTAL_SIZE / 3)
#define VIDEO_FRAME_PALS_CHUNK_SIZE_LAST ((VIDEO_FRAME_PALS_TOTAL_SIZE / 3) + (VIDEO_FRAME_PALS_TOTAL_SIZE % 3))

/// Number of Tiles to be transferred by DMA_flushQueue() with mandatory off/on VDP setting to speed up the transfer (otherwise it glitches up).
/// NOTE: this has to be enough to support VIDEO_FRAME_TILESET_TOTAL_SIZE / 3 which is the buffer size that holds the unpack of half a tileset.
/// 320 tiles * 32 bytes = 10240 as maxTransferPerFrame. 
/// 368 tiles * 32 bytes = 11776 as maxTransferPerFrame. 
/// 384 tiles * 32 bytes = 12282 as maxTransferPerFrame. 
/// Disabling VDP before DMA_flushQueue() helps to effectively increase the max transfer limit.
/// If number is bigger then you will notice some flickering on top of image meaning the transfer takes more time than Vertical retrace.
/// The flickering still exists but is not noticeable due to lower image Y position in plane. 
/// Using bigger image height or locating it at upper Y values will reveal the flickering.
#define TILES_PER_DMA_TRANSFER 368 // NOT USED ANYMORE since we now have splitted every frame's tileset in 3 chunks and using VIDEO_FRAME_TILESET_CHUNK_SIZE instead.

#define FADE_TO_BLACK_STEP_FREQ 4 // Every N frames we apply one fade to black step

#define STRINGIFY(x) #x
/// Healthy consumption time measured in scanlines is up to 260, otherwise code will be interrupted by hardware VInt and HInt too. 
/// HOWEVER VInt is executed at scanline 224 according to Shannon Birt. So better keep the limit to 223.
#define STOPWATCH_START(n) \
	u16 lineStart_##n = GET_VCOUNTER;
#define STOPWATCH_STOP(n) \
	u16 lineEnd_##n = GET_VCOUNTER;\
	u16 frameTime_##n;\
	char str_##n[] = "ft__"STRINGIFY(n)"     ";\
	if (lineEnd_##n < lineStart_##n) {\
		frameTime_##n = 261 - lineStart_##n;\
		frameTime_##n += lineEnd_##n;\
		/* Add a w to know this measure has wrapped around a display loop */ \
		*(str_##n + 2) = 'w';\
	} else {\
		frameTime_##n = lineEnd_##n - lineStart_##n;\
	}\
	{\
		*(str_##n + 8) = '0' + (frameTime_##n / 100);\
		*(str_##n + 9) = '0' + ((frameTime_##n / 10) % 10);\
		*(str_##n + 10) = '0' + (frameTime_##n % 10);\
		*(str_##n + 11) = '\0';\
		KLog(str_##n);\
	}\

void playMovie ();

#endif