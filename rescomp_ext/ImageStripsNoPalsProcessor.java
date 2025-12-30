package sgdk.rescomp.processor;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import sgdk.rescomp.Compiler;
import sgdk.rescomp.Processor;
import sgdk.rescomp.Resource;
import sgdk.rescomp.resource.ImageStripsNoPals;
import sgdk.rescomp.resource.ImageStripsNoPalsSplit2;
import sgdk.rescomp.resource.ImageStripsNoPalsSplit3;
import sgdk.rescomp.tool.CommonTilesRangeManager;
import sgdk.rescomp.tool.TilesCacheManager;
import sgdk.rescomp.tool.Util;
import sgdk.rescomp.type.Basics.Compression;
import sgdk.rescomp.type.Basics.TileOptimization;
import sgdk.rescomp.type.CompressionCustom;
import sgdk.rescomp.type.TilesetSplitStrategyEnum;
import sgdk.rescomp.type.ToggleMapTileBaseIndex;
import sgdk.tool.FileUtil;
import sgdk.tool.ImageUtil;
import sgdk.tool.ImageUtil.BasicImageInfo;
import sgdk.tool.StringUtil;

public class ImageStripsNoPalsProcessor implements Processor
{
	private static final String resId = "IMAGE_STRIPS_NO_PALS";

    @Override
    public String getId()
    {
        return resId;
    }

