#include <types.h>
#include <sys.h>
#include "videoPlayer.h"

int main (bool hard)
{
	if (!hard) SYS_hardReset();

	playMovie();

    return 0;
}
