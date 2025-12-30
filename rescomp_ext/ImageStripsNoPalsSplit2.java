package sgdk.rescomp.resource;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import sgdk.rescomp.Resource;
import sgdk.rescomp.tool.CommonTilesRangeManager;
import sgdk.rescomp.tool.ExtProperties;
import sgdk.rescomp.tool.ImageUtilFast;
import sgdk.rescomp.tool.TilesCacheManager;
import sgdk.rescomp.tool.TilesetSizeSplitCalculator;
import sgdk.rescomp.tool.TilesetStatsCollector;
import sgdk.rescomp.tool.Util;
import sgdk.rescomp.type.Basics.Compression;
import sgdk.rescomp.type.Basics.TileOptimization;
import sgdk.rescomp.type.Basics.TileOrdering;
import sgdk.rescomp.type.CommonTilesRange;
import sgdk.rescomp.type.CompressionCustom;
import sgdk.rescomp.type.CustomDataTypes;
import sgdk.rescomp.type.Tile;
import sgdk.rescomp.type.TilesetSplitStrategyEnum;
import sgdk.rescomp.type.ToggleMapTileBaseIndex;
import sgdk.tool.ImageUtil;
import sgdk.tool.ImageUtil.BasicImageInfo;

public class ImageStripsNoPalsSplit2 extends Resource
{
    final int hc;

	public final TilesetOriginalCustom tileset1, tileset2;
	public final TilemapCustom tilemap1, tilemap2;

