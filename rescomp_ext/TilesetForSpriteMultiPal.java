package sgdk.rescomp.resource;

import java.awt.Rectangle;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import sgdk.rescomp.Resource;
import sgdk.rescomp.tool.Util;
import sgdk.rescomp.type.Basics.Compression;
import sgdk.rescomp.type.Basics.TileEquality;
import sgdk.rescomp.type.Basics.TileOptimization;
import sgdk.rescomp.type.Tile;
import sgdk.tool.ImageUtil;
import sgdk.tool.ImageUtil.BasicImageInfo;

public class TilesetForSpriteMultiPal extends Resource
{
    public static TilesetForSpriteMultiPal getTileset(String id, String imgFile, Compression compression, TileOptimization tileOpt, boolean addBlank, boolean temp) throws Exception
    {
        // get 8bpp pixels and also check image dimension is aligned to tile
        final byte[] image = ImageUtil.getImageAs8bpp(imgFile, true, true);

        // happen when we couldn't retrieve palette data from RGB image
        if (image == null)
            throw new IllegalArgumentException(
                    "RGB image '" + imgFile + "' does not contains palette data (see 'Important note about image format' in the rescomp.txt file");

        // retrieve basic infos about the image
        final BasicImageInfo imgInfo = ImageUtil.getBasicInfo(imgFile);
        final int w = imgInfo.w;
        // we determine 'h' from data length and 'w' as we can crop image vertically to remove palette data
        final int h = image.length / w;

        return new TilesetForSpriteMultiPal(id, image, w, h, 0, 0, w / 8, h / 8, tileOpt, compression, addBlank, temp);
    }

    // tiles
    final public List<Tile> tiles;
    final int hc;

    // binary data block (tiles)
    public final Bin bin;

    // internals
    final boolean isDuplicate;
    final private java.util.Map<Tile, Integer> tileIndexesMap;
    final private java.util.Map<Integer, List<Tile>> tileByHashcodeMap;

    // special constructor for TSX (can have several tilesets for a single map)
    public TilesetForSpriteMultiPal(List<TilesetForSpriteMultiPal> tilesets)
    {
        super("tilesets");

        tiles = new ArrayList<>();
        tileIndexesMap = new HashMap<>();
        tileByHashcodeMap = new HashMap<>();
        isDuplicate = false;

        // !! don't optimize tilesets (important to preserve tile indexes here) !!
        for (TilesetForSpriteMultiPal tileset : tilesets)
            for (Tile tile : tileset.tiles)
                add(tile);

        // build the binary bloc
        final int[] data = new int[tiles.size() * 8];

        int offset = 0;
        for (Tile t : tiles)
        {
            System.arraycopy(t.data, 0, data, offset, 8);
            offset += 8;
        }

        // build BIN (tiles data) - no stored as this is a temporary tileset
        bin = new Bin(id + "_data", data, Compression.NONE);

        // compute hash code
        hc = bin.hashCode();
    }

    // special constructor for empty tileset
    public TilesetForSpriteMultiPal()
    {
        super("empty_tileset");

        tiles = new ArrayList<>();
        tileIndexesMap = new HashMap<>();
        tileByHashcodeMap = new HashMap<>();
        isDuplicate = false;

        // dummy bin
        bin = new Bin("empty_bin", new byte[0], Compression.NONE);
        // hash code
        hc = bin.hashCode();
    }

    // special constructor for TSX (single blank tile tileset)
    public TilesetForSpriteMultiPal(String id, boolean blankTile)
    {
        super(id);

        tiles = new ArrayList<>();
        tileIndexesMap = new HashMap<>();
        tileByHashcodeMap = new HashMap<>();
        isDuplicate = false;

        final int[] data;
        
        if (blankTile)
        {
            // just add a blank tile
            add(new Tile(new int[8], 8, 0, false, 0));
    
            // build the binary bloc
            data = new int[tiles.size() * 8];
    
            int offset = 0;
            for (Tile t : tiles)
            {
                System.arraycopy(t.data, 0, data, offset, 8);
                offset += 8;
            }
        }
        else data = new int[0];

        // build BIN (tiles data) resource (temporary tileset so don't add as internal resource)
        bin = new Bin(id + "_data", data, Compression.NONE);

        // compute hash code
        hc = bin.hashCode();
    }

