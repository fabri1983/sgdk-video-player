#ifndef _UTILS_H_
#define _UTILS_H_

#include <types.h>
#include <vdp.h>

#ifdef __GNUC__
#define HINTERRUPT_CALLBACK __attribute__((interrupt)) void
#elif defined(_MSC_VER)
// Declare function for the hint callback (generate a RTE to return from interrupt instead of RTS)
#define HINTERRUPT_CALLBACK void
#endif

#ifdef __GNUC__
#define FORCE_INLINE inline __attribute__((always_inline))
#elif defined(_MSC_VER)
// To force method inlining (not sure that GCC does actually care of it)
#define FORCE_INLINE inline __forceinline
#endif

#ifdef __GNUC__
#define NO_INLINE __attribute__((noinline))
#elif defined(_MSC_VER)
// To force no inlining for this method
#define NO_INLINE __declspec(noinline)
#endif

#ifdef __GNUC__
#define VOID_OR_CHAR void
#elif defined(_MSC_VER)
#define VOID_OR_CHAR char
#endif

#define MEMORY_BARRIER() __asm volatile ("" : : : "memory")

#define CLAMP(x, minimum, maximum) ( min(max((x),(minimum)),(maximum)) )

#define SIGN(x) ( (x > 0) - (x < 0) )

// Calculates the position/index of the highest bit of n which corresponds to the power of 2 that 
// is the closest integer logarithm base 2 of n. The result is thus FLOOR(LOG2(n))
#define LOG2(n) (((sizeof(u32) * 8) - 1) - (__builtin_clz((n))))

/// @brief Waits for a certain amount of millisecond (~3.33 ms based timer when wait is >= 100ms). 
/// Lightweight implementation without calling SYS_doVBlankProcess().
/// This method CAN NOT be called from V-Int callback or when V-Int is disabled.
/// @param ms >= 100ms, otherwise use waitMs() from timer.h
void waitMs_ (u32 ms);

/// Wait for a certain amount of subticks. ONLY values < 150.
void waitSubTick_ (u32 subtick);

#endif // _UTILS_H_