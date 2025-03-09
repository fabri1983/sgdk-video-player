#include <genesis.h>
#include <vdp.h>
#include <pal.h>
#include <sys.h>
#include <tools.h>
#include <timer.h>
#include <memory.h>
#include "movieHVInts.h"

// u8 reg01; // Holds current VDP register 1 whole value (it holds other bits than VDP ON/OFF status)

// FORCE_INLINE void copyReg01 () {
//     reg01 = VDP_getReg(0x01);
// }

/// @brief Set bit 6 (64 decimal, 0x40 hexa) of reg 1.
/// @param reg01 VDP's Reg 1 holds other bits than just VDP ON/OFF status, so we need its current value.
FORCE_INLINE void turnOffVDP (u8 reg01)
{
    //reg01 &= ~0x40;
    //*(vu16*) VDP_CTRL_PORT = 0x8100 | reg01;
    *(vu16*) VDP_CTRL_PORT = 0x8100 | (reg01 & ~0x40);
}

/// @brief Set bit 6 (64 decimal, 0x40 hexa) of reg 1.
/// @param reg01 VDP's Reg 1 holds other bits than just VDP ON/OFF status, so we need its current value.
FORCE_INLINE void turnOnVDP (u8 reg01)
{
    //reg01 |= 0x40;
    //*(vu16*) VDP_CTRL_PORT = 0x8100 | reg01;
    *(vu16*) VDP_CTRL_PORT = 0x8100 | (reg01 | 0x40);
}

/**
 * Wait until HCounter 0xC00009 reaches nth position (actually the (n*2)th pixel since the VDP counts by 2).
*/
static FORCE_INLINE void waitHCounter_opt1 (u8 n)
{
    u32 regA = VDP_HVCOUNTER_PORT + 1; // HCounter address is 0xC00009
    __asm volatile (
        "1:\t\n" 
        "cmp.b     (%0),%1\t\n"   // cmp: n - (0xC00009). Compares byte because hcLimit won't be > 160 for our practical cases
        "bhi.s     1b"            // loop back if n is higher than (0xC00009)
        // bhi is for unsigned comparisons
        :
        : "a" (regA), "d" (n)
        : "cc"
    );
}

/**
 * Wait until HCounter 0xC00009 reaches nth position (actually the (n*2)th pixel since the VDP counts by 2).
*/
static FORCE_INLINE void waitHCounter_opt2 (u8 n)
{
    u32* regA; // placeholder used to indicate the use of an Ax register
    __asm volatile (
        "move.l    #0xC00009,%0\n\t" // Load HCounter (VDP_HVCOUNTER_PORT + 1 = 0xC00009) into an An register
        "1:\n\t" 
        "cmp.b     (%0),%1\n\t"      // cmp: n - (0xC00009). Compares byte because hcLimit won't be > 160 for our practical cases
        "bhi.s     1b"               // loop back if n is higher than (0xC00009)
        // bhi is for unsigned comparisons
        : "=a" (regA)
        : "d" (n)
        : "cc"
    );
}

FORCE_INLINE void waitVCounterReg (u16 n)
{
    // The docs straight up say to not trust the value of the V counter during vblank, in that case use VDP_getAdjustedVCounter().
    // - Sik: on PAL Systems it jumps from 0x102 (258) to 0x1CA (458) (assuming V28).
    // - Sik: Note that the 9th bit is not visible through the VCounter, so what happens from the 68000's viewpoint is that it reaches 0xFF (255), 
    // then counts from 0x00 to 0x02, then from 0xCA (202) to 0xFF (255), and finally starts back at 0x00.
    // - Stef: on PAL System the VCounter rollback occurs at 0xCA (202) so probably better to use n up to 202 to avoid that edge case.

    u32 regA = VDP_HVCOUNTER_PORT; // VCounter address is 0xC00008
    __asm volatile (
        "1:\n"
        "    cmp.w     (%0),%1\n"         // cmp: n - (0xC00008)
        "    bgt.s     1b\n"              // loop back if n is higher than (0xC00008)
            // bhi is for unsigned comparisons, 
            // bge/bgt are for signed comparisons in case n comes already smaller than value in VDP_HVCOUNTER_PORT memory
        :
        : "a" (regA), "d" (n << 8) // (n << 8) | 0xFF
        : "cc"
    );
}

/**
 * \brief Writes into VDP_CTRL_PORT (0xC00004) the setup for DMA (length and source address).
 * \param len How many colors to move.
 * \param fromAddr Must be >> 1 (shifted to right).
*/
FORCE_INLINE void setupDMAForPals (u16 len, u32 fromAddr)
{
    // Uncomment if you previously change it to 1 (CPU access to VRAM is 1 byte length, and 2 bytes length for CRAM and VSRAM)
    //VDP_setAutoInc(2);
/*
    vu16* pw = (vu16*) VDP_CTRL_PORT;
    // Setup DMA length (in word here)
    *pw = 0x9300 + (len & 0xff);
    *pw = 0x9400 + ((len >> 8) & 0xff);
    // Setup DMA address
    // fromAddr already comes with >> 1
    *pw = 0x9500 + (fromAddr & 0xff); // low
    fromAddr >>= 8;
    *pw = 0x9600 + (fromAddr & 0xff); // mid
    fromAddr >>= 8;
    *pw = 0x9700 + (fromAddr & 0x7f); // high
*/
    u32* dmaCtrl_ptr = (u32*) VDP_CTRL_PORT;
    u32 dmaLen = ((0x9300 | (u8)len) << 16) | (0x9400 | (u8)(len >> 8));
    u16 dn = 0x9500;
    __asm volatile (
        // Setup DMA length (in long word here)
        "move.l   %[dmaLen],(%[dmaCtrl_ptr])\n\t" // *((vu32*) VDP_CTRL_PORT) = ((0x9300 | (u8)len) << 16) | (0x9400 | (u8)(len >> 8));
        // Setup DMA address low
        //"move.w   #0x9500,%[dn]\n\t"              // dn: 0x9500
        "or.b     %[fromAddr],%[dn]\n\t"          // dn: 0x9500 | (u8)(fromAddr)
        "move.w   %[dn],(%[dmaCtrl_ptr])\n\t"     // *((vu16*) VDP_CTRL_PORT) = 0x9500 | (u8)fromAddr; // low
        // Setup DMA address mid
        "move.w   %[fromAddr],-(%%sp)\n\t"
        "move.w   #0x9600,%[dn]\n\t"              // dn: 0x9600
        "or.b     (%%sp)+,%[dn]\n\t"              // dn: 0x9600 | (u8)(fromAddr >> 8)
        "move.w   %[dn],(%[dmaCtrl_ptr])\n\t"     // *((vu16*) VDP_CTRL_PORT) = 0x9600 | (u8)(fromAddr >> 8); // mid
        // Setup DMA address high
        "move.l   %[fromAddr],%[dn]\n\t"          // dn: fromAddr
        "swap     %[dn]\n\t"                      // dn: fromAddr >> 16
        "andi.w   #0x007f,%[dn]\n\t"              // dn: (fromAddr >> 16) & 0x7f
        "ori.w    #0x9700,%[dn]\n\t"              // dn: 0x9700 | ((fromAddr >> 16) & 0x7f)
        "move.w   %[dn],(%[dmaCtrl_ptr])"         // *((vu16*) VDP_CTRL_PORT) = 0x9700 | ((fromAddr >> 16) & 0x7f); // high
        : [dmaCtrl_ptr] "+a" (dmaCtrl_ptr), [dn] "+d" (dn)
        : [fromAddr] "d" (fromAddr), [dmaLen] "id" (dmaLen)
        : "cc"
    );
}

static u16* palInFrameRootPtr; // points to the first pals the HInt starts to load
static u16* palInFramePtr; // pals pointer increased in every HInt call cycle
static u16 palCmdAddrrToggle = HINT_PALS_CMD_ADDR_RESET_VALUE; // used to toggle between the two different CRAM cmd addresses used to send the pals
static u16 vcounterManual = HINT_COUNTER_FOR_COLORS_UPDATE - 1;

void setMoviePalsPointerBeforeInterrupts (u16* rootPalsPtr)
{
    palInFrameRootPtr = rootPalsPtr;
    palInFramePtr = rootPalsPtr;
}

