:: Example:
::   convert_to_PNG8.bat tmpmv\*.png
@ECHO OFF

SET "IMAGES_PATTERN=%1"
SET "TARGET_FOLDER=%~p1"
IF "%TARGET_FOLDER%" == "" SET TARGET_FOLDER=.

ECHO Running mogrify for PNG8 convertion ...
mogrify -path %TARGET_FOLDER% -format PNG8 %IMAGES_PATTERN%

:: rename PNG8 files into png, removing first the old ones
RMDIR /S /Q %TARGET_FOLDER%\PNG8s 2>NUL
MD %TARGET_FOLDER%\PNG8s
MOVE /y %TARGET_FOLDER%\*.PNG8 %TARGET_FOLDER%\PNG8s\ >NUL
DEL /Q  %TARGET_FOLDER%\*.png 2>NUL
MOVE /y %TARGET_FOLDER%\PNG8s\*.PNG8 %TARGET_FOLDER%\ >NUL
REN %TARGET_FOLDER%\*.PNG8 *.png
RMDIR /S /Q %TARGET_FOLDER%\PNG8s 2>NUL

ECHO Done.