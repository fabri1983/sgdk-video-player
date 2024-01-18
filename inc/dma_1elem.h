#ifndef _DMA_1ELEM_H
#define _DMA_1ELEM_H

#include <types.h>

#ifdef __GNUC__
#define FORCE_INLINE            inline __attribute__ ((always_inline))
#elif defined(_MSC_VER)
#define FORCE_INLINE            inline __forceinline
#endif

#ifdef __GNUC__
#define NO_INLINE               __attribute__ ((noinline))
#elif defined(_MSC_VER)
#define NO_INLINE               __declspec(noinline)
#endif

#ifdef __GNUC__
#define ASM_STATEMENT __asm__
#elif defined(_MSC_VER)
#define ASM_STATEMENT __asm
#endif

/// It's not a queue. It holds only one DMAOpInfo element. Only for DMA_VRAM.
void enqueueDMA_1elem (void* from, u16 to, u16 len, u16 step);

void flushDMA_1elem ();

#endif // _DMA_1ELEM_H