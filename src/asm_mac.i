* This file included from other .s files

.macro func _name, _align=2
    .section .text.asm.\_name
    .globl  \_name
    .type   \_name, @function
    .align  \_align
  \_name:
.endm
        lea         0xC00004,a1 ;// a1: VDP_CTRL_PORT 0xC00004
        move.l      #0xC00004,a1 ;// a1: VDP_CTRL_PORT 0xC00004
        lea         5(a1),a2    ;// a2: HCounter address 0xC00009 (VDP_HVCOUNTER_PORT + 1)