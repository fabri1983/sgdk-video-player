#include "utils.h"
#include <vdp.h>
#include <vdp_bg.h>
#include <sys.h>
#include <tools.h>
#include <timer.h>

void waitMs_ (u32 ms)
{
	u32 tick = (ms * TICKPERSECOND) / 1000;
	const u32 start = getTick();
    u32 max = start + tick;
    u32 current;

    // need to check for overflow
    if (max < start) max = 0xFFFFFFFF;

    // wait until we reached subtick
    do {
        current = getTick();
    }
    while (current < max);
}

FORCE_INLINE void waitSubTick_ (u32 subtick)
{
	if (subtick == 0)
		return;

    u32 tmp = subtick*7;
    // Seems that every 7 loops it simulates a tick.
    // TODO: use cycle accurate wait loop in asm (about 100 cycles for 1 subtick)
    __asm volatile (
        "1:\n\t"
        "dbra   %0, 1b" // dbf/dbra: test if not zero, then decrement register dN and branch back (b) to label 1
        : "+d" (tmp)
        :
        : "cc"
	);
}
