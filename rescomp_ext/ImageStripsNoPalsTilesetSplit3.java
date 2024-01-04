package sgdk.rescomp.resource;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import sgdk.rescomp.Resource;
import sgdk.rescomp.tool.ExtProperties;
import sgdk.rescomp.tool.Util;
import sgdk.rescomp.type.Basics.Compression;
import sgdk.rescomp.type.Basics.TileOptimization;
import sgdk.tool.ImageUtil;
import sgdk.tool.ImageUtil.BasicImageInfo;

public class ImageStripsNoPalsTilesetSplit3 extends Resource
{
    final int hc;

	public final Tileset tileset1, tileset2, tileset3;
	public final Tilemap tilemap1, tilemap2, tilemap3;

    public ImageStripsNoPalsTilesetSplit3(String id, List<String> stripsFileList, int toggleMapTileBaseIndexFlag, boolean extendedMapWidth64, 
    		Compression compression, TileOptimization tileOpt, int mapBase) throws Exception
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

        boolean tempTileset = true;
        Tileset tilesetTemp = new Tileset(id + "_tileset", finalImageData, w, h, 0, 0, wt, ht, tileOpt, compression, false, tempTileset);
        checkTilesetMaxSizeForSplitIn2(tilesetTemp.getNumTile());

        int ht_1 =  ht/2;
        int ht_2 = ht/2; // tileset2 height in tiles is calculated considering if ht is even or odd
        if ((ht % 2) == 1)
        	ht_2 += (ht - 2*(ht/2)); // add the reminder

        // Tileset size is smaller than chunk size limit? Then we only have one Tileset and one Tilemap
        int maxChunkSize = ExtProperties.getInt(ExtProperties.MAX_TILESET_CHUNK_SIZE_FOR_SPLIT_IN_2);
        if (tilesetTemp.getNumTile() <= maxChunkSize) {
        	tileset1 = (Tileset) addInternalResource(new Tileset(id + "_chunk1_tileset", finalImageData, w, h, 0, 0, wt, ht, tileOpt, compression, false, false));
        	tileset2 = null;
        	System.out.print(" " + id + " -> numTiles (chunk1 + chunk2):\t  " + tileset1.getNumTile() + " + 0 = " + tileset1.getNumTile() + ". ");
        }
        // Split the Image in two tilesets and two tilemaps
        else {
        	tileset1 = (Tileset) addInternalResource(new Tileset(id + "_chunk1_tileset", finalImageData, w, h, 0, 0, wt, ht_1, tileOpt, compression, false, false));
        	checkTilesetMaxChunkSize(tileset1.getNumTile());
        	tileset2 = (Tileset) addInternalResource(new Tileset(id + "_chunk2_tileset", finalImageData, w, h, 0, ht/2, wt, ht_2, tileOpt, compression, false, false));
        	checkTilesetMaxChunkSize(tileset2.getNumTile());
        	System.out.print(" " + id + " -> Tileset numTiles (chunk1 + chunk2):\t  " + tileset1.getNumTile() + " + " + tileset2.getNumTile() + " = " + 
        			(tileset1.getNumTile() + tileset2.getNumTile()) + ". ");
        }
        tileset3 = null;

        tilemap1 = (Tilemap) addInternalResource(TilemapCustom.getTilemap(id + "_chunk1_tilemap", tileset1, toggleMapTileBaseIndexFlag, 
        		mapBase, finalImageData, w, h, 0, 0, wt, ht_1, tileOpt, compression, extendedMapWidth64));
        Tileset tileset_t2 = tileset2 == null ? tileset1 : tileset2;
        int mapBaseOffset = tileset2 == null ? 0 : tileset1.getNumTile();
        tilemap2 = (Tilemap) addInternalResource(TilemapCustom.getTilemap(id + "_chunk2_tilemap", tileset_t2, toggleMapTileBaseIndexFlag, 
        		mapBase + mapBaseOffset, finalImageData, w, h, 0, ht/2, wt, ht_2, tileOpt, compression, extendedMapWidth64));
        tilemap3 = null;

        // compute hash code
        int hcTemp = tileset1.hashCode() ^ tilemap1.hashCode() ^ tilemap2.hashCode();
        if (tileset2 != null)
        	hcTemp ^= tileset2.hashCode();
        hc = hcTemp;
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
        byte[] image0 = ImageUtil.getImageAs8bpp(stripsFileList.get(0), true, true);
        int stripLength = image0.length;
        // allocate space for bigger image
        final byte[] finalImage = new byte[stripLength * stripsFileList.size()];

        // copy all the strips into finalImage
        for (int i = 0; i < stripsFileList.size(); ++i) {
        	String imgFile = stripsFileList.get(i);
        	byte[] image = ImageUtil.getImageAs8bpp(imgFile, true, true);
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
        return tilemap1.w * 8;
    }

    public int getHeight()
    {
    	return tilemap1.h * 8 + tilemap2.h * 8 + tilemap3.h * 8;
    }

    @Override
    public int internalHashCode()
    {
        return hc;
    }

    @Override
    public boolean internalEquals(Object obj)
    {
        if (obj instanceof ImageStripsNoPalsTilesetSplit3)
        {
        	final ImageStripsNoPalsTilesetSplit3 image = (ImageStripsNoPalsTilesetSplit3) obj;

            // Check for nulls and then compare the non-null fields
            boolean tileset2Equals = (tileset2 == null && image.tileset2 == null) ||
                    (tileset2 != null && tileset2.equals(image.tileset2));
            // Check for nulls and then compare the non-null fields
            boolean tileset3Equals = (tileset3 == null && image.tileset3 == null) ||
                    (tileset3 != null && tileset3.equals(image.tileset3));

            return tileset2Equals && tileset3Equals && tileset1.equals(image.tileset1)
            		&& tilemap1.equals(image.tilemap1) && tilemap2.equals(image.tilemap2);
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
        return 4 + 4 + 4 + 4 + 4 + 4;
    }

    @Override
    public int totalSize()
    {
    	if (tileset2 == null && tileset3 == null) {
    		return shallowSize() + tileset1.totalSize() + tilemap1.totalSize() + tilemap2.totalSize() + tilemap3.totalSize();
    	} else if (tileset2 != null && tileset3 == null) {
    		return shallowSize() + tileset1.totalSize() + tileset2.totalSize() + tilemap1.totalSize() + tilemap2.totalSize() + tilemap3.totalSize();
    	} else {
    		return shallowSize() + tileset1.totalSize() + tileset2.totalSize() + tileset3.totalSize() + tilemap1.totalSize() + tilemap2.totalSize() + tilemap3.totalSize();
    	}
    }

    @Override
    public void out(ByteArrayOutputStream outB, StringBuilder outS, StringBuilder outH)
    {
		// can't store pointer so we just reset binary stream here (used for compression only)
		outB.reset();

		// output Image structure
		Util.decl(outS, outH, "ImageNoPalsTilesetSplit2", id, 2, global);
		// Tileset1 pointer
		outS.append("    dc.l    " + tileset1.id + "\n");
		// Tileset2 pointer
		String t2_id = tileset2 != null ? tileset2.id : "0";
		outS.append("    dc.l    " + t2_id + "\n");
		// Tileset3 pointer
		String t3_id = tileset3 != null ? tileset3.id : "0";
		// Tilemap1 pointer
		outS.append("    dc.l    " + tilemap1.id + "\n");
		// Tilemap2 pointer
		outS.append("    dc.l    " + tilemap2.id + "\n");
		// Tilemap3 pointer
		outS.append("    dc.l    " + tilemap3.id + "\n");
		outS.append("\n");
    }
}
