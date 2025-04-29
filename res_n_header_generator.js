/**
 * Usage:
 *   node res_n_header_generator.js <frame Width in px> <frame Height in px> <strip Height in px> <frame rate>
 * Eg:
 *   node res_n_header_generator.js 272 192 8 15
*/

const fs = require('fs');

var args = process.argv.slice(2);
if (args.length != 4) {
	throw new Error("Wrong parameters count. See usage in this script source code.");
}

const FRAMES_DIR = 'rgb/';
const GEN_INC_DIR = 'inc/generated/';
const RES_DIR = 'res/';

const removeExtension = s => s.replace(/\.(png|PNG|bmp|BMP)$/, '');
const FILE_REGEX_2 = /^\w+_(\d+)_(\d+)(_RGB)?\.(png|PNG|bmp|BMP)$/; // frame_100_0_RGB.png OR frame_100_0.png

const widthTiles = parseInt(args[0])/8;
const heightTiles = parseInt(args[1])/8;
const stripsPerFrame = parseInt(args[1])/parseInt(args[2]);
const frameRate = parseInt(args[3]);

const fileNames = fs.readdirSync(RES_DIR + FRAMES_DIR)
	.filter(s => FILE_REGEX_2.test(s));

const sortedFileNames = fileNames
	.map(name => {
		const match2 = FILE_REGEX_2.exec(name);
		return {idx: parseInt(match2[1])*100000 + parseInt(match2[2]), name};
	})
	.sort((a, b) => a.idx - b.idx)
	.map(o => o.name);

// Keeps only every stripsPerFrame elements
const sortedFileNamesEveryFirstStrip = sortedFileNames
	.filter((_, index) => index % stripsPerFrame === 0);

const resPropertiesMap = loadResourceProperties("res/ext.resource.properties");

const tilesetStatsId = "tilesetStats_1"

// You first enable the stats and disable the loader, so you end up with the stats file.
// Extract the tiles you want from the file and create a new one with the tiles you choose.
// Then you disable the stats and enable the loader.
// Finally check the new TILESET_STATS_COLLECTOR's output stats to accomodate new tiles max chunk and total values.
const enableTilesCacheStats = false;
const loadTilesCache = true;
const tilesCacheId = "tilesCache_movie1"; // this is also the name of the variable contaning the Tileset with the cached tiles (it keeps the case)
// 1792 (which is BG_A address 0xE000/32) is the max amount of tiles we allow with the plane size of 64x32 and custom config of BG_B 
// (Window plane too) and BG_A. Additionally, by moving the SAT into the HScroll address we have 127 free tiles between 0xF020 and 0xFFFF.
// So if we have a cache of 216+127=343 elements, then 1792-163=1576 is our cache starting index for 216 tiles, and remaining 127 tiles 
// locate starting at address 0xF020.
const tilesCacheFile_countLines = countTilesCacheLines("res/" + tilesCacheId + ".txt");
const cacheFixedTilesNum = 127;
const cacheVarTilesNum = (tilesCacheFile_countLines - cacheFixedTilesNum);
const cacheStartIndexInVRAM_var = 1792 - cacheVarTilesNum;
const cacheRangesInVRAM_fixed = [ {start: 0xF020/32, length: cacheFixedTilesNum} ];
const cacheRangesInVRAM_fixed_str = cacheRangesInVRAM_fixed
                                        .map(range => `${range.start}-${range.length}`)
                                        .join(','); // Comma separated list of ranges

// Ensure no override happens between frame tiles and cache tiles
checkCacheStartIndexAfterTilesets(loadTilesCache, cacheStartIndexInVRAM_var, resPropertiesMap);

// split tileset in N chunks. Current valid values are [1, 2, 3]
const tilesetSplit = 3;
// split tilemap in N chunks. Current values are [1, 2, 3]. Always <= tilesetSplit
const tilemapSplit = 1;
// add compression field if you know some tilesets and tilemaps are not compressed (by rescomp rules) or if you plan to test different compression algorithms
const imageAddCompressionField = true;

