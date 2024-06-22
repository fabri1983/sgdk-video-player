#include "decomp/rlewxmap.h"
#include "compatibilities.h"

#define ADVANCE_ON_PARITY_ODD(in)\
    u8 dx = 0;\
    ASM_STATEMENT __volatile__ (\
        "    move.w  %0, %1\n"\
        "    andi.b  #1, %1\n"\
        "    beq     1f\n"\
        "    lea     (1,%0), %0\n"\
        "1:\n"\
        : "+a" (in), "=d" (dx) \
        : "0" (in), "1" (dx)\
        :\
    )\

// NOTE: use +d for vword since it is a read/write operand
#define DUPLICATE_WORD_INTO_LONG(vword, vlong)\
    ASM_STATEMENT __volatile__ (\
        "    move.w  %0, %1\n"\
        "    swap    %1\n"\
        "    move.w  %0, %1\n"\
        : "=d" (vword), "=d" (vlong)\
        : "0" (vword), "1" (vlong)\
        :\
    )\

// NOTE: modify the macro so vlong is read only
#define COPY_LONG_INTO_OUT(vlong, out)\
    ASM_STATEMENT __volatile__ (\
        "    move.l  %0, (%1)+\n"\
        : "=d" (vlong), "=a" (out)\
        : "0" (vlong), "1" (out)\
        : "memory"\
    )\

#define GET_LONG_AND_COPY_INTO_OUT(in, out)\
    ASM_STATEMENT __volatile__ (\
        "    move.l  (%0)+, (%1)+\n"\
        : "+a" (in), "=a" (out)\
        : "0" (in), "1" (out)\
        : "memory"\
    )\

#define GET_BYTE_AS_HIGH_INTO_WORD(in, vword)\
    ASM_STATEMENT __volatile__ (\
        "    move.b  (%0)+, -(%%sp)\n" /* byte goes to high half of new word on stack */ \
        "    move.w  (%%sp)+, %1\n"    /* pop the word into dx. Lower byte is 0 */ \
        : "+a" (in), "=d" (vword)\
        : "0" (in), "1" (vword)\
        : "memory"\
    )\

#define GET_BYTE_AS_LOW_INTO_WORD_AND_COPY_INTO_OUT(in, vword, out)\
    ASM_STATEMENT __volatile__ (\
        "    move.b  (%0)+, %1\n" /* byte goes to low half of destination leaving high half as it is */ \
        "    move.w  %1, (%2)+\n"\
        : "+a" (in), "+d" (vword), "=a" (out)\
        : "0" (in), "1" (vword), "2" (out)\
        : "memory"\
    )\