void setMoviePalsPointer (u16* rootPalsPtr)
{
    palInFrameRootPtr = rootPalsPtr;
}

void VIntMovieCallback ()
{
	palInFramePtr = palInFrameRootPtr; // Resets to 1st strip's palettes due to ptr modification made by HInt
	palCmdAddrrToggle = HINT_PALS_CMD_ADDR_RESET_VALUE; // Resets pal index due to modification made by HInt.
	vcounterManual = HINT_COUNTER_FOR_COLORS_UPDATE - 1; // Resets due to modification made by HInt
}

static FORCE_INLINE void swapPalettes_CPU_ASM ()
{
    __asm volatile (
        // prepare_regs
        "   movea.l     %c[palInFramePtr],%%a0\n" // a0: palInFramePtr
        "   lea         0xC00004,%%a1\n"          // a1: VDP_CTRL_PORT 0xC00004
        "   lea         -4(%%a1),%%a2\n"          // a2: VDP_DATA_PORT 0xC00000
        "   lea         5(%%a1),%%a3\n"           // a3: HCounter address 0xC00009 (VDP_HVCOUNTER_PORT + 1)
        "   move.w      %[turnOff],%%d4\n"        // d4: VDP's register with display OFF value
        "   move.w      %[turnOn],%%d5\n"         // d5: VDP's register with display ON value
        "   move.b      %[hcLimit],%%d7\n"        // d7: HCounter limit
        "   movea.l     #0x100000,%%a4\n"         // a4: 0x100000 is the command offset for 8 colors sent to the VDP, used as: cmdAddress += 0x100000

		// color_batch_1_cmd:
		// cmdAddress = palIdx == 0 ? 0xC0000000 : 0xC0400000;
        // set base command address once and then we'll add the right offset in next color batch blocks
		"   move.l      #0xC0000000,%%d6\n"     // d6: cmdAddress = 0xC0000000
		"   tst.b       %[palIdx]\n"            // palIdx == 0?
		"   beq.s       0f\n"
		"   move.l      #0xC0400000,%%d6\n"     // d6: cmdAddress = 0xC0400000
        "0:\n"
		// color_batch_1_pal:
        //"   move.l      (%%a0)+,%%d0\n"         // d0: colors2_A = *((u32*) (palInFramePtr + 0)); // 2 colors
		//"   move.l      (%%a0)+,%%d1\n"         // d1: colors2_B = *((u32*) (palInFramePtr + 2)); // next 2 colors
		//"   move.l      (%%a0)+,%%d2\n"         // d2: colors2_C = *((u32*) (palInFramePtr + 4)); // next 2 colors
		//"   move.l      (%%a0)+,%%d3\n"         // d3: colors2_D = *((u32*) (palInFramePtr + 6)); // next 2 colors
        "   movem.l     (%%a0)+,%%d0-%%d3\n"
        // wait HCounter
        "1:\n"
        "   cmp.b       (%%a3),%%d7\n"          // cmp: d7 - (a3). Compare byte size given that d7 won't be > 160 for our practical cases
        "   bhi.s       1b\n"                   // loop back if d7 is higher than (a3)
		// turn off VDP
		"   move.w      %%d4,(%%a1)\n"          // *(vu16*) VDP_CTRL_PORT = 0x8100 | (reg01 & ~0x40);
		// send colors
		"   move.l      %%d6,(%%a1)\n"          // *((vu32*) VDP_CTRL_PORT) = cmdAddress;
		"   move.l      %%d0,(%%a2)\n"          // *((vu32*) VDP_DATA_PORT) = colors2_A;
		"   move.l      %%d1,(%%a2)\n"          // *((vu32*) VDP_DATA_PORT) = colors2_B;
		"   move.l      %%d2,(%%a2)\n"          // *((vu32*) VDP_DATA_PORT) = colors2_C;
		"   move.l      %%d3,(%%a2)\n"          // *((vu32*) VDP_DATA_PORT) = colors2_D;
		// turn on VDP
		"   move.w      %%d5,(%%a1)\n"          // *(vu16*) VDP_CTRL_PORT = 0x8100 | (reg01 | 0x40);

		// color_batch_2_cmd
		// cmdAddress = palIdx == 0 ? 0xC0100000 : 0xC0500000;
		"   add.l       %%a4,%%d6\n"            // d6: cmdAddress += 0x100000 // previous batch advanced 8 colors
		// color_batch_2_pal
		//"   move.l      (%%a0)+,%%d0\n"         // d0: colors2_A = *((u32*) (palInFramePtr + 8)); // 2 colors
		//"   move.l      (%%a0)+,%%d1\n"         // d1: colors2_B = *((u32*) (palInFramePtr + 10)); // next 2 colors
		//"   move.l      (%%a0)+,%%d2\n"         // d2: colors2_C = *((u32*) (palInFramePtr + 12)); // next 2 colors
		//"   move.l      (%%a0)+,%%d3\n"         // d3: colors2_D = *((u32*) (palInFramePtr + 14)); // next 2 colors
        "   movem.l     (%%a0)+,%%d0-%%d3\n"
        // wait HCounter
        "1:\n"
        "   cmp.b       (%%a3),%%d7\n"          // cmp: d7 - (a3). Compare byte size given that d7 won't be > 160 for our practical cases
        "   bhi.s       1b\n"                   // loop back if d7 is higher than (a3)
		// turn off VDP
		"   move.w      %%d4,(%%a1)\n"          // *(vu16*) VDP_CTRL_PORT = 0x8100 | (reg01 & ~0x40);
		// send colors
		"   move.l      %%d6,(%%a1)\n"          // *((vu32*) VDP_CTRL_PORT) = cmdAddress;
		"   move.l      %%d0,(%%a2)\n"          // *((vu32*) VDP_DATA_PORT) = colors2_A;
		"   move.l      %%d1,(%%a2)\n"          // *((vu32*) VDP_DATA_PORT) = colors2_B;
		"   move.l      %%d2,(%%a2)\n"          // *((vu32*) VDP_DATA_PORT) = colors2_C;
		"   move.l      %%d3,(%%a2)\n"          // *((vu32*) VDP_DATA_PORT) = colors2_D;
		// turn on VDP
		"   move.w      %%d5,(%%a1)\n"          // *(vu16*) VDP_CTRL_PORT = 0x8100 | (reg01 | 0x40);

		// color_batch_3_cmd
		// cmdAddress = palIdx == 0 ? 0xC0200000 : 0xC0600000;
		"   add.l       %%a4,%%d6\n"            // d6: cmdAddress += 0x100000 // previous batch advanced 8 colors
		// color_batch_3_pal
		//"   move.l      (%%a0)+,%%d0\n"         // d0: colors2_A = *((u32*) (palInFramePtr + 16)); // 2 colors
		//"   move.l      (%%a0)+,%%d1\n"         // d1: colors2_B = *((u32*) (palInFramePtr + 18)); // next 2 colors
		//"   move.l      (%%a0)+,%%d2\n"         // d2: colors2_C = *((u32*) (palInFramePtr + 20)); // next 2 colors
		//"   move.l      (%%a0)+,%%d3\n"         // d3: colors2_D = *((u32*) (palInFramePtr + 22)); // next 2 colors
        "   movem.l     (%%a0)+,%%d0-%%d3\n"
        // wait HCounter
        "1:\n"
        "   cmp.b       (%%a3),%%d7\n"          // cmp: d7 - (a3). Compare byte size given that d7 won't be > 160 for our practical cases
        "   bhi.s       1b\n"                   // loop back if d7 is higher than (a3)
		// turn off VDP
		"   move.w      %%d4,(%%a1)\n"          // *(vu16*) VDP_CTRL_PORT = 0x8100 | (reg01 & ~0x40);
		// send colors
		"   move.l      %%d6,(%%a1)\n"          // *((vu32*) VDP_CTRL_PORT) = cmdAddress;
		"   move.l      %%d0,(%%a2)\n"          // *((vu32*) VDP_DATA_PORT) = colors2_A;
		"   move.l      %%d1,(%%a2)\n"          // *((vu32*) VDP_DATA_PORT) = colors2_B;
		"   move.l      %%d2,(%%a2)\n"          // *((vu32*) VDP_DATA_PORT) = colors2_C;
		"   move.l      %%d3,(%%a2)\n"          // *((vu32*) VDP_DATA_PORT) = colors2_D;
		// turn on VDP
		"   move.w      %%d5,(%%a1)\n"          // *(vu16*) VDP_CTRL_PORT = 0x8100 | (reg01 | 0x40);

		// color_batch_4_cmd
		// cmdAddress = palIdx == 0 ? 0xC0300000 : 0xC0700000;
		"   add.l       %%a4,%%d6\n"            // d6: cmdAddress += 0x100000 // previous batch advanced 8 colors
		// color_batch_4_pal
		//"   move.l      (%%a0)+,%%d0\n"         // d0: colors2_A = *((u32*) (palInFramePtr + 24)); // 2 colors
		//"   move.l      (%%a0)+,%%d1\n"         // d1: colors2_B = *((u32*) (palInFramePtr + 26)); // next 2 colors
		//"   move.l      (%%a0)+,%%d2\n"         // d2: colors2_C = *((u32*) (palInFramePtr + 28)); // next 2 colors
		//"   move.l      (%%a0)+,%%d3\n"         // d3: colors2_D = *((u32*) (palInFramePtr + 30)); // next 2 colors
        "   movem.l     (%%a0)+,%%d0-%%d3\n"

        // Accomodate vars here so we can aliviate the waitHCounter loop and exit the HInt sooner
        "   eori.b      %[_MOVIE_FRAME_COLORS_PER_STRIP],%c[palIdx]\n"  // palIdx ^= MOVIE_FRAME_COLORS_PER_STRIP // cycles between 0 and 32
        "   move.l      %%a0,%c[palInFramePtr]\n"                       // store current pointer value of a0 into variable palInFramePtr

        // wait HCounter
        "1:\n"
        "   cmp.b       (%%a3),%%d7\n"          // cmp: d7 - (a3). Compare byte size given that d7 won't be > 160 for our practical cases
        "   bhi.s       1b\n"                   // loop back if d7 is higher than (a3)
		// turn off VDP
		"   move.w      %%d4,(%%a1)\n"          // *(vu16*) VDP_CTRL_PORT = 0x8100 | (reg01 & ~0x40);
		// send colors
		"   move.l      %%d6,(%%a1)\n"          // *((vu32*) VDP_CTRL_PORT) = cmdAddress;
		"   move.l      %%d0,(%%a2)\n"          // *((vu32*) VDP_DATA_PORT) = colors2_A;
		"   move.l      %%d1,(%%a2)\n"          // *((vu32*) VDP_DATA_PORT) = colors2_B;
		"   move.l      %%d2,(%%a2)\n"          // *((vu32*) VDP_DATA_PORT) = colors2_C;
		"   move.l      %%d3,(%%a2)\n"          // *((vu32*) VDP_DATA_PORT) = colors2_D;
		// turn on VDP
		"   move.w      %%d5,(%%a1)\n"          // *(vu16*) VDP_CTRL_PORT = 0x8100 | (reg01 | 0x40);
		: 
		[palInFramePtr] "+m" (palInFramePtr),
		[palIdx] "+m" (palCmdAddrrToggle)
		: 
		[turnOff] "i" (0x8100 | (0x74 & ~0x40)), // 0x8134
		[turnOn] "i" (0x8100 | (0x74 | 0x40)), // 0x8174
        [hcLimit] "i" (156),
		[_MOVIE_FRAME_COLORS_PER_STRIP] "i" (MOVIE_FRAME_COLORS_PER_STRIP)
		:
        // backup registers used in the asm implementation including the scratch pad since this code is used in an interrupt call.
		"d0","d1","d2","d3","d4","d5","d6","d7","a0","a1","a2","a3","a4","cc","memory"
    );
}