// Next data got experimentally from rescomp output (using resource TILESET_STATS_COLLECTOR). If odd then use next even number.
var videoFrameTilesetChunkSize = resPropertiesMap.get('MAX_TILESET_NUM_FOR_MAP_BASE_TILE_INDEX');
var videoFrameTilesetTotalSize = resPropertiesMap.get('MAX_TILESET_NUM_FOR_MAP_BASE_TILE_INDEX');

var type_ImageNoPals = "ImageNoPals";
if (tilesetSplit == 2) {
    if (tilemapSplit == 1)
	    type_ImageNoPals = "ImageNoPalsSplit21";
    else
        type_ImageNoPals = "ImageNoPalsSplit22";
	videoFrameTilesetChunkSize = resPropertiesMap.get('MAX_TILESET_CHUNK_SIZE_FOR_SPLIT_IN_2');
	videoFrameTilesetTotalSize = resPropertiesMap.get('MAX_TILESET_TOTAL_SIZE_FOR_SPLIT_IN_2');
}
else if (tilesetSplit == 3) {
    if (tilemapSplit == 1)
        type_ImageNoPals = "ImageNoPalsSplit31";
    else if (tilemapSplit == 2)
        type_ImageNoPals = "ImageNoPalsSplit32";
    else
	    type_ImageNoPals = "ImageNoPalsSplit33";
	videoFrameTilesetChunkSize = resPropertiesMap.get('MAX_TILESET_CHUNK_SIZE_FOR_SPLIT_IN_3');
	videoFrameTilesetTotalSize = resPropertiesMap.get('MAX_TILESET_TOTAL_SIZE_FOR_SPLIT_IN_3');
}

if (imageAddCompressionField)
	type_ImageNoPals += "CompField";

// split palettes in N chunks. Current valid values are [1, 2, 3]
const palette32Split = 1;
// add compression field if you know some palettes are not compressed (by rescomp rules) or if you plan to test different compression algorithms
const paletteAddCompressionField = false;

var type_Palette32AllStrips = "Palette32AllStrips";
if (palette32Split == 2)
	type_Palette32AllStrips = "Palette32AllStripsSplit2";
else if (palette32Split == 3)
	type_Palette32AllStrips = "Palette32AllStripsSplit3";

if (paletteAddCompressionField)
	type_Palette32AllStrips += "CompField";

// This activates the use of a map base tile index which sets the initial tile index for the tilemap of the resource.
// As the video player uses a buffer to allocate next frame's tilemaps (among tilesets and palettes) whilst current frame 
// was sent to VRAM previously and is being display, we need a way to toggle between the initial tile index: user base tile index, 
// and the next tile index: 716 (or max frame tileset size) + user base tile index.
// So we can take the first frame number from its name/id, knowing that they are declared in ascendant order, and test its parity: even or odd.
// Values: NONE disabled, EVEN if first frame num is even, ODD if odd.
const matchFirstFrame = FILE_REGEX_2.exec(sortedFileNamesEveryFirstStrip[0]);
const toggleMapTileBaseIndexFlag = parseInt(matchFirstFrame[1]) % 2 == 0 ? "EVEN" : "ODD"; // use NONE to disable it

// Resource plugin: extends map width to N tiles. Values: 0 (disabled), 32, 64, 128.
// videoPlayer.c: buffer allocation and copy algorithm.
// NOTE: changing any of the values here demands you to review/update enqueueTilemapData() in videoPlayer.c.
const mapExtendedWidth_forResource = 64; // If using RLEW compression this width is internally changed to the real width of the tilemap
const widthTilesExt_forVideoPlayer = 64;

if (!fs.existsSync(GEN_INC_DIR)) {
	fs.mkdirSync(GEN_INC_DIR, { recursive: true });
}

