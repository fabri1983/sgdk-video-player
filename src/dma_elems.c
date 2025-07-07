#include <types.h>
#include <dma.h>
#include <tools.h>
#include <sys.h>
#include "dma_elems.h"
#include "videoPlayer.h"
#include "utils.h"

static DMAOpInfo dma_elems[VIDEO_PLAYER_DMA_MAX_ELEMS];
static u16 elem_index;

FORCE_INLINE void DMA_ELEMS_reset ()
{
    elem_index = 0;
}

FORCE_INLINE void DMA_ELEMS_queue (u32 fromAddr, u16 to, u16 len)
{
    DMAOpInfo* dma_elem = &dma_elems[elem_index];
    ++elem_index;

    // $13:len L  $14:len H (DMA length in word)
    dma_elem->regLenL = 0x9300 | (len & 0xFF);
    dma_elem->regLenH = 0x9400 | ((len >> 8) & 0xFF);
    // $16:M  $f:step (DMA address M and Step register)
    dma_elem->regAddrMStep = 0x96008F00 | ((fromAddr << 7) & 0xFF0000) | 2; // step = 2
    // $17:H  $15:L (DMA address H & L)
    dma_elem->regAddrHAddrL = 0x97009500 | ((fromAddr >> 1) & 0x7F00FF);

    dma_elem->regCtrlWrite = VDP_DMA_VRAM_ADDR((u32)to);

    #ifdef DEBUG_VIDEO_PLAYER
    // if transfer size above max transfer then warn it
    if ((len << 1) > (VIDEO_FRAME_TILESET_CHUNK_SIZE * 32))
        KLog_U2("dma_1elem.c: Max DMA transfer limit exceeded: ", (len << 1), ". Max is ", (VIDEO_FRAME_TILESET_CHUNK_SIZE * 32));
    #endif
}

void DMA_ELEMS_flush ()
{
    vu32* vdpCtrl_ptr_l = (vu32*) VDP_CTRL_PORT;

    #if (MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES == MOVIE_FRAME_WIDTH_IN_TILES)
    #pragma GCC unroll 256 // Always set a big number since it does not accept defines
    for (u8 i=0; i < MOVIE_FRAME_HEIGHT_IN_TILES; ++i) {
        doDMAfast_fixed_args(vdpCtrl_ptr_l, RAM_FIXED_MOVIE_FRAME_UNPACKED_TILEMAP_ADDRESS + i*MOVIE_FRAME_WIDTH_IN_TILES*2, VDP_DMA_VRAM_ADDR(VIDEO_FRAME_PLANE_ADDRESS + i*VIDEO_PLANE_COLUMNS*2), MOVIE_FRAME_WIDTH_IN_TILES);
    }
    #endif

    DMAOpInfo* ptr = dma_elems;
    __asm volatile (
        "subq.w   #1,%0\n\t" // decrement 1 for proper use of dbf/dbra
        "bmi.s    2f\n"      // if NEGATIVE flag is set then it means elem_index was 0
        "1:\n\t"
        "move.l   (%1)+, (%2)\n\t"
        "move.l   (%1)+, (%2)\n\t"
        "move.l   (%1)+, (%2)\n\t"
        "move.w   (%1)+, (%2)\n\t"
        "move.w   (%1)+, (%2)\n\t"      // Stef: important to use word write for command triggering DMA (see SEGA notes)
        "dbf      %0,1b\n"
        "2:\n\t"
        "moveq    #0,%0\n"   // elem_index = 0
        : "+d" (elem_index), "+a" (ptr)
        : "a" (vdpCtrl_ptr_l)
        :
    );
}