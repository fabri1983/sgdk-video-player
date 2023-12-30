package sgdk.rescomp.resource;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import sgdk.rescomp.tool.ExtProperties;
import sgdk.rescomp.type.Basics.Compression;
import sgdk.rescomp.type.Basics.TileEquality;
import sgdk.rescomp.type.Basics.TileOptimization;
import sgdk.rescomp.type.Tile;

public class TilemapCustom extends Tilemap
{
    public TilemapCustom(String id, short[] data, int w, int h, Compression compression) {
		super(id, data, w, h, compression);
	}

	public static Tilemap getTilemap(String id, Tileset tileset, int toggleMapTileBaseIndexFlag, int mapBase, byte[] image8bpp, 
			int imageWidth, int imageHeight, int startTileX, int startTileY, int widthTile, int heightTile, TileOptimization opt, 
			Compression compression, boolean extendedMapWidth64)
    {
        int w = widthTile;
        int h = heightTile;

        final boolean mapBasePrio = (mapBase & Tile.TILE_PRIORITY_MASK) != 0;
        final int mapBasePal = (mapBase & Tile.TILE_PALETTE_MASK) >> Tile.TILE_PALETTE_SFT;
        int mapBaseTileInd = mapBase & Tile.TILE_INDEX_MASK;

        // fabri1983: get frame num
        Pattern idNamePattern = Pattern.compile("^[A-Za-z_]+_(\\d+)(_\\d+)?(_RGB)?(_chunk\\d)?_tilemap$", Pattern.CASE_INSENSITIVE);
        Matcher idNameMatcher = idNamePattern.matcher(id);
        Integer frameNum = null;
        if (idNameMatcher.matches()) {
        	String group1 = idNameMatcher.group(1);
        	frameNum = getFrameNum(group1);
        	if (frameNum == null) {
        		System.out.println(" ##### frameNum SHOULDN'T BE NULL HERE. TilemapCustom class");
        	}
        }

        // fabri1983: only print message at first frame and only for chunk1
        printMessageForParamToggleMapTileBaseIndexFlag(toggleMapTileBaseIndexFlag, frameNum, id);

    	// fabri1983: here we calculate videoFrameBufferOffsetIndex according frameNum
    	int videoFrameBufferOffsetIndex = 0;
    	if (toggleMapTileBaseIndexFlag != -1) {
    		int tileIndexA = ExtProperties.getInt(ExtProperties.STARTING_TILESET_ON_SGDK);
    		int tileIndexB = tileIndexA + ExtProperties.getInt(ExtProperties.MAX_TILESET_NUM_FOR_MAP_BASE_TILE_INDEX);
    		videoFrameBufferOffsetIndex = calculateVideoFrameBufferOffsetIndex(toggleMapTileBaseIndexFlag, frameNum, tileIndexA, tileIndexB);
    	}
    	// fabri1983: add to current mapBaseTileInd value
    	mapBaseTileInd += videoFrameBufferOffsetIndex;

		// we have a base offset --> we can use system plain tiles
		// fabri1983: don't use system plain tiles
        final boolean useSystemTiles = false;//mapBaseTileInd != 0;

        short[] data = new short[w * h];

        int offset = 0;
        // important to always use the same loop order when building Tileset and Tilemap object
        for (int j = 0; j < h; j++)
        {
            for (int i = 0; i < w; i++)
            {
                // tile position
                final int ti = i + startTileX;
                final int tj = j + startTileY;

                // get tile
                final Tile tile = Tile.getTile(image8bpp, imageWidth, imageHeight, ti * 8, tj * 8, 8);
                int index;
                TileEquality equality = TileEquality.NONE;

                // if no optimization, just use current offset as index
                if (opt == TileOptimization.NONE)
                    index = offset + mapBaseTileInd;
                else
                {
                    // use system tiles for plain tiles if possible
                    if (useSystemTiles && tile.isPlain())
                        index = tile.getPlainValue();
                    else
                    {
                        // otherwise we try to get tile index in the tileset
                        index = tileset.getTileIndex(tile, opt);
                        // not found ? (should never happen)
                        if (index == -1)
                            throw new RuntimeException("Can't find tile [" + ti + "," + tj + "] in tileset, something wrong happened...");

                        // get equality info
                        equality = tile.getEquality(tileset.get(index));
                        // can add base index now
                        index += mapBaseTileInd;
                    }
                }

                // set tilemap
                data[offset++] = (short) Tile.TILE_ATTR_FULL(mapBasePal + tile.pal, mapBasePrio | tile.prio, equality.vflip, equality.hflip, index);
            }
        }

        if (extendedMapWidth64) {
        	data = convertDataTo64TilesWidth(data, w, h);
        	w = 64;
        }

        return new Tilemap(id, data, w, h, compression);
    }

