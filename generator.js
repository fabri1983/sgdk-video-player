/**
 * Usage:
 *   node generator.js <frame Width in px> <frame Height in px> <strip Height in px> <frame rate>
 * Eg:
 *   node generator.js 264 168 8 15
*/

const fs = require('fs');

var args = process.argv.slice(2);
if (args.length != 3) {
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

const sortedFileNamesEveryFirstStrip = sortedFileNames
	.filter((_, index) => index % stripsPerFrame === 0);

// split tileset in N chunks. Current valid values are 1 or 2
const tilesetSplit = 2;

// This activates the use of a map base tile index which sets the initial tile index for the tilemap of the resource.
// As the video player uses a buffer to allocate next frame's tilemaps (among tilesets and palettes) whilst current frame 
// was sent to VRAM previously and is being display, we need a way to toggle between the initial tile index: user base tile index, 
// and the next tile index: 716 (or max frame tileset size) + user base tile index.
// So we can take the first frame number from its name/id, knowing that they are declared in ascendant order, and test its parity: even or odd.
// 0 if first frame num is even, 1 if odd.
const matchFirstFrame = FILE_REGEX_2.exec(sortedFileNamesEveryFirstStrip[0]);
const toggleMapTileBaseIndexFlag = parseInt(matchFirstFrame[1]) % 2;

// extends map width to 64 tiles
const extendMapWidthTo64 = true;
var widthTilesExt = 64;
if (extendMapWidthTo64 == false) {
	widthTilesExt = widthTiles;
}
const extendMapWidthTo64_str = extendMapWidthTo64 ? "TRUE" : "FALSE";

if (!fs.existsSync(GEN_INC_DIR)) {
	fs.mkdirSync(GEN_INC_DIR, { recursive: true });
}

// --------- Generate movie_data_consts.h file
fs.writeFileSync(`${GEN_INC_DIR}/movie_data_consts.h`, 
`#ifndef _MOVIE_DATA_CONSTS_H
#define _MOVIE_DATA_CONSTS_H

#define MOVIE_FRAME_RATE (${frameRate}-1) // Minus 1 so it delays enough to be in sync with audio. IT'S A TEMPORARY HACK BUT WORKS FLAWLESSLY!
#define MOVIE_FRAME_COUNT ${sortedFileNamesEveryFirstStrip.length}
#define MOVIE_FRAME_WIDTH_IN_TILES ${widthTiles}
#define MOVIE_FRAME_HEIGHT_IN_TILES ${heightTiles}
#define MOVIE_FRAME_EXTENDED_WIDTH_IN_TILES ${widthTilesExt}
#define MOVIE_FRAME_STRIPS ${stripsPerFrame}

#define MOVIE_FRAME_COLORS_PER_STRIP 32
// in case you were to split any calculation over the colors of strip by an odd divisor n
#define MOVIE_FRAME_COLORS_PER_STRIP_REMINDER(n) (MOVIE_FRAME_COLORS_PER_STRIP - n*(MOVIE_FRAME_COLORS_PER_STRIP/n))

#endif // _MOVIE_DATA_CONSTS_H
`);

// --------- Generate movie_data.h file
fs.writeFileSync(`${GEN_INC_DIR}/movie_data.h`, 
`#ifndef _MOVIE_DATA_H
#define _MOVIE_DATA_H

#include <types.h>
#include <vdp_bg.h>
#include "movie_frames.h"
#include "movie_sound.h"
#include "generated/movie_data_consts.h"

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
    TileMap *tilemap1;
    TileMap *tilemap2;
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
	.map(s => `IMAGE_STRIPS_NO_PALS\t\tmv_${removeExtension(s)}\t\t"${FRAMES_DIR}${s}"\t\t${stripsPerFrame}\t\t${tilesetSplit}\t\t${toggleMapTileBaseIndexFlag}\t\t${extendMapWidthTo64_str}\t\tFAST\t\tALL`)
	.join('\n') + '\n\n';

// Eg: PALETTE_32_COLORS_ALL_STRIPS  pal_frame_46_0_RGB  "rgb/frame_46_0_RGB.png"  21  PAL0PAL1  TRUE  FAST
const paletteResListStr = sortedFileNamesEveryFirstStrip
	.map(s => `PALETTE_32_COLORS_ALL_STRIPS\t\tpal_${removeExtension(s)}\t\t"${FRAMES_DIR}${s}"\t\t${stripsPerFrame}\t\tPAL0PAL1\t\tTRUE\t\tFAST`)
	.join('\n') + '\n\n';

// Create .res file
fs.writeFileSync(`${RES_DIR}/movie_frames.res`, headerappender + imageResListStr + paletteResListStr);
