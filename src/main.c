#include <types.h>
#include <sys.h>
#include "videoPlayer.h"
#include "genesis.h"

int main (bool hardReset)
{
	// on soft reset do like a hard reset
	if (!hardReset) {
		VDP_waitDMACompletion(); // avoids some glitches as per Genesis Manual's Addendum section
		SYS_hardReset();
	}

	playMovie();

    return 0;
}
