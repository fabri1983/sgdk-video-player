#ifndef _PALS_BUFFERS_SWAPPER_H
#define _PALS_BUFFERS_SWAPPER_H

#include <types.h>

void setPalsBufferA (u16* ptr);

void setPalsBufferB (u16* ptr);

u16* getPalsBufferA ();

u16* getPalsBufferB ();

bool getDoSwapPalsBuffers ();

void setDoSwapPalsBuffers (bool value);

void swapBuffersForPalettes ();

#endif