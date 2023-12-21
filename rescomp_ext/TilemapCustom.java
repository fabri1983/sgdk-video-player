package sgdk.rescomp.resource;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import sgdk.rescomp.type.Basics.Compression;
import sgdk.rescomp.type.Basics.TileEquality;
import sgdk.rescomp.type.Basics.TileOptimization;
import sgdk.rescomp.type.Tile;

public class TilemapCustom extends Tilemap
{
	private static int MAX_TILESET_NUM_FOR_MAP_BASE_TILE_INDEX = 724;

    public TilemapCustom(String id, short[] data, int w, int h, Compression compression) {
		super(id, data, w, h, compression);
	}

	public static Tilemap getTilemap(String id, Tileset tileset, int mapBase, byte[] image8bpp, int imageWidth, int imageHeight, int startTileX, int startTileY,
            int widthTile, int heightTile, TileOptimization opt, Compression compression, boolean extendedMapWidth64)
    {
        int w = widthTile;
        int h = heightTile;

        final boolean mapBasePrio = (mapBase & Tile.TILE_PRIORITY_MASK) != 0;
        final int mapBasePal = (mapBase & Tile.TILE_PALETTE_MASK) >> Tile.TILE_PALETTE_SFT;
        int mapBaseTileInd = mapBase & Tile.TILE_INDEX_MASK;

        // fabri1983: get frame num
        Pattern idNamePattern = Pattern.compile("^[A-Za-z_]+_(\\d+)(_\\d+)?(_RGB)?_tilemap$", Pattern.CASE_INSENSITIVE);
        Matcher idNameMatcher = idNamePattern.matcher(id);
        String group1 = null;
        if (idNameMatcher.matches()) {
        	group1 = idNameMatcher.group(1);
        }
        Integer frameNum = getFrameNum(group1); // might be null

        // fabri1983: only print message at first frame whether is odd or even
    	printMessage(mapBaseTileInd, frameNum);

    	// fabri1983: here we set mapBaseTileInd according frameNum and mapBaseTileInd
    	mapBaseTileInd = setMapBaseTileInd(mapBaseTileInd, frameNum, 0, MAX_TILESET_NUM_FOR_MAP_BASE_TILE_INDEX);

    	// we have a base offset --> we can use system plain tiles
    	//final boolean useSystemTiles = mapBaseTileInd != 0;
    	// fabri1983: don't use system tiles if mapBaseTileInd is 2047 or 2046
        final boolean useSystemTiles = mapBaseTileInd != 0 && mapBaseTileInd != Tile.TILE_INDEX_MASK && mapBaseTileInd != (Tile.TILE_INDEX_MASK - 1);

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
	 */
	private static void printMessage(int mapBaseTileInd, Integer frameNum) {
		if (mapBaseTileInd == Tile.TILE_INDEX_MASK || mapBaseTileInd == (Tile.TILE_INDEX_MASK - 1)) {
    		boolean printMsg = false;
    		// 2047 if frame num is odd then frame num has to be 1
    		if (mapBaseTileInd == Tile.TILE_INDEX_MASK && frameNum != null) {
    			printMsg = frameNum.intValue() == 1;
    		}
    		// 2046 if frame num is even then frame num has to be 0
    		else if (mapBaseTileInd == (Tile.TILE_INDEX_MASK - 1) && frameNum != null) {
    			printMsg = frameNum.intValue() == 0;
    		}

    		if (printMsg) {
    			String mn = String.valueOf(MAX_TILESET_NUM_FOR_MAP_BASE_TILE_INDEX);
    			System.out.println("");
		    	System.out.println("####################################################################################################");
		    	System.out.println("TilemapCustom class by fabri1983.");
		    	System.out.println("When mapBase parameter = 2047 (TILE_INDEX_MASK) or 2046 (TILE_INDEX_MASK - 1) we use the frame num to ");
		    	System.out.println("toggle between tile index 0 and " + mn + " (max tileset numTile value got in another run).");
		    	System.out.println("Use 2047 if first frame num is odd => base tile index 0 for odd frames and " + mn + " for even frames.");
		    	System.out.println("Use 2046 if first frame num is even => base tile index 0 for even frames and " + mn + " for odd frames.");
		    	System.out.println("(NOTE: using 2047 or 2046 is a non common value. No other resource will use them since it would set as ");
		    	System.out.println("static only 2 tiles at the end of the VRAM space dedicated for tileset)");
		    	System.out.println("####################################################################################################");
    		}
    	}
	}

	/**
	 * fabri1983
	 */
    private static int setMapBaseTileInd(int mapBaseTileInd, Integer frameNum, int tileIndexA, int tileIndexB) {
    	if (frameNum == null || mapBaseTileInd == 0 || (mapBaseTileInd != Tile.TILE_INDEX_MASK && mapBaseTileInd != (Tile.TILE_INDEX_MASK - 1)))
    		return mapBaseTileInd;

    	// 2047 and framNum is odd then base tile index is tileIndexA otherwise tileIndexB
		if (mapBaseTileInd == Tile.TILE_INDEX_MASK) {
			if ((frameNum.intValue() % 2) == 1)
				return tileIndexA;
			else
				return tileIndexB;
		}
		// 2046 if frameNum is even then base tile index is tileIndexA otherwise tileIndexB
		else if (mapBaseTileInd == (Tile.TILE_INDEX_MASK - 1)) {
			if ((frameNum.intValue() % 2) == 0)
				return tileIndexA;
			else
				return tileIndexB;
		}

		return mapBaseTileInd;
	}

	public static Tilemap getTilemap(String id, Tileset tileset, int mapBase, byte[] image8bpp, int widthTile, int heightTile, TileOptimization opt,
            Compression compression, boolean extendedMapWidth64)
    {
        return TilemapCustom.getTilemap(id, tileset, mapBase, image8bpp, widthTile * 8, heightTile * 8, 0, 0, widthTile, heightTile, opt, compression, 
        		extendedMapWidth64);
    }

}