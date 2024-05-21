/**
 * Usage:
 *   node res_n_header_generator.js <frame Width in px> <frame Height in px> <strip Height in px> <frame rate>
 * Eg:
 *   node res_n_header_generator.js 264 168 8 15
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

const tilesetStatsId = "tilesetStats_1"

// You first enable the stats and disable the loader, so you end up with the stats file.
// Extract the tiles you want from the file and create a new one with the tiles you choose.
// Then you disable the stats and enable the loader.
// Finally check the new TILESET_STATS_COLLECTOR's output stats to accomodate new tiles max chunk and total values.
const enableTilesCacheStats = false;
const loadTilesCache = false;
const tilesCacheId = "tilesCache_movie1"; // this is also the name of the variable contaning the Tileset with the cached tiles (it keeps the case)
// 1792 is the max amount of tiles we allow with the plane size of 64x32 and custom config of BG_B (and the window) and BG_A starting at address 0xE000.
// If we have a cache of 144 elemens then 1792-144=1648 is our cache starting index. 
// You can set whatever other index, although you need to know where your game tiles will be placed to avoid overwriting the cache.
const tilesCacheFile_countLines = countTilesCacheLines("res/" + tilesCacheId + ".txt");
const cacheStartIndexInVRAM = 1792 - tilesCacheFile_countLines;

// split tileset in N chunks. Current valid values are [1, 2, 3]
const tilesetSplit = 3;
// split tilemap in N chunks. Current values are [1, 2, 3]. Always <= tilesetSplit
const tilemapSplit = 1;
// add compression field if you know some tilesets and tilemaps are not compressed (by rescomp rules) or if you plan to test different compression algorithms
const imageAddCompressionField = true;

var type_ImageNoPals = "ImageNoPals";
if (tilesetSplit == 2) {
    if (tilemapSplit == 1)
	    type_ImageNoPals = "ImageNoPalsSplit21";
    else
        type_ImageNoPals = "ImageNoPalsSplit22";
}
else if (tilesetSplit == 3) {
    if (tilemapSplit == 1)
        type_ImageNoPals = "ImageNoPalsSplit31";
    else if (tilemapSplit == 2)
        type_ImageNoPals = "ImageNoPalsSplit32";
    else
	    type_ImageNoPals = "ImageNoPalsSplit33";
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

// extends map width to 64 (0: not extended). Setting 0 demands you to update enqueueTilemapData() in videoPlayer.c
const mapExtendedWidth = 64;
var widthTilesExt = mapExtendedWidth;
if (mapExtendedWidth == 0) {
	widthTilesExt = widthTiles;
}

if (!fs.existsSync(GEN_INC_DIR)) {
	fs.mkdirSync(GEN_INC_DIR, { recursive: true });
}

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
#define MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES ${widthTilesExt}
#define MOVIE_FRAME_STRIPS ${stripsPerFrame}

#define MOVIE_FRAME_COLORS_PER_STRIP 32
// In case you were to split any calculation over the colors of strip by an odd divisor n
#define MOVIE_FRAME_COLORS_PER_STRIP_REMAINDER(n) (MOVIE_FRAME_COLORS_PER_STRIP % n)

#define MOVIE_TILES_CACHE_START_INDEX ${cacheStartIndexInVRAM}

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

const ${type_ImageNoPals}* data[${sortedFileNamesEveryFirstStrip.length}] = {
	${sortedFileNamesEveryFirstStrip.map(s => `&mv_${removeExtension(s)}`).join(',\n	')}
};

const ${type_Palette32AllStrips}* pals_data[${sortedFileNamesEveryFirstStrip.length}] = {
	${sortedFileNamesEveryFirstStrip.map(s => `&pal_${removeExtension(s)}`).join(',\n	')}
};

#endif // _MOVIE_DATA_H
`);

// --------- Generate .res file

// You can provide a "comma separated list" of the types you want to include in the header file
// Eg: "TileMapCustom, ImageNoPalsSplit31, Palette32AllStripsSplit3"
const headerAppenderAllCustom = `HEADER_APPENDER_ALL_CUSTOM  headerAllCustomTypes` + '\n\n';

// Eg: TILES_CACHE_LOADER  movieFrames_cache  TRUE  1648  movieFrames_cache.txt APLIB NONE
// Flag 'enable' possible values: FALSE, TRUE
const loadTilesCacheStr = `TILES_CACHE_LOADER  ${tilesCacheId}  ${loadTilesCache?"TRUE":"FALSE"}  `
		+ `${cacheStartIndexInVRAM}  ${tilesCacheId}.txt  APLIB  NONE` + '\n\n';

// Eg: TILES_CACHE_STATS_ENABLER  movieFrames_cache  TRUE  600
// Flag 'enable' possible values: FALSE, TRUE
const enableTilesCacheStatsStr = `TILES_CACHE_STATS_ENABLER  ${tilesCacheId}  ${enableTilesCacheStats?"TRUE":"FALSE"}  600` + '\n\n';

// Eg: IMAGE_STRIPS_NO_PALS  mv_frame_46_0_RGB  "rgb/frame_46_0_RGB.png"  22  tilesCache_movie1  3  1  ODD  64  FAST  NONE  NONE  TRUE  ALL
const imageResListStr = sortedFileNamesEveryFirstStrip
	.map(s => `IMAGE_STRIPS_NO_PALS  mv_${removeExtension(s)}  "${FRAMES_DIR}${s}"  ${stripsPerFrame}  ${tilesetStatsId}`
			+ `  ${tilesCacheId}  ${tilesetSplit}  ${tilemapSplit}`
            + `  ${toggleMapTileBaseIndexFlag}  ${mapExtendedWidth}  FAST  NONE  NONE`
			+ `  ` + (imageAddCompressionField ? 'TRUE' : 'FALSE')
			+ `  ALL`)
	.join('\n') + '\n\n';

// Eg: PALETTE_32_COLORS_ALL_STRIPS  pal_frame_46_0_RGB  "rgb/frame_46_0_RGB.png"  22  3  PAL0PAL1  TRUE  FAST  NONE  FALSE
const paletteResListStr = sortedFileNamesEveryFirstStrip
	.map(s => `PALETTE_32_COLORS_ALL_STRIPS  pal_${removeExtension(s)}  "${FRAMES_DIR}${s}"  ${stripsPerFrame}  ${palette32Split}`
			+ `  PAL0PAL1  TRUE  FAST  NONE`
			+ `  ` + (paletteAddCompressionField ? 'TRUE' : 'FALSE'))
	.join('\n') + '\n\n';

// Eg: TILES_CACHE_STATS_PRINTER  movieFrames_cache  CONSOLE
// Flag printTo possible values: CONSOLE, FILE, NONE
// This resource runs only if the tile cache stats were set enabled, at TILES_CACHE_STATS_ENABLER
const printTilesCacheStatsStr = `TILES_CACHE_STATS_PRINTER  ${tilesCacheId}  FILE` + '\n\n';

// This resource runs only if the tile cache stats were set enabled, at TILES_CACHE_STATS_ENABLER
const printTilesetStatsCollector = `TILESET_STATS_COLLECTOR  ${tilesetStatsId}` + '\n\n';

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