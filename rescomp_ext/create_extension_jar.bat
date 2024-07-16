:: Locate this script at %GDK_WIN%\tools\rescomp
:: Before you run this script make sure you have compiled your Java classes so bin folder is generated.
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
	./sgdk/rescomp/processor/HeaderAppenderAllCustomProcessor.class ^
	./sgdk/rescomp/processor/HeaderAppenderCompressionCustomProcessor.class ^
	./sgdk/rescomp/processor/HeaderAppenderCompressionCustomTrackerProcessor.class ^
	./sgdk/rescomp/processor/HeaderAppenderProcessor.class ^
	./sgdk/rescomp/processor/ImageStripsNoPalsProcessor.class ^
	./sgdk/rescomp/processor/Palette16AllStripsProcessor.class ^
	./sgdk/rescomp/processor/Palette16Processor.class ^
	./sgdk/rescomp/processor/Palette32AllStripsProcessor.class ^
	./sgdk/rescomp/processor/Palette32Processor.class ^
	./sgdk/rescomp/processor/SpriteMultiPalProcessor.class ^
	./sgdk/rescomp/processor/TilesCacheLoaderProcessor.class ^
	./sgdk/rescomp/processor/TilesCacheStatsEnablerProcessor.class ^
	./sgdk/rescomp/processor/TilesCacheStatsPrinterProcessor.class ^
	./sgdk/rescomp/processor/TilesetStatsCollectorProcessor.class ^
	./sgdk/rescomp/processor/ext.processor.properties ^
	./sgdk/rescomp/resource/BinCustom.class ^
	./sgdk/rescomp/resource/HeaderAppender.class ^
	./sgdk/rescomp/resource/HeaderAppenderAllCustomResource.class ^
	./sgdk/rescomp/resource/HeaderAppenderCompressionCustom.class ^
	./sgdk/rescomp/resource/HeaderAppenderCompressionCustomTracker.class ^
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
	./sgdk/rescomp/resource/SpriteMultiPal.class ^
	./sgdk/rescomp/resource/TilemapCustom.class ^
	./sgdk/rescomp/resource/TilemapOriginalCustom.class ^
	./sgdk/rescomp/resource/TilesCacheLoader.class ^
	./sgdk/rescomp/resource/TilesCacheStatsEnabler.class ^
	./sgdk/rescomp/resource/TilesCacheStatsPrinter.class ^
	./sgdk/rescomp/resource/TilesetOriginalCustom.class ^
	./sgdk/rescomp/resource/TilesetStatsCollectorPrinter.class ^
	./sgdk/rescomp/resource/ext.resource.properties ^
	./sgdk/rescomp/tool/CompressionCustomUsageTracker.class ^
	./sgdk/rescomp/tool/ExtProperties.class ^
	./sgdk/rescomp/tool/MdComp.class ^
	./sgdk/rescomp/tool/RLEWCompressor.class ^
	./sgdk/rescomp/tool/TilemapCustomTools.class ^
	./sgdk/rescomp/tool/TilesCacheManager.class ^
	./sgdk/rescomp/tool/TilesetStatsCollector.class ^
	./sgdk/rescomp/type/CompressionCustom.class ^
	./sgdk/rescomp/type/CustomDataTypes.class ^
	./sgdk/rescomp/type/PackedDataCustom.class ^
	./sgdk/rescomp/type/PalettesPositionEnum.class ^
	./sgdk/rescomp/type/TileCacheMatch.class ^
	./sgdk/rescomp/type/TilemapCreationData.class ^
	./sgdk/rescomp/type/ToggleMapTileBaseIndex.class
::	-C tempClassesDir/ .
RMDIR /S /Q tempClassesDir 2>NUL
MOVE rescomp_ext.jar ../rescomp_ext.jar
CD ..

:FINISHED
EXIT /B