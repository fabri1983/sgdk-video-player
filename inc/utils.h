#ifndef _UTILS_H_
#define _UTILS_H_

#include <types.h>

#define MEMORY_BARRIER() __asm volatile ("" : : : "memory")

#define SIGN(x) ( (x > 0) - (x < 0) )

// Calculates the position/index of the highest bit of n which corresponds to the power of 2 that 
// is the closest integer logarithm base 2 of n. The result is thus FLOOR(LOG2(n))
#define LOG2(n) (((sizeof(u32) * 8) - 1) - (__builtin_clz((n))))

/// @brief Writes into VDP_CTRL_PORT (0xC00004) the setup for DMA (length and source address) and writes the command too.
/// Assumes the 4 arguments are known values at compile time, and the VDP auto inc stepping was already set accordingly.
/// @param ctrl_port a variable defined as (vu32*)VDP_CTRL_PORT.
/// @param fromAddr source RAM address in u32 format.
/// @param cmdAddr destination address as a command. One of: VDP_DMA_VRAM_ADDR, VDP_DMA_CRAM_ADDR, VDP_DMA_VSRAM_ADDR.
/// @param len words to move (is words because DMA RAM/ROM to VRAM moves 2 bytes per VDP cycle op).
#define doDMAfast_fixed_args(ctrl_port,fromAddr,cmdAddr,len) \
    __asm volatile ( \
        /* Setup DMA length (in long word here) */ \
        "move.l  %[_len_low_high],(%[_ctrl_port])\n\t" /* *((vu32*) VDP_CTRL_PORT) = ((0x9300 | (u8)len) << 16) | (0x9400 | (u8)(len >> 8)); */ \
        /* Setup DMA address low and mid */ \
        "move.l  %[_addr_low_mid],(%[_ctrl_port])\n\t" /* *((vu32*) VDP_CTRL_PORT) = ((0x9500 | ((fromAddr >> 1) & 0xff)) << 16) | (0x9600 | ((fromAddr >> 9) & 0xff)); */ \
        /* Setup DMA address high */ \
        "move.w  %[_addr_high],(%[_ctrl_port])\n\t" /* *((vu32*) VDP_CTRL_PORT) = (0x9700 | ((fromAddr >> 17) & 0x7f)); */ \
        /* Trigger DMA */ \
        /* NOTE: this should be done as Stef does with two .w writes from memory. See DMA_doDmaFast() */ \
        "move.l  %[_cmdAddr],(%[_ctrl_port])" /* *((vu32*) VDP_CTRL_PORT) = cmdAddr; */ \
        : \
        : [_ctrl_port] "a" (ctrl_port), \
          [_len_low_high] "i" ( ((0x9300 | ((len) & 0xff)) << 16) | (0x9400 | (((len) >> 8) & 0xff)) ), \
          [_addr_low_mid] "i" ( ((0x9500 | (((fromAddr) >> 1) & 0xff)) << 16) | (0x9600 | (((fromAddr) >> 9) & 0xff)) ), \
          [_addr_high] "i" ( (0x9700 | (((fromAddr) >> 17) & 0x7f)) ), \
          /* If you want to add VDP stepping then use: ((0x9700 | (((fromAddr) >> 17) & 0x7f)) << 16) | (0x8F00 | ((step) & 0xff)) \ */ \
          [_cmdAddr] "i" ((u32)(cmdAddr)) \
        : \
    )

/// @brief Waits for a certain amount of millisecond (~3.33 ms based timer when wait is >= 100ms). 
/// Lightweight implementation without calling SYS_doVBlankProcess().
/// This method CAN NOT be called from V-Int callback or when V-Int is disabled.
/// @param ms >= 100ms, otherwise use waitMs() from timer.h
void waitMs_ (u32 ms);

/// Wait for a certain amount of subticks. ONLY values < 150.
void waitSubTick_ (u32 subtick);

#endif // _UTILS_H_