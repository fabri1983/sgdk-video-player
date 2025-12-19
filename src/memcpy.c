#include <types.h>
#include <sys.h>
#include "memcpy.h"

FORCE_INLINE void memcpy_asm (u16 lenBytes, u8* from, u8* to)
{
    __asm volatile (
        "lsr.w    #2,%0\n\t"        // divide lenBytes by 4
        "subq.w   #1,%0\n\t"        // prepare it for dbra/dbf
        "1:\n\t"
        "move.l   (%1)+,(%2)+\n\t"
        "dbra     %0,1b"            // dbra/dbf: decrement lenBytes, test if lenBytes >= 0 then branch back. When lenBytes == -1 exits loop
        : "+d" (lenBytes)
        : "a" (from), "a" (to)
        :
    );
}
