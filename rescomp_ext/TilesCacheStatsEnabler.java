package sgdk.rescomp.resource;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;

import sgdk.rescomp.Resource;
import sgdk.rescomp.tool.TilesCacheManager;

public class TilesCacheStatsEnabler extends Resource
{
    final int hc;

    public TilesCacheStatsEnabler(String id, boolean enable, int minTilesetSize) throws Exception
    {
        super(id);

        if (enable)
        	TilesCacheManager.enableStatsFor(id);

        TilesCacheManager.setMinTilesetSizeForStatsFor(id, minTilesetSize);

        // compute hash code
        hc = id.hashCode() ^ TilesCacheStatsEnabler.class.getSimpleName().hashCode();
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
    }
}