    public ImageStripsNoPalsSplit2(String id, List<String> stripsFileList, int splitTilemap, ToggleMapTileBaseIndex toggleMapTileBaseIndexFlag, 
    		int mapExtendedWidth, Compression compression, TileOptimization tileOpt, int mapBase, CompressionCustom compressionCustomTileset, 
    		CompressionCustom compressionCustomTilemap, boolean addCompressionField, String tilesCacheId, String tilesetStatsCollectorId, 
    		String commonTilesRangeId, TilesetSplitStrategyEnum splitStrategy) throws Exception
    {
        super(id);

        // finalImageData has no more palette definitions at the top
        byte[] finalImageData = mergeAllStrips(stripsFileList);

        // get info from first strip
        BasicImageInfo strip0Info = ImageUtil.getBasicInfo(stripsFileList.get(0));
        // width and height in pixels
        int w = strip0Info.w;
        // we determine 'h' from data length and 'w' as we can crop image vertically to remove palette data
        int h = finalImageData.length / w;
        // get size in tile
        int wt = w / 8;
        int ht = h / 8;

        boolean isTempTileset = true;
        TilesetOriginalCustom tilesetTemp = new TilesetOriginalCustom(id + "_tileset", finalImageData, w, h, 0, 0, wt, ht, tileOpt, compression, 
        		compressionCustomTileset, false, isTempTileset, TileOrdering.ROW, tilesCacheId, addCompressionField, commonTilesRangeId);
        checkTilesetMaxSizeForSplitIn2(tilesetTemp.getNumTile());

        // Calculate height for each tileset/tilemap
        int ht_1 = ht/2;
        int ht_2 = ht/2 + (ht % 2); // tileset2 height in tiles is calculated considering if ht is even or odd

        if (splitStrategy == TilesetSplitStrategyEnum.SPLIT_NORMAL || splitStrategy == TilesetSplitStrategyEnum.SPLIT_NONE) {
	    	tileset1 = (TilesetOriginalCustom) addInternalResource(new TilesetOriginalCustom(id + "_chunk1_tileset", finalImageData, w, h, 0, 0, wt, ht_1, 
	    			tileOpt, compression, compressionCustomTileset, false, false, TileOrdering.ROW, tilesCacheId, addCompressionField, commonTilesRangeId));
	    	tileset2 = (TilesetOriginalCustom) addInternalResource(new TilesetOriginalCustom(id + "_chunk2_tileset", finalImageData, w, h, 0, ht_1, wt, ht_2, 
	    			tileOpt, compression, compressionCustomTileset, false, false, TileOrdering.ROW, tilesCacheId, addCompressionField, commonTilesRangeId));
        }
        else {
			List<List<Tile>> splitTiles = new ArrayList<>(2);
			splitTiles.add(Collections.emptyList());
			splitTiles.add(Collections.emptyList());

        	int maxTilesetChunkSize = ExtProperties.getInt(ExtProperties.MAX_TILESET_CHUNK_SIZE_FOR_SPLIT_IN_2);

			if (splitStrategy == TilesetSplitStrategyEnum.SPLIT_MAX_CAPACITY_FIRST) {
        		splitTiles = TilesetSizeSplitCalculator.splitWithMaxTilesFirst(tilesetTemp.tiles, 2, maxTilesetChunkSize);
        	}
        	else if (splitStrategy == TilesetSplitStrategyEnum.SPLIT_EVENLY) {
        		splitTiles = TilesetSizeSplitCalculator.splitWithMaxEvenlyDistribution(tilesetTemp.tiles, 2, maxTilesetChunkSize);
        	}

    		// If there is common tiles for this image and is also the first image of the range 
			// then we have to accommodate the tiles in such a way that common tiles go at the end
    		List<Tile> commonTilesFirstImage = needsTilesAccomodationForCommonTiles(commonTilesRangeId, stripsFileList.get(0));
			if (!commonTilesFirstImage.isEmpty()) {
				splitTiles = accomodateTilesWithCommonTiles(splitTiles, maxTilesetChunkSize, commonTilesFirstImage);
			}

	    	tileset1 = (TilesetOriginalCustom) addInternalResource(new TilesetOriginalCustom(id + "_chunk1_tileset", splitTiles.get(0),
					tileOpt, compression, compressionCustomTileset, false, false, TileOrdering.ROW, tilesCacheId, addCompressionField, commonTilesRangeId));
			tileset2 = (TilesetOriginalCustom) addInternalResource(new TilesetOriginalCustom(id + "_chunk2_tileset", splitTiles.get(1),
					tileOpt, compression, compressionCustomTileset, false, false, TileOrdering.ROW, tilesCacheId, addCompressionField, commonTilesRangeId));
			
        }

        checkTilesetMaxChunkSize(tileset1.getNumTile());
		checkTilesetMaxChunkSize(tileset2.getNumTile());

    	List<TilesetOriginalCustom> tilesetsList = Arrays.asList(tileset1, tileset2);

    	System.out.print(" " + id + " -> numTiles (chunk1 + chunk2): " + tileset1.getNumTile() + " + " + tileset2.getNumTile() + " = " + 
    			(tileset1.getNumTile() + tileset2.getNumTile()) + ". ");
    	if (tilesetStatsCollectorId != null && !"".equals(tilesetStatsCollectorId)) {
    		TilesetStatsCollector.count2chunks(tilesetStatsCollectorId, tileset1.getNumTile(), tileset2.getNumTile());
        }

    	int maxFrameTilesetTotalSize = ExtProperties.getInt(ExtProperties.MAX_TILESET_TOTAL_SIZE_FOR_SPLIT_IN_2);
        int[] offsetAccumPerTilesetChunk = {0, tileset1.getNumTile()};
//    	int[] offsetAccumPerTilesetChunk = {0, ExtProperties.getInt(ExtProperties.MAX_TILESET_CHUNK_1_SIZE_FOR_SPLIT_IN_2)};

        if (splitTilemap == 1) {
			tilemap1 = (TilemapCustom) addInternalResource(TilemapCustom.getTilemap(id + "_chunk1_tilemap", tilesetsList, offsetAccumPerTilesetChunk, 
	        		toggleMapTileBaseIndexFlag, mapBase, finalImageData, w, h, 0, 0, wt, ht, tileOpt, compression, compressionCustomTilemap, 
	        		mapExtendedWidth, TileOrdering.ROW, tilesCacheId, addCompressionField, commonTilesRangeId, maxFrameTilesetTotalSize));
			tilemap2 = null;
        }
        else {
			tilemap1 = (TilemapCustom) addInternalResource(TilemapCustom.getTilemap(id + "_chunk1_tilemap", tilesetsList, offsetAccumPerTilesetChunk, 
	        		toggleMapTileBaseIndexFlag, mapBase, finalImageData, w, h, 0, 0, wt, ht_1, tileOpt, compression, compressionCustomTilemap, 
	        		mapExtendedWidth, TileOrdering.ROW, tilesCacheId, addCompressionField, commonTilesRangeId, maxFrameTilesetTotalSize));
	
	        tilemap2 = (TilemapCustom) addInternalResource(TilemapCustom.getTilemap(id + "_chunk2_tilemap", tilesetsList, offsetAccumPerTilesetChunk, 
	        		toggleMapTileBaseIndexFlag, mapBase, finalImageData, w, h, 0, ht_1, wt, ht_2, tileOpt, compression, compressionCustomTilemap, 
	        		mapExtendedWidth, TileOrdering.ROW, tilesCacheId, addCompressionField, commonTilesRangeId, maxFrameTilesetTotalSize));
        }

        if (TilesCacheManager.isStatsEnabledFor(tilesCacheId) && 
        		tilesetTemp.getNumTile() >= TilesCacheManager.getMinTilesetSizeForStatsFor(tilesCacheId)) {
	        TilesCacheManager.countResourcesPerTile(tilesCacheId, tilesetTemp.tiles);
	        TilesCacheManager.countTotalTiles(tilesCacheId, tileset1.tiles);
	        TilesCacheManager.countTotalTiles(tilesCacheId, tileset2.tiles);
        }

        // compute hash code
        int hcTemp = tileset1.hashCode() ^ tileset2.hashCode() ^ tilemap1.hashCode();
        if (tilemap2 != null)
        	hcTemp ^= tilemap2.hashCode();
        hc = hcTemp;
    }

