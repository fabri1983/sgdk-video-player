* ---------------------------------------------------------------------------
* Original version written by fabri1983
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
* 	rlewxmap_decomp_b
*
* DESCRIPTION
* 	Method B: higher compression ratio but slower decompression time.
*   Decompress a RLEWX compressed map of N rows of M tiles each and applying 
*   offsets in the out buffer at the end of each row according to the extended 
*   width of 64 tiles the target map buffer is lay out with.
*
* INPUT:
*   d0  Jump gap in bytes on destination buffer when a row has been decompressed
* 	a0	Source address
* 	a1	Destination address
* ---------------------------------------------------------------------------

#include "asm_mac.i"

#define RLEWXMAP_WIDTH_IN_TILES 40

.macro RLEWXM_ADVANCE_ON_PARITY_ODD
    move.w      a0, d4
	andi.b      #1, d4
    beq.s       1f                  ;// if parity is even then jump and continue
    lea         (1,a0), a0          ;// parity is odd then advance one byte
1:
.endm

* ||||||||||||||| S U B R O U T I N E |||||||||||||||||||||||||||||||||||||||
* ---------------------------------------------------------------------------
* C prototype: extern void rlewxmap_decomp_B_asm (u8 jumpGap, u8* src, u8* dest);
func rlewxmap_decomp_B_asm
	movem.l     4(sp), d0/a0-a1     ;// copy parameters into registers d0/a0-a1
	movem.l     d1-d4, -(sp)        ;// save registers (except the scratch pad)

    move.b      (a0)+, d1           ;// d1: rows
    andi.w      #0xFF,d1            ;// clean higher byte of d1
    subq.b      #1, d1              ;// decrement rows here because we use dbra/dbf for the big loop at the end

.b_rlewxm_get_desc:
    move.b      (a0)+, d2           ;// d2: rleDescriptor
    tst.b       d2
    bne.s       .b_rlewxm_new_segment   ;// if (descriptor != 0) then we continue with a new segment
    ;// descriptor == 0 => is end of row
    adda.l      d0, a1              ;// jumps the gap used as expanded width in the map
    dbra        d1, .b_rlewxm_get_desc  ;// dbra/dbf: decrement rows, test if rows >= 0 then branch back. When rows = -1 then no branch
    ;// no more rows then quit
    movem.l     (sp)+, d1-d4        ;// restore registers (except the scratch pad)
    rts

.b_rlewxm_new_segment:
    cmpi.b      #0x80, d2           ;// test rleDescriptor < 0b10000000 => bit 8 set and 7 not set
    bcs         .b_rlewxm_rle       ;// if (rleDescriptor < 0b10000000) then is a simple RLE
    cmpi.b      #0xC0, d2           ;// test rleDescriptor < 0b11000000 => bit 8 and 7 set
    ;// At this point MSB == 1 so its a stream. Then check if it's a stream of words or bytes
    bcs         .b_rlewxm_stream_sw     ;// if (rleDescriptor < 0b11000000) then is a stream of words
    ;// It's a stream of a common high byte followed by lower bytes

.b_rlewxm_stream_cb:
    andi.w      #0x3F, d2           ;// d2: length = rleDescriptor & 0b00111111
    ;// prepare jump offset
    moveq       #RLEWXMAP_WIDTH_IN_TILES, d3    ;// moveq is longword operation and won't hold garbage in higher bytes
    sub.w       d2, d3              ;// jump offset => d3 = RLEWXMAP_WIDTH_IN_TILES - length
    add.w       d3, d3
    add.w       d3, d3              ;// d3 * 4 because every target instruction set takes 4 bytes
    move.b      (a0)+, -(sp)        ;// byte goes to high half of new word on stack
    move.w      (sp)+, d2           ;// pop the word into d2. Lower byte is garbage (whatever was in the stack)
    jmp         .b_jmp_stream_cb(pc,d3.w)
.b_jmp_stream_cb:
.rept (RLEWXMAP_WIDTH_IN_TILES)
    move.b      (a0)+, d2           ;// byte goes to low half of destination leaving high half as it is
    move.w      d2, (a1)+
.endr
    bra         .b_rlewxm_get_desc  ;// jump to get next descriptor

.b_rlewxm_stream_sw:
    RLEWXM_ADVANCE_ON_PARITY_ODD    ;// current in address is odd? then consume additional parity byte
    andi.w      #0x3F, d2           ;// d2: length = rleDescriptor & 0b00111111
    btst        #0, d2              ;// test length parity. Here we know length >= 2.
    beq.s       2f                  ;// length is even? then jump
    ;// length is odd => copy first word
    move.w      (a0)+, (a1)+        ;// *(u16*) out = *(u16*) in
    subq.w      #1, d2              ;// --length
2:
    ;// length is even => copy 2 words (1 long) at a time
    ;// prepare jump offset
    moveq       #RLEWXMAP_WIDTH_IN_TILES, d3    ;// moveq is longword operation and won't hold garbage in higher bytes
    sub.w       d2, d3              ;// jump offset => d3 = RLEWXMAP_WIDTH_IN_TILES - length
                                    ;// every target instruction takes 2 bytes but we have *2 inherently in d3
    jmp         .b_jmp_stream_w(pc,d3.w)
.b_jmp_stream_w:
.rept (RLEWXMAP_WIDTH_IN_TILES/2)   ;// for RLEWXMAP_WIDTH_IN_TILES being odd we already covered that case above
    move.l	    (a0)+, (a1)+
.endr
    bra         .b_rlewxm_get_desc  ;// jump to get next descriptor

.b_rlewxm_rle:
    RLEWXM_ADVANCE_ON_PARITY_ODD    ;// current in address is odd? then consume additional parity byte
    move.w      (a0)+, d3           ;// d3: u16 value_w = *(u16*) in
    andi.w      #0x3F, d2           ;// d2: length = rleDescriptor & 0b00111111
    btst        #0, d2              ;// test length parity. Here we know length >= 1.
    beq.s       2f                  ;// length is even? then jump
    ;// length is odd => copy first word
    move.w      d3, (a1)+           ;// *(u16*) out = value_w
    subq.w      #1, d2              ;// --length
    beq         .b_rlewxm_get_desc  ;// if length == 0 then jump to get next descriptor
2:
    ;// length is even => copy 2 words (1 long) at a time
    move.w      d3, d4
    swap        d3
    move.w      d4, d3              ;// d3: u32 value_l = (value_w << 16) | value_w;
    ;// prepare jump offset
    moveq       #RLEWXMAP_WIDTH_IN_TILES, d4    ;// moveq is longword and operation won't hold garbage in higher bytes
    sub.w       d2, d4              ;// jump offset => d4 = RLEWXMAP_WIDTH_IN_TILES - length
                                    ;// every target instruction takes 2 bytes but we have *2 inherently in d4
    jmp         .b_jmp_rle(pc,d4.w)
.b_jmp_rle:
.rept (RLEWXMAP_WIDTH_IN_TILES/2)   ;// for RLEWXMAP_WIDTH_IN_TILES being odd we already covered that case above
    move.l	    d3, (a1)+
.endr
    bra         .b_rlewxm_get_desc  ;// jump to get next descriptor