    @Override
    public Resource execute(String[] fields) throws Exception
    {
		if (fields.length < 4)
		{
			System.out.println("Wrong " + resId + " definition");
			System.out.println(resId + " name \"baseFile\" strips [tilesetStatsCollectorId tilesCacheId commonTilesId splitTileset splitTilesetStrategy splitTilemap toggleMapTileBaseIndexFlag mapExtendedWidth compression compressionCustomTileSet compressionCustomTileMap addCompressionField map_opt map_base]");
			System.out.println("  name               Image variable name. Eg: frame_12");
			System.out.println("  baseFile           Path of the first strip for input RGB image file with palettes (BMP or PNG image). Eg: \"res/rgb/frame_12_0.png\" or \"res/rgb/frame_12_0_RGB.png\"");
			System.out.println("  strips             How many strips is the final image composed of. Eg: 21. It means there are frame_12_0.png, frame_12_1.png, ... frame_12_20.png");
			System.out.println("  tilesetStatsCollectorId      Group under same id the stats collected from tilesets. Use " + TilesetStatsCollectorProcessor.resId + " with the same id to print the stats in the console.");
			System.out.println("                               Use NONE or NULL to avoid the stats collecting.");
			System.out.println("  tilesCacheId       Set an id (case insensitive) to identify and re use the same cache of tiles along other resources.");
			System.out.println("                       Use NONE or NULL to disable the use of the tile cache.");
			System.out.println("                       Use " + TilesCacheStatsEnablerProcessor.resId + " and " + TilesCacheStatsPrinterProcessor.resId + " with the same id to collect data and print them (file or console).");
			System.out.println("                       You need to collect the stats in order to create the cache file and use it with " + TilesCacheLoaderProcessor.resId + " in a future run to effectivelly let the tilemaps use the cache. Declare that processor before this one.");
			System.out.println("  commonTilesId      Set an id (case insensitive) to identify and re use common tiles between all the images following the same baseFile naming pattern.");
			System.out.println("                       Use NONE or NULL to disable the use of the tile cache.");
			System.out.println("                       You need to declare the processor " + ImageStripsCommonTilesRangeProcessor.resId + " prior to this one in order to populate the common tiles list.");
			System.out.println("  splitTileset       How many chunks is the tileset splitted into. Default 1 (no split), otherwise 2 or 3.");
			System.out.println("  splitTilesetStrategy   Choose split tileset strategy:");
			System.out.println("                       " + TilesetSplitStrategyEnum.SPLIT_NONE.getValue() + " only valid when splitTileset = 1.");
			System.out.println("                       " + TilesetSplitStrategyEnum.SPLIT_NORMAL.getValue() + " uses distribution of tiles according the height.");
			System.out.println("                       " + TilesetSplitStrategyEnum.SPLIT_EVENLY.getValue() + " distributes all tiles evenly in all chunks.");
			System.out.println("                       " + TilesetSplitStrategyEnum.SPLIT_MAX_CAPACITY_FIRST.getValue() + " sets as much tiles as possible in first chunks.");
			System.out.println("  splitTilemap       How many chunks is the tilemap splitted into. Always less or equal than splitTileset.");
			System.out.println("  toggleMapTileBaseIndexFlag   This is for a swap buffer mechanism:");
			System.out.println("                       " + ToggleMapTileBaseIndex.NONE.getValue() + " disabled.");
			System.out.println("                       " + ToggleMapTileBaseIndex.EVEN.getValue() + " if first frame num is even.");
			System.out.println("                       " + ToggleMapTileBaseIndex.ODD.getValue() + " if first frame is odd.");
			System.out.println("  mapExtendedWidth   Extends the map width to 32, 64, or 128 tiles (filled with 0s) so you can use VDP_setTileMapData() if your plane width is 32, 64, or 128 tiles.");
			System.out.println("                       Accepted values: 0 (disabled), 32, 64 or 128.");
			System.out.println("  compression        Compression type for Tileset and Tilemaps. Accepted values:");
			System.out.println("                       -1 / BEST / AUTO = use best compression");
			System.out.println("                        0 / NONE        = no compression (default)");
			System.out.println("                        1 / APLIB       = aplib library (good compression ratio but slow)");
			System.out.println("                        2 / FAST / LZ4W = custom lz4 compression (average compression ratio but fast)");
			System.out.println("  compressionCustomTileSet  Overrides the compression parameter. Only for TileSet. Accepted values:");
			for (CompressionCustom cc : CompressionCustom.values())
				System.out.println("                       " + cc.getValue());
			System.out.println("  compressionCustomTileMap  Overrides the compression parameter. Only for TileMap. Accepted values:");
			for (CompressionCustom cc : CompressionCustom.values())
				System.out.println("                       " + cc.getValue());
			System.out.println("  addCompressionField  Include or exclude the compression field. TRUE or FALSE (default)");
			System.out.println("  map_opt            Define the map optimisation level, accepted values:");
			System.out.println("                        0 / NONE        = no optimisation (each tile is unique)");
			System.out.println("                        1 / ALL         = find duplicate and flipped tile (default)");
			System.out.println("                        2 / DUPLICATE   = find duplicate tile only");
			System.out.println("  map_base           Define the base tilemap value, useful to set a default priority, palette and base tile index offset.");
			return null;
		}

        // get resource id (name parameter)
        String name = fields[1];

        // get input base file
        String baseFile = FileUtil.adjustPath(Compiler.resDir, fields[2]);
        File baseFileDesc = new File(baseFile);
        String baseFileName = baseFileDesc.getName();
        String baseFileAbsPath = baseFileDesc.getAbsolutePath().replace(baseFileName, "");
        Matcher baseFileNameMatcher = CommonTilesRangeManager.stripsBaseFileNamePattern.matcher(baseFileName);
		if (!baseFileNameMatcher.matches()) {
			throw new IllegalArgumentException("baseFile doesn't match expected pattern.");
		}

		// get number of strips
        int strips = StringUtil.parseInt(fields[3], 0);

        // tilesetStatsCollectorId
        String tilesetStatsCollectorId = null; // null or empty string is considered as an invalid tile stats cache id
        if (fields.length >= 5) {
	        tilesetStatsCollectorId = fields[4].toUpperCase();
	        if ("NONE".equals(tilesetStatsCollectorId) || "NULL".equals(tilesetStatsCollectorId)) {
	        	tilesetStatsCollectorId = null;
	        }
        }

        // tileCacheId
        String tilesCacheId = null; // null or empty string is considered as an invalid tile cache id
        if (fields.length >= 6) {
        	String valueId = fields[5].toUpperCase();
        	if (!"NONE".equals(valueId) && !"NULL".equals(valueId)) {
        		tilesCacheId = valueId;
        		TilesCacheManager.createStatsCacheIfNotExist(tilesCacheId);
        	}
        }

        // commonTilesId
        String commonTilesRangeId = null; // null or empty string is considered as an invalid common tiles id
        if (fields.length >= 7) {
        	String valueId = fields[6];
        	if (!"NONE".equals(valueId) && !"NULL".equals(valueId)) {
        		commonTilesRangeId = valueId;
        	}
        }

        // get tileset split number
        int splitTileset = 1;
        if (fields.length >= 8) {
        	int value = StringUtil.parseInt(fields[7], 1);
			splitTileset = Math.min(3, Math.max(1, value)); // clamp(1, 3, value)
        }

        // get the tileset split strategy
        TilesetSplitStrategyEnum splitTilesetStrategy = TilesetSplitStrategyEnum.SPLIT_NORMAL;
        if (fields.length >= 9) {
        	splitTilesetStrategy = TilesetSplitStrategyEnum.from(fields[8]);
        }
        if (splitTileset > 1 && splitTilesetStrategy == TilesetSplitStrategyEnum.SPLIT_NONE)
        	throw new IllegalArgumentException("splitTilesetStrategy can't be " + TilesetSplitStrategyEnum.SPLIT_NONE.getValue() + " when splitTileset > 1.");

        // get tilemap split number
        int splitTilemap = 1;
        if (fields.length >= 10) {
        	int value = StringUtil.parseInt(fields[9], 1);
        	splitTilemap = Math.min(3, Math.max(1, value)); // clamp(1, 3, value)
        	splitTilemap = Math.min(splitTileset, splitTilemap); // can't be bigger than splitTileset value
        }

        // get the value for toggling map tile base index for video frame swap buffer
        ToggleMapTileBaseIndex toggleMapTileBaseIndexFlag = ToggleMapTileBaseIndex.NONE;
        if (fields.length >= 11) {
        	toggleMapTileBaseIndexFlag = ToggleMapTileBaseIndex.from(fields[10]);
        }

        // extend map width to 64 tiles
        int mapExtendedWidth = 0;
        if (fields.length >= 12) {
        	int value = StringUtil.parseInt(fields[11], 0);
        	if (value == 0 || value == 32 || value == 64 || value == 128)
        		mapExtendedWidth = value;
        }

        // get packed value
        Compression compression = Compression.NONE;
        if (fields.length >= 13)
            compression = Util.getCompression(fields[12]);

        // get custom compression value for tileset
        CompressionCustom compressionCustomTileset = CompressionCustom.NONE;
        if (fields.length >= 14)
        	compressionCustomTileset = CompressionCustom.from(fields[13]);

        // get custom compression value for tilemap
        CompressionCustom compressionCustomTilemap = CompressionCustom.NONE;
        if (fields.length >= 15)
        	compressionCustomTilemap = CompressionCustom.from(fields[14]);

        boolean addCompressionField = false;
        if (fields.length >= 16)
        	addCompressionField = Boolean.parseBoolean(fields[15]);

        // get map optimization value
        TileOptimization tileOpt = TileOptimization.ALL;
        if (fields.length >= 17)
            tileOpt = Util.getTileOpt(fields[16]);

        // get map base
        int mapBase = 0;
        if (fields.length >= 18)
            mapBase = Integer.parseInt(fields[17]);

        // generate the list of strip files
        List<String> stripsInList = generateFilesInForStrips(baseFileAbsPath, baseFileName, baseFileNameMatcher, strips);

        // add resource file (used for deps generation)
        // NOTE: missing split in 2 parts when splitTileset > 1
//		String baseFileIn = baseFileAbsPath + name + FileUtil.getFileExtension(baseFile, true);
//		saveFinalImage(baseFileIn, stripsInList, mergeOpt);
//		Compiler.addResourceFile(baseFileIn);

        if (splitTileset == 1)
        	return new ImageStripsNoPals(name, stripsInList, toggleMapTileBaseIndexFlag, mapExtendedWidth, compression, tileOpt, mapBase, 
        			compressionCustomTileset, compressionCustomTilemap, addCompressionField, tilesCacheId, tilesetStatsCollectorId,
        			commonTilesRangeId);
        else if (splitTileset == 2)
        	return new ImageStripsNoPalsSplit2(name, stripsInList, splitTilemap, toggleMapTileBaseIndexFlag, mapExtendedWidth, compression, 
        			tileOpt, mapBase, compressionCustomTileset, compressionCustomTilemap, addCompressionField, tilesCacheId, tilesetStatsCollectorId,
        			commonTilesRangeId, splitTilesetStrategy);
        else
        	return new ImageStripsNoPalsSplit3(name, stripsInList, splitTilemap, toggleMapTileBaseIndexFlag, mapExtendedWidth, compression, 
        			tileOpt, mapBase, compressionCustomTileset, compressionCustomTilemap, addCompressionField, tilesCacheId, tilesetStatsCollectorId,
        			commonTilesRangeId, splitTilesetStrategy);
    }

