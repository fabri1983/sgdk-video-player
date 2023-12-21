#include <genesis.h>
#include <vdp.h>
#include <pal.h>
#include <sys.h>
#include <tools.h>
#include <timer.h>
#include <memory.h>
#include "movieHVInterrupts.h"

// u8 reg01; // Holds current VDP register 1 whole value (it holds other bits than VDP ON/OFF status)

// FORCE_INLINE void copyReg01 () {
//     reg01 = VDP_getReg(0x01);
// }

/// @brief Set bit 6 (64 decimal, 0x40 hexa) of reg 1.
/// @param reg01 VDP's Reg 1 holds other bits than just VDP ON/OFF status, so we need its current value.
FORCE_INLINE void turnOffVDP (u8 reg01) {
    //reg01 &= ~0x40;
    //*(vu16*) VDP_CTRL_PORT = 0x8100 | reg01;
    *(vu16*) VDP_CTRL_PORT = 0x8100 | (reg01 & ~0x40);
}

/// @brief Set bit 6 (64 decimal, 0x40 hexa) of reg 1.
/// @param reg01 VDP's Reg 1 holds other bits than just VDP ON/OFF status, so we need its current value.
FORCE_INLINE void turnOnVDP (u8 reg01) {
    //reg01 |= 0x40;
    //*(vu16*) VDP_CTRL_PORT = 0x8100 | reg01;
    *(vu16*) VDP_CTRL_PORT = 0x8100 | (reg01 | 0x40);
}

/**
 * Wait until HCounter 0xC00009 reaches nth position (in fact (n*2)th pixel since the VDP counts by 2)
*/
FORCE_INLINE void waitHCounter (u16 n) {
    ASM_STATEMENT volatile (
        ".LoopHC%=:\n"
        "\t  cmpi.b  %[hcLimit], 0xC00009.l;\n"  // we only interested in comparing byte since n won't be > 160 for our practical cases
        "\t  blo     .LoopHC%=;"
        :
        : [hcLimit] "i" (n)
        : "cc" // Clobbers: condition codes
    );
}

/**
 * \brief
 * \param len How many colors to move.
 * \param fromAddr Must be >> 1.
*/
void NO_INLINE setupDMAForPals (u16 len, u32 fromAddr) {
    // Uncomment if you previously change it to 1 (CPU access to VRAM is 1 byte lenght, and 2 bytes length for CRAM and VSRAM)
    //VDP_setAutoInc(2);

    vu16* palDmaPtr = (vu16*) VDP_CTRL_PORT;

    // Setup DMA length (in word here)
    *palDmaPtr = 0x9300 + (len & 0xff);
    *palDmaPtr = 0x9400 + ((len >> 8) & 0xff);

    // Setup DMA address
    *palDmaPtr = 0x9500 + (fromAddr & 0xff);
    *palDmaPtr = 0x9600 + ((fromAddr >> 8) & 0xff);
    *palDmaPtr = 0x9700 + ((fromAddr >> 16) & 0x7f);
}

u16* palInFrameRootPtr;
u16* palInFramePtr;
u16 palIdxInVDP;
u16 vcounterManual;

void VIntCallback () {
	palInFramePtr = palInFrameRootPtr; // Resets to 3rd strip's palette
	palIdxInVDP = 0; // 0: [PAL0,PAL1]. 32: [PAL2,PAL3].
	vcounterManual = HINT_COUNTER_FOR_COLORS_UPDATE - 1;
}

