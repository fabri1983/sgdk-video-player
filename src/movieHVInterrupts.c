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
 * Wait until HCounter 0xC00009 reaches nth position (actually the (n*2)th pixel since the VDP counts by 2).
*/
FORCE_INLINE void waitHCounter (u8 n) {
    u32* regA=0; // placeholder used to indicate the use of an An register
    ASM_STATEMENT __volatile__ (
        "    move.l    #0xC00009,%0\n"    // Load HCounter (VDP_HVCOUNTER_PORT + 1 = 0xC00009) into an An register
        ".loopHC%=:\n" 
        "    cmp.b     (%0),%1\n"         // cmp: n - (0xC00009). Compares byte because hcLimit won't be > 160 for our practical cases
        "    bhi       .loopHC%=\n"       // loop back if n is higher than (0xC00009)
            // bhi is for unsigned comparisons
        : "+a" (regA)
        : "d" (n)
        : "cc"
    );
}

/**
 * \brief Writes into VDP_CTRL_PORT (0xC00004) the setup for DMA (length and source address). 
 * Optimizations may apply manually if you know the source address is only 8 bits or 12 bits, and same for the length parameter.
 * \param len How many colors to move.
 * \param fromAddr Must be >> 1.
*/
void NO_INLINE setupDMAForPals (u16 len, u32 fromAddr) {
    // Uncomment if you previously change it to 1 (CPU access to VRAM is 1 byte lenght, and 2 bytes length for CRAM and VSRAM)
    //VDP_setAutoInc(2);

    vu16* palDmaPtr = (vu16*) VDP_CTRL_PORT;

    // Setup DMA length (in word here): low and high
    *palDmaPtr = 0x9300 | (len & 0xff);
    *palDmaPtr = 0x9400 | ((len >> 8) & 0xff); // This step is useless if the length has only set first 8 bits

    // Setup DMA address
    *palDmaPtr = 0x9500 | (fromAddr & 0xff);
    *palDmaPtr = 0x9600 | ((fromAddr >> 8) & 0xff); // This step is useless if the address has only set first 8 bits
    *palDmaPtr = 0x9700 | ((fromAddr >> 16) & 0x7f); // This step is useless if the address has only set first 12 bits
}

static u16* palInFrameRootPtr; // points to the first pals the HInt starts to load
static u16* palInFramePtr; // pals pointer increased in every HInt call cycle
static u16 palCmdAddrrToggle = HINT_PALS_CMD_ADDR_RESET_VALUE; // used to toggle between the two different CRAM cmd addresses used to send the pals
static u16 vcounterManual = HINT_COUNTER_FOR_COLORS_UPDATE - 1;

void setMoviePalsPointerBeforeInterrupts (u16* rootPalsPtr) {
    palInFrameRootPtr = rootPalsPtr;
    palInFramePtr = rootPalsPtr;
}

void setMoviePalsPointer (u16* rootPalsPtr) {
    palInFrameRootPtr = rootPalsPtr;
}

void VIntMovieCallback () {
	palInFramePtr = palInFrameRootPtr; // Resets to 1st strip's palettes due to ptr modification made by HInt
	palCmdAddrrToggle = HINT_PALS_CMD_ADDR_RESET_VALUE; // Resets pal index due to modification made by HInt.
	vcounterManual = HINT_COUNTER_FOR_COLORS_UPDATE - 1; // Resets due to modification made by HInt
}

