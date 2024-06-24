#ifndef _RLEWMAP_H
#define _RLEWMAP_H

#include <types.h>

/// @brief Method A: lower compression ratio but faster decompression time.
/// Decompress a RLEWXMAP compressed map of N rows of M tiles each and applying offsets in the out buffer at 
/// the end of each row according to the extended width of 64 tiles the target map buffer is lay out with.
/// @param jumpGap Is the number of bytes to jump once the end of a row is reached on the output buffer.
/// @param in 
/// @param out 
void rlewxmap_decomp_A (const u8 jumpGap, u8* in, u8* out);

/// @brief Method B: higher compression ratio but slower decompression time.
/// Decompress a RLEWXMAP compressed map of N rows of M tiles each and applying offsets in the out buffer at 
/// the end of each row according to the extended width of 64 tiles the target map buffer is lay out with.
/// @param jumpGap Is the number of bytes to jump once the end of a row is reached on the output buffer.
/// @param in 
/// @param out 
void rlewxmap_decomp_B (const u8 jumpGap, u8* in, u8* out);

/// @brief Method B in ASM: higher compression ratio but slower decompression time.
/// Decompress a RLEWXMAP compressed map of N rows of M tiles each and applying offsets in the out buffer at 
/// the end of each row according to the extended width of 64 tiles the target map buffer is lay out with.
/// @param jumpGap Is the number of bytes to jump once the end of a row is reached on the output buffer.
/// @param in 
/// @param out 
extern void rlewxmap_decomp_B_asm (const u8 jumpGap, u8* in, u8* out);

#endif // _RLEWMAP_H