    private List<Tile> needsTilesAccomodationForCommonTiles(String commonTilesRangeId, String firstImageStripName) {
        List<CommonTilesRange> commonTilesRanges = CommonTilesRangeManager.getFromResId(commonTilesRangeId);
        if (commonTilesRanges.isEmpty())
        	return Collections.emptyList();

    	Matcher baseFileNameMatcher = CommonTilesRangeManager.stripsBaseFileNamePattern.matcher(firstImageStripName);
		if (baseFileNameMatcher.matches()) {
			int imageNum = Integer.parseInt(baseFileNameMatcher.group(1));
			CommonTilesRange commonTileObj = CommonTilesRangeManager.findRangeForImageNum(commonTilesRanges, imageNum);
			if (commonTileObj != null && commonTileObj.getStartingImgNumInName() == imageNum)
				return commonTileObj.getTiles();
		}
		else {
    		System.out.println("[WARNING] Couldn't extract imageNum for CommonTilesRange from " + firstImageStripName + ". " 
    				+ ImageStripsNoPalsSplit2.class.getSimpleName());
    	}
		
		return Collections.emptyList();
	}

	private List<List<Tile>> accomodateTilesWithCommonTiles(List<List<Tile>> splitTiles, int maxTilesetChunkSize, List<Tile> commonTilesFirstImage) {
		// Merge all tiles preserving order
		List<Tile> mergedTiles = splitTiles.stream().flatMap(List::stream).collect(Collectors.toList());

		// Now, those lists at the beginning, if possible, will hold up to maxTilesetChunkSize.
		List<List<Tile>> newSplitTiles = new ArrayList<>(3);
		int index = 0;
		int totalTiles = mergedTiles.size();
		for (int i = 0; i < 3; i++) {
			if (index < totalTiles) {
				int end = Math.min(index + maxTilesetChunkSize, totalTiles);
				newSplitTiles.add(new ArrayList<>(mergedTiles.subList(index, end)));
				index = end;
			} else {
				newSplitTiles.add(new ArrayList<>());
			}
		}
		splitTiles = newSplitTiles;

		// Now, add common tiles after current tiles by filling existing lists that
		// didn't cope up with maxTilesetChunkSize elements in previous step
		int commonIndex = 0;
		for (List<Tile> chunk : splitTiles) {
			if (commonIndex >= commonTilesFirstImage.size())
				break;
			int spaceLeft = maxTilesetChunkSize - chunk.size();
			if (spaceLeft > 0) {
				int end = Math.min(commonIndex + spaceLeft, commonTilesFirstImage.size());
				chunk.addAll(commonTilesFirstImage.subList(commonIndex, end));
				commonIndex = end;
			}
		}
		return splitTiles;
	}

    private void checkTilesetMaxSizeForSplitIn2(int numTile) {
    	int max = ExtProperties.getInt(ExtProperties.MAX_TILESET_TOTAL_SIZE_FOR_SPLIT_IN_2);
		if (numTile > max) {
			throw new RuntimeException("Can't split in 2 tileset because size " + numTile + " > " + max
					+ " (" + ExtProperties.MAX_TILESET_TOTAL_SIZE_FOR_SPLIT_IN_2 + ")");
		}
	
	}

    private void checkTilesetMaxChunkSize(int numTile) {
    	int max = ExtProperties.getInt(ExtProperties.MAX_TILESET_CHUNK_SIZE_FOR_SPLIT_IN_2);
		if (numTile > max) {
			throw new RuntimeException("numTile " + numTile + " chunk size is greater than max allowed " + max
					+ " (" + ExtProperties.MAX_TILESET_CHUNK_SIZE_FOR_SPLIT_IN_2 + ")");
		}
	
	}

