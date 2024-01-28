package sgdk.rescomp.processor;

import sgdk.rescomp.Processor;
import sgdk.rescomp.Resource;
import sgdk.rescomp.resource.TilesCacheStatsPrinter;

public class TilesCacheStatsPrinterProcessor implements Processor
{
	public static final String resId = "TILES_CACHE_STATS_PRINTER";

	@Override
    public String getId()
    {
        return resId;
    }

    @Override
    public Resource execute(String[] fields) throws Exception
    {
        if (fields.length < 3)
        {
            System.out.println("Wrong " + resId + " definition");
            System.out.println(resId + " tilesCacheId printTo");
            System.out.println("  tilesCacheId      The same id you used in other resources to match this cache.");
            System.out.println("  printTo           FILE or CONSOLE or NONE.");
            return null;
        }

        // get the tilesCacheId as the resource id
        String tilesCacheId = fields[1].toUpperCase();
        if (tilesCacheId == null || tilesCacheId.isEmpty() || tilesCacheId.isBlank())
        	throw new IllegalArgumentException("tilesCacheId is invalid");

        String printTo = fields[2].toUpperCase();
        if (!"FILE".equals(printTo) && !"CONSOLE".equals(printTo) && !"NONE".equals(printTo))
        	throw new IllegalArgumentException("printTo is invalid");

        return new TilesCacheStatsPrinter(tilesCacheId, printTo);
    }
}
