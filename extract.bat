:: Example:
::   extract.bat video.mp4 tmpmv 272 176 8 15 n
::   272: frame width (multiple of 8)
::   192: frame height (multiple of 8)
::   8: height per strip. Use 0 or frame height to skip strips creation
::   15: frame rate
::   n: color reduction (optional parameter). Max value 256 (for PNGs).
@ECHO OFF
setlocal enabledelayedexpansion

SET "TARGET_FOLDER=%2"
RMDIR /S /Q %TARGET_FOLDER% 2>NUL
MD %TARGET_FOLDER%
RMDIR /S /Q res\sound 2>NUL
MD res\sound

SET /A frameW=%3
SET /A frameH=%4
SET /A heightPerStrip=%5
SET /A fps=%6
SET "COLORS=%7"

:: Accomodate heightPerStrip accordingly
IF %heightPerStrip% EQU 0 (
	SET /A heightPerStrip=%frameH%
)

:: Extract audio
ffmpeg -y -i "%1" -r %fps% -ar 13300 -ac 1 -acodec pcm_u8 res/sound/sound.wav

:: Initial implementation.
REM ffmpeg -y -i "%1" -r %fps% -qmin 1 -qmax 2 -qscale:v 1 -s %frameW%x%frameH% "%TARGET_FOLDER%/frame_%%d.png"
:: The -vsync 0 parameter avoids needing to specify -r, but forces to process all frames in the input video

:: Another implementation with unique maximum palette of 256 colors and bayer dithering level 3.
:: Create a lossless compression intermediate video using HuffYUV video codec
REM SET "video_path_only=%~dp1"
REM SET "video_name_only=%~n1"
REM SET "video_HY=%video_path_only%%video_name_only%_HY.avi"
REM ffmpeg -y -i "%1" -r %fps% -s %frameW%x%frameH% -c:v huffyuv "%video_HY%"
:: Create max size palette for a png
REM SET "video_HY_pal=%video_path_only%%video_name_only%_HY_pal.png"
REM ffmpeg -y -i "%video_HY%" -vf "palettegen=256" "%video_HY_pal%"
:: Output the frames, and use dithering Bayer level N (1 to 4) to avoid color banding
REM ffmpeg -y -i "%video_HY%" -i "%video_HY_pal%" -r %fps% -filter_complex "[0:v][1:v] paletteuse=bayer:4" "%TARGET_FOLDER%/frame_%%d.png"
REM DEL /Q "%video_HY%" 2>NUL
REM DEL /Q "%video_HY_pal%" 2>NUL

:_COLOR_REDUCTION
:: This color reduciton is applied per generated frame.
:: Test if COLORS var is empty
IF "%COLORS%" == "" (
	ECHO No color reduction.
	GOTO _STRIPS
)

ECHO Running mogrify for color reduction: %COLORS% ...
mogrify -path %TARGET_FOLDER% -colors %COLORS% -quantize YIQ -quality 100 -format PNG8 %TARGET_FOLDER%\frame_*.png

:: Rename PNG8 files into png, removing first the old ones
RMDIR /S /Q %TARGET_FOLDER%\PNG8s 2>NUL
MD %TARGET_FOLDER%\PNG8s
MOVE /y %TARGET_FOLDER%\*.PNG8 %TARGET_FOLDER%\PNG8s\ >NUL
DEL /Q %TARGET_FOLDER%\*.png 2>NUL
MOVE /y %TARGET_FOLDER%\PNG8s\*.PNG8 %TARGET_FOLDER%\ >NUL
REN %TARGET_FOLDER%\*.PNG8 *.png
RMDIR /S /Q %TARGET_FOLDER%\PNG8s 2>NUL

ECHO Done Color Reduction.

:_STRIPS
SET /A "NUM_STRIPS=%frameH%/%heightPerStrip%"
ECHO Creating %NUM_STRIPS% strips out of every frame ...

SET STRIPS_FOLDER=%TARGET_FOLDER%\strips
RMDIR /S /Q %STRIPS_FOLDER% 2>NUL
MD %STRIPS_FOLDER%
FOR %%f IN (%TARGET_FOLDER%\frame_*.png) DO (
	magick %%f -set filename:base "%%[basename]" -unique -depth 8 -colors 256 -crop x%heightPerStrip% -define png:format=png8 -define histogram:unique-colors=false "%STRIPS_FOLDER%\%%[filename:base]_%%d.png"
)
:: NOTE: the strips are already generated in PNG8 format

ECHO Done Strips.

:_DONE
EXIT /B 0