
* ------------------------
* Kosinski macros
* ------------------------
.macro _Kos_RunBitStream 
	dbra	%d2, 1f
	moveq	#7, %d2				// We have 8 new bits, but will use one up below.
	move.b	%d1, %d0			// Use the remaining 8 bits.
	not.w	%d3					// Have all 16 bits been used up?
	bne.s	1f					// Branch if not.
	move.b	(%a0)+, %d0			// Get desc field low-byte.
	move.b	(%a0)+, %d1			// Get desc field hi-byte.
#if _Kos_UseLUT == 1
	move.b	(%a4,%d0.w), %d0	// Invert bit order...
	move.b	(%a4,%d1.w), %d1	// ... for both bytes.
#endif
1:
.endm

.macro _Kos_ReadBit 
#if _Kos_UseLUT == 1
    add.b	%d0, %d0		// Get a bit from the bitstream.
#else
    lsr.b	#1, %d0			// Get a bit from the bitstream.
#endif
.endm

* ------------------------
* Kosinski Plus macros
* ------------------------
.macro _KosPlus_ReadBit 
    dbra	%d2, 2f         // suffix f used to search for forward label definition, useful when macro is called many times
    moveq	#7, %d2         // We have 8 new bits, but will use one up below.
    move.b	(%a0)+, %d0     // Get desc field low-byte.
2:
    add.b	%d0, %d0        // Get a bit from the bitstream.
.endm

* ------------------------
* Comper macros
* ------------------------
.macro _Comp_RunBitStream_s 
	dbra	%d3, .comp_mainloop	    // if bits counter remains, parse the next word
	bra.s	.comp_newblock	        // start a new block
.endm

.macro _Comp_ReadBit 
	add.w   %d0, %d0        // roll description field
.endm