static FORCE_INLINE void swapPalettes_CPU () {
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

	// Value under current conditions is always 116
    //u8 reg01 = VDP_getReg(0x01); // Holds current VDP register 1 value (it holds other bits than VDP ON/OFF status)
    // NOTE: here is OK to call VDP_getReg(0x01) only if we didn't previously change the the VDP's reg 1 using direct access without VDP_setReg()

	colors2_A = *((u32*) (palInFramePtr + 0)); // 2 colors
	colors2_B = *((u32*) (palInFramePtr + 2)); // next 2 colors
	colors2_C = *((u32*) (palInFramePtr + 4)); // next 2 colors
	colors2_D = *((u32*) (palInFramePtr + 6)); // next 2 colors
	cmdAddress = (palCmdAddrrToggle == 0) ? 0xC0000000 : 0xC0400000;
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
	cmdAddress = (palCmdAddrrToggle == 0) ? 0xC0100000 : 0xC0500000;
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
	cmdAddress = (palCmdAddrrToggle == 0) ? 0xC0200000 : 0xC0600000;
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
	cmdAddress = (palCmdAddrrToggle == 0) ? 0xC0300000 : 0xC0700000;
	waitHCounter(145);
	turnOffVDP(116);
	*((vu32*) VDP_CTRL_PORT) = cmdAddress;
	*((vu32*) VDP_DATA_PORT) = colors2_A;
	*((vu32*) VDP_DATA_PORT) = colors2_B;
	*((vu32*) VDP_DATA_PORT) = colors2_C;
	*((vu32*) VDP_DATA_PORT) = colors2_D;
	turnOnVDP(116);

    vcounterManual += HINT_COUNTER_FOR_COLORS_UPDATE;
	palInFramePtr += MOVIE_FRAME_COLORS_PER_STRIP; // advance to next strip's palettes (if pointer wasn't incremented previously)
	palCmdAddrrToggle ^= MOVIE_FRAME_COLORS_PER_STRIP; // cycles between 0 and 32
    //palCmdAddrrToggle = palCmdAddrrToggle == 0 ? 32 : 0;
    //palCmdAddrrToggle = (palCmdAddrrToggle + 32) & 63; // (palCmdAddrrToggle + 32) % 64 => x mod y = x & (y-1) when y is power of 2
}

HINTERRUPT_CALLBACK HIntCallback_CPU_NTSC () {
	if (vcounterManual < MOVIE_HINT_COLORS_SWAP_START_SCANLINE_NTSC || vcounterManual > MOVIE_HINT_COLORS_SWAP_END_SCANLINE_NTSC) {
	    vcounterManual += HINT_COUNTER_FOR_COLORS_UPDATE;
        return;
    }
    swapPalettes_CPU();
}

HINTERRUPT_CALLBACK HIntCallback_CPU_PAL () {
	if (vcounterManual < MOVIE_HINT_COLORS_SWAP_START_SCANLINE_PAL || vcounterManual > MOVIE_HINT_COLORS_SWAP_END_SCANLINE_PAL) {
	    vcounterManual += HINT_COUNTER_FOR_COLORS_UPDATE;
        return;
    }
    swapPalettes_CPU();
}