    /**
	 * fabri1983
	 */
	private static short[] convertDataTo64TilesWidth(short[] data, int wOrig, int hOrig) {
		short[] dataExtended = new short[64 * hOrig];
		for (int i = 0; i < hOrig; i++) {
			// Copy each row from the original data to the expanded data
			System.arraycopy(data, i * wOrig, dataExtended, i * 64, wOrig);
			// Fill the remaining space in each row with zeros
			for (int j = wOrig; j < 64; j++) {
				dataExtended[i * 64 + j] = 0;
			}
		}
		return dataExtended;
	}

	/**
	 * fabri1983
	 */
	private static Integer getFrameNum(String numStr) {
		if (numStr == null || "".equals(numStr))
			return null;
		try {
			return Integer.valueOf(numStr);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * fabri1983
	 * @param id 
	 */
	private static void printMessageForParamToggleMapTileBaseIndexFlag(int toggleMapTileBaseIndexFlag, Integer frameNum, String id) {
		if (id.contains("_chunk1") && toggleMapTileBaseIndexFlag != -1 && frameNum != null) {
    		boolean printMsg = false;
    		// toggleMapTileBaseIndexFlag == 1 then expected first frame num has to be 1 (odd)
    		if (toggleMapTileBaseIndexFlag == 1) {
    			printMsg = frameNum.intValue() == 1;
    		}
    		// toggleMapTileBaseIndexFlag == 1 then expected first frame num has to be 0 (even)
    		else if (toggleMapTileBaseIndexFlag == 0) {
    			printMsg = frameNum.intValue() == 0;
    		}
    		else {
    			System.out.println("");
    			System.out.println("####################################################################################################");
    			System.out.println("TilemapCustom class by fabri1983.");
    			System.out.println("SOMETHING WRONG WITH YOUR FRAME NUM SETUP! WAS toggleMapTileBaseIndexFlag SET CORRECTLY?");
    			System.out.println("####################################################################################################");
    		}

    		if (printMsg) {
    			int a = ExtProperties.getInt(ExtProperties.STARTING_TILESET_ON_SGDK);
        		int b = a + ExtProperties.getInt(ExtProperties.MAX_TILESET_NUM_FOR_MAP_BASE_TILE_INDEX);
    			System.out.println("");
		    	System.out.println("####################################################################################################");
		    	System.out.println("TilemapCustom class by fabri1983.");
		    	System.out.println("Parameter toggleMapTileBaseIndexFlag was set to we use the frame num for toggling between ");
		    	System.out.println("tile index " + a + " (" + ExtProperties.STARTING_TILESET_ON_SGDK + ") and " + b + " (" + ExtProperties.MAX_TILESET_NUM_FOR_MAP_BASE_TILE_INDEX + ").");
		    	System.out.println("(max tileset numTile value got experimentally in other runs).");
		    	System.out.println("Use toggleMapTileBaseIndexFlag = 0 if first frame num is even.");
		    	System.out.println("Use toggleMapTileBaseIndexFlag = 1 if first frame num is odd.");
		    	System.out.println("####################################################################################################");
    		}
    	}
	}

	/**
	 * fabri1983
	 */
    private static int calculateVideoFrameBufferOffsetIndex(int toggleMapTileBaseIndexFlag, Integer frameNum, int tileIndexA, int tileIndexB) {
    	if (frameNum == null)
    		return 0;

		// toggleMapTileBaseIndexFlag is 0 => frameNum is even then base tile index is tileIndexA, otherwise tileIndexB
		if (toggleMapTileBaseIndexFlag == 0) {
			if ((frameNum.intValue() % 2) == 0)
				return tileIndexA;
			else
				return tileIndexB;
		}
		// toggleMapTileBaseIndexFlag is 1 => framNum is odd then base tile index is tileIndexA, otherwise tileIndexB
		else if (toggleMapTileBaseIndexFlag == 1) {
			if ((frameNum.intValue() % 2) == 1)
				return tileIndexA;
			else
				return tileIndexB;
		}

		return 0;
	}

	public static Tilemap getTilemap(String id, Tileset tileset, int toggleMapTileBaseIndexFlag, int mapBase, byte[] image8bpp, int widthTile, int heightTile, 
			TileOptimization opt, Compression compression, boolean extendedMapWidth64)
    {
        return TilemapCustom.getTilemap(id, tileset, toggleMapTileBaseIndexFlag, mapBase, image8bpp, widthTile * 8, heightTile * 8, 0, 0, widthTile, heightTile, opt, compression, 
        		extendedMapWidth64);
    }

}