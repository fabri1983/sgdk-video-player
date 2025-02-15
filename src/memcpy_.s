#include "asm_mac.i"
#include "generated/movie_data_consts.h"

;// It has to be a divisor of MOVIE_FRAME_WIDTH_IN_TILES*2 (*2 converts it to bytes)
#define MOVEL_PER_LOOP 1

;// a0 = from
;// a1 = to
;// This method adds the extended width to the destination pointer after each bulk of MOVIE_FRAME_HEIGHT_IN_TILES.
func memcpy_tilemap_asm

    movem.l  4(sp),a0-a1       ;// copy parameters into registers a0-a1

    moveq    #((MOVIE_FRAME_WIDTH_IN_TILES*2)/(MOVEL_PER_LOOP*4)-1),d0 ;// set N move.l instructions per loop and prepare for dbra/dbf
    moveq    #(MOVIE_FRAME_HEIGHT_IN_TILES-2),d1  ;// set iterations and prepare for dbra/dbf and one additional iteration less

1:
    .rept MOVEL_PER_LOOP
    move.l   (a0)+,(a1)+
    .endr
    dbra     d0,1b             ;// dbra/dbf: decrement dN, test if dN >= 0 then branch back. When dN = -1 exits loop

    * add the gap to jump over the extended width
    lea      MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES*2-MOVIE_FRAME_WIDTH_IN_TILES*2(a1),a1
    * set N-1 move.l instructions per loop and prepare for dbra/dbf
    moveq    #((MOVIE_FRAME_WIDTH_IN_TILES*2)/(MOVEL_PER_LOOP*4)-1),d0
    dbra     d1,1b             ;// dbra/dbf: decrement dN, test if dN >= 0 then branch back. When dN = -1 exits loop

;// last iteration without the setup of variables
2:
    .rept MOVEL_PER_LOOP
    move.l   (a0)+,(a1)+
    .endr
    dbra     d0,2b

    rts