// Next data got experimentally from rescomp output (using resource TILESET_STATS_COLLECTOR). If odd then use next even number.
const tileUserIndexCustom = resPropertiesMap.get('STARTING_TILESET_ON_SGDK');
var tilesetMaxChunk1Size = resPropertiesMap.get('MAX_TILESET_NUM_FOR_MAP_BASE_TILE_INDEX');
var tilesetMaxChunk2Size = 0;
var tilesetMaxChunk3Size = 0;
if (tilesetSplit == 2) {
	tilesetMaxChunk1Size = resPropertiesMap.get('MAX_TILESET_CHUNK_1_SIZE_FOR_SPLIT_IN_2');
	tilesetMaxChunk2Size = resPropertiesMap.get('MAX_TILESET_CHUNK_2_SIZE_FOR_SPLIT_IN_2');
}
else if (tilesetSplit == 3) {
	tilesetMaxChunk1Size = resPropertiesMap.get('MAX_TILESET_CHUNK_1_SIZE_FOR_SPLIT_IN_3');
	tilesetMaxChunk2Size = resPropertiesMap.get('MAX_TILESET_CHUNK_2_SIZE_FOR_SPLIT_IN_3');
	tilesetMaxChunk3Size = resPropertiesMap.get('MAX_TILESET_CHUNK_3_SIZE_FOR_SPLIT_IN_3');
}

// --------- Generate movie_cache_consts.h file
fs.writeFileSync(`${GEN_INC_DIR}/movie_cache_consts.h`, 
`#ifndef _MOVIE_CACHE_CONSTS_H
#define _MOVIE_CACHE_CONSTS_H

#define MOVIE_TILES_CACHE_START_INDEX_VAR ${cacheStartIndexInVRAM_var}
#define MOVIE_TILES_CACHE_TILES_NUM_VAR ${cacheVarTilesNum}

typedef struct {
    unsigned short start;
    unsigned short length;
} RangeFixedVRAM;

#define MOVIE_TILES_CACHE_RANGES_NUM ${cacheRangesInVRAM_fixed.length}

const RangeFixedVRAM cacheRangesInVRAM_fixed [MOVIE_TILES_CACHE_RANGES_NUM] = {
    ${cacheRangesInVRAM_fixed.map(range => `{ ${range.start}, ${range.length} }`).join(',\n    ')}
};

#endif // _MOVIE_CACHE_CONSTS_H
`);

// --------- Generate movie_data_consts.h file
fs.writeFileSync(`${GEN_INC_DIR}/movie_data_consts.h`, 
`#ifndef _MOVIE_DATA_CONSTS_H
#define _MOVIE_DATA_CONSTS_H

/* -------------------------------- */
/*         AUTO GENERATED           */
/*     DO NOT MODIFY THIS FILE      */
/*  See res_n_header_generator.js   */
/* -------------------------------- */

#define MOVIE_FRAME_RATE ${frameRate}
#define MOVIE_FRAME_COUNT ${sortedFileNamesEveryFirstStrip.length}
#define MOVIE_FRAME_WIDTH_IN_TILES ${widthTiles}
#define MOVIE_FRAME_HEIGHT_IN_TILES ${heightTiles}
#define MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES ${widthTilesExt_forVideoPlayer}
#define MOVIE_FRAME_STRIPS ${stripsPerFrame}

#define MOVIE_FRAME_COLORS_PER_STRIP 32
// In case you were to split any calculation over the colors of strip by an odd divisor n
#define MOVIE_FRAME_COLORS_PER_STRIP_REMAINDER(n) (MOVIE_FRAME_COLORS_PER_STRIP % n)

/// SGDK reserves 16 tiles starting at address 0. That's the purpose of using SGDK's TILE_USER_INDEX so you don't its tiles.
/// Tile address 0 holds a black tile and it shouldn't be overriden since is what an empty tilemap in VRAM points to. Also other internal effects use it.
/// Remaining 15 tiles are OK to override for re use. So we can start using tiles at index 1.
#define TILE_USER_INDEX_CUSTOM ${tileUserIndexCustom}

#define VIDEO_FRAME_TILESET_CHUNK_SIZE ${videoFrameTilesetChunkSize}
#define VIDEO_FRAME_TILESET_TOTAL_SIZE ${videoFrameTilesetTotalSize}
#define VIDEO_FRAME_TILESET_MAX_CHUNK_1_SIZE ${tilesetMaxChunk1Size}
#define VIDEO_FRAME_TILESET_MAX_CHUNK_2_SIZE ${tilesetMaxChunk2Size}
#define VIDEO_FRAME_TILESET_MAX_CHUNK_3_SIZE ${tilesetMaxChunk3Size}

#endif // _MOVIE_DATA_CONSTS_H
`);