HINTERRUPT_CALLBACK HIntCallback_CPU_NTSC () {
    u16 prevVCounter = vcounterManual;
	vcounterManual += HINT_COUNTER_FOR_COLORS_UPDATE;
	if (prevVCounter < MOVIE_HINT_COLORS_SWAP_START_SCANLINE_NTSC || prevVCounter > MOVIE_HINT_COLORS_SWAP_END_SCANLINE_NTSC)
        return;

    /*
        u32 cmd1st = VDP_WRITE_CRAM_ADDR((u32)(palIdx * 2));
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

    u32 cmdBaseAddress;
    u32 cmdAddress = cmdBaseAddress;
	u32 colors2_A, colors2_B, colors2_C, colors2_D;

	// Value under current conditions is always 116
    //u8 reg01 = VDP_getReg(0x01); // Holds current VDP register 1 value (it holds other bits than VDP ON/OFF status)
    // NOTE: here is OK to call VDP_getReg(0x01) only if we didn't previously change the the VDP's reg 1 using direct access without VDP_setReg()

	colors2_A = *((u32*) (palInFramePtr + 0)); // 2 colors
	colors2_B = *((u32*) (palInFramePtr + 2)); // next 2 colors
	colors2_C = *((u32*) (palInFramePtr + 4)); // next 2 colors
	colors2_D = *((u32*) (palInFramePtr + 6)); // next 2 colors
	cmdBaseAddress = (palIdxInVDP == 0) ? 0xC0000000 : 0xC0400000;
	waitHCounter(145);
	turnOffVDP(116);
	*((vu32*) VDP_CTRL_PORT) = cmdAddress;
	*((vu32*) VDP_DATA_PORT) = colors2_A;
	*((vu32*) VDP_DATA_PORT) = colors2_B;
	*((vu32*) VDP_DATA_PORT) = colors2_C;
	*((vu32*) VDP_DATA_PORT) = colors2_D;
	turnOnVDP(116);

	colors2_A = *((u32*) (palInFramePtr +  8)); // 2 colors
	colors2_B = *((u32*) (palInFramePtr + 10)); // next 2 colors
	colors2_C = *((u32*) (palInFramePtr + 12)); // next 2 colors
	colors2_D = *((u32*) (palInFramePtr + 14)); // next 2 colors
	cmdAddress = (palIdxInVDP == 0) ? 0xC0100000 : 0xC0500000;
	waitHCounter(145);
	turnOffVDP(116);
	*((vu32*) VDP_CTRL_PORT) = cmdAddress;
	*((vu32*) VDP_DATA_PORT) = colors2_A;
	*((vu32*) VDP_DATA_PORT) = colors2_B;
	*((vu32*) VDP_DATA_PORT) = colors2_C;
	*((vu32*) VDP_DATA_PORT) = colors2_D;
	turnOnVDP(116);

	colors2_A = *((u32*) (palInFramePtr + 16)); // 2 colors
	colors2_B = *((u32*) (palInFramePtr + 18)); // next 2 colors
	colors2_C = *((u32*) (palInFramePtr + 20)); // next 2 colors
	colors2_D = *((u32*) (palInFramePtr + 22)); // next 2 colors
	cmdAddress = (palIdxInVDP == 0) ? 0xC0200000 : 0xC0600000;
	waitHCounter(145);
	turnOffVDP(116);
	*((vu32*) VDP_CTRL_PORT) = cmdAddress;
	*((vu32*) VDP_DATA_PORT) = colors2_A;
	*((vu32*) VDP_DATA_PORT) = colors2_B;
	*((vu32*) VDP_DATA_PORT) = colors2_C;
	*((vu32*) VDP_DATA_PORT) = colors2_D;
	turnOnVDP(116);

	colors2_A = *((u32*) (palInFramePtr + 24)); // 2 colors
	colors2_B = *((u32*) (palInFramePtr + 26)); // next 2 colors
	colors2_C = *((u32*) (palInFramePtr + 28)); // next 2 colors
	colors2_D = *((u32*) (palInFramePtr + 30)); // next 2 colors
	cmdAddress = (palIdxInVDP == 0) ? 0xC0300000 : 0xC0700000;
	waitHCounter(145);
	turnOffVDP(116);
	*((vu32*) VDP_CTRL_PORT) = cmdAddress;
	*((vu32*) VDP_DATA_PORT) = colors2_A;
	*((vu32*) VDP_DATA_PORT) = colors2_B;
	*((vu32*) VDP_DATA_PORT) = colors2_C;
	*((vu32*) VDP_DATA_PORT) = colors2_D;
	turnOnVDP(116);

	palInFramePtr += MOVIE_DATA_COLORS_PER_STRIP; // advance to next strip's palettes (if pointer wasn't incremented previously)
	palIdxInVDP ^= MOVIE_DATA_COLORS_PER_STRIP; // cycles between 0 and 32
    //palIdxInVDP = palIdxInVDP == 0 ? 32 : 0;
    //palIdxInVDP = (palIdxInVDP + 32) & 63; // (palIdxInVDP + 32) % 64 => x mod y = x & (y-1) when y is power of 2
}

HINTERRUPT_CALLBACK HIntCallback_CPU_PAL () {
    u16 prevVCounter = vcounterManual;
	vcounterManual += HINT_COUNTER_FOR_COLORS_UPDATE;
	if (prevVCounter < MOVIE_HINT_COLORS_SWAP_START_SCANLINE_PAL || prevVCounter > MOVIE_HINT_COLORS_SWAP_END_SCANLINE_PAL)
        return;

    /*
        u32 cmd1st = VDP_WRITE_CRAM_ADDR((u32)(palIdx * 2));
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

    u32 cmdBaseAddress;
    u32 cmdAddress = cmdBaseAddress;
	u32 colors2_A, colors2_B, colors2_C, colors2_D;

	// Value under current conditions is always 116
    //u8 reg01 = VDP_getReg(0x01); // Holds current VDP register 1 value (it holds other bits than VDP ON/OFF status)
    // NOTE: here is OK to call VDP_getReg(0x01) only if we didn't previously change the the VDP's reg 1 using direct access without VDP_setReg()

	colors2_A = *((u32*) (palInFramePtr + 0)); // 2 colors
	colors2_B = *((u32*) (palInFramePtr + 2)); // next 2 colors
	colors2_C = *((u32*) (palInFramePtr + 4)); // next 2 colors
	colors2_D = *((u32*) (palInFramePtr + 6)); // next 2 colors
	cmdBaseAddress = (palIdxInVDP == 0) ? 0xC0000000 : 0xC0400000;
	waitHCounter(145);
	turnOffVDP(116);
	*((vu32*) VDP_CTRL_PORT) = cmdAddress;
	*((vu32*) VDP_DATA_PORT) = colors2_A;
	*((vu32*) VDP_DATA_PORT) = colors2_B;
	*((vu32*) VDP_DATA_PORT) = colors2_C;
	*((vu32*) VDP_DATA_PORT) = colors2_D;
	turnOnVDP(116);

	colors2_A = *((u32*) (palInFramePtr +  8)); // 2 colors
	colors2_B = *((u32*) (palInFramePtr + 10)); // next 2 colors
	colors2_C = *((u32*) (palInFramePtr + 12)); // next 2 colors
	colors2_D = *((u32*) (palInFramePtr + 14)); // next 2 colors
	cmdAddress = (palIdxInVDP == 0) ? 0xC0100000 : 0xC0500000;
	waitHCounter(145);
	turnOffVDP(116);
	*((vu32*) VDP_CTRL_PORT) = cmdAddress;
	*((vu32*) VDP_DATA_PORT) = colors2_A;
	*((vu32*) VDP_DATA_PORT) = colors2_B;
	*((vu32*) VDP_DATA_PORT) = colors2_C;
	*((vu32*) VDP_DATA_PORT) = colors2_D;
	turnOnVDP(116);

	colors2_A = *((u32*) (palInFramePtr + 16)); // 2 colors
	colors2_B = *((u32*) (palInFramePtr + 18)); // next 2 colors
	colors2_C = *((u32*) (palInFramePtr + 20)); // next 2 colors
	colors2_D = *((u32*) (palInFramePtr + 22)); // next 2 colors
	cmdAddress = (palIdxInVDP == 0) ? 0xC0200000 : 0xC0600000;
	waitHCounter(145);
	turnOffVDP(116);
	*((vu32*) VDP_CTRL_PORT) = cmdAddress;
	*((vu32*) VDP_DATA_PORT) = colors2_A;
	*((vu32*) VDP_DATA_PORT) = colors2_B;
	*((vu32*) VDP_DATA_PORT) = colors2_C;
	*((vu32*) VDP_DATA_PORT) = colors2_D;
	turnOnVDP(116);

	colors2_A = *((u32*) (palInFramePtr + 24)); // 2 colors
	colors2_B = *((u32*) (palInFramePtr + 26)); // next 2 colors
	colors2_C = *((u32*) (palInFramePtr + 28)); // next 2 colors
	colors2_D = *((u32*) (palInFramePtr + 30)); // next 2 colors
	cmdAddress = (palIdxInVDP == 0) ? 0xC0300000 : 0xC0700000;
	waitHCounter(145);
	turnOffVDP(116);
	*((vu32*) VDP_CTRL_PORT) = cmdAddress;
	*((vu32*) VDP_DATA_PORT) = colors2_A;
	*((vu32*) VDP_DATA_PORT) = colors2_B;
	*((vu32*) VDP_DATA_PORT) = colors2_C;
	*((vu32*) VDP_DATA_PORT) = colors2_D;
	turnOnVDP(116);

	palInFramePtr += MOVIE_DATA_COLORS_PER_STRIP; // advance to next strip's palettes (if pointer wasn't incremented previously)
	palIdxInVDP ^= MOVIE_DATA_COLORS_PER_STRIP; // cycles between 0 and 32
    //palIdxInVDP = palIdxInVDP == 0 ? 32 : 0;
    //palIdxInVDP = (palIdxInVDP + 32) & 63; // (palIdxInVDP + 32) % 64 => x mod y = x & (y-1) when y is power of 2
}

HINTERRUPT_CALLBACK HIntCallback_DMA_NTSC () {
	u16 prevVCounter = vcounterManual;
	vcounterManual += HINT_COUNTER_FOR_COLORS_UPDATE;
	if (prevVCounter < MOVIE_HINT_COLORS_SWAP_START_SCANLINE_NTSC || prevVCounter > MOVIE_HINT_COLORS_SWAP_END_SCANLINE_NTSC)
        return;

    /*
        u32 palCmdForDMA_A = VDP_DMA_CRAM_ADDR((u32)palIdx * 2);
        u32 palCmdForDMA_B = VDP_DMA_CRAM_ADDR(((u32)palIdx + MOVIE_DATA_COLORS_PER_STRIP/2) * 2);
        cmd     palIdx = 0      palIdx = 32
        A       0xC0000080      0xC0400080
        B       0xC0200080      0xC0600080
    */

    u32 palCmdForDMA;
    u32 fromAddrForDMA;

	// Value under current conditions is always 116
    //u8 reg01 = VDP_getReg(0x01); // Holds current VDP register 1 value (it holds other bits than VDP ON/OFF status)
    // NOTE: here is OK to call VDP_getReg(0x01) only if we didn't previously change the the VDP's reg 1 using direct access without VDP_setReg()

    fromAddrForDMA = (u32) palInFramePtr >> 1;
    palInFramePtr += MOVIE_DATA_COLORS_PER_STRIP/2;
    palCmdForDMA = palIdxInVDP == 0 ? 0xC0000080 : 0xC0400080;
    waitHCounter(136);
    setupDMAForPals(MOVIE_DATA_COLORS_PER_STRIP/2, fromAddrForDMA);

    waitHCounter(150);
    turnOffVDP(116);
    *((vu32*) VDP_CTRL_PORT) = palCmdForDMA; // trigger DMA transfer
    turnOnVDP(116);

    fromAddrForDMA = (u32) palInFramePtr >> 1;
    palInFramePtr += MOVIE_DATA_COLORS_PER_STRIP/2;
    palCmdForDMA = palIdxInVDP == 0 ? 0xC0200080 : 0xC0600080;
    waitHCounter(136);
    setupDMAForPals(MOVIE_DATA_COLORS_PER_STRIP/2, fromAddrForDMA);

    waitHCounter(150);
    turnOffVDP(116);
    *((vu32*) VDP_CTRL_PORT) = palCmdForDMA; // trigger DMA transfer
    turnOnVDP(116);

	//palInFramePtr += MOVIE_DATA_COLORS_PER_STRIP; // advance to next strip's palettes (if pointer wasn't incremented previously)
	palIdxInVDP ^= MOVIE_DATA_COLORS_PER_STRIP; // cycles between 0 and 32
    //palIdxInVDP = palIdxInVDP == 0 ? 32 : 0;
    //palIdxInVDP = (palIdxInVDP + 32) & 63; // (palIdxInVDP + 32) % 64 => x mod y = x & (y-1) when y is power of 2
}