    public TilesetForSpriteMultiPal(String id, byte[] image8bpp, int imageWidth, int imageHeight, int startTileX, int startTileY, int widthTile, int heightTile,
            TileOptimization opt, Compression compression, boolean addBlank, boolean temp)
    {
        super(id);

        boolean hasBlank = false;

        tiles = new ArrayList<>();
        tileIndexesMap = new HashMap<>();
        tileByHashcodeMap = new HashMap<>();

        // important to always use the same loop order when building Tileset and Tilemap/Map object
        for (int j = 0; j < heightTile; j++)
        {
            for (int i = 0; i < widthTile; i++)
            {
                // get tile
                final Tile tile = Tile.getTile(image8bpp, imageWidth, imageHeight, (i + startTileX) * 8, (j + startTileY) * 8, 8);
                // find if tile already exist
                final int index = getTileIndex(tile, opt);

                // blank tile
                hasBlank |= tile.isBlank();

                // not found --> add it
                if (index == -1)
                	add(tile);
            }
        }

        // add a blank tile if not already present
        if (!hasBlank && addBlank)
            add(new Tile(new int[8], 8, 0, false, 0));

        // build the binary bloc
        final int[] data = new int[tiles.size() * 8];

        int offset = 0;
        for (Tile t : tiles)
        {
            System.arraycopy(t.data, 0, data, offset, 8);
            offset += 8;
        }

        // build BIN (tiles data) with wanted compression
        final Bin binResource = new Bin(id + "_data", data, compression);
        // internal
        binResource.global = false;

        // temporary tileset --> don't store the bin data
        if (temp)
        {
            isDuplicate = false;
            bin = binResource;
        }
        else
        {
            // keep track of duplicate bin resource here
            isDuplicate = findResource(binResource) != null;
            // add as resource (avoid duplicate)
            bin = (Bin) addInternalResource(binResource);
        }

        // compute hash code
        hc = bin.hashCode();
    }

	public TilesetForSpriteMultiPal(String id, byte[] image8bpp, int imageWidth, int imageHeight, List<? extends Rectangle> sprites, Compression compression, boolean temp)
    {
        super(id);

        tiles = new ArrayList<>();
        tileIndexesMap = new HashMap<>();
        tileByHashcodeMap = new HashMap<>();

        for (Rectangle rect : sprites)
        {
            // get width and height
            final int widthTile = rect.width / 8;
            final int heightTile = rect.height / 8;

            // important to respect sprite tile ordering (vertical)
            for (int i = 0; i < widthTile; i++)
                for (int j = 0; j < heightTile; j++) {
                    Tile tile = getTileMultiPal(image8bpp, imageWidth, imageHeight, rect.x + (i * 8), rect.y + (j * 8), 8);
                	add(tile);
                }
        }

        // build the binary bloc
        final int[] data = new int[tiles.size() * 8];

        int offset = 0;
        for (Tile t : tiles)
        {
            System.arraycopy(t.data, 0, data, offset, 8);
            offset += 8;
        }

        // build BIN (tiles data) with wanted compression
        final Bin binResource = new Bin(id + "_data", data, compression);
        // internal
        binResource.global = false;

        // temporary tileset --> don't store the bin data
        if (temp)
        {
            isDuplicate = false;
            bin = binResource;
        }
        else
        {
            // keep track of duplicate bin resource here
            isDuplicate = findResource(binResource) != null;
            // add as resource (avoid duplicate)
            bin = (Bin) addInternalResource(binResource);
        }

        // compute hash code
        hc = bin.hashCode();
    }

    public int getNumTile()
    {
        return tiles.size();
    }

    public boolean isEmpty()
    {
        return getNumTile() == 0;
    }

    public Tile get(int index)
    {
        return tiles.get(index);
    }

    public void add(Tile tile)
    {
        // need to be called first
        addInternal(tile);
        tiles.add(tile);
    }

    private void addInternal(Tile tile)
    {
        // better to keep first index if duplicated (should not be really useful)..
        // if (!tileIndexesMap.containsKey(tile))
        tileIndexesMap.put(tile, Integer.valueOf(tiles.size()));

        final Integer hashKey = Integer.valueOf(tile.hashCode());
        List<Tile> hashTiles = tileByHashcodeMap.get(hashKey);

        if (hashTiles == null)
        {
            hashTiles = new ArrayList<>();
            tileByHashcodeMap.put(hashKey, hashTiles);
        }

        hashTiles.add(tile);
    }

