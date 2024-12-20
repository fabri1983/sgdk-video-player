#ifndef _DMA_ELEMS_H
#define _DMA_ELEMS_H

#include <types.h>
#include "compatibilities.h"

/// Sets DMAOpInfo first or second slot depending on enqueueAtSlot1. 
/// Only for DMA_VRAM. Assumees step = 2.
void enqueueDMA_elem (void* from, u16 to, u16 len, bool enqueueAtSlot1);

void flushDMA_elems ();

#endif // _DMA_ELEMS_H