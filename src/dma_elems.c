#include <types.h>
#include <dma.h>
#include <tools.h>
#include <sys.h>
#include "dma_elems.h"
#include "videoPlayer.h"
#include "utils.h"

static DMAOpInfo dmaElemTileset;
static bool dmaElemTileset_ready;
#if MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES > MOVIE_FRAME_WIDTH_IN_TILES
static DMAOpInfo dmaElemTilemap;
#endif
static bool dmaElemTilemap_ready;
#if MOVIE_FRAME_STRIPS == 1
// TODO PALS_1: this is going to be useful when first 2 strips' palettes (previously unpacked) will be enqueued
static DMAOpInfo dmaElemPalette;
static bool dmaElemPalette_ready;
#endif

FORCE_INLINE void DMA_ELEMS_queue (u32 fromAddr, u16 to, u16 len, u8 dmaElemType)
{
    if (dmaElemType == VIDEO_PLAYER_DMA_ELEM_TYPE_TILESET)
    {
        // $13:len L  $14:len H (DMA length in word)
        dmaElemTileset.regLenL = 0x9300 | (len & 0xFF);
        dmaElemTileset.regLenH = 0x9400 | (len >> 8);
        // $16:M  $f:step (DMA address M and Step register)
        dmaElemTileset.regAddrMStep = 0x96008F00 | ((fromAddr << 7) & 0xFF0000) | 2; // VDP step = 2
        // $17:H  $15:L (DMA address H & L)
        dmaElemTileset.regAddrHAddrL = 0x97009500 | ((fromAddr >> 1) & 0x7F00FF);
        // VDP command
        dmaElemTileset.regCtrlWrite = VDP_DMA_VRAM_ADDR((u32)to);

        dmaElemTileset_ready = TRUE;
    }
    else if (dmaElemType == VIDEO_PLAYER_DMA_ELEM_TYPE_TILEMAP)
    {
        #if MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES > MOVIE_FRAME_WIDTH_IN_TILES
        // $13:len L  $14:len H (DMA length in word)
        dmaElemTilemap.regLenL = 0x9300 | (len & 0xFF);
        dmaElemTilemap.regLenH = 0x9400 | (len >> 8);
        // $16:M  $f:step (DMA address M and Step register)
        dmaElemTilemap.regAddrMStep = 0x96008F00 | ((fromAddr << 7) & 0xFF0000) | 2; // VDP step = 2
        // $17:H  $15:L (DMA address H & L)
        dmaElemTilemap.regAddrHAddrL = 0x97009500 | ((fromAddr >> 1) & 0x7F00FF);
        // VDP command
        dmaElemTilemap.regCtrlWrite = VDP_DMA_VRAM_ADDR((u32)to);
        #endif

        dmaElemTilemap_ready = TRUE;
    }
    #if MOVIE_FRAME_STRIPS == 1
    // TODO PALS_1: this is going to be useful when first 2 strips' palettes (previously unpacked) will be enqueued
    else if (dmaElemType == VIDEO_PLAYER_DMA_ELEM_TYPE_PALETTE)
    {
        // $13:len L  $14:len H (DMA length in word)
        dmaElemPalette.regLenL = 0x9300 | (len & 0xFF);
        dmaElemPalette.regLenH = 0x9400 | (len >> 8);
        // $16:M  $f:step (DMA address M and Step register)
        dmaElemPalette.regAddrMStep = 0x96008F00 | ((fromAddr << 7) & 0xFF0000) | 2; // VDP step = 2
        // $17:H  $15:L (DMA address H & L)
        dmaElemPalette.regAddrHAddrL = 0x97009500 | ((fromAddr >> 1) & 0x7F00FF);
        // VDP command
        dmaElemPalette.regCtrlWrite = VDP_DMA_CRAM_ADDR((u32)to);

        dmaElemPalette_ready = TRUE;
    }
    #endif

    #ifdef DEBUG_VIDEO_PLAYER
    // if transfer size above max transfer (which is the tileset chunk) then warn it
    if ((len << 1) > (VIDEO_FRAME_TILESET_CHUNK_SIZE * 32))
        KLog_U2("dma_elems.c: Max DMA transfer limit exceeded: ", (len << 1), ". Max is ", (VIDEO_FRAME_TILESET_CHUNK_SIZE * 32));
    #endif
}

void DMA_ELEMS_flush ()
{
    vu32* vdpCtrl_ptr_l = (vu32*) VDP_CTRL_PORT;

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

    #if MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES == MOVIE_FRAME_WIDTH_IN_TILES
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

    #if MOVIE_FRAME_STRIPS == 1
    if (dmaElemPalette_ready) {
        dmaElemPalette_ready = FALSE;
        DMAOpInfo* elem_ptr = &dmaElemPalette;
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
}