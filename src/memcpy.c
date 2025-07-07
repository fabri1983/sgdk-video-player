#include <types.h>
#include <sys.h>
#include "memcpy.h"

FORCE_INLINE void memcpy_asm (u16 lenBytes, u8* from, u8* to)
{
    __asm volatile (
        "lsr.w    #2,%2\n\t"        // divide by 4
        "subq.w   #1,%2\n\t"        // prepare for dbra/dbf
        "1:\n\t"
        "move.l   (%0)+,(%1)+\n\t"
        "dbra     %2,1b"            // dbra/dbf: decrement %2, test if %2 >= 0 then branch back. When %2 = -1 then exits loop
        : 
        : "a" (from), "a" (to), "d" (lenBytes)
        : "cc","memory"
    );
}
