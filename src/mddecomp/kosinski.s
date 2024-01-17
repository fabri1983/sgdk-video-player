
* ---------------------------------------------------------------------------
* For format explanation see https://segaretro.org/Kosinski_compression
* New faster version written by vladikcomper, with additional improvements
* by MarkeyJester and Flamewing
* ---------------------------------------------------------------------------
* Permission to use, copy, modify, and/or distribute this software for any
* purpose with or without fee is hereby granted.
*
* THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
* WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
* MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
* ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
* WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
* ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT
* OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
* ---------------------------------------------------------------------------
* FUNCTION:
* 	KosDec
*
* DESCRIPTION
* 	Kosinski Decompressor
*
* INPUT:
* 	a0	source address
* 	a1	destination address
* ---------------------------------------------------------------------------

#define _Kos_UseLUT 1
#define _Kos_LoopUnroll 3
#define _Kos_ExtremeUnrolling 1

#include "asm_mac.i"
#include "asm_decomp_mac.i"

* ||||||||||||||| S U B R O U T I N E |||||||||||||||||||||||||||||||||||||||
* ---------------------------------------------------------------------------
func KosDec
#if _Kos_LoopUnroll > 0
    moveq	#(1 << _Kos_LoopUnroll) - 1, %d7
#endif
#if _Kos_UseLUT == 1
    moveq	#0, %d0
    moveq	#0, %d1
    lea	kos_KosDec_ByteMap(%pc), %a4	// Load LUT pointer.
#endif
    move.b	(%a0)+, %d0				    // Get desc field low-byte.
    move.b	(%a0)+, %d1				    // Get desc field hi-byte.
#if _Kos_UseLUT==1
    move.b	(%a4,%d0.w), %d0	        // Invert bit order...
    move.b	(%a4,%d1.w), %d1		    // ... for both bytes.
#endif
    moveq	#7, %d2					    // Set repeat count to 8.
    moveq	#0, %d3					    // d3 will be desc field switcher.
    bra.s	.kos_FetchNewCode
* ---------------------------------------------------------------------------
.kos_FetchCodeLoop:
	* Code 1 (Uncompressed byte).
	_Kos_RunBitStream
	move.b	(%a0)+, (%a1)+

.kos_FetchNewCode:
    _Kos_ReadBit
    bcs.s	.kos_FetchCodeLoop			// If code = 1, branch.

    * Codes 00 and 01.
    moveq	#-1, %d5
    lea	    (%a1), %a5
    _Kos_RunBitStream
#if _Kos_ExtremeUnrolling == 1
    _Kos_ReadBit
    bcs.w	.kos_Code_01

    * Code 00 (Dictionary ref. short).
    _Kos_RunBitStream
    _Kos_ReadBit
    bcs.s	.kos_Copy45
    _Kos_RunBitStream
    _Kos_ReadBit
    bcs.s	.kos_Copy3
    _Kos_RunBitStream
    move.b	(%a0)+, %d5				// d5 = displacement.
    adda.w	%d5, %a5
    move.b	(%a5)+, (%a1)+
    move.b	(%a5)+, (%a1)+
    bra.s	.kos_FetchNewCode
* ---------------------------------------------------------------------------
.kos_Copy3:
    _Kos_RunBitStream
    move.b	(%a0)+, %d5				// d5 = displacement.
    adda.w	%d5, %a5
    move.b	(%a5)+, (%a1)+
    move.b	(%a5)+, (%a1)+
    move.b	(%a5)+, (%a1)+
    bra.w	.kos_FetchNewCode
* ---------------------------------------------------------------------------
.kos_Copy45:
    _Kos_RunBitStream
    _Kos_ReadBit
    bcs.s	.kos_Copy5
    _Kos_RunBitStream
    move.b	(%a0)+, %d5				// d5 = displacement.
    adda.w	%d5, %a5
    move.b	(%a5)+, (%a1)+
    move.b	(%a5)+, (%a1)+
    move.b	(%a5)+, (%a1)+
    move.b	(%a5)+, (%a1)+
    bra.w	.kos_FetchNewCode
* ---------------------------------------------------------------------------
.kos_Copy5:
    _Kos_RunBitStream
    move.b	(%a0)+, %d5				// d5 = displacement.
    adda.w	%d5, %a5
    move.b	(%a5)+, (%a1)+
    move.b	(%a5)+, (%a1)+
    move.b	(%a5)+, (%a1)+
    move.b	(%a5)+, (%a1)+
    move.b	(%a5)+, (%a1)+
    bra.w	.kos_FetchNewCode
* ---------------------------------------------------------------------------
#else
    moveq	#0, %d4					// d4 will contain copy count.
    _Kos_ReadBit
    bcs.s	.kos_Code_01

    * Code 00 (Dictionary ref. short).
    _Kos_RunBitStream
    _Kos_ReadBit
    addx.w	%d4, %d4
    _Kos_RunBitStream
    _Kos_ReadBit
    addx.w	%d4, %d4
    _Kos_RunBitStream
    move.b	(%a0)+, %d5				// d5 = displacement.

.kos_StreamCopy:
    adda.w	%d5, %a5
    move.b	(%a5)+, (%a1)+				; Do 1 extra copy (to compensate +1 to copy counter).

.kos_copy:
    move.b	(%a5)+, (%a1)+
    dbra	%d4, .kos_copy
    bra.w	.kos_FetchNewCode