static FORCE_INLINE void swapPalettes_CPU ()
{
    /*
        Every command is CRAM address to start write 4 colors (2 times u32 bits)
        u32 cmd1st = VDP_WRITE_CRAM_ADDR((u32)((palIdx + 0) * 2));
        u32 cmd2nd = VDP_WRITE_CRAM_ADDR((u32)((palIdx + 4) * 2));
        u32 cmd3rd = VDP_WRITE_CRAM_ADDR((u32)((palIdx + 8) * 2));
        u32 cmd4th = VDP_WRITE_CRAM_ADDR((u32)((palIdx + 12) * 2));
        u32 cmd5th = VDP_WRITE_CRAM_ADDR((u32)((palIdx + 16) * 2));
        u32 cmd6th = VDP_WRITE_CRAM_ADDR((u32)((palIdx + 20) * 2));
        u32 cmd7th = VDP_WRITE_CRAM_ADDR((u32)((palIdx + 24) * 2));
        u32 cmd8th = VDP_WRITE_CRAM_ADDR((u32)((palIdx + 28) * 2));
        cmd     palIdx = 0      palIdx = 32
        1       0xC0000000      0xC0400000
        2       0xC0080000      0xC0480000
        3       0xC0100000      0xC0500000
        4       0xC0180000      0xC0580000
        5       0xC0200000      0xC0600000
        6       0xC0280000      0xC0680000
        7       0xC0300000      0xC0700000
        8       0xC0380000      0xC0780000
    */

    u32 cmdAddress;
	u32 colors2_A, colors2_B, colors2_C, colors2_D;

	// Value under current conditions is always 0x74
    //u8 reg01 = VDP_getReg(0x01); // Holds current VDP register 1 value (it holds other bits than VDP ON/OFF status)
    // NOTE: here is OK to call VDP_getReg(0x01) only if we didn't previously change the the VDP's reg 1 using direct access without VDP_setReg()

	colors2_A = *((u32*) (palInFramePtr + 0)); // 2 colors
	colors2_B = *((u32*) (palInFramePtr + 2)); // next 2 colors
	colors2_C = *((u32*) (palInFramePtr + 4)); // next 2 colors
	colors2_D = *((u32*) (palInFramePtr + 6)); // next 2 colors
	cmdAddress = (palCmdAddrrToggle == 0) ? 0xC0000000 : 0xC0400000;
	waitHCounter_opt1(145);
	turnOffVDP(0x74);
	*((vu32*) VDP_CTRL_PORT) = cmdAddress;
	*((vu32*) VDP_DATA_PORT) = colors2_A;
	*((vu32*) VDP_DATA_PORT) = colors2_B;
	*((vu32*) VDP_DATA_PORT) = colors2_C;
	*((vu32*) VDP_DATA_PORT) = colors2_D;
	turnOnVDP(0x74);

	colors2_A = *((u32*) (palInFramePtr +  8)); // 2 colors
	colors2_B = *((u32*) (palInFramePtr + 10)); // next 2 colors
	colors2_C = *((u32*) (palInFramePtr + 12)); // next 2 colors
	colors2_D = *((u32*) (palInFramePtr + 14)); // next 2 colors
	cmdAddress = (palCmdAddrrToggle == 0) ? 0xC0100000 : 0xC0500000;
	waitHCounter_opt1(145);
	turnOffVDP(0x74);
	*((vu32*) VDP_CTRL_PORT) = cmdAddress;
	*((vu32*) VDP_DATA_PORT) = colors2_A;
	*((vu32*) VDP_DATA_PORT) = colors2_B;
	*((vu32*) VDP_DATA_PORT) = colors2_C;
	*((vu32*) VDP_DATA_PORT) = colors2_D;
	turnOnVDP(0x74);

	colors2_A = *((u32*) (palInFramePtr + 16)); // 2 colors
	colors2_B = *((u32*) (palInFramePtr + 18)); // next 2 colors
	colors2_C = *((u32*) (palInFramePtr + 20)); // next 2 colors
	colors2_D = *((u32*) (palInFramePtr + 22)); // next 2 colors
	cmdAddress = (palCmdAddrrToggle == 0) ? 0xC0200000 : 0xC0600000;
	waitHCounter_opt1(145);
	turnOffVDP(0x74);
	*((vu32*) VDP_CTRL_PORT) = cmdAddress;
	*((vu32*) VDP_DATA_PORT) = colors2_A;
	*((vu32*) VDP_DATA_PORT) = colors2_B;
	*((vu32*) VDP_DATA_PORT) = colors2_C;
	*((vu32*) VDP_DATA_PORT) = colors2_D;
	turnOnVDP(0x74);

	colors2_A = *((u32*) (palInFramePtr + 24)); // 2 colors
	colors2_B = *((u32*) (palInFramePtr + 26)); // next 2 colors
	colors2_C = *((u32*) (palInFramePtr + 28)); // next 2 colors
	colors2_D = *((u32*) (palInFramePtr + 30)); // next 2 colors
	cmdAddress = (palCmdAddrrToggle == 0) ? 0xC0300000 : 0xC0700000;

    // Prepare vars for next HInt here so we can aliviate the waitHCounter loop and exit the HInt sooner
    palInFramePtr += MOVIE_FRAME_COLORS_PER_STRIP; // advance to next strip's palettes (if pointer wasn't incremented previously)
	palCmdAddrrToggle ^= MOVIE_FRAME_COLORS_PER_STRIP; // cycles between 0 and 32
    //palCmdAddrrToggle = palCmdAddrrToggle == 0 ? 32 : 0;
    //palCmdAddrrToggle = (palCmdAddrrToggle + 32) & 63; // (palCmdAddrrToggle + 32) % 64 => x mod y = x & (y-1) when y is power of 2

	waitHCounter_opt1(145);
	turnOffVDP(0x74);
	*((vu32*) VDP_CTRL_PORT) = cmdAddress;
	*((vu32*) VDP_DATA_PORT) = colors2_A;
	*((vu32*) VDP_DATA_PORT) = colors2_B;
	*((vu32*) VDP_DATA_PORT) = colors2_C;
	*((vu32*) VDP_DATA_PORT) = colors2_D;
	turnOnVDP(0x74);
}

