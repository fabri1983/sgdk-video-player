#ifndef _DMA_ELEMS_H
#define _DMA_ELEMS_H

#include <types.h>

void DMA_ELEMS_reset ();

/// Only for DMA_VRAM destination. Assumees VDP step is 2.
void DMA_ELEMS_queue (u32 fromAddr, u16 to, u16 len);

void DMA_ELEMS_flush ();

#endif // _DMA_ELEMS_H