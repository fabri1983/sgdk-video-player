package sgdk.rescomp.resource;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import sgdk.rescomp.Resource;
import sgdk.rescomp.tool.ExtProperties;
import sgdk.rescomp.tool.Util;
import sgdk.rescomp.type.CompressionCustom;
import sgdk.rescomp.type.Basics.Compression;
import sgdk.rescomp.type.Basics.TileOptimization;
import sgdk.tool.ImageUtil;
import sgdk.tool.ImageUtil.BasicImageInfo;

public class ImageStripsNoPalsTilesetSplit3 extends Resource
{
    final int hc;

	public final Tileset tileset1, tileset2, tileset3;
	public final TilemapCustom tilemap1, tilemap2, tilemap3;

    public ImageStripsNoPalsTilesetSplit3(String id, List<String> stripsFileList, int toggleMapTileBaseIndexFlag, boolean extendedMapWidth64, 
    		Compression compression, TileOptimization tileOpt, int mapBase, CompressionCustom compressionCustom) throws Exception
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
        Tileset tilesetTemp = new Tileset(id + "_tileset", finalImageData, w, h, 0, 0, wt, ht, tileOpt, compression, false, isTempTileset);
        checkTilesetMaxSizeForSplitIn2(tilesetTemp.getNumTile());

        int ht_1 = ht/3;
        int ht_2 = ht/3;
        int ht_3 = ht/3 + (ht % 3); // tileset3 height in tiles is calculated considering the reminder

    	tileset1 = (Tileset) addInternalResource(new Tileset(id + "_chunk1_tileset", finalImageData, w, h, 0, 0, wt, ht_1, tileOpt, compression, false, false));
    	checkTilesetMaxChunkSize(tileset1.getNumTile());
    	tileset2 = (Tileset) addInternalResource(new Tileset(id + "_chunk2_tileset", finalImageData, w, h, 0, ht_1, wt, ht_2, tileOpt, compression, false, false));
    	checkTilesetMaxChunkSize(tileset2.getNumTile());
    	tileset3 = (Tileset) addInternalResource(new Tileset(id + "_chunk3_tileset", finalImageData, w, h, 0, ht_1 + ht_2, wt, ht_3, tileOpt, compression, false, false));
    	checkTilesetMaxChunkSize(tileset3.getNumTile());
    	System.out.print(" " + id + " -> numTiles (chunk1 + chunk2 + chunk3):\t  " + tileset1.getNumTile() + " + " + tileset2.getNumTile() + " + " + tileset3.getNumTile() + " = " + 
    			(tileset1.getNumTile() + tileset2.getNumTile() + tileset3.getNumTile()) + ". ");

        int[] offsetForTilesets = {0, tileset1.getNumTile(), tileset1.getNumTile() + tileset2.getNumTile()};

        List<Tileset> tilesetsList_t1 = Arrays.asList(tileset1);
        tilemap1 = (TilemapCustom) addInternalResource(TilemapCustom.getTilemap(id + "_chunk1_tilemap", tilesetsList_t1, offsetForTilesets, 
        		toggleMapTileBaseIndexFlag, mapBase, finalImageData, w, h, 0, 0, wt, ht_1, tileOpt, compression, compressionCustom, extendedMapWidth64));

		List<Tileset> tilesetsList_t2 = Arrays.asList(tileset1, tileset2);
        tilemap2 = (TilemapCustom) addInternalResource(TilemapCustom.getTilemap(id + "_chunk2_tilemap", tilesetsList_t2, offsetForTilesets, 
        		toggleMapTileBaseIndexFlag, mapBase, finalImageData, w, h, 0, ht_1, wt, ht_2, tileOpt, compression, compressionCustom, extendedMapWidth64));

        List<Tileset> tilesetsList_t3 = Arrays.asList(tileset1, tileset2, tileset3);
        tilemap3 = (TilemapCustom) addInternalResource(TilemapCustom.getTilemap(id + "_chunk3_tilemap", tilesetsList_t3, offsetForTilesets, 
        		toggleMapTileBaseIndexFlag, mapBase, finalImageData, w, h, 0, ht_1 + ht_2, wt, ht_3, tileOpt, compression, compressionCustom, extendedMapWidth64));

        // compute hash code
        int hcTemp = tileset1.hashCode() ^ tileset2.hashCode() ^tileset3.hashCode() ^ tilemap1.hashCode() ^ tilemap2.hashCode();
        hc = hcTemp;
    }

    private void checkTilesetMaxSizeForSplitIn2(int numTile) {
    	int max = ExtProperties.getInt(ExtProperties.MAX_TILESET_TOTAL_SIZE_FOR_SPLIT_IN_3);
		if (numTile > max) {
			throw new RuntimeException("Can't split in 2 tileset because size " + numTile + " > " + max
					+ " (" + ExtProperties.MAX_TILESET_TOTAL_SIZE_FOR_SPLIT_IN_3 + ")");
		}
	
	}

    private void checkTilesetMaxChunkSize(int numTile) {
    	int max = ExtProperties.getInt(ExtProperties.MAX_TILESET_CHUNK_SIZE_FOR_SPLIT_IN_3);
		if (numTile > max) {
			throw new RuntimeException("numTile " + numTile + " chunk size is greater than max allowed " + max
					+ " (" + ExtProperties.MAX_TILESET_CHUNK_SIZE_FOR_SPLIT_IN_3 + ")");
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
            return tileset1.equals(image.tileset1) && tileset2.equals(image.tileset2) && tileset3.equals(image.tileset3)
            		&& tilemap1.equals(image.tilemap1) && tilemap2.equals(image.tilemap2) && tilemap3.equals(image.tilemap3);
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
    	return shallowSize() + tileset1.totalSize() + tileset2.totalSize() + tileset3.totalSize() 
    			+ tilemap1.totalSize() + tilemap2.totalSize() + tilemap3.totalSize();
    }

    @Override
    public void out(ByteArrayOutputStream outB, StringBuilder outS, StringBuilder outH)
    {
		// can't store pointer so we just reset binary stream here (used for compression only)
		outB.reset();

		// output Image structure
		Util.decl(outS, outH, "ImageNoPalsTilesetSplit3", id, 2, global);
		// Tileset1 pointer
		outS.append("    dc.l    " + tileset1.id + "\n");
		// Tileset2 pointer
		outS.append("    dc.l    " + tileset2.id + "\n");
		// Tileset3 pointer
		outS.append("    dc.l    " + tileset3.id + "\n");
		// Tilemap1 pointer
		outS.append("    dc.l    " + tilemap1.id + "\n");
		// Tilemap2 pointer
		outS.append("    dc.l    " + tilemap2.id + "\n");
		// Tilemap3 pointer
		outS.append("    dc.l    " + tilemap3.id + "\n");
		outS.append("\n");
    }
}