HINTERRUPT_CALLBACK HIntCallback_DMA_PAL () {
	u16 prevVCounter = vcounterManual;
	vcounterManual += HINT_COUNTER_FOR_COLORS_UPDATE;
	if (prevVCounter < MOVIE_HINT_COLORS_SWAP_START_SCANLINE_PAL || prevVCounter > MOVIE_HINT_COLORS_SWAP_END_SCANLINE_PAL)
        return;

    /*
        u32 palCmdForDMA_A = VDP_DMA_CRAM_ADDR((u32)palIdx * 2);
        u32 palCmdForDMA_B = VDP_DMA_CRAM_ADDR(((u32)palIdx + MOVIE_DATA_COLORS_PER_STRIP/2) * 2);
        cmd     palIdx = 0      palIdx = 32
        A       0xC0000080      0xC0400080
        B       0xC0200080      0xC0600080
    */

    u32 palCmdForDMA;
    u32 fromAddrForDMA;

	// Value under current conditions is always 116
    //u8 reg01 = VDP_getReg(0x01); // Holds current VDP register 1 value (it holds other bits than VDP ON/OFF status)
    // NOTE: here is OK to call VDP_getReg(0x01) only if we didn't previously change the the VDP's reg 1 using direct access without VDP_setReg()

    fromAddrForDMA = (u32) palInFramePtr >> 1;
    palInFramePtr += MOVIE_DATA_COLORS_PER_STRIP/2;
    palCmdForDMA = palIdxInVDP == 0 ? 0xC0000080 : 0xC0400080;
    waitHCounter(136);
    setupDMAForPals(MOVIE_DATA_COLORS_PER_STRIP/2, fromAddrForDMA);

    waitHCounter(150);
    turnOffVDP(116);
    *((vu32*) VDP_CTRL_PORT) = palCmdForDMA; // trigger DMA transfer
    turnOnVDP(116);

    fromAddrForDMA = (u32) palInFramePtr >> 1;
    palInFramePtr += MOVIE_DATA_COLORS_PER_STRIP/2;
    palCmdForDMA = palIdxInVDP == 0 ? 0xC0200080 : 0xC0600080;
    waitHCounter(136);
    setupDMAForPals(MOVIE_DATA_COLORS_PER_STRIP/2, fromAddrForDMA);

    waitHCounter(150);
    turnOffVDP(116);
    *((vu32*) VDP_CTRL_PORT) = palCmdForDMA; // trigger DMA transfer
    turnOnVDP(116);

	//palInFramePtr += MOVIE_DATA_COLORS_PER_STRIP; // advance to next strip's palettes (if pointer wasn't incremented previously)
	palIdxInVDP ^= MOVIE_DATA_COLORS_PER_STRIP; // cycles between 0 and 32
    //palIdxInVDP = palIdxInVDP == 0 ? 32 : 0;
    //palIdxInVDP = (palIdxInVDP + 32) & 63; // (palIdxInVDP + 32) % 64 => x mod y = x & (y-1) when y is power of 2
}