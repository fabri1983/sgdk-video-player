package sgdk.rescomp.resource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import sgdk.rescomp.Resource;
import sgdk.rescomp.tool.ExtProperties;
import sgdk.rescomp.tool.TilemapCustomTools;
import sgdk.rescomp.tool.TilesCacheManager;
import sgdk.rescomp.tool.Util;
import sgdk.rescomp.type.Basics.Compression;
import sgdk.rescomp.type.Basics.TileEquality;
import sgdk.rescomp.type.Basics.TileOptimization;
import sgdk.rescomp.type.CompressionCustom;
import sgdk.rescomp.type.CustomDataTypes;
import sgdk.rescomp.type.Tile;
import sgdk.rescomp.type.TileCacheMatch;
import sgdk.rescomp.type.TilemapCreationData;
import sgdk.rescomp.type.ToggleMapTileBaseIndex;
import sgdk.tool.ArrayUtil;

public class TilemapCustom extends Resource
{
	private static TilemapCreationData createTilemap(String id, List<TilesetOriginalCustom> tilesets, int[] offsetForTilesets,
			ToggleMapTileBaseIndex toggleMapTileBaseIndexFlag, int mapBase, byte[] image8bpp, int imageWidth, int imageHeight,
			int startTileX, int startTileY, int widthTile, int heightTile, TileOptimization opt, Compression compression, 
			int mapExtendedWidth, String tilesCacheId) {
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
        	frameNum = TilemapCustomTools.getFrameNum(group1);
        	if (frameNum == null) {
        		System.out.println(" ##### frameNum SHOULDN'T BE NULL HERE. TilemapCustom class");
        	}
        }

        // fabri1983: only print message at first frame and only for chunk1
        if (id.contains("_chunk1"))
        	TilemapCustomTools.printMessageForParamToggleMapTileBaseIndexFlag(toggleMapTileBaseIndexFlag, frameNum, id);