#endif
* ---------------------------------------------------------------------------
.kos_Code_01:
	moveq	#0, %d4				    // d4 will contain copy count.
	* Code 01 (Dictionary ref. long / special).
	_Kos_RunBitStream
	move.b	(%a0)+, %d6			    // d6 = %LLLLLLLL.
	move.b	(%a0)+, %d4			    // d4 = %HHHHHCCC.
	move.b	%d4, %d5				// d5 = %11111111 HHHHHCCC.
	lsl.w	#5, %d5				    // d5 = %111HHHHH CCC00000.
	move.b	%d6, %d5				// d5 = %111HHHHH LLLLLLLL.
#if _Kos_LoopUnroll == 3
	and.w	%d7, %d4				// d4 = %00000CCC.
#else
	andi.w	#7, %d4				    // d4 = %00000CCC.
#endif
	bne.s	.kos_StreamCopy			// if CCC=0, branch.

	* special mode (extended counter)
	move.b	(%a0)+, %d4				// Read cnt
	beq.s	.kos_Quit				// If cnt=0, quit decompression.
	subq.b	#1, %d4
	beq.w	.kos_FetchNewCode		// If cnt=1, fetch a new code.

	adda.w	%d5, %a5
	move.b	(%a5)+, (%a1)+			// Do 1 extra copy (to compensate +1 to copy counter).
#if _Kos_LoopUnroll > 0
    move.w	%d4, %d6
    not.w	%d6
    and.w	%d7, %d6
    add.w	%d6, %d6
    lsr.w	#_Kos_LoopUnroll, %d4
    jmp	    .kos_largecopy(%pc,%d6.w)
#endif
* ---------------------------------------------------------------------------
.kos_largecopy:
#rept (1 << _Kos_LoopUnroll)
    move.b	(%a5)+, (%a1)+
#endr
	dbra	%d4, .kos_largecopy
	bra.w	.kos_FetchNewCode
* ---------------------------------------------------------------------------
#if _Kos_ExtremeUnrolling == 1
.kos_StreamCopy:
    adda.w	%d5, %a5
    move.b	(%a5)+, (%a1)+			// Do 1 extra copy (to compensate +1 to copy counter).
#if _Kos_LoopUnroll == 3
    eor.w	%d7, %d4
#else
    eori.w	#7, %d4
#endif
    add.w	%d4, %d4
    jmp	    .kos_mediumcopy(%pc,%d4.w)
* ---------------------------------------------------------------------------
.kos_mediumcopy:
#rept 8
    move.b	(%a5)+, (%a1)+
#endr
    bra.w	.kos_FetchNewCode
#endif
* ---------------------------------------------------------------------------
.kos_Quit:
	rts								// End of function KosDec.
* ===========================================================================
#if _Kos_UseLUT == 1
kos_KosDec_ByteMap:
	dc.b	$00,$80,$40,$C0,$20,$A0,$60,$E0,$10,$90,$50,$D0,$30,$B0,$70,$F0
	dc.b	$08,$88,$48,$C8,$28,$A8,$68,$E8,$18,$98,$58,$D8,$38,$B8,$78,$F8
	dc.b	$04,$84,$44,$C4,$24,$A4,$64,$E4,$14,$94,$54,$D4,$34,$B4,$74,$F4
	dc.b	$0C,$8C,$4C,$CC,$2C,$AC,$6C,$EC,$1C,$9C,$5C,$DC,$3C,$BC,$7C,$FC
	dc.b	$02,$82,$42,$C2,$22,$A2,$62,$E2,$12,$92,$52,$D2,$32,$B2,$72,$F2
	dc.b	$0A,$8A,$4A,$CA,$2A,$AA,$6A,$EA,$1A,$9A,$5A,$DA,$3A,$BA,$7A,$FA
	dc.b	$06,$86,$46,$C6,$26,$A6,$66,$E6,$16,$96,$56,$D6,$36,$B6,$76,$F6
	dc.b	$0E,$8E,$4E,$CE,$2E,$AE,$6E,$EE,$1E,$9E,$5E,$DE,$3E,$BE,$7E,$FE
	dc.b	$01,$81,$41,$C1,$21,$A1,$61,$E1,$11,$91,$51,$D1,$31,$B1,$71,$F1
	dc.b	$09,$89,$49,$C9,$29,$A9,$69,$E9,$19,$99,$59,$D9,$39,$B9,$79,$F9
	dc.b	$05,$85,$45,$C5,$25,$A5,$65,$E5,$15,$95,$55,$D5,$35,$B5,$75,$F5
	dc.b	$0D,$8D,$4D,$CD,$2D,$AD,$6D,$ED,$1D,$9D,$5D,$DD,$3D,$BD,$7D,$FD
	dc.b	$03,$83,$43,$C3,$23,$A3,$63,$E3,$13,$93,$53,$D3,$33,$B3,$73,$F3
	dc.b	$0B,$8B,$4B,$CB,$2B,$AB,$6B,$EB,$1B,$9B,$5B,$DB,$3B,$BB,$7B,$FB
	dc.b	$07,$87,$47,$C7,$27,$A7,$67,$E7,$17,$97,$57,$D7,$37,$B7,$77,$F7
	dc.b	$0F,$8F,$4F,$CF,$2F,$AF,$6F,$EF,$1F,$9F,$5F,$DF,$3F,$BF,$7F,$FF
#endif
* ===========================================================================
