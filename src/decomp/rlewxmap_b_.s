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

#define RLEWXMAP_WIDTH_IN_TILES 34

.macro RLEWXM_ADVANCE_ON_PARITY_ODD
    move.w      a0, d4
	andi.b      #1, d4
    beq         1f                  ;// if parity is even then jump and continue
    lea         (1,a0), a0        ;// parity is odd then advance one byte
1:
.endm

* ||||||||||||||| S U B R O U T I N E |||||||||||||||||||||||||||||||||||||||
* ---------------------------------------------------------------------------
* C prototype: extern void rlewxmap_decomp_b (u8 jumpGap, u8* src, u8* dest);
func rlewxmap_decomp_b
    move.b      4(sp), d0         ;// copy parameter into register d0
	movem.l     8(sp), a0-a1      ;// copy parameters into registers a0-a1
	movem.l     d1-d4/a2, -(sp)      ;// save registers (except the scratch pad)

    move.b      (a0)+, d1          ;// d1: rows
    subq.b      #1, d1              ;// decrement rows here because we use dbra/dbf for the big loop at the end

.rlewxm_desc:
    move.b      (a0)+, d2            ;// d2: rleDescriptor
    move.b      d2, d3
    andi.b      #$3F, d3              ;// d3: length = rleDescriptor & 0b00111111
    beq         .rlewxm_end_of_row    ;// if (descriptor == 0) then is end of row
    cmpi.b      #$80, d2             ;// test rleDescriptor < 0b10000000 => bit 8 set and 7 not set
    bcs         .rlewxm_rle           ;// if (rleDescriptor < 0b10000000) then is a simple RLE
    cmpi.b      #$C0, d2             ;// test rleDescriptor < 0b11000000 => bit 8 and 7 set
    ;// At this point d2 is not needed anymore
    ;// At this point MSB == 1 so its a stream. Then check if it's a stream of words or bytes
    bcs         .rlewxm_stream_w     ;// if (rleDescriptor < 0b11000000) then is a stream of words
    ;// It's a stream of bytes
.rlewxm_stream_b:

.rlewxm_stream_w:
    RLEWXM_ADVANCE_ON_PARITY_ODD
    
.rlewxm_rle:
    RLEWXM_ADVANCE_ON_PARITY_ODD
    move.w      (a0)+, d2          ;// d2: u16 value_w = *(u16*) in; // read word
    btst        #0, d3              ;// test length parity
    beq         2f                   ;// length is even? then jump
    ;// length parity is odd => copy a single word
    move.w      d2, (a1)+          ;// *(u16*) out = value_w
    subq.b      #1, d3              ;// --length
    beq         .rlewxm_desc        ;// if length == 0 then jump to get next descriptor
2:
    ;// length parity is even => copy 2 words at a time
    move.w      d2, d4
    swap        d2
    move.w      d4, d2              ;// u32 value_l = (value_w << 16) | value_w;
    ;// prepare jump offset
    lsr.b       #1, d3              ;// length /= 2 so we can use it in the jump table
    moveq       #RLEWXMAP_WIDTH_IN_TILES/2, d4  ;// jmp_offset = RLEWXMAP_WIDTH_IN_TILES/2
    sub.b       d3, d4
    add.b       d4, d4
    add.b       d4, d4              ;// jmp_offset * 4 because every instruction in the jump table takes 4 bytes
    jmp         .jmp_table_rle(pc,d4)
.jmp_table_rle:
.rept (RLEWXMAP_WIDTH_IN_TILES/2)
    move.l	    d2, (a1)+
.endr
    jmp         .rlewxm_desc        ;// jump to get next descriptor

.rlewxm_end_of_row:
    lea         (a1,d0), a1          ;// jumps the gap used as expanded width of the map
    dbf         d1, .rlewxm_desc      ;// dbra/dbf: test if not zero, then decrement rows and branch

* ---------------------------------------------------------------------------
.rlewxm_quit:
    movem.l     (sp)+, d1-d4/a2         ;// restore registers (except the scratch pad)
