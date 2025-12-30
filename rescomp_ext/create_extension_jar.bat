@ECHO OFF
setlocal EnableDelayedExpansion

:: Locate this script at %GDK_WIN%\tools\rescomp

SET JAVA_SOURCE=8
SET JAVA_TARGET=21

IF "%JAVA_HOME%"=="" (
    echo ERROR: JAVA_HOME environment variable is not set!
	PAUSE
	GOTO FINISHED
)

:: Check Java's installed directory
IF NOT EXIST "%JAVA_HOME%" (
    echo ERROR: JAVA_HOME path does not exist: %JAVA_HOME%
	PAUSE
	GOTO FINISHED
)

:: Detect Java major version
for /f "tokens=2 delims==" %%A in ('java -XshowSettings:properties -version 2^>^&1 ^| findstr "java.specification.version"') do (
    set JAVA_SPEC=%%A
)

:: Normalize version (8 vs 11+)
if "%JAVA_SPEC%"=="1.8" (
    set JAVA_MAJOR=8
) else (
    set JAVA_MAJOR=%JAVA_SPEC%
)

echo Java version found: %JAVA_MAJOR%

IF "%~dp0" == "%GDK_WIN%\tools\rescomp\" (
	GOTO COMPILE_DEPENDENCIES
) ELSE (
	ECHO ERROR: Please locate this script at %GDK_WIN%\tools\rescomp\
	PAUSE
	GOTO FINISHED
)

:COMPILE_DEPENDENCIES

set "javac_source_target_flags=-source %JAVA_SOURCE% -target %JAVA_TARGET%"
:: Java 9+ accepts -release flag
if %JAVA_MAJOR% GEQ 9 (
    set "javac_source_target_flags=--release %JAVA_TARGET%"
)

IF EXIST "../apj/src" (
	ECHO Compiling apj project...
    IF NOT EXIST "../apj/bin" MD "../apj/bin"
    dir /s /b "../apj/src\*.java" > apj_sources.txt
    javac %javac_source_target_flags% -d "../apj/bin" @apj_sources.txt
    del apj_sources.txt
)

IF EXIST "../commons/src" (
	ECHO Compiling commons project...
    IF NOT EXIST "../commons/bin" MD "../commons/bin"  
    dir /s /b "../commons/src\*.java" > commons_sources.txt
    javac %javac_source_target_flags% -d "../commons/bin" @commons_sources.txt
    del commons_sources.txt
)

IF EXIST "../lz4w/src" (
	ECHO Compiling lz4w project...
    IF NOT EXIST "../lz4w/bin" MD "../lz4w/bin"
    dir /s /b "../lz4w/src\*.java" > lz4w_sources.txt
    javac %javac_source_target_flags% -d "../lz4w/bin" @lz4w_sources.txt
    del lz4w_sources.txt
)

:COMPILE_RESCOMP

ECHO Compiling rescomp project...

IF NOT EXIST src\ (
    ECHO ERROR: src folder not found
    PAUSE
    GOTO FINISHED
)

:: Create list of all Java files
dir /s /b src\*.java > rescomp_sources.txt
javac %javac_source_target_flags% -d bin -cp "../apj/bin;../commons/bin;../lz4w/bin" @rescomp_sources.txt
del rescomp_sources.txt

