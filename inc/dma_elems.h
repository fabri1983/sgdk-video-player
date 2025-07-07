#ifndef _DMA_ELEMS_H
#define _DMA_ELEMS_H

#include <types.h>

/// Sets DMAOpInfo first or second slot depending on queueAtSlot1 parameter. 
/// Only for DMA_VRAM destination. Assumees VDP step is 2.
void DMA_ELEMS_queue (u32 fromAddr, u16 to, u16 len, bool queueAtSlot1);

void DMA_ELEMS_flush ();

#endif // _DMA_ELEMS_H