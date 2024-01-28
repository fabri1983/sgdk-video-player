#include <types.h>
#include <dma.h>
#include <tools.h>
#include "dma_1elem.h"
#include "videoPlayer.h"

DMAOpInfo elem;

FORCE_INLINE void enqueueDMA_1elem (void* from, u16 to, u16 len, u16 step) {
    u32 fromAddr = (u32) from;

    // $13:len L  $14:len H (DMA length in word)
    elem.regLenL = 0x9300 + (len & 0xFF);
    elem.regLenH = 0x9400 + ((len >> 8) & 0xFF);
    // $16:M  $f:step (DMA address M and Step register)
    elem.regAddrMStep = 0x96008F00 + ((fromAddr << 7) & 0xFF0000) + step;
    // $17:H  $15:L (DMA address H & L)
    elem.regAddrHAddrL = 0x97009500 + ((fromAddr >> 1) & 0x7F00FF);

    elem.regCtrlWrite = VDP_DMA_VRAM_ADDR((u32)to);

    #ifdef DEBUG_VIDEO_PLAYER
    // if transfer size above max transfer then warn it
    if ((len << 1) > (VIDEO_FRAME_TILESET_CHUNK_SIZE * 32))
        KLog_U2("dma1_elem.c: Max DMA transfer limit exceeded: ", (len << 1), ". Max is ", (VIDEO_FRAME_TILESET_CHUNK_SIZE * 32));
    #endif
}

FORCE_INLINE void flushDMA_1elem () {
    // -------------------------------------------------------
    // Flush DMA Queue with only 1 elem: 216 cycles.
    // Slightly faster than Stef's dma_a.s: 244 cycles.
    //-------------------------------------------------------
    DMAOpInfo* elemPtr = &elem;
	ASM_STATEMENT __volatile__ (
        "\n\t"
		"move.l %0, %%a0\n\t"
		"move.l #0xC00004, %%a1\n\t"  // VDP_CTRL_PORT
		"\n\t"
		"move.l (%%a0)+, (%%a1)\n\t"
		"move.l (%%a0)+, (%%a1)\n\t"
		"move.l (%%a0)+, (%%a1)\n\t"
		"move.w (%%a0)+, (%%a1)\n\t"
		"move.w (%%a0)+, (%%a1)\n"  // Stef: important to use word write for command triggering DMA (see SEGA notes)
		: 
		: "m" (elemPtr)
		: "a0", "a1", "memory"      // Clobbers
	);
}