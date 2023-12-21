#ifndef _MOVIE_PLAYER_H
#define _MOVIE_PLAYER_H

#define STRINGIFY(x) #x
#define STOPWATCH_START(n) \
			u16 lineStart_##n = GET_VCOUNTER;
#define STOPWATCH_STOP(n) \
			u16 lineEnd_##n = GET_VCOUNTER;\
			u16 frameTime_##n;\
			if (lineEnd_##n < lineStart_##n) {\
				frameTime_##n = 261 - lineStart_##n;\
				frameTime_##n += lineEnd_##n;\
			} else {\
				frameTime_##n = lineEnd_##n - lineStart_##n;\
			}\
			{\
				char str[] = "frameTime_"STRINGIFY(n)"     ";\
				*(str + 14) = '0' + (frameTime_##n / 100);\
				*(str + 15) = '0' + ((frameTime_##n / 10) % 10);\
				*(str + 16) = '0' + (frameTime_##n % 10);\
				*(str + 17) = '\0';\
				KLog(str);\
			}\

void playMovie ();

#endif