HINTERRUPT_CALLBACK HIntCallback_CPU_NTSC ()
{
	if (vcounterManual < MOVIE_HINT_COLORS_SWAP_START_SCANLINE_NTSC || vcounterManual > MOVIE_HINT_COLORS_SWAP_END_SCANLINE_NTSC) {
	    vcounterManual += HINT_COUNTER_FOR_COLORS_UPDATE;
    }
    else {
        vcounterManual += HINT_COUNTER_FOR_COLORS_UPDATE;
        swapPalettes_CPU_ASM();
    }
}

HINTERRUPT_CALLBACK HIntCallback_CPU_PAL ()
{
	if (vcounterManual < MOVIE_HINT_COLORS_SWAP_START_SCANLINE_PAL || vcounterManual > MOVIE_HINT_COLORS_SWAP_END_SCANLINE_PAL) {
	    vcounterManual += HINT_COUNTER_FOR_COLORS_UPDATE;
    }
    else {
        vcounterManual += HINT_COUNTER_FOR_COLORS_UPDATE;
        swapPalettes_CPU_ASM();
    }
}

static FORCE_INLINE void swapPalettes_DMA_2_cmds_ASM ()
{
    __asm volatile (
        // prepare_regs
        "   movea.l     %c[palInFramePtr],%%a0\n" // a0: palInFramePtr
        "   lea         0xC00004,%%a1\n"          // a1: VDP_CTRL_PORT 0xC00004
        "   lea         5(%%a1),%%a2\n"           // a2: HCounter address 0xC00009 (VDP_HVCOUNTER_PORT + 1)
        "   move.w      %[turnOff],%%d3\n"        // d3: VDP's register with display OFF value
        "   move.w      %[turnOn],%%d4\n"         // d4: VDP's register with display ON value
        "   move.b      %[hcLimit],%%d6\n"        // d6: HCounter limit
        "   movea.l     #0x200000,%%a3\n"         // a3: 0x200000 is the command offset for 16 colors (MOVIE_FRAME_COLORS_PER_STRIP/2) sent to the VDP, used as: cmdAddress += 0x200000

        // DMA batch 1
        "   move.l      %%a0,%%d2\n"            // d2: palInFramePtr
        "   lea         (%c[_MOVIE_FRAME_COLORS_PER_STRIP_DIV_2]*2,%%a0),%%a0\n"  // palInFramePtr += MOVIE_FRAME_COLORS_PER_STRIP/2;
        // palCmdForDMA = palIdx == 0 ? 0xC0000080 : 0xC0400080;
        // set base command address once and then we'll add the right offset in next sets
		"   move.l      #0xC0000080,%%d5\n"     // d5: palCmdForDMA = 0xC0000080
		"   tst.b       %[palIdx]\n"            // palIdx == 0?
		"   beq.s       0f\n"
		"   move.l      #0xC0400080,%%d5\n"     // d5: palCmdForDMA = 0xC0400080
        "0:\n"
        // Setup DMA command
        "   lsr.w       #1,%%d2\n"              // d2: fromAddrForDMA = (u32) palInFramePtr >> 1;
            // NOTE: previous lsr.l can be replaced by lsr.w in case we don't need to use d2: fromAddrForDMA >> 16
        "   move.w      #0x9500,%%d0\n"         // d0: 0x9500
        "   or.b        %%d2,%%d0\n"            // d0: 0x9500 | (u8)(fromAddrForDMA)
        "   move.w      %%d2,-(%%sp)\n"
        "   move.w      #0x9600,%%d1\n"         // d1: 0x9600
        "   or.b        (%%sp)+,%%d1\n"         // d1: 0x9600 | (u8)(fromAddrForDMA >> 8)
        //"   swap        %%d2\n"                 // d2: fromAddrForDMA >> 16
        //"   andi.w      #0x007f,%%d2\n"         // d2: (fromAddrForDMA >> 16) & 0x7f
            // NOTE: previous & 0x7f operation might be discarded if higher bits are somehow already zeroed
        //"   ori.w       #0x9700,%%d2\n"         // d2: 0x9700 | ((fromAddrForDMA >> 16) & 0x7f)
        // Setup DMA length (in word here)
        "   move.w      %[_DMA_9300_LEN_DIV_2],(%%a1)\n"  // *((vu16*) VDP_CTRL_PORT) = 0x9300 | ((MOVIE_FRAME_COLORS_PER_STRIP/2) & 0xff);
        //"   move.w      %[_DMA_9400_LEN_DIV_2],(%%a1)\n"  // *((vu16*) VDP_CTRL_PORT) = 0x9400 | (((MOVIE_FRAME_COLORS_PER_STRIP/2) >> 8) & 0xff);
        // Setup DMA length (in long word here)
        //"   move.l      %[_DMA_9300_9400_LEN_DIV_2],(%%a1)\n"
        // Setup DMA address
        "   move.w      %%d0,(%%a1)\n"          // *((vu16*) VDP_CTRL_PORT) = 0x9500 | (u8)(fromAddrForDMA);
        "   move.w      %%d1,(%%a1)\n"          // *((vu16*) VDP_CTRL_PORT) = 0x9600 | (u8)(fromAddrForDMA >> 8);
        //"   move.w      %%d2,(%%a1)\n"          // *((vu16*) VDP_CTRL_PORT) = 0x9700 | (u8)((fromAddrForDMA >> 16) & 0x7f);
        // wait HCounter
        "1:\n"
        "   cmp.b       (%%a2),%%d6\n"          // cmp: d6 - (a2). Compare byte size given that d6 won't be > 160 for our practical cases
        "   bhi.s       1b\n"                   // loop back if d6 is higher than (a2)
		// turn off VDP
		"   move.w      %%d3,(%%a1)\n"          // *(vu16*) VDP_CTRL_PORT = 0x8100 | (reg01 & ~0x40);
        // trigger DMA transfer
        "   move.l      %%d5,(%%a1)\n"          // *((vu32*) VDP_CTRL_PORT) = palCmdForDMA;
		// turn on VDP
		"   move.w      %%d4,(%%a1)\n"          // *(vu16*) VDP_CTRL_PORT = 0x8100 | (reg01 | 0x40);

        // DMA batch 2
        "   move.l      %%a0,%%d2\n"            // d2: palInFramePtr
        "   lea         (%c[_MOVIE_FRAME_COLORS_PER_STRIP_DIV_2]*2,%%a0),%%a0\n"  // palInFramePtr += MOVIE_FRAME_COLORS_PER_STRIP/2;
        // palCmdForDMA = palCmdAddrrToggle == 0 ? 0xC0200080 : 0xC0600080;
		"   add.l       %%a3,%%d5\n"            // d5: palCmdForDMA += 0x200000 // previous batch advanced 16 colors (MOVIE_FRAME_COLORS_PER_STRIP/2)
        // Setup DMA command
        "   lsr.w       #1,%%d2\n"              // d2: fromAddrForDMA = (u32) palInFramePtr >> 1;
            // NOTE: previous lsr.l can be replaced by lsr.w in case we don't need to use d2: fromAddrForDMA >> 16
        "   move.w      #0x9500,%%d0\n"         // d0: 0x9500
        "   or.b        %%d2,%%d0\n"            // d0: 0x9500 | (u8)(fromAddrForDMA)
        "   move.w      %%d2,-(%%sp)\n"
        "   move.w      #0x9600,%%d1\n"         // d1: 0x9600
        "   or.b        (%%sp)+,%%d1\n"         // d1: 0x9600 | (u8)(fromAddrForDMA >> 8)
        //"   swap        %%d2\n"                 // d2: fromAddrForDMA >> 16
        //"   andi.w      #0x007f,%%d2\n"         // d2: (fromAddrForDMA >> 16) & 0x7f
            // NOTE: previous & 0x7f operation might be discarded if higher bits are somehow already zeroed
        //"   ori.w       #0x9700,%%d2\n"         // d2: 0x9700 | (u8)((fromAddrForDMA >> 16) & 0x7f)
        // Setup DMA length (in word here)
        "   move.w      %[_DMA_9300_LEN_DIV_2],(%%a1)\n"  // *((vu16*) VDP_CTRL_PORT) = 0x9300 | ((MOVIE_FRAME_COLORS_PER_STRIP/2) & 0xff);
        //"   move.w      %[_DMA_9400_LEN_DIV_2],(%%a1)\n"  // *((vu16*) VDP_CTRL_PORT) = 0x9400 | (((MOVIE_FRAME_COLORS_PER_STRIP/2) >> 8) & 0xff);
        // Setup DMA length (in long word here)
        //"   move.l      %[_DMA_9300_9400_LEN_DIV_2],(%%a1)\n"
        // Setup DMA address
        "   move.w      %%d0,(%%a1)\n"          // *((vu16*) VDP_CTRL_PORT) = 0x9500 | (u8)(fromAddrForDMA);
        "   move.w      %%d1,(%%a1)\n"          // *((vu16*) VDP_CTRL_PORT) = 0x9600 | (u8)(fromAddrForDMA >> 8);
        //"   move.w      %%d2,(%%a1)\n"          // *((vu16*) VDP_CTRL_PORT) = 0x9700 | (u8)((fromAddrForDMA >> 16) & 0x7f);
        // Prepare vars for next HInt here so we can aliviate the waitHCounter loop and exit the HInt sooner
        "   eori.b      %[_MOVIE_FRAME_COLORS_PER_STRIP],%c[palIdx]\n"  // palIdx ^= MOVIE_FRAME_COLORS_PER_STRIP // cycles between 0 and 32
        "   move.l      %%a0,%c[palInFramePtr]\n"                       // store current pointer value of a0 into variable palInFramePtr
        // wait HCounter
        "1:\n"
        "   cmp.b       (%%a2),%%d6\n"          // cmp: d6 - (a2). Compare byte size given that d6 won't be > 160 for our practical cases
        "   bhi.s       1b\n"                   // loop back if d6 is higher than (a2)
		// turn off VDP
		"   move.w      %%d3,(%%a1)\n"          // *(vu16*) VDP_CTRL_PORT = 0x8100 | (reg01 & ~0x40);
        // trigger DMA transfer
        "   move.l      %%d5,(%%a1)\n"          // *((vu32*) VDP_CTRL_PORT) = palCmdForDMA;
		// turn on VDP
		"   move.w      %%d4,(%%a1)\n"          // *(vu16*) VDP_CTRL_PORT = 0x8100 | (reg01 | 0x40);
		: 
		[palInFramePtr] "+m" (palInFramePtr),
		[palIdx] "+m" (palCmdAddrrToggle)
		: 
		[turnOff] "i" (0x8100 | (0x74 & ~0x40)), // 0x8134
		[turnOn] "i" (0x8100 | (0x74 | 0x40)), // 0x8174
        [hcLimit] "i" (152),
        [_DMA_9300_LEN_DIV_2] "i" (0x9300 | ((MOVIE_FRAME_COLORS_PER_STRIP/2) & 0xff)),
        [_DMA_9400_LEN_DIV_2] "i" (0x9400 | (((MOVIE_FRAME_COLORS_PER_STRIP/2) >> 8) & 0xff)),
        [_DMA_9300_9400_LEN_DIV_2] "i" ( ((0x9300 | ((MOVIE_FRAME_COLORS_PER_STRIP/2) & 0xff)) << 16) | (0x9400 | (((MOVIE_FRAME_COLORS_PER_STRIP/2) >> 8) & 0xff)) ),
		[_MOVIE_FRAME_COLORS_PER_STRIP] "i" (MOVIE_FRAME_COLORS_PER_STRIP),
        [_MOVIE_FRAME_COLORS_PER_STRIP_DIV_2] "i" (MOVIE_FRAME_COLORS_PER_STRIP/2)
		:
        // backup registers used in the asm implementation including the scratch pad since this code is used in an interrupt call.
		"d0","d1","d2","d3","d4","d5","d6","a0","a1","a2","a3","cc","memory"
    );
}

