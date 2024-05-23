#ifndef _DMA_1ELEM_H
#define _DMA_1ELEM_H

#include <types.h>
#include "compatibilities.h"

/// It's not a queue. It holds only one DMAOpInfo element. Only for DMA_VRAM. Assumees step = 2.
void enqueueDMA_1elem (void* from, u16 to, u16 len);

void flushDMA_1elem ();

#endif // _DMA_1ELEM_H