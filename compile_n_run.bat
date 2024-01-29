@ECHO OFF
SET EMU_PATH=C:\Games\blastem-win64-0.6.3-pre-cde4ea2b4929\blastem.exe
%GDK_WIN%\bin\make -f %GDK_WIN%\makefile.gen && %EMU_PATH% out\rom.bin
EXIT /B
