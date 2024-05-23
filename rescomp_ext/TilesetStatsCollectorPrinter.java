package sgdk.rescomp.resource;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;

import sgdk.rescomp.Resource;
import sgdk.rescomp.processor.TilesetStatsCollectorProcessor;
import sgdk.rescomp.tool.TilesetStatsCollector;

public class TilesetStatsCollectorPrinter extends Resource
{
    final int hc;

    public TilesetStatsCollectorPrinter(String id) throws Exception
    {
        super(id);

        // compute hash code
        hc = id.hashCode() ^ TilesetStatsCollectorPrinter.class.getSimpleName().hashCode();
    }

	@Override
    public int internalHashCode()
    {
        return hc;
    }

    @Override
    public boolean internalEquals(Object obj)
    {
        if (obj instanceof TilesetStatsCollectorPrinter)
        {
            final TilesetStatsCollectorPrinter other = (TilesetStatsCollectorPrinter) obj;
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
    	String tilesetStatsCollectorId = this.id;

		System.out.println(TilesetStatsCollectorProcessor.resId + ": stats for id " + tilesetStatsCollectorId);    		
		System.out.println("Min chunk size: " + TilesetStatsCollector.getMinTileNum(tilesetStatsCollectorId));
		System.out.println("Max chunk size: " + TilesetStatsCollector.getMaxTileNum(tilesetStatsCollectorId));
		System.out.println("Max chunk1 size: " + TilesetStatsCollector.getMaxTileNumChunk1(tilesetStatsCollectorId));
		System.out.println("Max chunk2 size: " + TilesetStatsCollector.getMaxTileNumChunk2(tilesetStatsCollectorId));
		System.out.println("Max chunk3 size: " + TilesetStatsCollector.getMaxTileNumChunk3(tilesetStatsCollectorId));
		System.out.println("Min Total size: " + TilesetStatsCollector.getMinTotalTileNum(tilesetStatsCollectorId));
		System.out.println("Max Total size: " + TilesetStatsCollector.getMaxTotalTileNum(tilesetStatsCollectorId));
    }
}
