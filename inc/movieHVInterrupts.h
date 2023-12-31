#ifndef MOVIE_HV_INTERRUPTS_H
#define MOVIE_HV_INTERRUPTS_H

#include <types.h>
#include <sys.h>

#ifdef __GNUC__
#define ASM_STATEMENT __asm__
#elif defined(_MSC_VER)
#define ASM_STATEMENT __asm
#endif

#define MOVIE_DATA_COLORS_PER_STRIP 32
// in case you were to split any calculation over the colors of strip by an odd divisor n
#define MOVIE_DATA_COLORS_PER_STRIP_REMINDER(n) (MOVIE_DATA_COLORS_PER_STRIP - n*(MOVIE_DATA_COLORS_PER_STRIP/n))

#define HINT_COUNTER_FOR_COLORS_UPDATE 8 // add -1 when set on VDP_setHIntCounter()

#define MOVIE_MIN_TILE_Y_POS_AVOID_DMA_FLICKER 3 // 0 based

// For NTSC systems: ONLY VALID IF STRIP HEIGHT IS ALWAYS 8
//   Screen has 224/8=28 tiles height, and the video is 22 tiles height centered in Y axis.
//   So starts at (28-22)/2=3. But we start swapping at 2nd strip. So +1 strip => at 4th row of tiles => 4*8=32th scanline.
//   And ends at 28-3=25th tile: 25*8=200th scanline. -2*8 since we load the last palette one additional strip before the last strip.
#define MOVIE_HINT_COLORS_SWAP_START_SCANLINE_NTSC (max(MOVIE_MIN_TILE_Y_POS_AVOID_DMA_FLICKER, ((28 - 22) / 2)) + 1) * 8 - 1 // Frame with 22 tiles height: 32-1=31
#define MOVIE_HINT_COLORS_SWAP_END_SCANLINE_NTSC (28 - max(MOVIE_MIN_TILE_Y_POS_AVOID_DMA_FLICKER, ((28 - 22) / 2))) * 8 - 2*8 - 1 // Frame with 22 tiles height: 200-2*8-1=183

// For PAL systems: ONLY VALID IF STRIP HEIGHT IS ALWAYS 8
//   Screen has 240/8=30 tiles height, and the video is 22 tiles height centered in Y axis.
//   So starts at (30-22)/2=4. But we start swapping at 2nd strip. So +1 strip => at 5th row of tiles => 5*8=40th scanline.
//   And ends at 30-4=26th tile: 26*8=208th scanline. -2*8 since we load the last palette one additional strip before the last strip.
#define MOVIE_HINT_COLORS_SWAP_START_SCANLINE_PAL (max(MOVIE_MIN_TILE_Y_POS_AVOID_DMA_FLICKER, ((30 - 22) / 2)) + 1) * 8 - 1 // Frame with 22 tiles height: 40-1=39
#define MOVIE_HINT_COLORS_SWAP_END_SCANLINE_PAL (30 - max(MOVIE_MIN_TILE_Y_POS_AVOID_DMA_FLICKER, ((30 - 22) / 2))) * 8 - 2*8 - 1 // Frame with 22 tiles height: 208-2*8-1=191

void setPalsPointers (u16* rootPalsPtr);

void VIntCallback ();

HINTERRUPT_CALLBACK HIntCallback_CPU_NTSC ();

HINTERRUPT_CALLBACK HIntCallback_CPU_PAL ();

HINTERRUPT_CALLBACK HIntCallback_DMA_NTSC ();

HINTERRUPT_CALLBACK HIntCallback_DMA_PAL ();

#endif