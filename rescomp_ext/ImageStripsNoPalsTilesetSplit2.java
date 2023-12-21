package sgdk.rescomp.resource;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import sgdk.rescomp.Resource;
import sgdk.rescomp.tool.Util;
import sgdk.rescomp.type.Basics.Compression;
import sgdk.rescomp.type.Basics.TileOptimization;
import sgdk.tool.ImageUtil;
import sgdk.tool.ImageUtil.BasicImageInfo;

public class ImageStripsNoPalsTilesetSplit2 extends Resource
{
	private static final int MAX_TILESET_CHUNK_SIZE = 384;
	private static final int MAX_TILESET_SIZE_ALLOWED_FOR_SPLIT_IN_2 = MAX_TILESET_CHUNK_SIZE * 2;

    final int hc;

	public final Tileset tileset;
	private final Tileset tileset1, tileset2;
	public final Tilemap tilemap;
//	public final Palette palette;

    public ImageStripsNoPalsTilesetSplit2(String id, List<String> stripsFileList, boolean extendedMapWidth64, Compression compression, 
    		TileOptimization tileOpt, int mapBase) throws Exception
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

        // build TILESET with wanted compression
        boolean tempTileset = true;
        tileset = new Tileset(id + "_tileset", finalImageData, w, h, 0, 0, wt, ht, tileOpt, compression, false, tempTileset);
        
        // build TILESET 1 and TILESET 2 accordingly
        checkTilesetMaxSizeForSplitIn2(tileset.getNumTile());
        if (tileset.getNumTile() <= MAX_TILESET_CHUNK_SIZE) {
        	tileset1 = (Tileset) addInternalResource(new Tileset(id + "_chunk1_tileset", finalImageData, w, h, 0, 0, wt, ht, tileOpt, compression, false, false));
        	tileset2 = null;
        	System.out.print(" " + id + " -> Tileset numTiles (chunk1 + chunk2): " + tileset1.getNumTile() + " + 0 = " + tileset1.getNumTile() + ". ");
        }
        else {
        	int t1_ht =  ht/2;
        	tileset1 = (Tileset) addInternalResource(new Tileset(id + "_chunk1_tileset", finalImageData, w, h, 0, 0, wt, t1_ht, tileOpt, compression, false, false));
        	checkTilesetMaxChunkSize(tileset1.getNumTile());
        	// tileset2 height in tiles is calculated considering if ht is even or odd
            int t2_ht = ht/2;
            if ((ht % 2) == 1)
            	t2_ht += (ht - 2*(ht/2)); // add the reminder
        	tileset2 = (Tileset) addInternalResource(new Tileset(id + "_chunk2_tileset", finalImageData, w, h, 0, ht/2, wt, t2_ht, tileOpt, compression, false, false));
        	checkTilesetMaxChunkSize(tileset2.getNumTile());
        	System.out.print(" " + id + " -> Tileset numTiles (chunk1 + chunk2): " + tileset1.getNumTile() + " + " + tileset2.getNumTile() + " = " + 
        			(tileset1.getNumTile() + tileset2.getNumTile()) + ". ");
        }
        
        // build TILEMAP with wanted compression
        tilemap = (Tilemap) addInternalResource(TilemapCustom.getTilemap(id + "_tilemap", tileset, mapBase, finalImageData, wt, ht, tileOpt, compression, extendedMapWidth64));
        // build PALETTE
        //palette = (Palette) addInternalResource(new Palette(id + "_palette", stripsFileList.get(0), 1, false));

        // compute hash code
        hc = tileset.hashCode() ^ tilemap.hashCode();// ^ palette.hashCode();
    }

    private void checkTilesetMaxSizeForSplitIn2(int numTile) {
		if (numTile > MAX_TILESET_SIZE_ALLOWED_FOR_SPLIT_IN_2) {
			throw new RuntimeException("Can't split in 2 tileset because size is greater than " + MAX_TILESET_SIZE_ALLOWED_FOR_SPLIT_IN_2);
		}
	
	}

    private void checkTilesetMaxChunkSize(int numTile) {
		if (numTile > MAX_TILESET_CHUNK_SIZE) {
			throw new RuntimeException("Max tilset chunk size is greater then " + MAX_TILESET_CHUNK_SIZE);
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
        return tilemap.w * 8;
    }

    public int getHeight()
    {
        return tilemap.h * 8;
    }

    @Override
    public int internalHashCode()
    {
        return hc;
    }

    @Override
    public boolean internalEquals(Object obj)
    {
        if (obj instanceof ImageStripsNoPalsTilesetSplit2)
        {
            final ImageStripsNoPalsTilesetSplit2 image = (ImageStripsNoPalsTilesetSplit2) obj;
            return tilemap.equals(image.tilemap) && tileset.equals(image.tileset);// && palette.equals(image.palette);
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
        return 4 + 4 + 4;// + 4;
    }

    @Override
    public int totalSize()
    {
    	if (tileset2 == null)
    		return shallowSize() + tileset1.totalSize() + tilemap.totalSize();// + palette.totalSize();
    	else
    		return shallowSize() + tileset1.totalSize() + tileset2.totalSize() + tilemap.totalSize();// + palette.totalSize();
    }

    @Override
    public void out(ByteArrayOutputStream outB, StringBuilder outS, StringBuilder outH)
    {
		// can't store pointer so we just reset binary stream here (used for compression only)
		outB.reset();

		// output Image structure
		Util.decl(outS, outH, "ImageNoPalsTilesetSplit2", id, 2, global);
		// Palette pointer
		//outS.append("    dc.l    " + palette.id + "\n"); // 0 instead of palette.id to set it as null
		// Tileset1 pointer
		outS.append("    dc.l    " + tileset1.id + "\n");
		// Tileset2 pointer
		String t2_id = tileset2 != null ? tileset2.id : "0";
		outS.append("    dc.l    " + t2_id + "\n");
		// Tilemap pointer
		outS.append("    dc.l    " + tilemap.id + "\n");
		outS.append("\n");
    }
}
