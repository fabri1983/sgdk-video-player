#include <types.h>
#include <sys.h>
#include <vdp.h>
#include "videoPlayer.h"
#include "teddyBearLogo.h"
#include "utils.h"

int main (bool hardReset)
{
	// on soft reset do like a hard reset
	if (!hardReset) {
		VDP_waitDMACompletion(); // avoids some glitches as per Genesis Manual's Addendum section
		SYS_hardReset();
	}

    displayTeddyBearLogo();
    waitMs_(200);

	playMovie();

    return 0;
}
