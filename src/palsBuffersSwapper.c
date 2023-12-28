#include "palsBuffersSwapper.h"

static u16* unpackedPalsBufferA;
static u16* unpackedPalsBufferB;
static bool doSwap = FALSE;

void setPalsBufferA (u16* ptr) {
    unpackedPalsBufferA = ptr;
}

void setPalsBufferB (u16* ptr) {
    unpackedPalsBufferB = ptr;
}

u16* getPalsBufferA () {
    return unpackedPalsBufferA;
}

u16* getPalsBufferB () {
    return unpackedPalsBufferB;
}

bool getDoSwapPalsBuffers () {
    return doSwap;
}

void setDoSwapPalsBuffers (bool value) {
    doSwap = value;
}

void swapBuffersForPalettes () {
	u16* tmpPtr = unpackedPalsBufferB;
	unpackedPalsBufferB = unpackedPalsBufferA;
	unpackedPalsBufferA = tmpPtr;
}