static FORCE_INLINE void swapPalettes_DMA_2_cmds ()
{
    /*
        With 2 DMA commands and same DMA lengths:
        Every command is CRAM address to start DMA MOVIE_FRAME_COLORS_PER_STRIP/2 colors
        u32 palCmdForDMA_A = VDP_DMA_CRAM_ADDR((u32)(palIdx + 0) * 2);
        u32 palCmdForDMA_B = VDP_DMA_CRAM_ADDR(((u32)palIdx + MOVIE_FRAME_COLORS_PER_STRIP/2) * 2);
        cmd     palIdx = 0      palIdx = 32
        A       0xC0000080      0xC0400080
        B       0xC0200080      0xC0600080
    */

    u32 palCmdForDMA;
    u32 fromAddrForDMA;

	// Value under current conditions is always 0x74
    //u8 reg01 = VDP_getReg(0x01); // Holds current VDP register 1 value (it holds other bits than VDP ON/OFF status)
    // NOTE: here is OK to call VDP_getReg(0x01) only if we didn't previously change the the VDP's reg 1 using direct access without VDP_setReg()

    fromAddrForDMA = (u32) palInFramePtr >> 1;
    palInFramePtr += MOVIE_FRAME_COLORS_PER_STRIP/2; // advance into next color batch
    palCmdForDMA = palCmdAddrrToggle == 0 ? 0xC0000080 : 0xC0400080;
MEMORY_BARRIER();
    // Setup DMA length (in long word here): low at higher word, high at lower word
    *((vu32*) VDP_CTRL_PORT) = ((0x9300 | ((MOVIE_FRAME_COLORS_PER_STRIP/2) & 0xff)) << 16) |
            (0x9400 | (((MOVIE_FRAME_COLORS_PER_STRIP/2) >> 8) & 0xff));
    // Setup DMA address
    *((vu16*) VDP_CTRL_PORT) = 0x9500 | (u8)(fromAddrForDMA);
    *((vu16*) VDP_CTRL_PORT) = 0x9600 | (u8)(fromAddrForDMA >> 8);
    //*((vu16*) VDP_CTRL_PORT) = 0x9700 | ((fromAddrForDMA >> 16) & 0x7f);

    waitHCounter_opt2(152);
    turnOffVDP(0x74);
    *((vu32*) VDP_CTRL_PORT) = palCmdForDMA; // trigger DMA transfer
    turnOnVDP(0x74);

    fromAddrForDMA = (u32) palInFramePtr >> 1;
    palInFramePtr += MOVIE_FRAME_COLORS_PER_STRIP/2; // advance into next color batch
    palCmdForDMA = palCmdAddrrToggle == 0 ? 0xC0200080 : 0xC0600080; // advance command for next MOVIE_FRAME_COLORS_PER_STRIP/2 colors
MEMORY_BARRIER();
    //setupDMAForPals(16, fromAddrForDMA);
    // Setup DMA length (in long word here): low at higher word, high at lower word
    *((vu32*) VDP_CTRL_PORT) = ((0x9300 | ((MOVIE_FRAME_COLORS_PER_STRIP/2) & 0xff)) << 16) |
            (0x9400 | (((MOVIE_FRAME_COLORS_PER_STRIP/2) >> 8) & 0xff));
    // Setup DMA address
    *((vu16*) VDP_CTRL_PORT) = 0x9500 | (u8)(fromAddrForDMA);
    *((vu16*) VDP_CTRL_PORT) = 0x9600 | (u8)(fromAddrForDMA >> 8);
    //*((vu16*) VDP_CTRL_PORT) = 0x9700 | ((fromAddrForDMA >> 16) & 0x7f);

    // Prepare vars for next HInt here so we can aliviate the waitHCounter loop and exit the HInt sooner
    //palInFramePtr += MOVIE_FRAME_COLORS_PER_STRIP; // advance to next strip's palettes (if pointer wasn't incremented previously)
	palCmdAddrrToggle ^= MOVIE_FRAME_COLORS_PER_STRIP; // cycles between 0 and 32

    waitHCounter_opt2(152);
    turnOffVDP(0x74);
    *((vu32*) VDP_CTRL_PORT) = palCmdForDMA; // trigger DMA transfer
    turnOnVDP(0x74);
}

