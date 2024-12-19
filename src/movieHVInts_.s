;//----------------------------------------------------------------------------
;// CPU approach.
;// Loads 2 palettes (32 colors) into CRAM in 4 batches of 8 colors each,
;// during the span of 8 scanlines. HInt is called every 8 scanlines.
;// For a display in V28 mode (224px) this routine is called 27 times.
;//----------------------------------------------------------------------------

;// prepare_regs
    movea.l     palInFramePtr,a0      ;// a0: palInFramePtr
    lea         0xC00004,a1           ;// a1: VDP_CTRL_PORT 0xC00004
    lea         -4(a1),a2             ;// a2: VDP_DATA_PORT 0xC00000
    lea         5(a1),a3              ;// a3: HCounter address 0xC00009 (VDP_HVCOUNTER_PORT + 1)
    move.w      #0x8134,d4            ;// d4: VDP's register with display OFF value: (0x8100 | (0x74 & ~0x40)) = 0x8134
    move.w      #0x8174,d5            ;// d5: VDP's register with display ON value: (0x8100 | (0x74 | 0x40)) = 0x8174
    move.b      #156,d7               ;// d7: HCounter limit
    movea.l     #0x100000,a4          ;// a4: 0x100000 is the command offset for 8 colors sent to the VDP, used as: cmdAddress += 0x100000

;// color_batch_1_cmd
    ;// cmdAddress = palIdx == 0 ? 0xC0000000 : 0xC0400000;
    ;// set base command address once and then we'll add the right offset in next color batch blocks
    move.l      #0xC0000000,d6        ;// d6: cmdAddress = 0xC0000000
    tst.b       palIdx                ;// palIdx == 0?
    beq.s       0f
    move.l      #0xC0400000,d6        ;// d6: cmdAddress = 0xC0400000
0:
;// color_batch_1_pal
    ;//move.l      (a0)+,d0           ;// d0: colors2_A = *((u32*) (palInFramePtr + 0)); // 2 colors
    ;//move.l      (a0)+,d1           ;// d1: colors2_B = *((u32*) (palInFramePtr + 2)); // next 2 colors
    ;//move.l      (a0)+,d2           ;// d2: colors2_C = *((u32*) (palInFramePtr + 4)); // next 2 colors
    ;//move.l      (a0)+,d3           ;// d3: colors2_D = *((u32*) (palInFramePtr + 6)); // next 2 colors
    movem.l     (a0)+,d0-d3
1:
    ;// wait HCounter
    cmp.b       (a3),d7               ;// cmp: d7 - (a3). Compare byte size given that d7 won't be > 160 for our practical cases
    bhi.s       1b                    ;// loop back if d7 is higher than (a3)
    ;// turn off VDP
    move.w      d4,(a1)               ;// *(vu16*) VDP_CTRL_PORT = 0x8100 | (reg01 & ~0x40);
    ;// send colors
    move.l      d6,(a1)               ;// *((vu32*) VDP_CTRL_PORT) = cmdAddress;
    move.l      d0,(a2)               ;// *((vu32*) VDP_DATA_PORT) = colors2_A;
    move.l      d1,(a2)               ;// *((vu32*) VDP_DATA_PORT) = colors2_B;
    move.l      d2,(a2)               ;// *((vu32*) VDP_DATA_PORT) = colors2_C;
    move.l      d3,(a2)               ;// *((vu32*) VDP_DATA_PORT) = colors2_D;
    ;// turn on VDP
    move.w      d5,(a1)               ;// *(vu16*) VDP_CTRL_PORT = 0x8100 | (reg01 | 0x40);

;// color_batch_2_cmd
    ;// cmdAddress = palIdx == 0 ? 0xC0100000 : 0xC0500000;
    add.l       a4,d6                 ;// d6: cmdAddress += 0x100000 // previous batch advanced 8 colors
;// color_batch_2_pal
    ;//move.l      (a0)+,d0           ;// d0: colors2_A = *((u32*) (palInFramePtr + 8)); // 2 colors
    ;//move.l      (a0)+,d1           ;// d1: colors2_B = *((u32*) (palInFramePtr + 10)); // next 2 colors
    ;//move.l      (a0)+,d2           ;// d2: colors2_C = *((u32*) (palInFramePtr + 12)); // next 2 colors
    ;//move.l      (a0)+,d3           ;// d3: colors2_D = *((u32*) (palInFramePtr + 14)); // next 2 colors
    movem.l     (a0)+,d0-d3
1:
    ;// wait HCounter
    cmp.b       (a3),d7               ;// cmp: d7 - (a3). Compare byte size given that d7 won't be > 160 for our practical cases
    bhi.s       1b                    ;// loop back if d7 is higher than (a3)
    ;// turn off VDP
    move.w      d4,(a1)               ;// *(vu16*) VDP_CTRL_PORT = 0x8100 | (reg01 & ~0x40);
    ;// send colors
    move.l      d6,(a1)               ;// *((vu32*) VDP_CTRL_PORT) = cmdAddress;
    move.l      d0,(a2)               ;// *((vu32*) VDP_DATA_PORT) = colors2_A;
    move.l      d1,(a2)               ;// *((vu32*) VDP_DATA_PORT) = colors2_B;
    move.l      d2,(a2)               ;// *((vu32*) VDP_DATA_PORT) = colors2_C;
    move.l      d3,(a2)               ;// *((vu32*) VDP_DATA_PORT) = colors2_D;
    ;// turn on VDP
    move.w      d5,(a1)               ;// *(vu16*) VDP_CTRL_PORT = 0x8100 | (reg01 | 0x40);

