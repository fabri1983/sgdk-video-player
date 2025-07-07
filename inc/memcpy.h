#ifndef _MEMCPY_H
#define _MEMCPY_H

#include <types.h>

// This method adds the extended width to the destination pointer after each bulk of MOVIE_FRAME_HEIGHT_IN_TILES.
extern void memcpy_tilemap_asm (u8* from, u8* to);

/// Tileset length in bytes is multiple of 4. So we can use long words to copy 4 bytes in one instruction.
/// Tileset length in bytes >= 56 due to use of movem.l instruction with 14 registers.
extern void memcpy_tileset_64_asm (u8* from, u8* to);
extern void memcpy_tileset_128_asm (u8* from, u8* to);

/// lenBytes is multiple of 4 and >= 4. So we can use long words to copy 4 bytes in one instruction.
void memcpy_asm (u16 lenBytes, u8* from, u8* to);

#endif // _MEMCPY_H