    public int getTileIndex(Tile tile, TileOptimization opt)
    {
        // no optimization allowed --> need to duplicate tile
        if (opt == TileOptimization.NONE)
            return -1;

        // fast perfect match test (preferred choice if possible)
        final Integer key = tileIndexesMap.get(tile);
        // found ? --> return index
        if (key != null)
            return key.intValue();

        // allow flip ?
        if (opt == TileOptimization.ALL)
        {
            // get all tiles with same hash code
            final List<Tile> hashTiles = tileByHashcodeMap.get(Integer.valueOf(tile.hashCode()));

            // have some ?
            if (hashTiles != null)
            {
                for (Tile t : hashTiles)
                {
                    // flipped version ?
                    if (t.getFlipEquality(tile) != TileEquality.NONE)
                        // return index of the original tile
                        return tileIndexesMap.get(t).intValue();
                }
            }
        }

        // // always do a first pass for direct matching (preferred choice if possible)
        // for (int ind = 0; ind < tiles.size(); ind++)
        // if (tiles.get(ind).equals(tile))
        // return ind;
        //
        // // allow flip ?
        // if (opt == TileOptimization.ALL)
        // {
        // for (int ind = 0; ind < tiles.size(); ind++)
        // // found a flip equality ?
        // if (tiles.get(ind).getFlipEquality(tile) != TileEquality.NONE)
        // return ind;
        // }

        // not found
        return -1;
    }

    @Override
    public int internalHashCode()
    {
        return hc;
    }

    @Override
    public boolean internalEquals(Object obj)
    {
        if (obj instanceof TilesetForSpriteMultiPal)
        {
            final TilesetForSpriteMultiPal tileset = (TilesetForSpriteMultiPal) obj;
            return bin.equals(tileset.bin);
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
        return 2 + 2 + 4;
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

        // output TileSet structure
        Util.decl(outS, outH, "TileSet", id, 2, global);
        // set compression info (very important that binary data had already been exported at this point)
        outS.append("    dc.w    " + (bin.doneCompression.ordinal() - 1) + "\n");
        // set number of tile
        outS.append("    dc.w    " + getNumTile() + "\n");
        // set data pointer
        outS.append("    dc.l    " + bin.id + "\n");
        outS.append("\n");
    }

    /**
     * @param x
     *        X position in pixel
     * @param y
     *        Y position in pixel
     */
    private static Tile getTileMultiPal(byte[] image8bpp, int imgW, int imgH, int x, int y, int size)
    {
        final byte[] imageTile = Tile.getImageTile(image8bpp, imgW, imgH, x, y, size);
        final byte[] data = new byte[size * size];

        int plainCol = -1;
        boolean plain = true;

        int pal = -1;
        int prio = -1;
        int transPal = -1;
        int transPrio = -1;

        int off = 0;
        for (int j = 0; j < size; j++)
        {
            for (int i = 0; i < size; i++)
            {
                final int pixel = imageTile[off];
                final int color = pixel & 0xF;

                // first pixel --> affect color
                if (plainCol == -1)
                    plainCol = color;
                // not the same color --> not a plain tile
                else if (plainCol != color)
                    plain = false;

                final int curPal = (pixel >> 4) & 3;
                final int curPrio = (pixel >> 7) & 1;

                // transparent pixel ?
                if (color == 0)
                {
                    // set palette
                    if (transPal == -1)
                        transPal = curPal;
                    // test for difference with previous palette from transparent pixels
                    // fabri1983: commented out to allow multiple palettes
//                    else if (transPal != curPal)
//                        throw new IllegalArgumentException("Error: transparent pixel at [" + (x + i) + "," + (y + j) + "] reference a different palette ("
//                                + curPal + " != " + transPal + ").");
                    // fabri1983: set the transPal for this tile as curPal 
                    else
                    	transPal = curPal;

                    // set prio
                    if (transPrio == -1)
                        transPrio = curPrio;
                    // test for difference with previous priority from transparent pixels
                    else if (transPrio != curPrio)
                        throw new IllegalArgumentException("Error: transparent pixel at [" + (x + i) + "," + (y + j) + "] reference a different priority ("
                                + curPrio + " != " + transPrio + ").");
                }
                // opaque pixel
                else
                {
                    // set palette
                    if (pal == -1)
                        pal = curPal;
                    // fabri1983: commented out to allow multiple palettes
                    // test for difference with previous palette from opaque pixels
//                    else if (pal != curPal)
//                        throw new IllegalArgumentException(
//                                "Error: pixel at [" + (x + i) + "," + (y + j) + "] reference a different palette (" + curPal + " != " + pal + ").");
                    // fabri1983: set the pal for this tile as curPal 
                    else
                    	pal = curPal;

                    // set prio
                    if (prio == -1)
                        prio = curPrio;
                    // test for difference with previous priority from opaque pixels
                    else if (prio != curPrio)
                        throw new IllegalArgumentException(
                                "Error: pixel at [" + (x + i) + "," + (y + j) + "] reference a different priority (" + curPrio + " != " + prio + ").");
                }

                data[off] = (byte) color;
                off++;
            }
        }

        // use transparent pixel extended attributes if no opaque pixels
        if (pal == -1)
            pal = transPal;
        if (prio == -1)
            prio = transPrio;

        return new Tile(data, size, pal, prio != 0, plain ? plainCol : -1);
    }
}