static FORCE_INLINE void swapPalettes_DMA_3_cmds_ASM ()
{
    /*
        With 3 DMA commands and different DMA lenghts:
        Every command is CRAM address to start DMA MOVIE_FRAME_COLORS_PER_STRIP/3. The last one issues MOVIE_FRAME_COLORS_PER_STRIP/3 + REMAINDER.
        u32 palCmdForDMA_A = VDP_DMA_CRAM_ADDR((u32)(palIdx + 0) * 2);
        u32 palCmdForDMA_B = VDP_DMA_CRAM_ADDR(((u32)palIdx + MOVIE_FRAME_COLORS_PER_STRIP/3) * 2);
        u32 palCmdForDMA_C = VDP_DMA_CRAM_ADDR(((u32)palIdx + (MOVIE_FRAME_COLORS_PER_STRIP/3)*2) * 2);
        cmd     palIdx = 0      palIdx = 32
        A       0xC0000080      0xC0400080
        B       0xC0140080      0xC0540080
        C       0xC0280080      0xC0680080
    */

    __asm volatile (
        // prepare_regs
        "   movea.l     %c[palInFramePtr],%%a0\n" // a0: palInFramePtr
        "   lea         0xC00004,%%a1\n"          // a1: VDP_CTRL_PORT 0xC00004
        "   lea         5(%%a1),%%a2\n"           // a2: HCounter address 0xC00009 (VDP_HVCOUNTER_PORT + 1)
        "   move.w      %[turnOff],%%d3\n"        // d3: VDP's register with display OFF value
        "   move.w      %[turnOn],%%d4\n"         // d4: VDP's register with display ON value
        "   move.b      %[hcLimit],%%d6\n"        // d6: HCounter limit
        "   movea.l     #0x140000,%%a3\n"         // a3: 0x140000 is the command offset for 10 colors (MOVIE_FRAME_COLORS_PER_STRIP/3) sent to the VDP, used as: cmdAddress += 0x140000

        // DMA batch 1
        "   move.l      %%a0,%%d2\n"            // d2: palInFramePtr
        "   lea         (%c[_MOVIE_FRAME_COLORS_PER_STRIP_DIV_3]*2,%%a0),%%a0\n"  // palInFramePtr += MOVIE_FRAME_COLORS_PER_STRIP/3;
        // palCmdForDMA = palIdx == 0 ? 0xC0000080 : 0xC0400080;
        // set base command address once and then we'll add the right offset in next sets
		"   move.l      #0xC0000080,%%d5\n"     // d5: palCmdForDMA = 0xC0000080
		"   tst.b       %[palIdx]\n"            // palIdx == 0?
		"   beq.s       0f\n"
		"   move.l      #0xC0400080,%%d5\n"     // d5: palCmdForDMA = 0xC0400080
        "0:\n"
        // Setup DMA command
        "   lsr.w       #1,%%d2\n"              // d2: fromAddrForDMA = (u32) palInFramePtr >> 1;
            // NOTE: previous lsr.l can be replaced by lsr.w in case we don't need to use d2: fromAddrForDMA >> 16
        "   move.w      #0x9500,%%d0\n"         // d0: 0x9500
        "   or.b        %%d2,%%d0\n"            // d0: 0x9500 | (u8)(fromAddrForDMA)
        "   move.w      %%d2,-(%%sp)\n"
        "   move.w      #0x9600,%%d1\n"         // d1: 0x9600
        "   or.b        (%%sp)+,%%d1\n"         // d1: 0x9600 | (u8)(fromAddrForDMA >> 8)
        //"   swap        %%d2\n"                 // d2: fromAddrForDMA >> 16
        //"   andi.w      #0x007f,%%d2\n"         // d2: (fromAddrForDMA >> 16) & 0x7f
            // NOTE: previous & 0x7f operation might be discarded if higher bits are somehow already zeroed
        //"   ori.w       #0x9700,%%d2\n"         // d2: 0x9700 | (u8)((fromAddrForDMA >> 16) & 0x7f)
        // Setup DMA length (in word here)
        "   move.w      %[_DMA_9300_LEN_DIV_3],(%%a1)\n"  // *((vu16*) VDP_CTRL_PORT) = 0x9300 | ((MOVIE_FRAME_COLORS_PER_STRIP/3) & 0xff);
        //"   move.w      %[_DMA_9400_LEN_DIV_3],(%%a1)\n"  // *((vu16*) VDP_CTRL_PORT) = 0x9400 | (((MOVIE_FRAME_COLORS_PER_STRIP/3) >> 8) & 0xff);
        // Setup DMA length (in long word here)
        //"   move.l      %[_DMA_9300_9400_LEN_DIV_3],(%%a1)\n"
        // Setup DMA address
        "   move.w      %%d0,(%%a1)\n"          // *((vu16*) VDP_CTRL_PORT) = 0x9500 | (u8)(fromAddrForDMA);
        "   move.w      %%d1,(%%a1)\n"          // *((vu16*) VDP_CTRL_PORT) = 0x9600 | (u8)(fromAddrForDMA >> 8);
        //"   move.w      %%d2,(%%a1)\n"          // *((vu16*) VDP_CTRL_PORT) = 0x9700 | (u8)((fromAddrForDMA >> 16) & 0x7f);
        // wait HCounter
        "1:\n"
        "   cmp.b       (%%a2),%%d6\n"          // cmp: d6 - (a2). Compare byte size given that d6 won't be > 160 for our practical cases
        "   bhi.s       1b\n"                   // loop back if d6 is higher than (a2)
		// turn off VDP
		"   move.w      %%d3,(%%a1)\n"          // *(vu16*) VDP_CTRL_PORT = 0x8100 | (reg01 & ~0x40);
        // trigger DMA transfer
        "   move.l      %%d5,(%%a1)\n"          // *((vu32*) VDP_CTRL_PORT) = palCmdForDMA;
		// turn on VDP
		"   move.w      %%d4,(%%a1)\n"          // *(vu16*) VDP_CTRL_PORT = 0x8100 | (reg01 | 0x40);

        // DMA batch 2
        "   move.l      %%a0,%%d2\n"            // d2: palInFramePtr
        "   lea         (%c[_MOVIE_FRAME_COLORS_PER_STRIP_DIV_3]*2,%%a0),%%a0\n"  // palInFramePtr += MOVIE_FRAME_COLORS_PER_STRIP/3;
        // palCmdForDMA = palCmdAddrrToggle == 0 ? 0xC0140080 : 0xC0540080;
		"   add.l       %%a3,%%d5\n"            // d5: palCmdForDMA += 0x140000 // previous batch advanced 10 colors (MOVIE_FRAME_COLORS_PER_STRIP/3)
        // Setup DMA command
        "   lsr.w       #1,%%d2\n"              // d2: fromAddrForDMA = (u32) palInFramePtr >> 1;
            // NOTE: previous lsr.l can be replaced by lsr.w in case we don't need to use d2: fromAddrForDMA >> 16
        "   move.w      #0x9500,%%d0\n"         // d0: 0x9500
        "   or.b        %%d2,%%d0\n"            // d0: 0x9500 | (u8)(fromAddrForDMA)
        "   move.w      %%d2,-(%%sp)\n"
        "   move.w      #0x9600,%%d1\n"         // d1: 0x9600
        "   or.b        (%%sp)+,%%d1\n"         // d1: 0x9600 | (u8)(fromAddrForDMA >> 8)
        //"   swap        %%d2\n"                 // d2: fromAddrForDMA >> 16
        //"   andi.w      #0x007f,%%d2\n"         // d2: (fromAddrForDMA >> 16) & 0x7f
            // NOTE: previous & 0x7f operation might be discarded if higher bits are somehow already zeroed
        //"   ori.w       #0x9700,%%d2\n"         // d2: 0x9700 | (u8)((fromAddrForDMA >> 16) & 0x7f)
        // Setup DMA length (in word here)
        "   move.w      %[_DMA_9300_LEN_DIV_3],(%%a1)\n"  // *((vu16*) VDP_CTRL_PORT) = 0x9300 | ((MOVIE_FRAME_COLORS_PER_STRIP/3) & 0xff);
        //"   move.w      %[_DMA_9400_LEN_DIV_3],(%%a1)\n"  // *((vu16*) VDP_CTRL_PORT) = 0x9400 | (((MOVIE_FRAME_COLORS_PER_STRIP/3) >> 8) & 0xff);
        // Setup DMA length (in long word here)
        //"   move.l      %[_DMA_9300_9400_LEN_DIV_3],(%%a1)\n"
        // Setup DMA address
        "   move.w      %%d0,(%%a1)\n"          // *((vu16*) VDP_CTRL_PORT) = 0x9500 | (u8)(fromAddrForDMA);
        "   move.w      %%d1,(%%a1)\n"          // *((vu16*) VDP_CTRL_PORT) = 0x9600 | (u8)(fromAddrForDMA >> 8);
        //"   move.w      %%d2,(%%a1)\n"          // *((vu16*) VDP_CTRL_PORT) = 0x9700 | (u8)((fromAddrForDMA >> 16) & 0x7f);
        // wait HCounter
        "1:\n"
        "   cmp.b       (%%a2),%%d6\n"          // cmp: d6 - (a2). Compare byte size given that d6 won't be > 160 for our practical cases
        "   bhi.s       1b\n"                   // loop back if d6 is higher than (a2)
		// turn off VDP
		"   move.w      %%d3,(%%a1)\n"          // *(vu16*) VDP_CTRL_PORT = 0x8100 | (reg01 & ~0x40);
        // trigger DMA transfer
        "   move.l      %%d5,(%%a1)\n"          // *((vu32*) VDP_CTRL_PORT) = palCmdForDMA;
		// turn on VDP
		"   move.w      %%d4,(%%a1)\n"          // *(vu16*) VDP_CTRL_PORT = 0x8100 | (reg01 | 0x40);

        // DMA batch 3
        "   move.l      %%a0,%%d2\n"            // d2: palInFramePtr
        "   lea         (%c[_MOVIE_FRAME_COLORS_PER_STRIP_DIV_3_REM]*2,%%a0),%%a0\n"  // palInFramePtr += MOVIE_FRAME_COLORS_PER_STRIP/3 + REMAINDER;
        // palCmdForDMA = palCmdAddrrToggle == 0 ? 0xC0280080 : 0xC0680080;
		"   add.l       %%a3,%%d5\n"            // d5: palCmdForDMA += 0x140000 // previous batch advanced 10 colors (MOVIE_FRAME_COLORS_PER_STRIP/3)
        // Setup DMA command
        "   lsr.w       #1,%%d2\n"              // d2: fromAddrForDMA = (u32) palInFramePtr >> 1;
            // NOTE: previous lsr.l can be replaced by lsr.w in case we don't need to use d2: fromAddrForDMA >> 16
        "   move.w      #0x9500,%%d0\n"         // d0: 0x9500
        "   or.b        %%d2,%%d0\n"            // d0: 0x9500 | (u8)(fromAddrForDMA)
        "   move.w      %%d2,-(%%sp)\n"
        "   move.w      #0x9600,%%d1\n"         // d1: 0x9600
        "   or.b        (%%sp)+,%%d1\n"         // d1: 0x9600 | (u8)(fromAddrForDMA >> 8)
        //"   swap        %%d2\n"                 // d2: fromAddrForDMA >> 16
        //"   andi.w      #0x007f,%%d2\n"         // d2: (fromAddrForDMA >> 16) & 0x7f
            // NOTE: previous & 0x7f operation might be discarded if higher bits are somehow already zeroed
        //"   ori.w       #0x9700,%%d2\n"         // d2: 0x9700 | (u8)((fromAddrForDMA >> 16) & 0x7f)
        // Setup DMA length (in word here)
        "   move.w      %[_DMA_9300_LEN_DIV_3_REM],(%%a1)\n"  // *((vu16*) VDP_CTRL_PORT) = 0x9300 | ((MOVIE_FRAME_COLORS_PER_STRIP/3 + REMAINDER) & 0xff);
        //"   move.w      %[_DMA_9400_LEN_DIV_3_REM],(%%a1)\n"  // *((vu16*) VDP_CTRL_PORT) = 0x9400 | (((MOVIE_FRAME_COLORS_PER_STRIP/3 + REMAINDER) >> 8) & 0xff);
        // Setup DMA length (in long word here)
        //"   move.l      %[_DMA_9300_9400_LEN_DIV_3_REM],(%%a1)\n"
        // Setup DMA address
        "   move.w      %%d0,(%%a1)\n"          // *((vu16*) VDP_CTRL_PORT) = 0x9500 | (u8)(fromAddrForDMA);
        "   move.w      %%d1,(%%a1)\n"          // *((vu16*) VDP_CTRL_PORT) = 0x9600 | (u8)(fromAddrForDMA >> 8);
        //"   move.w      %%d2,(%%a1)\n"          // *((vu16*) VDP_CTRL_PORT) = 0x9700 | (u8)((fromAddrForDMA >> 16) & 0x7f);
        // Prepare vars for next HInt here so we can aliviate the waitHCounter loop and exit the HInt sooner
        "   eori.b      %[_MOVIE_FRAME_COLORS_PER_STRIP],%c[palIdx]\n"  // palIdx ^= MOVIE_FRAME_COLORS_PER_STRIP // cycles between 0 and 32
        "   move.l      %%a0,%c[palInFramePtr]\n"                       // store current pointer value of a0 into variable palInFramePtr
        // wait HCounter
        "1:\n"
        "   cmp.b       (%%a2),%%d6\n"          // cmp: d6 - (a2). Compare byte size given that d6 won't be > 160 for our practical cases
        "   bhi.s       1b\n"                   // loop back if d6 is higher than (a2)
		// turn off VDP
		"   move.w      %%d3,(%%a1)\n"          // *(vu16*) VDP_CTRL_PORT = 0x8100 | (reg01 & ~0x40);
        // trigger DMA transfer
        "   move.l      %%d5,(%%a1)\n"          // *((vu32*) VDP_CTRL_PORT) = palCmdForDMA;
		// turn on VDP
		"   move.w      %%d4,(%%a1)\n"          // *(vu16*) VDP_CTRL_PORT = 0x8100 | (reg01 | 0x40);
		: 
		[palInFramePtr] "+m" (palInFramePtr),
		[palIdx] "+m" (palCmdAddrrToggle)
		: 
		[turnOff] "i" (0x8100 | (0x74 & ~0x40)), // 0x8134
		[turnOn] "i" (0x8100 | (0x74 | 0x40)), // 0x8174
        [hcLimit] "i" (152),
        [_DMA_9300_LEN_DIV_3] "i" (0x9300 | ((MOVIE_FRAME_COLORS_PER_STRIP/3) & 0xff)),
        [_DMA_9400_LEN_DIV_3] "i" (0x9400 | (((MOVIE_FRAME_COLORS_PER_STRIP/3) >> 8) & 0xff)),
        [_DMA_9300_9400_LEN_DIV_3] "i" ( ((0x9300 | ((MOVIE_FRAME_COLORS_PER_STRIP/3) & 0xff)) << 16) | (0x9400 | (((MOVIE_FRAME_COLORS_PER_STRIP/3) >> 8) & 0xff)) ),
        [_DMA_9300_LEN_DIV_3_REM] "i" (0x9300 | ((MOVIE_FRAME_COLORS_PER_STRIP/3 + MOVIE_FRAME_COLORS_PER_STRIP_REMAINDER(3)) & 0xff)),
        [_DMA_9400_LEN_DIV_3_REM] "i" (0x9400 | (((MOVIE_FRAME_COLORS_PER_STRIP/3 + MOVIE_FRAME_COLORS_PER_STRIP_REMAINDER(3)) >> 8) & 0xff)),
        [_DMA_9300_9400_LEN_DIV_3_REM] "i" ( ((0x9300 | ((MOVIE_FRAME_COLORS_PER_STRIP/3 + MOVIE_FRAME_COLORS_PER_STRIP_REMAINDER(3)) & 0xff)) << 16) | (0x9400 | (((MOVIE_FRAME_COLORS_PER_STRIP/3 + MOVIE_FRAME_COLORS_PER_STRIP_REMAINDER(3)) >> 8) & 0xff)) ),
		[_MOVIE_FRAME_COLORS_PER_STRIP] "i" (MOVIE_FRAME_COLORS_PER_STRIP),
        [_MOVIE_FRAME_COLORS_PER_STRIP_DIV_3] "i" (MOVIE_FRAME_COLORS_PER_STRIP/3),
        [_MOVIE_FRAME_COLORS_PER_STRIP_DIV_3_REM] "i" (MOVIE_FRAME_COLORS_PER_STRIP/3 + MOVIE_FRAME_COLORS_PER_STRIP_REMAINDER(3))
		:
        // backup registers used in the asm implementation including the scratch pad since this code is used in an interrupt call.
		"d0","d1","d2","d3","d4","d5","d6","a0","a1","a2","a3","cc","memory"
    );
}

