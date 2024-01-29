package sgdk.rescomp.resource;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;

import sgdk.rescomp.Resource;
import sgdk.rescomp.tool.TilesCacheManager;
import sgdk.rescomp.tool.Util;
import sgdk.rescomp.type.Basics.Compression;
import sgdk.rescomp.type.Tile;

public class TilesCacheLoader extends Resource
{
	final int hc;
	final List<Tile> cachedTiles;
	final String originalCacheId_keepCase;

	// binary data block (tiles)
	public final Bin bin;

    public TilesCacheLoader(String id, String originalCacheId_keepCase, int cacheStartIndexInVRAM, String filename, boolean enable) throws Exception
    {
        super(id);

        this.originalCacheId_keepCase = originalCacheId_keepCase;
        
        TilesCacheManager.setStartIndexInVRAM(id, cacheStartIndexInVRAM);

        if (enable)
        	cachedTiles = TilesCacheManager.loadCacheFromFile(id, filename);
        else
        	cachedTiles = Collections.emptyList();

        // build the binary bloc
        final int[] data = new int[cachedTiles.size() * 8];

		int offset = 0;
		for (Tile t : cachedTiles) {
			System.arraycopy(t.data, 0, data, offset, 8);
			offset += 8;
		}

		// build BIN (tiles data) with wanted compression
		final Bin binResource = new Bin(id + "_data", data, Compression.AUTO);
		// internal
		binResource.global = false;

		// add as resource (avoid duplicate)
		bin = (Bin) addInternalResource(binResource);

		// compute hash code
		hc = bin.hashCode();
    }

	@Override
    public int internalHashCode()
    {
        return hc;
    }

    @Override
    public boolean internalEquals(Object obj)
    {
        if (obj instanceof TilesCacheStatsPrinter)
        {
            final TilesCacheStatsPrinter other = (TilesCacheStatsPrinter) obj;
            return hc == other.hc;
        }

        return false;
    }

    @Override
    public List<Bin> getInternalBinResources()
    {
        return Collections.emptyList();
    }

    @Override
    public int shallowSize()
    {
        return 0;
    }

    @Override
    public int totalSize()
    {
        return 0;
    }

    @Override
    public void out(ByteArrayOutputStream outB, StringBuilder outS, StringBuilder outH)
    {
        // can't store pointer so we just reset binary stream here (used for compression only)
        outB.reset();

        // output TileSet structure
        Util.decl(outS, outH, "TileSet", originalCacheId_keepCase, 2, global);
        outH.append("\n");

        // set compression info (very important that binary data had already been exported at this point)
        outS.append("    dc.w    " + (bin.doneCompression.ordinal() - 1) + "\n");
        // set number of tile
        outS.append("    dc.w    " + cachedTiles.size() + "\n");
        // set data pointer
        outS.append("    dc.l    " + (cachedTiles.isEmpty() ? 0 : bin.id) + "\n"); // 0 is NULL
        outS.append("\n");
    }
}
