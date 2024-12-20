#include <types.h>
#include <dma.h>
#include <tools.h>
#include "dma_elems.h"
#include "videoPlayer.h"

DMAOpInfo dma_elem1;
DMAOpInfo dma_elem2;
bool dma_elem1_is_set = FALSE;
bool dma_elem2_is_set = FALSE;

FORCE_INLINE void enqueueDMA_elem (void* from, u16 to, u16 len, bool enqueueAtSlot1) {
    u32 fromAddr = (u32) from;

    if (enqueueAtSlot1) {
        // $13:len L  $14:len H (DMA length in word)
        dma_elem1.regLenL = 0x9300 | (len & 0xFF);
        dma_elem1.regLenH = 0x9400 | ((len >> 8) & 0xFF);
        // $16:M  $f:step (DMA address M and Step register)
        dma_elem1.regAddrMStep = 0x96008F00 | ((fromAddr << 7) & 0xFF0000) | 2; // step = 2
        // $17:H  $15:L (DMA address H & L)
        dma_elem1.regAddrHAddrL = 0x97009500 | ((fromAddr >> 1) & 0x7F00FF);

        dma_elem1.regCtrlWrite = VDP_DMA_VRAM_ADDR((u32)to);
        dma_elem1_is_set = TRUE;
    }
    else {
        // $13:len L  $14:len H (DMA length in word)
        dma_elem2.regLenL = 0x9300 | (len & 0xFF);
        dma_elem2.regLenH = 0x9400 | ((len >> 8) & 0xFF);
        // $16:M  $f:step (DMA address M and Step register)
        dma_elem2.regAddrMStep = 0x96008F00 | ((fromAddr << 7) & 0xFF0000) | 2; // step = 2
        // $17:H  $15:L (DMA address H & L)
        dma_elem2.regAddrHAddrL = 0x97009500 | ((fromAddr >> 1) & 0x7F00FF);

        dma_elem2.regCtrlWrite = VDP_DMA_VRAM_ADDR((u32)to);
        dma_elem2_is_set = TRUE;
    }

    #ifdef DEBUG_VIDEO_PLAYER
    // if transfer size above max transfer then warn it
    if ((len << 1) > (VIDEO_FRAME_TILESET_CHUNK_SIZE * 32))
        KLog_U2("dma_1elem.c: Max DMA transfer limit exceeded: ", (len << 1), ". Max is ", (VIDEO_FRAME_TILESET_CHUNK_SIZE * 32));
    #endif
}

FORCE_INLINE void flushDMA_elems () {
    if (dma_elem1_is_set) {
        dma_elem1_is_set = FALSE;
        DMAOpInfo* elem1_ptr = &dma_elem1;
        vu16* pw = (u16*) VDP_CTRL_PORT;
        __asm volatile (
            "move.l   (%0)+, (%1)\n\t"
            "move.l   (%0)+, (%1)\n\t"
            "move.l   (%0)+, (%1)\n\t"
            "move.w   (%0)+, (%1)\n\t"
            "move.w   (%0)+, (%1)"      // Stef: important to use word write for command triggering DMA (see SEGA notes)
            : "+a" (elem1_ptr)
            : "a" (pw)
            :
        );
    }

    if (dma_elem2_is_set) {
        dma_elem2_is_set = FALSE;
        DMAOpInfo* elem2_ptr = &dma_elem2;
        vu16* pw = (u16*) VDP_CTRL_PORT;
        __asm volatile (
            "move.l   (%0)+, (%1)\n\t"
            "move.l   (%0)+, (%1)\n\t"
            "move.l   (%0)+, (%1)\n\t"
            "move.w   (%0)+, (%1)\n\t"
            "move.w   (%0)+, (%1)"      // Stef: important to use word write for command triggering DMA (see SEGA notes)
            : "+a" (elem2_ptr)
            : "a" (pw)
            :
        );
    }
}