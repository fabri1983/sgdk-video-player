#ifndef _PALS_BUFFERS_SWAPPER_H
#define _PALS_BUFFERS_SWAPPER_H

#include <types.h>

void setPalsRender (u16* ptr);

void setPalsUnpacked (u16* ptr);

u16* getPalsRender ();

u16* getPalsUnpacked ();

bool getDoSwapPalsBuffers ();

void doSwapPalsBuffers ();

void swapBuffersForPals ();

#endif