void NO_INLINE rlewxmap_decomp_A (const u8 jumpGap, u8* in, u8* out) {
    u8 rows = *in++; // get map rows
    while (rows) {
        u8 rleDescriptor = *in++; // read RLE descriptor byte and advance

        // if rleDescriptor != 0 then it's a simple RLE: just copy a word value N times
        if (rleDescriptor != 0) {
            // current address is odd? then consume additional parity byte
            ADVANCE_ON_PARITY_ODD(in);
            u16 value_w = *(u16*) in; // read word
            in += 2;
            u8 length = rleDescriptor & 0b00111111; // we know for sure length >= 1
            // length is odd? then copy first word
            if ((length & 1) != 0) {
                *(u16*) out = value_w;
                out += 2;
                --length;
            }
            if (length != 0) {
                // duplicate the word value into a long value
                u32 value_l = 0;
                DUPLICATE_WORD_INTO_LONG(value_w, value_l);
                // copy the long value N/2 times
                switch (length) {
                    case 34: COPY_LONG_INTO_OUT(value_l, out); // fall through
                    case 32: COPY_LONG_INTO_OUT(value_l, out); // fall through
                    case 30: COPY_LONG_INTO_OUT(value_l, out); // fall through
                    case 28: COPY_LONG_INTO_OUT(value_l, out); // fall through
                    case 26: COPY_LONG_INTO_OUT(value_l, out); // fall through
                    case 24: COPY_LONG_INTO_OUT(value_l, out); // fall through
                    case 22: COPY_LONG_INTO_OUT(value_l, out); // fall through
                    case 20: COPY_LONG_INTO_OUT(value_l, out); // fall through
                    case 18: COPY_LONG_INTO_OUT(value_l, out); // fall through
                    case 16: COPY_LONG_INTO_OUT(value_l, out); // fall through
                    case 14: COPY_LONG_INTO_OUT(value_l, out); // fall through
                    case 12: COPY_LONG_INTO_OUT(value_l, out); // fall through
                    case 10: COPY_LONG_INTO_OUT(value_l, out); // fall through
                    case  8: COPY_LONG_INTO_OUT(value_l, out); // fall through
                    case  6: COPY_LONG_INTO_OUT(value_l, out); // fall through
                    case  4: COPY_LONG_INTO_OUT(value_l, out); // fall through
                    case  2: COPY_LONG_INTO_OUT(value_l, out); // fall through
                    default: break;
                }
            }

            // is end of row bit set?
            if ((rleDescriptor & 0b10000000) != 0) {
                // makes the out buffer pointer jump over the region used as expanded width of the map
                out += jumpGap;
                --rows;
            }
        }
        // rleDescriptor == 0 then we're going to copy a stream of words
        else {
            u8 newRleDescriptor = *in++; // read new RLE descriptor byte and advance
            // current address is odd? then consume additional parity byte
            ADVANCE_ON_PARITY_ODD(in);
            u8 length = newRleDescriptor & 0b00111111; // we know for sure length >= 2
            // length is odd? then copy first word
            if ((length & 1) != 0) {
                *(u16*) out = *(u16*) in; // copy a word
                in += 2;
                out += 2;
                --length;
            }
            if (length != 0) {
                // copy remaining even number of words as pairs, ie copying 2 words (1 long) at a time
                switch (length) {
                    case 34: GET_LONG_AND_COPY_INTO_OUT(in, out); // fall through
                    case 32: GET_LONG_AND_COPY_INTO_OUT(in, out); // fall through
                    case 30: GET_LONG_AND_COPY_INTO_OUT(in, out); // fall through
                    case 28: GET_LONG_AND_COPY_INTO_OUT(in, out); // fall through
                    case 26: GET_LONG_AND_COPY_INTO_OUT(in, out); // fall through
                    case 24: GET_LONG_AND_COPY_INTO_OUT(in, out); // fall through
                    case 22: GET_LONG_AND_COPY_INTO_OUT(in, out); // fall through
                    case 20: GET_LONG_AND_COPY_INTO_OUT(in, out); // fall through
                    case 18: GET_LONG_AND_COPY_INTO_OUT(in, out); // fall through
                    case 16: GET_LONG_AND_COPY_INTO_OUT(in, out); // fall through
                    case 14: GET_LONG_AND_COPY_INTO_OUT(in, out); // fall through
                    case 12: GET_LONG_AND_COPY_INTO_OUT(in, out); // fall through
                    case 10: GET_LONG_AND_COPY_INTO_OUT(in, out); // fall through
                    case  8: GET_LONG_AND_COPY_INTO_OUT(in, out); // fall through
                    case  6: GET_LONG_AND_COPY_INTO_OUT(in, out); // fall through
                    case  4: GET_LONG_AND_COPY_INTO_OUT(in, out); // fall through
                    case  2: GET_LONG_AND_COPY_INTO_OUT(in, out); // fall through
                    default: break;
                }
            }

            // is end of row bit set?
            if ((newRleDescriptor & 0b10000000) != 0) {
                // makes the out buffer pointer jump over the region used as expanded width of the map
                out += jumpGap;
                --rows;
            }
        }
    }
}

