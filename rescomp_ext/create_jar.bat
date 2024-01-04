:: Locate this script at %GDK_WIN%\tools\rescomp
:: Before run it, make sure you compile your Java classes so bin folder is generated.
@ECHO OFF

IF "%~dp0" == "%GDK_WIN%\tools\rescomp\" (
  GOTO COMPILE_JAR
) ELSE (
  ECHO Please locate this script at %GDK_WIN%\tools\rescomp\
  PAUSE
  GOTO FINISHED
)

IF EXIST bin\ (
  GOTO COMPILE_JAR
) ELSE (
  ECHO bin folder not generated yet
  PAUSE
  GOTO FINISHED
)

:COMPILE_JAR
CD bin
RMDIR /S /Q tempClassesDir 2>NUL
MD tempClassesDir
CD tempClassesDir
::jar xf ../../lib/byte-buddy-1.12.19.jar
::RMDIR /S /Q META-INF 2>NUL
::DEL /Q LICENSE 2>NUL
CD ..
jar cvf rescomp_ext.jar ^
	./sgdk/rescomp/processor/HeaderAppenderProcessor.class ^
	./sgdk/rescomp/processor/ImageStripsNoPalsProcessor.class ^
	./sgdk/rescomp/processor/Palette32AllStripsProcessor.class ^
	./sgdk/rescomp/processor/Palette32Processor.class ^
	./sgdk/rescomp/processor/ext.processor.properties ^
	./sgdk/rescomp/resource/HeaderAppender.class ^
	./sgdk/rescomp/resource/ImageStripsNoPals.class ^
	./sgdk/rescomp/resource/ImageStripsNoPalsTilesetSplit2.class ^
	./sgdk/rescomp/resource/ImageStripsNoPalsTilesetSplit3.class ^
	./sgdk/rescomp/resource/Palette32AllStrips.class ^
	./sgdk/rescomp/resource/Palette32.class ^
	./sgdk/rescomp/resource/TilemapCustom.class ^
	./sgdk/rescomp/resource/ext.resource.properties ^
	./sgdk/rescomp/tool/ExtProperties.class ^
	./sgdk/rescomp/type/PalettesPositionEnum.class
::	-C tempClassesDir/ .
RMDIR /S /Q tempClassesDir 2>NUL
MOVE rescomp_ext.jar ../rescomp_ext.jar
CD ..

:FINISHED
EXIT /B