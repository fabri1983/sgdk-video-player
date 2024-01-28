package sgdk.rescomp.processor;

import sgdk.rescomp.Processor;
import sgdk.rescomp.Resource;
import sgdk.rescomp.resource.TilesCacheLoader;

public class TilesCacheLoaderProcessor implements Processor
{
	public static final String resId = "TILES_CACHE_LOADER";

	@Override
    public String getId()
    {
        return resId;
    }

    @Override
    public Resource execute(String[] fields) throws Exception
    {
        if (fields.length < 4)
        {
            System.out.println("Wrong " + resId + " definition");
            System.out.println(resId + " tilesCacheId enable filename");
            System.out.println("  tilesCacheId      The same id you used in other resources to match this cache.");
            System.out.println("  enable            TRUE or FALSE.");
            System.out.println("  filename          File containing the definition for cached tiles. Relative to res folder.");
            return null;
        }

        // get the tilesCacheId as the resource id
        String originalCacheId_keepCase = fields[1];
        if (originalCacheId_keepCase == null || originalCacheId_keepCase.isEmpty() || originalCacheId_keepCase.isBlank())
        	throw new IllegalArgumentException("tilesCacheId is invalid");
        String tilesCacheId = originalCacheId_keepCase.toUpperCase();

        boolean enable = "TRUE".equals(fields[2].toUpperCase());

        String filename = fields[3];

        return new TilesCacheLoader(tilesCacheId, originalCacheId_keepCase, filename, enable);
    }
}
