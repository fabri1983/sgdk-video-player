package sgdk.rescomp.resource;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;

import sgdk.rescomp.Resource;
import sgdk.rescomp.processor.TilesCacheStatsPrinterProcessor;
import sgdk.rescomp.tool.TilesCacheManager;

public class TilesCacheStatsPrinter extends Resource
{
    final int hc;
    final String printTo;

    public TilesCacheStatsPrinter(String id, String printTo) throws Exception
    {
        super(id);

        this.printTo = printTo;

        // compute hash code
        hc = id.hashCode() ^ TilesCacheStatsPrinter.class.getSimpleName().hashCode();
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
    	String tilesCacheId = this.id;

    	if (!TilesCacheManager.isStatsEnabledFor(tilesCacheId))
    		return;

    	if ("CONSOLE".equals(printTo)) {
    		String statsStr = TilesCacheManager.getStats(tilesCacheId);
    		System.out.println(TilesCacheStatsPrinterProcessor.resId + ": stats for " + tilesCacheId);    		
    		System.out.println(statsStr);
    	}
    	else if ("FILE".equals(printTo)) {
    		String fileDest = TilesCacheManager.saveStatsToFile(tilesCacheId);
    		if (!"".equals(fileDest))
    			System.out.println(TilesCacheStatsPrinterProcessor.resId + ": file saved " + fileDest);
    	}
    }
}