    	// fabri1983: here we calculate videoFrameBufferOffsetIndex according frameNum
    	int videoFrameBufferOffsetIndex = 0;
    	if (toggleMapTileBaseIndexFlag != ToggleMapTileBaseIndex.NONE) {
    		int tileIndexA = ExtProperties.getInt(ExtProperties.STARTING_TILESET_ON_SGDK);
    		int tileIndexB = tileIndexA + ExtProperties.getInt(ExtProperties.MAX_TILESET_NUM_FOR_MAP_BASE_TILE_INDEX);
    		videoFrameBufferOffsetIndex = TilemapCustomTools.calculateVideoFrameBufferOffsetIndex(
    				toggleMapTileBaseIndexFlag, frameNum, tileIndexA, tileIndexB);
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
                    	TileCacheMatch match = TilesCacheManager.getCachedTile(tilesCacheId, tile);
                    	
                    	// we found the cached tile
                    	if (match != null) {
                    		Tile existentTile = match.getTile();
	                        equality = tile.getEquality(existentTile);
	                        index = match.getIndexInCache();
                    	}
                    	// no cached tile
                    	else {
	                    	int tilesetsListIdx = TilemapCustomTools.getTilesetIndexFor(tile, opt, tilesets);
	                    	TilesetOriginalCustom tileset = tilesets.get(tilesetsListIdx);
	
	                        // otherwise we try to get tile index in the tileset
	                        index = tileset.getTileIndex(tile, opt);
	                        // not found ? (should never happen)
	                        if (index == -1)
	                            throw new RuntimeException("Can't find tile [" + ti + "," + tj + "] in tileset, something wrong happened...");
	
	                        Tile existentTile = tileset.get(index);
	                        // get equality info
	                        equality = tile.getEquality(existentTile);
	                        // can add base index now
	                        index += mapBaseTileInd + offsetForTilesets[tilesetsListIdx];
                    	}
                    }
                }

                // set tilemap
                data[offset++] = (short) Tile.TILE_ATTR_FULL(mapBasePal + tile.pal, mapBasePrio | tile.prio, equality.vflip, equality.hflip, index);
            }
        }

        if (mapExtendedWidth != 0) {
        	data = TilemapCustomTools.convertDataToNTilesWidth(data, w, h, mapExtendedWidth);
        	w = mapExtendedWidth;
        }

        return new TilemapCreationData(id, data, w, h, compression);
	}

	public static TilemapCustom getTilemap(String id, List<TilesetOriginalCustom> tilesets, int[] offsetForTilesets, ToggleMapTileBaseIndex toggleMapTileBaseIndexFlag, 
			int mapBase, byte[] image8bpp, int imageWidth, int imageHeight, int startTileX, int startTileY, int widthTile, int heightTile, 
			TileOptimization opt, Compression compression, CompressionCustom compressionCustom, int mapExtendedWidth, String tilesCacheId, boolean addCompressionField)
	{
		TilemapCreationData tmData = createTilemap(id, tilesets, offsetForTilesets, toggleMapTileBaseIndexFlag, mapBase, image8bpp,
				imageWidth, imageHeight, startTileX, startTileY, widthTile, heightTile, opt, compression, mapExtendedWidth, tilesCacheId);
		return new TilemapCustom(tmData.id, tmData.data, tmData.w, tmData.h, tmData.compression, compressionCustom, addCompressionField);
	}

	public static TilemapCustom getTilemap(String id, TilesetOriginalCustom tileset, ToggleMapTileBaseIndex toggleMapTileBaseIndexFlag, int mapBase, 
			byte[] image8bpp, int widthTile, int heightTile, TileOptimization opt, Compression compression, CompressionCustom compressionCustom, 
			int mapExtendedWidth, String tilesCacheId, boolean addCompressionField)
    {
		List<TilesetOriginalCustom> tilesets = Arrays.asList(tileset);
		TilemapCreationData tmData = createTilemap(id, tilesets, new int[]{0}, toggleMapTileBaseIndexFlag, mapBase, image8bpp, widthTile * 8, 
				heightTile * 8, 0, 0, widthTile, heightTile, opt, compression, mapExtendedWidth, tilesCacheId);
		return new TilemapCustom(tmData.id, tmData.data, tmData.w, tmData.h, tmData.compression, compressionCustom, addCompressionField);
    }

    public final int w;
    public final int h;
    final int hc;
    public final boolean addCompressionField;

    // binary data for tilemap
    public final BinCustom bin;

	public TilemapCustom(String id, short[] data, int w, int h, Compression compression, CompressionCustom compressionCustom, 
			boolean addCompressionField) {
		super(id);

        this.w = w;
        this.h = h;
        this.addCompressionField = addCompressionField;

        // build BIN (tilemap data) with wanted compression
        if (CompressionCustom.isOneOfSgdkCompression(compressionCustom)) {
        	compression = CompressionCustom.getSgdkCompression(compressionCustom);
        	compressionCustom = CompressionCustom.NONE;
        }
        final BinCustom binResource = new BinCustom(id + "_data", data, compression, compressionCustom);

        // add as resource (avoid duplicate)
        bin = (BinCustom) addInternalResource(binResource);

        // compute hash code
        hc = bin.hashCode() ^ (w << 8) ^ (h << 16);
	}

	public short[] getData()
    {
        return ArrayUtil.byteToShort(bin.data);
    }

    public void setData(short[] data)
    {
        if (data.length != (w * h))
            throw new RuntimeException("TilemapCustom.setData(..): size do not match !");

        ArrayUtil.shortToByte(data, 0, bin.data, 0, bin.data.length, false);
    }

    @Override
    public int internalHashCode()
    {
        return hc;

    }

    @Override
    public boolean internalEquals(Object obj)
    {
        if (obj instanceof TilemapCustom)
        {
            final TilemapCustom tilemap = (TilemapCustom) obj;
            return (w == tilemap.w) && (h == tilemap.h) && bin.equals(tilemap.bin);
        }

        return false;
    }

    @Override
    public List<Bin> getInternalBinResources()
    {
        return Arrays.asList(bin);
    }

    @Override
    public int shallowSize()
    {
    	if (addCompressionField)
    		return 2 + 4;
    	else
    		return 4;
    }

    @Override
    public int totalSize()
    {
        return bin.totalSize() + shallowSize();
    }

    @Override
    public void out(ByteArrayOutputStream outB, StringBuilder outS, StringBuilder outH) throws IOException
    {
        // can't store pointer so we just reset binary stream here (used for compression only)
        outB.reset();

        // output TileMap structure
        if (addCompressionField)
        	Util.decl(outS, outH, CustomDataTypes.TileMapCustomCompField.getValue(), id, 2, global);
		else
			Util.decl(outS, outH, CustomDataTypes.TileMapCustom.getValue(), id, 2, global);
        // set compression info (very important that binary data had already been exported at this point)
        if (addCompressionField) {
	        int compOrdinal = 0;
	        if (bin.doneCompression != Compression.NONE)
	        	compOrdinal = bin.doneCompression.ordinal() - 1;
	        else if (bin.doneCompressionCustom != CompressionCustom.NONE)
	        	compOrdinal = bin.doneCompressionCustom.getDefineValue();
			outS.append("    dc.w    " + compOrdinal + "\n");
        }
        // set size in tile
//        outS.append("    dc.w    " + w + ", " + h + "\n");
        // set data pointer
        outS.append("    dc.l    " + bin.id + "\n");
        outS.append("\n");
    }

}