static FORCE_INLINE void swapPalettes_DMA () {
    /*
        With 2 DMA commands and same DMA lengths:
        Every command is CRAM address to start DMA MOVIE_FRAME_COLORS_PER_STRIP/2 colors
        u32 palCmdForDMA_A = VDP_DMA_CRAM_ADDR((u32)(palIdx + 0) * 2);
        u32 palCmdForDMA_B = VDP_DMA_CRAM_ADDR(((u32)palIdx + MOVIE_FRAME_COLORS_PER_STRIP/2) * 2);
        cmd     palIdx = 0      palIdx = 32
        A       0xC0000080      0xC0400080
        B       0xC0200080      0xC0600080

        With 3 DMA commands and different DMA lenghts:
        Every command is CRAM address to start DMA 8 colors and then 12 and 12 (total 32 colors)
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

	// Value under current conditions is always 116
    //u8 reg01 = VDP_getReg(0x01); // Holds current VDP register 1 value (it holds other bits than VDP ON/OFF status)
    // NOTE: here is OK to call VDP_getReg(0x01) only if we didn't previously change the the VDP's reg 1 using direct access without VDP_setReg()

    fromAddrForDMA = (u32) palInFramePtr >> 1;
    palInFramePtr += 8;
    palCmdForDMA = palCmdAddrrToggle == 0 ? 0xC0000080 : 0xC0400080;
MEMORY_BARRIER();
    waitHCounter(136);
    //setupDMAForPals(8, fromAddrForDMA);
    // Setup DMA length (in word here)
    *((vu16*) VDP_CTRL_PORT) = 0x9300 | (8 & 0xff);
    //*((vu16*) VDP_CTRL_PORT) = 0x9400 | ((8 >> 8) & 0xff);
    // Setup DMA address
    *((vu16*) VDP_CTRL_PORT) = 0x9500 | (fromAddrForDMA & 0xff);
    *((vu16*) VDP_CTRL_PORT) = 0x9600 | ((fromAddrForDMA >> 8) & 0xff);
    *((vu16*) VDP_CTRL_PORT) = 0x9700 | ((fromAddrForDMA >> 16) & 0x7f);

    waitHCounter(150);
    turnOffVDP(116);
    *((vu32*) VDP_CTRL_PORT) = palCmdForDMA; // trigger DMA transfer
    turnOnVDP(116);

    fromAddrForDMA = (u32) palInFramePtr >> 1;
    palInFramePtr += 12;
    palCmdForDMA = palCmdAddrrToggle == 0 ? 0xC0100080 : 0xC0500080;
MEMORY_BARRIER();
    waitHCounter(136);
    //setupDMAForPals(12, fromAddrForDMA);
    // Setup DMA length (in word here)
    *((vu16*) VDP_CTRL_PORT) = 0x9300 | (12 & 0xff);
    //*((vu16*) VDP_CTRL_PORT) = 0x9400 | ((12 >> 8) & 0xff);
    // Setup DMA address
    *((vu16*) VDP_CTRL_PORT) = 0x9500 | (fromAddrForDMA & 0xff);
    *((vu16*) VDP_CTRL_PORT) = 0x9600 | ((fromAddrForDMA >> 8) & 0xff);
    *((vu16*) VDP_CTRL_PORT) = 0x9700 | ((fromAddrForDMA >> 16) & 0x7f);

    waitHCounter(150);
    turnOffVDP(116);
    *((vu32*) VDP_CTRL_PORT) = palCmdForDMA; // trigger DMA transfer
    turnOnVDP(116);

    fromAddrForDMA = (u32) palInFramePtr >> 1;
    palInFramePtr += 12;
    palCmdForDMA = palCmdAddrrToggle == 0 ? 0xC0280080 : 0xC0680080;
MEMORY_BARRIER();
    waitHCounter(136);
    //setupDMAForPals(12, fromAddrForDMA);
    // Setup DMA length (in word here)
    *((vu16*) VDP_CTRL_PORT) = 0x9300 | (12 & 0xff);
    //*((vu16*) VDP_CTRL_PORT) = 0x9400 | ((12 >> 8) & 0xff);
    // Setup DMA address
    *((vu16*) VDP_CTRL_PORT) = 0x9500 | (fromAddrForDMA & 0xff);
    *((vu16*) VDP_CTRL_PORT) = 0x9600 | ((fromAddrForDMA >> 8) & 0xff);
    *((vu16*) VDP_CTRL_PORT) = 0x9700 | ((fromAddrForDMA >> 16) & 0x7f);

    waitHCounter(150);
    turnOffVDP(116);
    *((vu32*) VDP_CTRL_PORT) = palCmdForDMA; // trigger DMA transfer
    turnOnVDP(116);

    vcounterManual += HINT_COUNTER_FOR_COLORS_UPDATE;
	//palInFramePtr += MOVIE_FRAME_COLORS_PER_STRIP; // advance to next strip's palettes (if pointer wasn't incremented previously)
	palCmdAddrrToggle ^= MOVIE_FRAME_COLORS_PER_STRIP; // cycles between 0 and 32
    //palCmdAddrrToggle = palCmdAddrrToggle == 0 ? 32 : 0;
    //palCmdAddrrToggle = (palCmdAddrrToggle + 32) & 63; // (palCmdAddrrToggle + 32) % 64 => x mod y = x & (y-1) when y is power of 2
}

HINTERRUPT_CALLBACK HIntCallback_DMA_NTSC () {
	if (vcounterManual < MOVIE_HINT_COLORS_SWAP_START_SCANLINE_NTSC || vcounterManual > MOVIE_HINT_COLORS_SWAP_END_SCANLINE_NTSC) {
	    vcounterManual += HINT_COUNTER_FOR_COLORS_UPDATE;
        return;
    }
    swapPalettes_DMA();
}

HINTERRUPT_CALLBACK HIntCallback_DMA_PAL () {
	if (vcounterManual < MOVIE_HINT_COLORS_SWAP_START_SCANLINE_PAL || vcounterManual > MOVIE_HINT_COLORS_SWAP_END_SCANLINE_PAL) {
	    vcounterManual += HINT_COUNTER_FOR_COLORS_UPDATE;
        return;
    }
    swapPalettes_DMA();
}
