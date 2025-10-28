#include <types.h>
#include <dma.h>
#include <tools.h>
#include <sys.h>
#include "dma_elems.h"
#include "videoPlayer.h"
#include "utils.h"

static DMAOpInfo dmaElemTileset;
#if (MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES != MOVIE_FRAME_WIDTH_IN_TILES)
static DMAOpInfo dmaElemTilemap;
#endif
static bool dmaElemTileset_ready;
static bool dmaElemTilemap_ready;

FORCE_INLINE void DMA_ELEMS_queue (u32 fromAddr, u16 to, u16 len, u8 dmaElemType)
{
    if (VIDEO_PLAYER_DMA_ELEM_TYPE_TILESET == dmaElemType)
    {
        // $13:len L  $14:len H (DMA length in word)
        dmaElemTileset.regLenL = 0x9300 | (len & 0xFF);
        dmaElemTileset.regLenH = 0x9400 | ((len >> 8) & 0xFF);
        // $16:M  $f:step (DMA address M and Step register)
        dmaElemTileset.regAddrMStep = 0x96008F00 | ((fromAddr << 7) & 0xFF0000) | 2; // VDP step = 2
        // $17:H  $15:L (DMA address H & L)
        dmaElemTileset.regAddrHAddrL = 0x97009500 | ((fromAddr >> 1) & 0x7F00FF);
        // VDP command
        dmaElemTileset.regCtrlWrite = VDP_DMA_VRAM_ADDR((u32)to);

        dmaElemTileset_ready = TRUE;
    }
    else if (VIDEO_PLAYER_DMA_ELEM_TYPE_TILEMAP == dmaElemType)
    {
        #if (MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES != MOVIE_FRAME_WIDTH_IN_TILES)
        // $13:len L  $14:len H (DMA length in word)
        dmaElemTilemap.regLenL = 0x9300 | (len & 0xFF);
        dmaElemTilemap.regLenH = 0x9400 | ((len >> 8) & 0xFF);
        // $16:M  $f:step (DMA address M and Step register)
        dmaElemTilemap.regAddrMStep = 0x96008F00 | ((fromAddr << 7) & 0xFF0000) | 2; // VDP step = 2
        // $17:H  $15:L (DMA address H & L)
        dmaElemTilemap.regAddrHAddrL = 0x97009500 | ((fromAddr >> 1) & 0x7F00FF);
        // VDP command
        dmaElemTilemap.regCtrlWrite = VDP_DMA_VRAM_ADDR((u32)to);
        #endif

        dmaElemTilemap_ready = TRUE;
    }

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
    if (dmaElemTilemap_ready) {
        dmaElemTilemap_ready = FALSE;

        // Setup DMA address ONLY ONCE
        u32 from = RAM_FIXED_MOVIE_FRAME_UNPACKED_TILEMAP_ADDRESS + 0*MOVIE_FRAME_WIDTH_IN_TILES*2;
        from >>= 1;
        *(vu16*)vdpCtrl_ptr_l = 0x9500 + (from & 0xff); // low
        from >>= 8;
        *(vu16*)vdpCtrl_ptr_l = 0x9600 + (from & 0xff); // mid
        from >>= 8;
        *(vu16*)vdpCtrl_ptr_l = 0x9700 + (from & 0x7f); // high

        // Setup DMA length high ONLY ONCE. Length in words because DMA RAM/ROM to VRAM moves 2 bytes per VDP cycle op
        *(vu16*)vdpCtrl_ptr_l = 0x9400 | ((MOVIE_FRAME_WIDTH_IN_TILES >> 8) & 0xff); // DMA length high
        // DMA length low has to be set every time before triggering the DMA command

        #pragma GCC unroll 256 // Always set a big number since it does not accept defines
        for (u8 i=0; i < MOVIE_FRAME_HEIGHT_IN_TILES; ++i) {
            doDMAfast_fixed_args_loop_ready(vdpCtrl_ptr_l, VDP_DMA_VRAM_ADDR(VIDEO_FRAME_PLANE_ADDRESS + i*VIDEO_PLANE_COLUMNS*2), MOVIE_FRAME_WIDTH_IN_TILES);
        }
    }
    #else
    if (dmaElemTilemap_ready) {
        dmaElemTilemap_ready = FALSE;
        DMAOpInfo* elem_ptr = &dmaElemTilemap;
        __asm volatile (
            "move.l   (%0)+, (%1)\n\t"
            "move.l   (%0)+, (%1)\n\t"
            "move.l   (%0)+, (%1)\n\t"
            "move.w   (%0)+, (%1)\n\t"
            "move.w   (%0)+, (%1)"      // Stef: important to use word write for command triggering DMA (see SEGA notes)
            : "+a" (elem_ptr)
            : "a" (vdpCtrl_ptr_l)
            :
        );
    }
    #endif

    if (dmaElemTileset_ready) {
        dmaElemTileset_ready = FALSE;
        DMAOpInfo* elem_ptr = &dmaElemTileset;
        __asm volatile (
            "move.l   (%0)+, (%1)\n\t"
            "move.l   (%0)+, (%1)\n\t"
            "move.l   (%0)+, (%1)\n\t"
            "move.w   (%0)+, (%1)\n\t"
            "move.w   (%0)+, (%1)"      // Stef: important to use word write for command triggering DMA (see SEGA notes)
            : "+a" (elem_ptr)
            : "a" (vdpCtrl_ptr_l)
            :
        );
    }
}