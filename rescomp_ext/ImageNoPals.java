package sgdk.rescomp.resource;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import sgdk.rescomp.Resource;
import sgdk.rescomp.tool.TilesetStatsCollector;
import sgdk.rescomp.tool.Util;
import sgdk.rescomp.type.Basics.Compression;
import sgdk.rescomp.type.Basics.TileOptimization;
import sgdk.rescomp.type.Basics.TileOrdering;
import sgdk.rescomp.type.CompressionCustom;
import sgdk.rescomp.type.CustomDataTypes;
import sgdk.rescomp.type.ToggleMapTileBaseIndex;
import sgdk.tool.ImageUtil;
import sgdk.tool.ImageUtil.BasicImageInfo;

public class ImageNoPals extends Resource
{
    final int hc;

	public final TilesetOriginalCustom tileset;
	public final TilemapOriginalCustom tilemap;

    public ImageNoPals(String id, String imgFile, int mapExtendedWidth, Compression compression, TileOptimization tileOpt, int mapBase, 
    		CompressionCustom compressionCustomTileset, CompressionCustom compressionCustomTilemap, boolean addCompressionField,
    		String tilesCacheId, String tilesetStatsCollectorId) throws Exception
    {
        super(id);

        // get 8bpp pixels and also check image dimension is aligned to tile
        final byte[] finalImageData = ImageUtil.getImageAs8bpp(imgFile, true, true);
        checkImageNotNull(imgFile, finalImageData);
        checkImageColorByte(imgFile, finalImageData);

        // get info from first strip
        BasicImageInfo imgInfo = ImageUtil.getBasicInfo(imgFile);
        // width and height in pixels
        int w = imgInfo.w;
        // we determine 'h' from data length and 'w' as we can crop image vertically to remove palette data
        int h = finalImageData.length / w;
        // get size in tile
        int wt = w / 8;
        int ht = h / 8;

        final ToggleMapTileBaseIndex toggleMapTileBaseIndexFlag = ToggleMapTileBaseIndex.NONE;

        // build TILESET with wanted compression
        tileset = (TilesetOriginalCustom) addInternalResource(new TilesetOriginalCustom(id + "_tileset", finalImageData, w, h, 0, 0, wt, ht, tileOpt, 
        		compression, compressionCustomTileset, false, false, TileOrdering.ROW, tilesCacheId, addCompressionField));

        System.out.print(" " + id + " -> numTiles: " + tileset.getNumTile() + ". ");
        if (tilesetStatsCollectorId != null && !"".equals(tilesetStatsCollectorId)) {
	        TilesetStatsCollector.count1chunk(tilesetStatsCollectorId, tileset.getNumTile());
        }

        // build TILEMAP with wanted compression
        tilemap = (TilemapOriginalCustom) addInternalResource(TilemapOriginalCustom.getTilemap(id + "_tilemap", tileset, toggleMapTileBaseIndexFlag, 
        		mapBase, finalImageData, wt, ht, tileOpt, compression, compressionCustomTilemap, mapExtendedWidth, TileOrdering.ROW, tilesCacheId, 
        		addCompressionField));

        // compute hash code
        hc = tileset.hashCode() ^ tilemap.hashCode();
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
        if (obj instanceof ImageNoPals)
        {
            final ImageNoPals image = (ImageNoPals) obj;
            return tilemap.equals(image.tilemap) && tileset.equals(image.tileset);
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
        return 4 + 4;
    }

    @Override
    public int totalSize()
    {
        return shallowSize() + tileset.totalSize() + tilemap.totalSize();
    }

    @Override
    public void out(ByteArrayOutputStream outB, StringBuilder outS, StringBuilder outH)
    {
		// can't store pointer so we just reset binary stream here (used for compression only)
		outB.reset();

		// output Image structure
		if (tileset.addCompressionField == true || tilemap.addCompressionField == true)
			Util.decl(outS, outH, CustomDataTypes.ImageNoPalsCompField.getValue(), id, 2, global);
		else
			Util.decl(outS, outH, CustomDataTypes.ImageNoPals.getValue(), id, 2, global);
		// Tileset pointer
		outS.append("    dc.l    " + tileset.id + "\n");
		// Tilemap pointer
		outS.append("    dc.l    " + tilemap.id + "\n");
		outS.append("\n");
    }
}
