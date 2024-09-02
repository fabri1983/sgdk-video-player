package sgdk.rescomp.processor;

import sgdk.rescomp.Compiler;
import sgdk.rescomp.Processor;
import sgdk.rescomp.Resource;
import sgdk.rescomp.resource.ImageNoPals;
import sgdk.rescomp.tool.Util;
import sgdk.rescomp.type.Basics.Compression;
import sgdk.rescomp.type.Basics.TileOptimization;
import sgdk.rescomp.type.CompressionCustom;
import sgdk.tool.FileUtil;
import sgdk.tool.StringUtil;

public class ImageNoPalsProcessor implements Processor
{
	private static final String resId = "IMAGE_NO_PALS";

    @Override
    public String getId()
    {
        return resId;
    }

    @Override
    public Resource execute(String[] fields) throws Exception
    {
		if (fields.length < 5)
		{
			System.out.println("Wrong " + resId + " definition");
			System.out.println(resId + " name \"baseFile\" tilesetStatsCollectorId [tilesCacheId splitTileset splitTilemap toggleMapTileBaseIndexFlag mapExtendedWidth compression compressionCustomTileSet compressionCustomTileMap addCompressionField map_opt map_base]");
			System.out.println("  name               Image variable name");
            System.out.println("  file               Path of the input image file (BMP or PNG image)");
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

        // get input file
        final String fileIn = FileUtil.adjustPath(Compiler.resDir, fields[2]);

        // extend map width to 64 tiles
        int mapExtendedWidth = 0;
        if (fields.length >= 4) {
        	int value = StringUtil.parseInt(fields[3], 0);
        	if (value == 0 || value == 32 || value == 64 || value == 128)
        		mapExtendedWidth = value;
        }

        // get packed value
        Compression compression = Compression.NONE;
        if (fields.length >= 5)
            compression = Util.getCompression(fields[4]);

        // get custom compression value for tileset
        CompressionCustom compressionCustomTileset = CompressionCustom.NONE;
        if (fields.length >= 6)
        	compressionCustomTileset = CompressionCustom.from(fields[5]);

        // get custom compression value for tilemap
        CompressionCustom compressionCustomTilemap = CompressionCustom.NONE;
        if (fields.length >= 7)
        	compressionCustomTilemap = CompressionCustom.from(fields[6]);

        boolean addCompressionField = false;
        if (fields.length >= 8)
        	addCompressionField = Boolean.parseBoolean(fields[7]);

        // get map optimization value
        TileOptimization tileOpt = TileOptimization.ALL;
        if (fields.length >= 9)
            tileOpt = Util.getTileOpt(fields[8]);

        // get map base
        int mapBase = 0;
        if (fields.length >= 10)
            mapBase = Integer.parseInt(fields[9]);

        // add resource file (used for deps generation)
        Compiler.addResourceFile(fileIn);

        return new ImageNoPals(name, fileIn, mapExtendedWidth, compression, tileOpt, mapBase, compressionCustomTileset, compressionCustomTilemap, addCompressionField);
    }
}