	private byte[] mergeAllStrips(List<String> stripsFileList) throws Exception
    {
		// get tile data per pixel (color position in palette), check image dimension is aligned to tile, remove palette info if any
        byte[] image0 = ImageUtilFast.getImageAs8bpp(stripsFileList.get(0), true, true);
        int stripLength = image0.length;
        // allocate space for bigger image
        final byte[] finalImage = new byte[stripLength * stripsFileList.size()];

        // copy all the strips into finalImage
        for (int i = 0; i < stripsFileList.size(); ++i) {
        	String imgFile = stripsFileList.get(i);
        	byte[] image = ImageUtilFast.getImageAs8bpp(imgFile, true, true);
        	checkImageNotNull(imgFile, image);
            checkImageColorByte(imgFile, image);
            System.arraycopy(image, 0, finalImage, i * stripLength, stripLength);
        }

        return finalImage;
	}

    /**
     * Happen when we couldn't retrieve palette data from RGB image
     * @param imgFile
     * @param image
     */
	private void checkImageNotNull(String imgFile, byte[] image) {
		if (image == null)
            throw new IllegalArgumentException(
                    "RGB image '" + imgFile + "' does not contains palette data (see 'Important note about image format' in the rescomp.txt file");
	}

	/**
	 * b0-b3 = pixel data; b4-b5 = palette index; b7 = priority bit
	 * check if image try to use bit 6 (probably mean that we have too much colors in our image)
	 * @param imgFile
	 * @param image
	 */
	private void checkImageColorByte(String imgFile, byte[] image) {
        for (byte d : image)
        {
            // bit 6 used ?
            if ((d & 0x40) != 0)
                throw new IllegalArgumentException(
                        "'" + imgFile + "' has color index in [64..127] range, IMAGE resource requires image with a maximum of 64 colors");
        }
	}

	public int getWidth()
    {
        return tilemap1.w * 8; // width is the same for every splitted tilemap 
    }

    public int getHeight()
    {
    	if (tilemap2 == null)
    		return tilemap1.h * 8;
    	else
    		return tilemap1.h * 8 + tilemap2.h * 8;
    }

    @Override
    public int internalHashCode()
    {
        return hc;
    }

    @Override
    public boolean internalEquals(Object obj)
    {
        if (obj instanceof ImageStripsNoPalsSplit2)
        {
        	final ImageStripsNoPalsSplit2 other = (ImageStripsNoPalsSplit2) obj;
        	boolean tilemap2equality = (tilemap2 == null && other.tilemap2 == null) 
        			|| (tilemap2 != null && other.tilemap2 != null && tilemap2.equals(other.tilemap2));
            return tileset1.equals(other.tileset1) && tileset2.equals(other.tileset2) 
            		&& tilemap1.equals(other.tilemap1) && tilemap2equality;
        }

        return false;
    }

    @Override
    public List<Bin> getInternalBinResources()
    {
        return new ArrayList<>();
    }

    @Override
    public int shallowSize()
    {
    	// 4 bytes (a long) per pointer declaration
        return 4 + 4 + 4 + (tilemap2 == null ? 0 : 4);
    }

    @Override
    public int totalSize()
    {
    	int tm2TotalSize = tilemap2 == null ? 0 : tilemap2.totalSize();
   		return shallowSize() + tileset1.totalSize() + tileset2.totalSize() + tilemap1.totalSize() + tm2TotalSize;
    }

    @Override
    public void out(ByteArrayOutputStream outB, StringBuilder outS, StringBuilder outH)
    {
		// can't store pointer so we just reset binary stream here (used for compression only)
		outB.reset();

		boolean addCompressedField = tileset1.addCompressionField == true || tilemap1.addCompressionField == true;

		// output Image structure
		if (tilemap2 == null) {
			if (addCompressedField)
				Util.decl(outS, outH, CustomDataTypes.ImageNoPals21CompField.getValue(), id, 2, global);
			else
				Util.decl(outS, outH, CustomDataTypes.ImageNoPalsSplit21.getValue(), id, 2, global);
		} else {
			if (addCompressedField)
				Util.decl(outS, outH, CustomDataTypes.ImageNoPalsSplit22CompField.getValue(), id, 2, global);
			else
				Util.decl(outS, outH, CustomDataTypes.ImageNoPalsSplit22.getValue(), id, 2, global);
		}
		// Tileset1 pointer
		outS.append("    dc.l    " + tileset1.id + "\n");
		// Tileset2 pointer
		outS.append("    dc.l    " + tileset2.id + "\n");
		// Tilemap1 pointer
		outS.append("    dc.l    " + tilemap1.id + "\n");
		// Tilemap2 pointer
		if (tilemap2 != null)
			outS.append("    dc.l    " + tilemap2.id + "\n");
		outS.append("\n");
    }
}
