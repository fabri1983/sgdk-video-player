
*-------------------------------------------------------
* Flush DMA Queue with only 1 elem: 216 cycles.
* Slightly faster than Stef's dma_a.s: 244 cycles.
*-------------------------------------------------------

#include "asm_mac.i"

func flushQueue_1elem
	move.l dmaQueues, %a0
	move.l #0xC00004, %a1

	move.l (%a0)+, (%a1)
	move.l (%a0)+, (%a1)
	move.l (%a0)+, (%a1)
	move.w (%a0)+, (%a1)
	move.w (%a0)+, (%a1)     // important to use word write for command triggering DMA (see SEGA notes)

	rts
