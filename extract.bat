:: Example:
::   extract.bat video.mp4 tmpmv 272 176 8 15 256
::   (color reduction parameter is optional)
@ECHO OFF

SET "TARGET_FOLDER=%2"
RMDIR /S /Q %TARGET_FOLDER% 2>NUL
MD %TARGET_FOLDER%
RMDIR /S /Q res\sound 2>NUL
MD res\sound

SET /A frameW=%3
SET /A frameH=%4
SET /A rowsPerStrip=%5
SET /A fps=%6
SET "COLORS=%7"

ffmpeg -i %1 -r %fps% -qmin 1 -qmax 2 -qscale:v 1 -s %frameW%x%frameH% %TARGET_FOLDER%/frame_%%d.png -ar 22050 -ac 1 -acodec pcm_u8 res/sound/sound.wav
:: The -vsync 0 parameter avoids needing to specify -r, and means all frames in the input file are processed

IF "%COLORS%" == "" (
	ECHO No color reduction.
	GOTO _STRIPS
)

:_COLOR_REDUCTION
ECHO Running mogrify for color reduction: %COLORS% ...
mogrify -path %TARGET_FOLDER% -colors %COLORS% -quality 100 -format PNG8 %TARGET_FOLDER%\frame_*.png

:: rename PNG8 files into png, removing first the old ones
RMDIR /S /Q %TARGET_FOLDER%\PNG8s 2>NUL
MD %TARGET_FOLDER%\PNG8s
MOVE /y %TARGET_FOLDER%\*.PNG8 %TARGET_FOLDER%\PNG8s\ >NUL
DEL /Q  %TARGET_FOLDER%\*.png 2>NUL
MOVE /y %TARGET_FOLDER%\PNG8s\*.PNG8 %TARGET_FOLDER%\ >NUL
REN %TARGET_FOLDER%\*.PNG8 *.png
RMDIR /S /Q %TARGET_FOLDER%\PNG8s 2>NUL

ECHO Done Color Reduction.

:_STRIPS
SET /A "NUM_STRIPS=%frameH%/%rowsPerStrip%"
ECHO Creating %NUM_STRIPS% strips out of every frame ...

SET STRIPS_FOLDER=%TARGET_FOLDER%\strips
RMDIR /S /Q %STRIPS_FOLDER% 2>NUL
MD %STRIPS_FOLDER%
FOR %%f IN (%TARGET_FOLDER%\frame_*.png) DO (
	magick %%f -set filename:base "%%[basename]" -unique -depth 8 -colors 256 -crop x%rowsPerStrip% -define png:format=png8 -define histogram:unique-colors=false "%STRIPS_FOLDER%\%%[filename:base]_%%d.png"
)
:: NOTE: the strips are already output in PNG8 format

ECHO Done Strips.

EXIT /B 0
