package sgdk.rescomp.tool;

import java.util.List;

import sgdk.rescomp.resource.TilesetOriginalCustom;
import sgdk.rescomp.type.Basics.TileOptimization;
import sgdk.rescomp.type.Tile;
import sgdk.rescomp.type.ToggleMapTileBaseIndex;

public class TilemapCustomTools {

	/**
	 * fabri1983
	 */
	public static short[] convertDataToNTilesWidth(short[] data, int wOrig, int hOrig, int newWidth) {
		short[] dataExtended = new short[newWidth * hOrig];
		for (int i = 0; i < hOrig; i++) {
			// Copy each row from the original data to the expanded data
			System.arraycopy(data, i * wOrig, dataExtended, i * newWidth, wOrig);
			// Fill the remaining space in each row with zeros
			for (int j = wOrig; j < newWidth; j++) {
				dataExtended[i * newWidth + j] = 0;
			}
		}
		return dataExtended;
	}

	/**
	 * fabri1983
	 */
	public static Integer getFrameNum(String numStr) {
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
	public static void printMessageForParamToggleMapTileBaseIndexFlag(ToggleMapTileBaseIndex toggleMapTileBaseIndexFlag, Integer frameNum, String id) {
		if (toggleMapTileBaseIndexFlag != ToggleMapTileBaseIndex.NONE && frameNum != null) {
    		boolean printMsg = false;
    		// toggleMapTileBaseIndexFlag == ODD then expected first frame num has to be 1
    		if (toggleMapTileBaseIndexFlag == ToggleMapTileBaseIndex.ODD) {
    			printMsg = frameNum.intValue() == 1;
    		}
    		// toggleMapTileBaseIndexFlag == EVEN then expected first frame num has to be 0
    		else if (toggleMapTileBaseIndexFlag == ToggleMapTileBaseIndex.EVEN) {
    			printMsg = frameNum.intValue() == 0;
    		}
    		else {
    			System.out.println("");
    			System.out.println("####################################################################################################");
    			System.out.println("[ERROR] SOMETHING WRONG WITH YOUR FRAME NUM SETUP! WAS toggleMapTileBaseIndexFlag SET CORRECTLY? " + TilemapCustomTools.class.getSimpleName());
    			System.out.println("####################################################################################################");
    		}

    		if (printMsg) {
    			int a = ExtProperties.getInt(ExtProperties.STARTING_TILESET_ON_SGDK);
        		int b = a + ExtProperties.getInt(ExtProperties.MAX_TILESET_NUM_FOR_MAP_BASE_TILE_INDEX);
    			System.out.println("");
		    	System.out.println("####################################################################################################");
		    	System.out.println(TilemapCustomTools.class.getSimpleName());
		    	System.out.println("Parameter toggleMapTileBaseIndexFlag was set to use the frame num for toggling between: ");
		    	System.out.println("   tile index " + a + " (" + ExtProperties.STARTING_TILESET_ON_SGDK + ")");
		    	System.out.println("and");
		    	System.out.println("   tile index " + b + " (" + ExtProperties.STARTING_TILESET_ON_SGDK + " + " + ExtProperties.MAX_TILESET_NUM_FOR_MAP_BASE_TILE_INDEX + ")");
		    	System.out.println("(the later got experimentally in other runs).");
		    	System.out.println("toggleMapTileBaseIndexFlag = EVEN when first frame num is even.");
		    	System.out.println("toggleMapTileBaseIndexFlag = ODD when first frame num is odd.");
		    	System.out.println("####################################################################################################");
    		}
    	}
	}

	/**
	 * fabri1983
	 */
	public static int calculateVideoFrameBufferOffsetIndex(ToggleMapTileBaseIndex toggleMapTileBaseIndexFlag, Integer frameNum, int tileIndexA, int tileIndexB) {
    	if (frameNum == null)
    		return 0;

		// toggleMapTileBaseIndexFlag is EVEN => frameNum is even then base tile index is tileIndexA, otherwise tileIndexB
		if (toggleMapTileBaseIndexFlag == ToggleMapTileBaseIndex.EVEN) {
			if ((frameNum.intValue() % 2) == 0)
				return tileIndexA;
			else
				return tileIndexB;
		}
		// toggleMapTileBaseIndexFlag is ODD => framNum is odd then base tile index is tileIndexA, otherwise tileIndexB
		else if (toggleMapTileBaseIndexFlag == ToggleMapTileBaseIndex.ODD) {
			if ((frameNum.intValue() % 2) == 1)
				return tileIndexA;
			else
				return tileIndexB;
		}

		return 0;
	}

    /**
	 * fabri1983
	 */
	public static int getTilesetIndexFor(Tile tile, TileOptimization opt, List<TilesetOriginalCustom> tilesets) {
    	for (int i=0; i < tilesets.size(); ++i) {
    		TilesetOriginalCustom tileset = tilesets.get(i);
    		int index = tileset.getTileIndex(tile, opt);
    		if (index != -1) // tile found in current tileset
    			return i;
    	}
    	System.out.print("\n[WARNING] Tile not found in any of the " + tilesets.size() + " tilesets. " + TilemapCustomTools.class.getSimpleName());
    	return 0;
    }
}