// --------- Generate movie_data.h file
fs.writeFileSync(`${GEN_INC_DIR}/movie_data.h`, 
`#ifndef _MOVIE_DATA_H
#define _MOVIE_DATA_H

/* -------------------------------- */
/*         AUTO GENERATED           */
/*     DO NOT MODIFY THIS FILE      */
/*  See res_n_header_generator.js   */
/* -------------------------------- */

#include <types.h>
#include <vdp_bg.h>
#include "movie_frames.h"
#include "movie_sound.h"
#include "generated/movie_data_consts.h"

const ${type_ImageNoPals}* data[MOVIE_FRAME_COUNT] = {
	${sortedFileNamesEveryFirstStrip.map(s => `&mv_${removeExtension(s)}`).join(',\n	')}
};

const ${type_Palette32AllStrips}* pals_data[MOVIE_FRAME_COUNT] = {
	${sortedFileNamesEveryFirstStrip.map(s => `&pal_${removeExtension(s)}`).join(',\n	')}
};

#endif // _MOVIE_DATA_H
`);

// --------- Generate .res file

// HEADER_APPENDER_ALL_CUSTOM name ["comma sep list"]
// You can provide a "comma separated list" of the types you want to include in the header file
// Eg: "TileMapCustom, ImageNoPalsSplit31, Palette32AllStripsSplit3"
const headerAppenderAllCustom = `HEADER_APPENDER_ALL_CUSTOM  headerAllCustomTypes` + '\n\n';

// TILES_CACHE_STATS_ENABLER tilesCacheId enable minTilesetSize
// Eg: TILES_CACHE_STATS_ENABLER  movieFrames_cache  TRUE  500
// Flag 'enable' possible values: FALSE, TRUE
const enableTilesCacheStatsStr = `TILES_CACHE_STATS_ENABLER  ${tilesCacheId}  ${enableTilesCacheStats?"TRUE":"FALSE"}  500` + '\n\n';

// TILES_CACHE_LOADER tilesCacheId enable cacheStartIndexInVRAM_var cacheVarTilesNum cacheRangesInVRAM_fixed filename compression compressionCustom
// Eg: TILES_CACHE_LOADER  movieFrames_cache  TRUE  1576  216  1921-127  movieFrames_cache.txt  APLIB  NONE
// Flag 'enable' possible values: FALSE, TRUE
const loadTilesCacheStr = `TILES_CACHE_LOADER  ${tilesCacheId}  ${loadTilesCache?"TRUE":"FALSE"}`
		+ `  ${cacheStartIndexInVRAM_var}  ${cacheVarTilesNum}  ${cacheRangesInVRAM_fixed_str}  ${tilesCacheId}.txt  APLIB  NONE` + '\n\n';

// IMAGE_STRIPS_NO_PALS name "baseFile" strips tilesetStatsCollectorId [tilesCacheId splitTileset splitTilemap toggleMapTileBaseIndexFlag mapExtendedWidth compression compressionCustomTileSet compressionCustomTileMap addCompressionField map_opt map_base]
// Eg: IMAGE_STRIPS_NO_PALS  mv_frame_46_0_RGB  "rgb/frame_46_0_RGB.png"  22  tilesetStats1  tilesCache_movie1  3  1  ODD  64  FAST  NONE  NONE  TRUE  ALL
const imageResListStr = sortedFileNamesEveryFirstStrip
	.map(s => `IMAGE_STRIPS_NO_PALS  mv_${removeExtension(s)}  "${FRAMES_DIR}${s}"  ${stripsPerFrame}  ${tilesetStatsId}`
			+ `  ${tilesCacheId}  ${tilesetSplit}  ${tilemapSplit}`
            + `  ${toggleMapTileBaseIndexFlag}  ${mapExtendedWidth_forResource}  NONE  FAST  RLEW_A`
			+ `  ` + (imageAddCompressionField ? 'TRUE' : 'FALSE')
			+ `  ALL`)
	.join('\n') + '\n\n';