IF EXIST bin\ (
  GOTO COMPILE_JAR
) ELSE (
  ECHO ERROR: bin folder not generated yet
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
	./sgdk/rescomp/processor/HeaderAppenderAllCustomProcessor.class ^
	./sgdk/rescomp/processor/HeaderAppenderCompressionCustomProcessor.class ^
	./sgdk/rescomp/processor/HeaderAppenderCompressionCustomTrackerProcessor.class ^
	./sgdk/rescomp/processor/HeaderAppenderProcessor.class ^
	./sgdk/rescomp/processor/ImageNoPalsProcessor.class ^
	./sgdk/rescomp/processor/ImageStripsCommonTilesRangeProcessor.class ^
	./sgdk/rescomp/processor/ImageStripsNoPalsProcessor.class ^
	./sgdk/rescomp/processor/Palette16AllStripsProcessor.class ^
	./sgdk/rescomp/processor/Palette16Processor.class ^
	./sgdk/rescomp/processor/Palette32AllStripsProcessor.class ^
	./sgdk/rescomp/processor/Palette32Processor.class ^
	./sgdk/rescomp/processor/Palette64Processor.class ^
	./sgdk/rescomp/processor/SpriteMultiPalNoPalProcessor.class ^
	./sgdk/rescomp/processor/SpriteMultiPalProcessor.class ^
	./sgdk/rescomp/processor/SpriteNoPalProcessor.class ^
	./sgdk/rescomp/processor/TilesCacheLoaderProcessor.class ^
	./sgdk/rescomp/processor/TilesCacheStatsEnablerProcessor.class ^
	./sgdk/rescomp/processor/TilesCacheStatsPrinterProcessor.class ^
	./sgdk/rescomp/processor/TilesetStatsCollectorProcessor.class ^
	./sgdk/rescomp/processor/ext.processor.properties ^
	./sgdk/rescomp/resource/BinCustom$1.class ^
	./sgdk/rescomp/resource/BinCustom.class ^
	./sgdk/rescomp/resource/HeaderAppender.class ^
	./sgdk/rescomp/resource/HeaderAppenderAllCustomResource.class ^
	./sgdk/rescomp/resource/HeaderAppenderCompressionCustom.class ^
	./sgdk/rescomp/resource/HeaderAppenderCompressionCustomTracker.class ^
	./sgdk/rescomp/resource/ImageNoPals.class ^
	./sgdk/rescomp/resource/ImageStripsCommonTilesRange.class ^
	./sgdk/rescomp/resource/ImageStripsNoPals.class ^
	./sgdk/rescomp/resource/ImageStripsNoPalsSplit2.class ^
	./sgdk/rescomp/resource/ImageStripsNoPalsSplit3.class ^
	./sgdk/rescomp/resource/Palette16.class ^
	./sgdk/rescomp/resource/Palette16AllStrips.class ^
	./sgdk/rescomp/resource/Palette16AllStripsSplit2.class ^
	./sgdk/rescomp/resource/Palette16AllStripsSplit3.class ^
	./sgdk/rescomp/resource/Palette32.class ^
	./sgdk/rescomp/resource/Palette32AllStrips.class ^
	./sgdk/rescomp/resource/Palette32AllStripsSplit2.class ^
	./sgdk/rescomp/resource/Palette32AllStripsSplit3.class ^
	./sgdk/rescomp/resource/Palette64.class ^
	./sgdk/rescomp/resource/SpriteMultiPal.class ^
	./sgdk/rescomp/resource/SpriteMultiPalNoPal.class ^
	./sgdk/rescomp/resource/SpriteNoPal.class ^
	./sgdk/rescomp/resource/TilemapCustom.class ^
	./sgdk/rescomp/resource/TilemapOriginalCustom.class ^
	./sgdk/rescomp/resource/TilesCacheLoader.class ^
	./sgdk/rescomp/resource/TilesCacheStatsEnabler.class ^
	./sgdk/rescomp/resource/TilesCacheStatsPrinter.class ^
	./sgdk/rescomp/resource/TilesetForSpriteMultiPal.class ^
	./sgdk/rescomp/resource/TilesetOriginalCustom.class ^
	./sgdk/rescomp/resource/TilesetStatsCollectorPrinter.class ^
	./sgdk/rescomp/resource/ext.resource.properties ^
	./sgdk/rescomp/resource/internal/SpriteAnimationMultiPal.class ^
	./sgdk/rescomp/resource/internal/SpriteFrameMultiPal$1.class ^
	./sgdk/rescomp/resource/internal/SpriteFrameMultiPal.class ^
	./sgdk/rescomp/resource/internal/VDPSpriteMultiPal.class ^
	./sgdk/rescomp/tool/CommonTilesRangeManager.class ^
	./sgdk/rescomp/tool/CommonTilesRangeOptimizerV1.class ^
	./sgdk/rescomp/tool/CommonTilesRangeOptimizerV2.class ^
	./sgdk/rescomp/tool/CompressionCustomUsageTracker.class ^
	./sgdk/rescomp/tool/ExtProperties.class ^
	./sgdk/rescomp/tool/ImageUtilFast.class ^
	./sgdk/rescomp/tool/MdComp.class ^
	./sgdk/rescomp/tool/RLEWCompressor$WordInfo.class ^
	./sgdk/rescomp/tool/RLEWCompressor.class ^
	./sgdk/rescomp/tool/SpriteBoundariesPalettes.class ^
	./sgdk/rescomp/tool/TilemapCustomTools.class ^
	./sgdk/rescomp/tool/TilesCacheManager.class ^
	./sgdk/rescomp/tool/TilesetSizeSplitCalculator.class ^
	./sgdk/rescomp/tool/TilesetStatsCollector.class ^
	./sgdk/rescomp/type/CompressionCustom.class ^
	./sgdk/rescomp/type/CommonTilesRange.class ^
	./sgdk/rescomp/type/CommonTilesRangeResData.class ^
	./sgdk/rescomp/type/CustomDataTypes.class ^
	./sgdk/rescomp/type/PackedDataCustom.class ^
	./sgdk/rescomp/type/PalettesPositionEnum.class ^
	./sgdk/rescomp/type/TileCacheMatch.class ^
	./sgdk/rescomp/type/TilemapCreationData.class ^
	./sgdk/rescomp/type/TilesetSplitStrategyEnum.class ^
	./sgdk/rescomp/type/ToggleMapTileBaseIndex.class
::	-C tempClassesDir/ .
RMDIR /S /Q tempClassesDir 2>NUL
MOVE rescomp_ext.jar ../rescomp_ext.jar
CD ..

:FINISHED
EXIT /B