;// color_batch_3_cmd
    ;// cmdAddress = palIdx == 0 ? 0xC0200000 : 0xC0600000;
    add.l       a4,d6                 ;// d6: cmdAddress += 0x100000 // previous batch advanced 8 colors
;// color_batch_3_pal
    ;//move.l      (a0)+,d0           ;// d0: colors2_A = *((u32*) (palInFramePtr + 16)); // 2 colors
    ;//move.l      (a0)+,d1           ;// d1: colors2_B = *((u32*) (palInFramePtr + 18)); // next 2 colors
    ;//move.l      (a0)+,d2           ;// d2: colors2_C = *((u32*) (palInFramePtr + 20)); // next 2 colors
    ;//move.l      (a0)+,d3           ;// d3: colors2_D = *((u32*) (palInFramePtr + 22)); // next 2 colors
    movem.l     (a0)+,d0-d3
1:
    ;// wait HCounter
    cmp.b       (a3),d7               ;// cmp: d7 - (a3). Compare byte size given that d7 won't be > 160 for our practical cases
    bhi.s       1b                    ;// loop back if d7 is higher than (a3)
    ;// turn off VDP
    move.w      d4,(a1)               ;// *(vu16*) VDP_CTRL_PORT = 0x8100 | (reg01 & ~0x40);
    ;// send colors
    move.l      d6,(a1)               ;// *((vu32*) VDP_CTRL_PORT) = cmdAddress;
    move.l      d0,(a2)               ;// *((vu32*) VDP_DATA_PORT) = colors2_A;
    move.l      d1,(a2)               ;// *((vu32*) VDP_DATA_PORT) = colors2_B;
    move.l      d2,(a2)               ;// *((vu32*) VDP_DATA_PORT) = colors2_C;
    move.l      d3,(a2)               ;// *((vu32*) VDP_DATA_PORT) = colors2_D;
    ;// turn on VDP
    move.w      d5,(a1)               ;// *(vu16*) VDP_CTRL_PORT = 0x8100 | (reg01 | 0x40);

;// color_batch_4_cmd
    ;// cmdAddress = palIdx == 0 ? 0xC0300000 : 0xC0700000;
    add.l       a4,d6                 ;// d6: cmdAddress += 0x100000 // previous batch advanced 8 colors
;// color_batch_4_pal
    ;//move.l      (a0)+,d0           ;// d0: colors2_A = *((u32*) (palInFramePtr + 24)); // 2 colors
    ;//move.l      (a0)+,d1           ;// d1: colors2_B = *((u32*) (palInFramePtr + 26)); // next 2 colors
    ;//move.l      (a0)+,d2           ;// d2: colors2_C = *((u32*) (palInFramePtr + 28)); // next 2 colors
    ;//move.l      (a0)+,d3           ;// d3: colors2_D = *((u32*) (palInFramePtr + 30)); // next 2 colors
    movem.l     (a0)+,d0-d3

;// accomodate_vars
    eori.b      #32,palIdx            ;// palIdx ^= MOVIE_FRAME_COLORS_PER_STRIP // cycles between 0 and 32
    move.l      a0,palInFramePtr      ;// store current pointer value of a0 into variable palInFramePtr

1:
    ;// wait HCounter
    cmp.b       (a3),d7               ;// cmp: d7 - (a3). Compare byte size given that d7 won't be > 160 for our practical cases
    bhi.s       1b                    ;// loop back if d7 is higher than (a3)
    ;// turn off VDP
    move.w      d4,(a1)               ;// *(vu16*) VDP_CTRL_PORT = 0x8100 | (reg01 & ~0x40);
    ;// send colors
    move.l      d6,(a1)               ;// *((vu32*) VDP_CTRL_PORT) = cmdAddress;
    move.l      d0,(a2)               ;// *((vu32*) VDP_DATA_PORT) = colors2_A;
    move.l      d1,(a2)               ;// *((vu32*) VDP_DATA_PORT) = colors2_B;
    move.l      d2,(a2)               ;// *((vu32*) VDP_DATA_PORT) = colors2_C;
    move.l      d3,(a2)               ;// *((vu32*) VDP_DATA_PORT) = colors2_D;
    ;// turn on VDP
    move.w      d5,(a1)               ;// *(vu16*) VDP_CTRL_PORT = 0x8100 | (reg01 | 0x40);


;//----------------------------------------------------------------------------
;// DMA approach.
;// Loads 2 palettes (32 colors) into CRAM in 2 batches of 16 colors each,
;// during the span of 8 scanlines. HInt is called every 8 scanlines.
;// For a display in V28 mode (224px) this routine is called 27 times.
;//----------------------------------------------------------------------------