void NO_INLINE rlewxmap_decomp_B (const u8 jumpGap, u8* in, u8* out) {
    u8 rows = *in++; // get map rows
    while (rows) {
        u8 rleDescriptor = *in++; // read RLE descriptor byte and advance
        u8 length = rleDescriptor & 0b00111111;

        // is end of row byte?
        if (rleDescriptor == 0) {
            // makes the out buffer pointer jump over the region used as expanded width of the map
            out += jumpGap;
            --rows;
        }
        // if descriptor's MSB == 0 (simple RLE) then just copy a word value N times
        else if (rleDescriptor < 0b10000000) {
            // current address is odd? then consume additional parity byte
            ADVANCE_ON_PARITY_ODD(in);
            u16 value_w = *(u16*) in; // read word
            in += 2;
            // length is odd? then copy first word
            if ((length & 1) != 0) {
                *(u16*) out = value_w;
                out += 2;
                --length;
                if (length == 0)
                    continue;
            }
            // duplicate the word value into a long value
            u32 value_l = 0;
            DUPLICATE_WORD_INTO_LONG(value_w, value_l);
            // copy the long value N/2 times
            switch (length) {
                case 34: COPY_LONG_INTO_OUT(value_l, out); // fall through
                case 32: COPY_LONG_INTO_OUT(value_l, out); // fall through
                case 30: COPY_LONG_INTO_OUT(value_l, out); // fall through
                case 28: COPY_LONG_INTO_OUT(value_l, out); // fall through
                case 26: COPY_LONG_INTO_OUT(value_l, out); // fall through
                case 24: COPY_LONG_INTO_OUT(value_l, out); // fall through
                case 22: COPY_LONG_INTO_OUT(value_l, out); // fall through
                case 20: COPY_LONG_INTO_OUT(value_l, out); // fall through
                case 18: COPY_LONG_INTO_OUT(value_l, out); // fall through
                case 16: COPY_LONG_INTO_OUT(value_l, out); // fall through
                case 14: COPY_LONG_INTO_OUT(value_l, out); // fall through
                case 12: COPY_LONG_INTO_OUT(value_l, out); // fall through
                case 10: COPY_LONG_INTO_OUT(value_l, out); // fall through
                case  8: COPY_LONG_INTO_OUT(value_l, out); // fall through
                case  6: COPY_LONG_INTO_OUT(value_l, out); // fall through
                case  4: COPY_LONG_INTO_OUT(value_l, out); // fall through
                case  2: COPY_LONG_INTO_OUT(value_l, out); // fall through
                default: break;
            }
        }
        // descriptor's MSB == 1 (stream) and if next bit for common high byte is 0, then is a stream of words
        else if (rleDescriptor < 0b11000000) {
            // current address is odd? then consume additional parity byte
            ADVANCE_ON_PARITY_ODD(in);
            // length is odd? then copy first word
            if ((length & 1) != 0) {
                *(u16*) out = *(u16*) in; // read word
                in += 2;
                out += 2;
                --length;
                if (length == 0)
                    continue;
            }
            // copy remaining even number of words as pairs, ie copying 2 words (1 long) at a time
            switch (length) {
                case 34: GET_LONG_AND_COPY_INTO_OUT(in, out); // fall through
                case 32: GET_LONG_AND_COPY_INTO_OUT(in, out); // fall through
                case 30: GET_LONG_AND_COPY_INTO_OUT(in, out); // fall through
                case 28: GET_LONG_AND_COPY_INTO_OUT(in, out); // fall through
                case 26: GET_LONG_AND_COPY_INTO_OUT(in, out); // fall through
                case 24: GET_LONG_AND_COPY_INTO_OUT(in, out); // fall through
                case 22: GET_LONG_AND_COPY_INTO_OUT(in, out); // fall through
                case 20: GET_LONG_AND_COPY_INTO_OUT(in, out); // fall through
                case 18: GET_LONG_AND_COPY_INTO_OUT(in, out); // fall through
                case 16: GET_LONG_AND_COPY_INTO_OUT(in, out); // fall through
                case 14: GET_LONG_AND_COPY_INTO_OUT(in, out); // fall through
                case 12: GET_LONG_AND_COPY_INTO_OUT(in, out); // fall through
                case 10: GET_LONG_AND_COPY_INTO_OUT(in, out); // fall through
                case  8: GET_LONG_AND_COPY_INTO_OUT(in, out); // fall through
                case  6: GET_LONG_AND_COPY_INTO_OUT(in, out); // fall through
                case  4: GET_LONG_AND_COPY_INTO_OUT(in, out); // fall through
                case  2: GET_LONG_AND_COPY_INTO_OUT(in, out); // fall through
                default: break;
            }
        }
        // descriptor's MSB == 1 (stream) and next bit for common high byte is 1, then is a stream with a common high byte
        else {
            // read common byte into high half of word and advance
            u16 value_w = 0;
            GET_BYTE_AS_HIGH_INTO_WORD(in, value_w);
            switch (length) { // we know length >= 2
                case 34: GET_BYTE_AS_LOW_INTO_WORD_AND_COPY_INTO_OUT(in, value_w, out); // fall through
                case 33: GET_BYTE_AS_LOW_INTO_WORD_AND_COPY_INTO_OUT(in, value_w, out); // fall through
                case 32: GET_BYTE_AS_LOW_INTO_WORD_AND_COPY_INTO_OUT(in, value_w, out); // fall through
                case 31: GET_BYTE_AS_LOW_INTO_WORD_AND_COPY_INTO_OUT(in, value_w, out); // fall through
                case 30: GET_BYTE_AS_LOW_INTO_WORD_AND_COPY_INTO_OUT(in, value_w, out); // fall through
                case 29: GET_BYTE_AS_LOW_INTO_WORD_AND_COPY_INTO_OUT(in, value_w, out); // fall through
                case 28: GET_BYTE_AS_LOW_INTO_WORD_AND_COPY_INTO_OUT(in, value_w, out); // fall through
                case 27: GET_BYTE_AS_LOW_INTO_WORD_AND_COPY_INTO_OUT(in, value_w, out); // fall through
                case 26: GET_BYTE_AS_LOW_INTO_WORD_AND_COPY_INTO_OUT(in, value_w, out); // fall through
                case 25: GET_BYTE_AS_LOW_INTO_WORD_AND_COPY_INTO_OUT(in, value_w, out); // fall through
                case 24: GET_BYTE_AS_LOW_INTO_WORD_AND_COPY_INTO_OUT(in, value_w, out); // fall through
                case 23: GET_BYTE_AS_LOW_INTO_WORD_AND_COPY_INTO_OUT(in, value_w, out); // fall through
                case 22: GET_BYTE_AS_LOW_INTO_WORD_AND_COPY_INTO_OUT(in, value_w, out); // fall through
                case 21: GET_BYTE_AS_LOW_INTO_WORD_AND_COPY_INTO_OUT(in, value_w, out); // fall through
                case 20: GET_BYTE_AS_LOW_INTO_WORD_AND_COPY_INTO_OUT(in, value_w, out); // fall through
                case 19: GET_BYTE_AS_LOW_INTO_WORD_AND_COPY_INTO_OUT(in, value_w, out); // fall through
                case 18: GET_BYTE_AS_LOW_INTO_WORD_AND_COPY_INTO_OUT(in, value_w, out); // fall through
                case 17: GET_BYTE_AS_LOW_INTO_WORD_AND_COPY_INTO_OUT(in, value_w, out); // fall through
                case 16: GET_BYTE_AS_LOW_INTO_WORD_AND_COPY_INTO_OUT(in, value_w, out); // fall through
                case 15: GET_BYTE_AS_LOW_INTO_WORD_AND_COPY_INTO_OUT(in, value_w, out); // fall through
                case 14: GET_BYTE_AS_LOW_INTO_WORD_AND_COPY_INTO_OUT(in, value_w, out); // fall through
                case 13: GET_BYTE_AS_LOW_INTO_WORD_AND_COPY_INTO_OUT(in, value_w, out); // fall through
                case 12: GET_BYTE_AS_LOW_INTO_WORD_AND_COPY_INTO_OUT(in, value_w, out); // fall through
                case 11: GET_BYTE_AS_LOW_INTO_WORD_AND_COPY_INTO_OUT(in, value_w, out); // fall through
                case 10: GET_BYTE_AS_LOW_INTO_WORD_AND_COPY_INTO_OUT(in, value_w, out); // fall through
                case  9: GET_BYTE_AS_LOW_INTO_WORD_AND_COPY_INTO_OUT(in, value_w, out); // fall through
                case  8: GET_BYTE_AS_LOW_INTO_WORD_AND_COPY_INTO_OUT(in, value_w, out); // fall through
                case  7: GET_BYTE_AS_LOW_INTO_WORD_AND_COPY_INTO_OUT(in, value_w, out); // fall through
                case  6: GET_BYTE_AS_LOW_INTO_WORD_AND_COPY_INTO_OUT(in, value_w, out); // fall through
                case  5: GET_BYTE_AS_LOW_INTO_WORD_AND_COPY_INTO_OUT(in, value_w, out); // fall through
                case  4: GET_BYTE_AS_LOW_INTO_WORD_AND_COPY_INTO_OUT(in, value_w, out); // fall through
                case  3: GET_BYTE_AS_LOW_INTO_WORD_AND_COPY_INTO_OUT(in, value_w, out); // fall through
                case  2: GET_BYTE_AS_LOW_INTO_WORD_AND_COPY_INTO_OUT(in, value_w, out); // fall through
                case  1: GET_BYTE_AS_LOW_INTO_WORD_AND_COPY_INTO_OUT(in, value_w, out); break;
                default: break;
            }
        }
    }
}