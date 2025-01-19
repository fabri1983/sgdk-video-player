package sgdk.rescomp.resource;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import sgdk.rescomp.Resource;
import sgdk.rescomp.tool.TilesCacheManager;
import sgdk.rescomp.tool.Util;
import sgdk.rescomp.type.Basics.Compression;
import sgdk.rescomp.type.CompressionCustom;
import sgdk.rescomp.type.Tile;

public class TilesCacheLoader extends Resource
{
	final int hc;
	final List<Tile> cachedTiles;
	final String originalCacheId_keepCase;

	// binary data block (tiles)
	public final BinCustom bin;

    public TilesCacheLoader(String id, String originalCacheId_keepCase, int cacheStartIndexInVRAM_var, int cacheVarTilesNum, int cacheStartIndexInVRAM_fixed, 
    		int cacheFixedTilesNum, String filename, boolean enable, Compression compression, CompressionCustom compressionCustom) throws Exception
    {
        super(id);

        this.originalCacheId_keepCase = originalCacheId_keepCase;
        
        TilesCacheManager.setStartIndexInVRAM_var(id, cacheStartIndexInVRAM_var);
        TilesCacheManager.setCacheVarTilesNum(id, cacheVarTilesNum);
        TilesCacheManager.setStartIndexInVRAM_fixed(id, cacheStartIndexInVRAM_fixed);
        TilesCacheManager.setCacheFixedTilesNum(id, cacheFixedTilesNum);

        if (enable) {
        	cachedTiles = TilesCacheManager.loadCacheFromFile(id, filename);
        }
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
		final BinCustom binResource = new BinCustom(id + "_data", data, compression, compressionCustom);
		// internal
		binResource.global = false;

		// add as resource (avoid duplicate)
		bin = (BinCustom) addInternalResource(binResource);

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
    public void out(ByteArrayOutputStream outB, StringBuilder outS, StringBuilder outH)
    {
        // can't store pointer so we just reset binary stream here (used for compression only)
        outB.reset();

        // output TileSet structure
        Util.decl(outS, outH, "TileSet", originalCacheId_keepCase, 2, global);
        outH.append("\n");

        // set compression info (very important that binary data had already been exported at this point)
        int compOrdinal = 0;
        if (bin.doneCompression != Compression.NONE)
        	compOrdinal = bin.doneCompression.ordinal() - 1;
        else if (bin.doneCompressionCustom != CompressionCustom.NONE)
        	compOrdinal = bin.doneCompressionCustom.getDefineValue();
		outS.append("    dc.w    " + compOrdinal + "\n");
        // set number of tile
        outS.append("    dc.w    " + cachedTiles.size() + "\n");
        // set data pointer
        outS.append("    dc.l    " + (cachedTiles.isEmpty() ? 0 : bin.id) + "\n"); // 0 is NULL
        outS.append("\n");
    }
}