	private List<String> generateFilesInForStrips(String absPath, String baseFileName, Matcher baseFileNameMatcher, int strips)
	{
		// Eg: mv_frame_47_0_RGB.png
        String baseName = baseFileName.substring(0, baseFileNameMatcher.start(2)); // mv_frame_47_
        String middle = baseFileNameMatcher.group(2); // 0
        String ending = baseFileName.substring(baseFileNameMatcher.end(2), baseFileName.length()); // _RGB.png
        int startingStrip = Integer.parseInt(middle);
        int length = startingStrip + strips;

        List<String> sortedFiles = new ArrayList<>(strips);
        for (int i = startingStrip; i < length; i++) {
            String newFilename = absPath + baseName + String.format("%d", i) + ending;
            sortedFiles.add(newFilename);
        }

		return sortedFiles;
	}

	private void saveFinalImage(String finalImgPath, List<String> stripsInList) throws IOException
	{
        // get info from first strip
        BasicImageInfo strip0Info = ImageUtil.getBasicInfo(stripsInList.get(0));
        int w = strip0Info.w;
        int h = strip0Info.h;
        int hStrip = strip0Info.h;
        // true color / RGB image ? removes palettes
        int cropPalettesOffset = strip0Info.bpp > 8 ? 32 : 0;
        h -= cropPalettesOffset;
        hStrip -= cropPalettesOffset;
        h *= stripsInList.size();

        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        Rectangle rect = new Rectangle(0, cropPalettesOffset, w, hStrip);
        for (int i=0; i < stripsInList.size(); ++i) {
        	BufferedImage stripImg = ImageUtil.load(stripsInList.get(i));
        	ImageUtil.waitImageReady(stripImg); // be sure image data are ready
        	BufferedImage subStripImage = stripImg.getSubimage(rect.x, rect.y, rect.width, rect.height);
        	g2d.drawImage(subStripImage, 0, i * hStrip, null);
        }

        g2d.dispose();

        String extension = FileUtil.getFileExtension(finalImgPath, false);
        ImageUtil.save(image, extension, finalImgPath);
        image.flush();
	}

//	public static void main(String[] args) throws Exception
//	{
//		ImageStripsNoPalsProcessor p = new ImageStripsNoPalsProcessor();
//		String[] fields_test_A = {
//				resId, "mv_frame_47_0_RGB", "C:\\MyProjects\\VSCode\\sgdk\\sgdk-video-player-main\\res\\rgb\\frame_47_0_RGB.png", "22", "tilesetStats1", 
//				"TilesCache_Movie1", "NONE", "2", TilesetSplitStrategyEnum.SPLIT_NORMAL.getValue(), "2", "ODD", "0", "FAST", "NONE", "NONE", "TRUE", "ALL"
//			};
//		String[] fields_test_B = {
//				resId, "mv_frame_161_0_RGB", "C:\\MyProjects\\VSCode\\sgdk\\sgdk-video-player-main\\res\\rgb\\frame_161_0_RGB.png", "22", "tilesetStats1", 
//				"TilesCache_Movie1", "NONE", "3", TilesetSplitStrategyEnum.SPLIT_NORMAL.getValue(), "1", "ODD", "0", "NONE", "NONE", "RLEW_B", "TRUE", "ALL"
//			};
//		String[] fields_test_one_strip = {
//				resId, "mv_frame_161_0_RGB", "C:\\MyProjects\\VSCode\\sgdk\\sgdk-video-player-main\\res\\rgb\\frame_1_0_RGB.png", "1", "tilesetStats1", 
//				"TilesCache_Movie1", "NONE", "3", TilesetSplitStrategyEnum.SPLIT_NORMAL.getValue(), "1", "ODD", "0", "NONE", "FAST", "FAST", "TRUE", "ALL"
//			};
//		String[] fields_test_Titan = {
//				resId, "titanRGB", "C:\\MyProjects\\VSCode\\sgdk\\titan256c\\res\\titan256c\\titan_0_0_RGB.png", "28", "tilesetStats1", 
//				"NULL", "NONE", "1", TilesetSplitStrategyEnum.SPLIT_NORMAL.getValue(), "1", "NONE", "0", "FAST", "NONE", "NONE", "TRUE", "ALL"
//			};
//		String[] fields_test_doom_bg_shifted = {
//				resId, "img_title_bg_full_shifted", "C:\\MyProjects\\VSCode\\sgdk\\raycasting_anael\\res\\title\\doom_title_screen_full_shifted_0_0_RGB.png",
//				"28", "stats_strips_2", "cache_titleBGfullTiles", "NONE", "1", TilesetSplitStrategyEnum.SPLIT_NORMAL.getValue(), "1", "NONE", "0",
//				"APLIB", "NONE", "NONE", "TRUE", "ALL"
//			};
//
//		p.execute(fields_test_one_strip);
//	}
}
