/**
 * Usage:
 *   node generator.js <frame Width in px> <frame Height in px> <strip Height in px>
 * Eg:
 *   node generator.js 264 168 8
*/

const fs = require('fs');

var args = process.argv.slice(2);
if (args.length != 3) {
	throw new Error("Wrong parameters count. See usage in this script source code.");
}

const FRAMES_DIR = 'rgb/';
const GENSRC_DIR = 'inc/generated/';
const RES_DIR = 'res/';

const removeExtension = s => s.replace(/\.(png|PNG|bmp|BMP)$/, '');
const FILE_REGEX_2 = /^\w+_(\d+)_(\d+)(_RGB)?\.(png|PNG|bmp|BMP)$/; // frame_100_0_RGB.png OR frame_100_0.png

const widthTiles = parseInt(args[0])/8;
const heightTiles = parseInt(args[1])/8;
const stripsPerFrame = parseInt(args[1])/parseInt(args[2]);

const fileNames = fs.readdirSync(RES_DIR + FRAMES_DIR)
	.filter(s => FILE_REGEX_2.test(s));

const sortedFileNames = fileNames
	.map(name => {
		const match2 = FILE_REGEX_2.exec(name);
		return {idx: parseInt(match2[1])*10000 + parseInt(match2[2]), name};
	})
	.sort((a, b) => a.idx - b.idx)
	.map(o => o.name);

const sortedFileNamesEveryFirstStrip = sortedFileNames
	.filter((_, index) => index % stripsPerFrame === 0);

// split tileset in N chunks. Current valid values are 1 or 2
const tilesetSplit = 2;

// This activates the use of a map base tile index which sets the initial tile index for the tilemap of the resource.
// As the video player uses a buffer to allocate next frame's tilemaps (among tilesets and palettes) whilst current frame 
// was sent to VRAM previously and is being display, we need a way to toggle between the initial tile index (0) and the 
// next tile index (724 or the max tileset size of all frames).
// So we can take the first frame number from its name/id, knowing that they are declared in ascendant order, and test its parity: even or odd.
// 2046 if first frame num is even, 2047 if odd.
var mapTileBaseIndexFlag = 2046;
const matchFirstFrame = FILE_REGEX_2.exec(sortedFileNamesEveryFirstStrip[0]);
if ((parseInt(matchFirstFrame[1]) % 2) == 1) {
	mapTileBaseIndexFlag = 2047;
}

// extends map width to 64 tiles
const extendMapWidthTo64 = true;
var widthTilesExt = 64;
if (extendMapWidthTo64 == false) {
	widthTilesExt = widthTiles;
}
const extendMapWidthTo64_str = extendMapWidthTo64 ? "TRUE" : "FALSE";

if (!fs.existsSync(GENSRC_DIR)) {
	fs.mkdirSync(GENSRC_DIR, { recursive: true });
}

// --------- Generate .h file

fs.writeFileSync(`${GENSRC_DIR}/movie_data.h`, 
`#ifndef _MOVIE_DATA_H
#define _MOVIE_DATA_H

#include <types.h>
#include <vdp_bg.h>
#include "movie_frames.h"
#include "movie_sound.h"

#define MOVIE_FRAME_COUNT ${sortedFileNamesEveryFirstStrip.length}
#define MOVIE_FRAME_WIDTH_IN_TILES ${widthTiles}
#define MOVIE_FRAME_HEIGHT_IN_TILES ${heightTiles}
#define MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES ${widthTilesExt}
#define MOVIE_FRAME_STRIPS ${stripsPerFrame}

const ImageNoPalsTilesetSplit2* data[${sortedFileNamesEveryFirstStrip.length}] = {
	${sortedFileNamesEveryFirstStrip.map(s => `&mv_${removeExtension(s)}`).join(',\n	')}
};

const Palette32AllStrips* pals_data[${sortedFileNamesEveryFirstStrip.length}] = {
	${sortedFileNamesEveryFirstStrip.map(s => `&pal_${removeExtension(s)}`).join(',\n	')}
};

#endif // _MOVIE_DATA_H
`);

// --------- Generate .res file

// Next struct type definitions will be added at the top of generated movie_frames.h
const headerContent = `
typedef struct
{
    TileSet *tileset;
    TileMap *tilemap;
} ImageNoPals;

typedef struct
{
    TileSet *tileset1;
    TileSet *tileset2;
    TileMap *tilemap;
} ImageNoPalsTilesetSplit2;

typedef struct
{
    u16 compression;
    u16* data;
} Palette32AllStrips;
`.replace(/ {4}/g, '\\t').replace(/\n/g, '\\n'); // this convert a multiline string into a single line string

const headerappender = `HEADER_APPENDER\t\headerCustomTypes\t\t\"${headerContent}\"\n\n`;

// Eg: IMAGE_STRIPS_NO_PALS  mv_frame_46_0_RGB  "rgb/frame_46_0_RGB.png"  21  2  FAST  ALL  2047
const imageResListStr = sortedFileNamesEveryFirstStrip
	.map(s => `IMAGE_STRIPS_NO_PALS\t\tmv_${removeExtension(s)}\t\t"${FRAMES_DIR}${s}"\t\t${stripsPerFrame}\t\t${tilesetSplit}\t\t${extendMapWidthTo64_str}\t\tFAST\t\tALL\t\t${mapTileBaseIndexFlag}`)
	.join('\n') + '\n\n';

// Eg: PALETTE_32_COLORS_ALL_STRIPS  pal_frame_46_0_RGB  "rgb/frame_46_0_RGB.png"  21  PAL0PAL1  TRUE  FAST
const paletteResListStr = sortedFileNamesEveryFirstStrip
	.map(s => `PALETTE_32_COLORS_ALL_STRIPS\t\tpal_${removeExtension(s)}\t\t"${FRAMES_DIR}${s}"\t\t${stripsPerFrame}\t\tPAL0PAL1\t\tTRUE\t\tFAST`)
	.join('\n') + '\n\n';

// Create .res file
fs.writeFileSync(`${RES_DIR}/movie_frames.res`, headerappender + imageResListStr + paletteResListStr);