// PALETTE_32_COLORS_ALL_STRIPS name baseFile strips palsPosition palsPosition addCompressionField compression compressionCustom addCompressionField
// Eg: PALETTE_32_COLORS_ALL_STRIPS  pal_frame_46_0_RGB  "rgb/frame_46_0_RGB.png"  22  3  PAL0PAL1  TRUE  FAST  NONE  FALSE
const paletteResListStr = sortedFileNamesEveryFirstStrip
	.map(s => `PALETTE_32_COLORS_ALL_STRIPS  pal_${removeExtension(s)}  "${FRAMES_DIR}${s}"  ${stripsPerFrame}  ${palette32Split}`
			+ `  PAL0PAL1  TRUE  FAST  NONE`
			+ `  ` + (paletteAddCompressionField ? 'TRUE' : 'FALSE'))
	.join('\n') + '\n\n';

// This resource runs only if a tile stats id was set in previous rresources
const printTilesetStatsCollector = `TILESET_STATS_COLLECTOR  ${tilesetStatsId}` + '\n\n';

// Eg: TILES_CACHE_STATS_PRINTER  movieFrames_cache  CONSOLE
// Flag printTo possible values: CONSOLE, FILE, NONE
// This resource runs only if the tile cache stats were set enabled, at TILES_CACHE_STATS_ENABLER
const printTilesCacheStatsStr = `TILES_CACHE_STATS_PRINTER  ${tilesCacheId}  FILE` + '\n\n';

const customCompressorTracker = `HEADER_APPENDER_COMPRESSION_CUSTOM_TRACKER  compressionCustomTrackerHeader_movie1` + '\n\n';

// Create movie_frames.res file
fs.writeFileSync(`${RES_DIR}/movie_frames.res`, 
        headerAppenderAllCustom + 
        loadTilesCacheStr + 
        enableTilesCacheStatsStr + 
        imageResListStr + paletteResListStr + 
        printTilesCacheStatsStr + 
		printTilesetStatsCollector +
		customCompressorTracker);

function countTilesCacheLines (filePath) {
	try {
		const fileContent = fs.readFileSync(filePath, 'utf8');
		const lines = fileContent.split('\n');
		const count = lines.filter(line => line.trim() !== '').length;
		if (count > 0)
			return count - 1; // first line is the cache id
		return 0;
	} catch (error) {
		return 0;
		//throw error;
	}
}

function loadResourceProperties (filePath) {
	const table = new Map();
  
	try {
	  const fileContent = fs.readFileSync(filePath, 'utf-8');
	  const lines = fileContent.split('\n');
  
	  for (const line of lines) {
		if (line.trim() === '' || line.startsWith('#')) {
		  continue;
		}
  
		const [key, value] = line.split('=');
		table.set(key.trim(), parseInt(value.trim()));
	  }
	} catch (error) {
		throw new Error("Error loading file " + filePath);
	}

	return table;
}

function checkCacheStartIndexAfterTilesets (loadTilesCache, cacheStartIndexInVRAM, resPropertiesMap) {
	if (loadTilesCache != true)
		return;
	const maxTilesetNum = resPropertiesMap.get('MAX_TILESET_NUM_FOR_MAP_BASE_TILE_INDEX');
	const startingTilesetIndex = resPropertiesMap.get('STARTING_TILESET_ON_SGDK');
	const finalTilesetIndex = startingTilesetIndex + (2 * maxTilesetNum);
	if (cacheStartIndexInVRAM <= finalTilesetIndex)
		throw new Error("Starting Tiles Cache Index in VRAM overlaps the last movie tileset location. Try to reduce the number of cache tiles.");
}