static FORCE_INLINE void swapPalettes_DMA_3_cmds ()
{
    /*
        With 3 DMA commands and different DMA lenghts:
        Every command is CRAM address to start DMA 8 colors, then 12 more colors, and last 12 colors. Totaling 32 colors.
        u32 palCmdForDMA_A = VDP_DMA_CRAM_ADDR((u32)(palIdx + 0) * 2);
        u32 palCmdForDMA_B = VDP_DMA_CRAM_ADDR(((u32)palIdx + 8) * 2);
        u32 palCmdForDMA_C = VDP_DMA_CRAM_ADDR(((u32)palIdx + 20) * 2);
        cmd     palIdx = 0      palIdx = 32
        A       0xC0000080      0xC0400080
        B       0xC0100080      0xC0500080
        C       0xC0280080      0xC0680080
    */

    u32 palCmdForDMA;
    u32 fromAddrForDMA;

	// Value under current conditions is always 0x74
    //u8 reg01 = VDP_getReg(0x01); // Holds current VDP register 1 value (it holds other bits than VDP ON/OFF status)
    // NOTE: here is OK to call VDP_getReg(0x01) only if we didn't previously change the the VDP's reg 1 using direct access without VDP_setReg()

    fromAddrForDMA = (u32) palInFramePtr >> 1;
    palInFramePtr += 8; // advance into next color batch
    palCmdForDMA = palCmdAddrrToggle == 0 ? 0xC0000080 : 0xC0400080;
MEMORY_BARRIER();
    // Setup DMA length (in long word here): low at higher word, high at lower word
    *((vu32*) VDP_CTRL_PORT) = ((0x9300 | 8) << 16) | (0x9400 | (u8)(8 >> 8));
    // Setup DMA address
    *((vu16*) VDP_CTRL_PORT) = 0x9500 | (u8)(fromAddrForDMA);
    *((vu16*) VDP_CTRL_PORT) = 0x9600 | (u8)(fromAddrForDMA >> 8);
    //*((vu16*) VDP_CTRL_PORT) = 0x9700 | ((fromAddrForDMA >> 16) & 0x7f);

    waitHCounter_opt2(152);
    turnOffVDP(0x74);
    *((vu32*) VDP_CTRL_PORT) = palCmdForDMA; // trigger DMA transfer
    turnOnVDP(0x74);

    fromAddrForDMA = (u32) palInFramePtr >> 1;
    palInFramePtr += 12; // advance into next color batch
    palCmdForDMA = palCmdAddrrToggle == 0 ? 0xC0100080 : 0xC0500080; // advance command for next 8 colors
MEMORY_BARRIER();
    // Setup DMA length (in long word here): low at higher word, high at lower word
    *((vu32*) VDP_CTRL_PORT) = ((0x9300 | 12) << 16) | (0x9400 | (u8)(12 >> 8));
    // Setup DMA address
    *((vu16*) VDP_CTRL_PORT) = 0x9500 | (u8)(fromAddrForDMA);
    *((vu16*) VDP_CTRL_PORT) = 0x9600 | (u8)(fromAddrForDMA >> 8);
    //*((vu16*) VDP_CTRL_PORT) = 0x9700 | ((fromAddrForDMA >> 16) & 0x7f);

    waitHCounter_opt2(152);
    turnOffVDP(0x74);
    *((vu32*) VDP_CTRL_PORT) = palCmdForDMA; // trigger DMA transfer
    turnOnVDP(0x74);

    fromAddrForDMA = (u32) palInFramePtr >> 1;
    palInFramePtr += 12; // advance into next color batch
    palCmdForDMA = palCmdAddrrToggle == 0 ? 0xC0280080 : 0xC0680080; // advance command for next 12 colors
MEMORY_BARRIER();
    // Setup DMA length (in long word here): low at higher word, high at lower word
    *((vu32*) VDP_CTRL_PORT) = ((0x9300 | 12) << 16) | (0x9400 | (u8)(12 >> 8));
    // Setup DMA address
    *((vu16*) VDP_CTRL_PORT) = 0x9500 | (u8)(fromAddrForDMA);
    *((vu16*) VDP_CTRL_PORT) = 0x9600 | (u8)(fromAddrForDMA >> 8);
    //*((vu16*) VDP_CTRL_PORT) = 0x9700 | ((fromAddrForDMA >> 16) & 0x7f);

    // Prepare vars for next HInt here so we can aliviate the waitHCounter loop and exit the HInt sooner
    //palInFramePtr += MOVIE_FRAME_COLORS_PER_STRIP; // advance to next strip's palettes (if pointer wasn't incremented previously)
	palCmdAddrrToggle ^= MOVIE_FRAME_COLORS_PER_STRIP; // cycles between 0 and 32

    waitHCounter_opt2(152);
    turnOffVDP(0x74);
    *((vu32*) VDP_CTRL_PORT) = palCmdForDMA; // trigger DMA transfer
    turnOnVDP(0x74);
}

HINTERRUPT_CALLBACK HIntCallback_DMA_NTSC ()
{
	if (vcounterManual < MOVIE_HINT_COLORS_SWAP_START_SCANLINE_NTSC || vcounterManual > MOVIE_HINT_COLORS_SWAP_END_SCANLINE_NTSC) {
	    vcounterManual += HINT_COUNTER_FOR_COLORS_UPDATE;
    }
    else {
        vcounterManual += HINT_COUNTER_FOR_COLORS_UPDATE;
        swapPalettes_DMA_3_cmds();
    }
}

HINTERRUPT_CALLBACK HIntCallback_DMA_PAL ()
{
	if (vcounterManual < MOVIE_HINT_COLORS_SWAP_START_SCANLINE_PAL || vcounterManual > MOVIE_HINT_COLORS_SWAP_END_SCANLINE_PAL) {
	    vcounterManual += HINT_COUNTER_FOR_COLORS_UPDATE;
    }
    else {
        vcounterManual += HINT_COUNTER_FOR_COLORS_UPDATE;
        swapPalettes_DMA_3_cmds();
    }
}
