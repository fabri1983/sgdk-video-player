#ifndef _MOVIE_PLAYER_H
#define _MOVIE_PLAYER_H

#include "generated/movie_data_consts.h"
#include "utils.h"

// #define DEBUG_VIDEO_PLAYER
// #define DEBUG_TILES_CACHE
// #define DEBUG_FIXED_VFRAME 160 // Always use an even frame number due to the map base tile index statically set on each frame by our custom rescomp extension
// #define LOG_DIFF_BETWEEN_VIDEO_FRAMES

/// If TRUE then it will add Joy plling logic and user can exit the video playback at any time. FALSE otherwise.
/// The JOY polling logic is a bit heavy and it adds some overhead. So test it.
#define EXIT_PLAYER_WITH_JOY_START FALSE

/// If TRUE then it will display every video frame. Might cause audio desync if video frames takes more than 60/MOVIE_FRAME_RATE for NTSC or 50/MOVIE_FRAME_RATE for PAL.
#define FORCE_NO_MISSING_FRAMES FALSE

// 1: Use normal division formula when calculating current frame.
// 2: Use reciprocal magic numbers approximation for 1/50 and 1/60.
// 3: Use the delta between vtimer (system's internal frame counter) and current video frame.
// 4: Use a LUT instead of using division. Based on IMPL 1.
#define VIDEO_FRAME_ADVANCE_STRATEGY 4

// Enables HInt callback implementation using DMA (TRUE) or pure CPU (FALSE).
// Using DMA adds some pressure to the Z80 due to bus contention, or something like that. But I didn't test it in real hardware.
#define HINT_USE_DMA FALSE

/// If you are 100% sure ALL the tilemaps were compressed by rescomp tool (see console output) then set it TRUE
#define ALL_TILEMAPS_COMPRESSED FALSE
/// If you are 100% sure ALL the tilemaps were NOT compressed by rescomp tool (see console output) then set it TRUE
#define ALL_TILEMAPS_UNCOMPRESSED FALSE

/// If you are 100% sure ALL the palettes were compressed by rescomp tool (see console output) then set it TRUE
#define ALL_PALETTES_COMPRESSED TRUE

#define VIDEO_FRAME_PALS_NUM (MOVIE_FRAME_STRIPS * MOVIE_FRAME_COLORS_PER_STRIP)
#define VIDEO_FRAME_PALS_CHUNK_SIZE (VIDEO_FRAME_PALS_NUM / 3)
#define VIDEO_FRAME_PALS_CHUNK_SIZE_LAST ((VIDEO_FRAME_PALS_NUM / 3) + (VIDEO_FRAME_PALS_NUM % 3))

/// NOT USED ANYMORE! We now have splitted every frame's tileset in 3 chunks and using VIDEO_FRAME_TILESET_CHUNK_SIZE instead.
/// LEGACY.
/// Number of Tiles to be transferred by DMA_flushQueue() with mandatory off/on VDP setting to speed up the transfer (otherwise it glitches up).
/// NOTE: this has to be enough to support VIDEO_FRAME_TILESET_TOTAL_SIZE / 3 which is the buffer size that holds the unpack of half a tileset.
/// 320 tiles * 32 bytes = 10240 as maxTransferPerFrame. 
/// 368 tiles * 32 bytes = 11776 as maxTransferPerFrame. 
/// 384 tiles * 32 bytes = 12282 as maxTransferPerFrame. 
/// Disabling VDP before DMA_flushQueue() helps to effectively increase the max transfer limit.
/// If number is bigger then you will notice some flickering on top of image meaning the transfer takes more time than Vertical retrace.
/// The flickering still exists but is not noticeable due to lower image Y position in plane. 
/// Using bigger image height or locating it at upper Y values will reveal the flickering.
#define TILES_PER_DMA_TRANSFER 368

#define FADE_TO_BLACK_STEPS 7 // How many steps needs to be applied as much to reach black color. Max is 7.
#define FADE_TO_BLACK_STEP_FREQ 4 // Every N frames we apply one fade to black step.

void playMovie ();

#endif // _MOVIE_PLAYER_H