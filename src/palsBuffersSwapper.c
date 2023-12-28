#include "palsBuffersSwapper.h"

static u16* unpackedPalsBufferA;
static u16* unpackedPalsBufferB;
static bool doSwap = FALSE;

void setPalsRender (u16* ptr) {
    unpackedPalsBufferA = ptr;
}

void setPalsUnpacked (u16* ptr) {
    unpackedPalsBufferB = ptr;
}

u16* getPalsRender () {
    return unpackedPalsBufferA;
}

u16* getPalsUnpacked () {
    return unpackedPalsBufferB;
}

bool getDoSwapPalsBuffers () {
    return doSwap;
}

void doSwapPalsBuffers () {
    doSwap = TRUE;
}

void swapBuffersForPals () {
	u16* tmp = unpackedPalsBufferA;
	unpackedPalsBufferA = unpackedPalsBufferB;
	unpackedPalsBufferB = tmp;
    doSwap = FALSE;
}