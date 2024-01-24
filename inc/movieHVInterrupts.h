#ifndef MOVIE_HV_INTERRUPTS_H
#define MOVIE_HV_INTERRUPTS_H

#include <types.h>
#include <sys.h>
#include "generated/movie_data_consts.h"
#include "compatibilities.h"

#define HINT_PALS_CMD_ADDRR_RESET_VALUE 0 // 0 if HInt starts swap for [PAL0,PAL1]. MOVIE_DATA_COLORS_PER_STRIP if HInt starts swap for [PAL2,PAL3].

#define HINT_COUNTER_FOR_COLORS_UPDATE 8 // add -1 when set on VDP_setHIntCounter()

#define MOVIE_MIN_TILE_Y_POS_AVOID_DMA_FLICKER 3 // 0 based (starts at the top of the screen)

// For NTSC systems: ONLY VALID IF STRIP HEIGHT IS ALWAYS 8
//   Screen has 224/8=28 tiles height, and the video is 22 tiles height centered in Y axis.
//   So starts at (28-22)/2=3rd tile row. But we start loading pals 1 strip earlier. So -1 strip => starts at 2th row of tiles => 2*8=16th scanline.
//   And ends at 28-3=25th tile row: 25*8=200th scanline. -2*8 since we load the last palette one additional strip before the last strip.

// Since we have a min Y tile position to start drawing we have to adjust with the ideal scenario calculation
#define TILES_HEIGHT_OFFSET_DUE_TO_MIN_TILE_Y_POS_NTSC \
    max(0, MOVIE_MIN_TILE_Y_POS_AVOID_DMA_FLICKER - ((28 - MOVIE_FRAME_STRIPS) / 2))
// Frame with 22 tiles height: 16-1=15
#define MOVIE_HINT_COLORS_SWAP_START_SCANLINE_NTSC \
    (((28 - MOVIE_FRAME_STRIPS) / 2) + TILES_HEIGHT_OFFSET_DUE_TO_MIN_TILE_Y_POS_NTSC -1) * 8 - 1
// Frame with 22 tiles height: 200-2*8-1=183
#define MOVIE_HINT_COLORS_SWAP_END_SCANLINE_NTSC \
    (28 - ((28 - MOVIE_FRAME_STRIPS) / 2) + TILES_HEIGHT_OFFSET_DUE_TO_MIN_TILE_Y_POS_NTSC) * 8 - 2*8 - 1

// For PAL systems: ONLY VALID IF STRIP HEIGHT IS ALWAYS 8
//   Screen has 240/8=30 tiles height, and the video is 22 tiles height centered in Y axis.
//   So starts at (30-22)/2=4th tile row. But we start loading pals 1 strip earlier. So -1 strip => starts at 3th row of tiles => 3*8=24th scanline.
//   And ends at 30-4=26th tile row: 26*8=208th scanline. -2*8 since we load the last palette one additional strip before the last strip.

// Since we have a min Y tile position to start drawing we have to adjust with the ideal scenario calculation
#define TILES_HEIGHT_OFFSET_DUE_TO_MIN_TILE_Y_POS_PAL \
    max(0, MOVIE_MIN_TILE_Y_POS_AVOID_DMA_FLICKER - ((30 - MOVIE_FRAME_STRIPS) / 2))
// Frame with 22 tiles height: 24-1=23
#define MOVIE_HINT_COLORS_SWAP_START_SCANLINE_PAL \
    (((30 - MOVIE_FRAME_STRIPS) / 2) + TILES_HEIGHT_OFFSET_DUE_TO_MIN_TILE_Y_POS_PAL + 1) * 8 - 1
// Frame with 22 tiles height: 208-2*8-1=191
#define MOVIE_HINT_COLORS_SWAP_END_SCANLINE_PAL \
    (30 - ((30 - MOVIE_FRAME_STRIPS) / 2) + TILES_HEIGHT_OFFSET_DUE_TO_MIN_TILE_Y_POS_PAL) * 8 - 2*8 - 1

void setPalsPointer (u16* rootPalsPtr);

void VIntCallback ();

HINTERRUPT_CALLBACK HIntCallback_CPU_NTSC ();

HINTERRUPT_CALLBACK HIntCallback_CPU_PAL ();

HINTERRUPT_CALLBACK HIntCallback_DMA_NTSC ();

HINTERRUPT_CALLBACK HIntCallback_DMA_PAL ();

#endif