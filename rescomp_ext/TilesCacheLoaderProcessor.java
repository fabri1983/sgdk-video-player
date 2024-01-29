package sgdk.rescomp.processor;

import sgdk.rescomp.Processor;
import sgdk.rescomp.Resource;
import sgdk.rescomp.resource.TilesCacheLoader;
import sgdk.tool.StringUtil;

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
        if (fields.length < 5)
        {
            System.out.println("Wrong " + resId + " definition");
            System.out.println(resId + " tilesCacheId enable cacheStartIndexInVRAM filename");
            System.out.println("  tilesCacheId            The same id you used in other resources to match this cache.");
            System.out.println("  enable                  TRUE or FALSE.");
            System.out.println("  cacheStartIndexInVRAM   Starting index of the tiles cache in VRAM. SGDK places tiles from VRAM address 0.");
            System.out.println("  filename                File containing the definition for cached tiles. Relative to res folder.");
            return null;
        }

        // get the tilesCacheId as the resource id
        String originalCacheId_keepCase = fields[1];
        if (originalCacheId_keepCase == null || originalCacheId_keepCase.isEmpty() || originalCacheId_keepCase.isBlank())
        	throw new IllegalArgumentException("tilesCacheId is invalid");
        String tilesCacheId = originalCacheId_keepCase.toUpperCase();

		boolean enable = "TRUE".equals(fields[2].toUpperCase());

		int cacheStartIndexInVRAM = StringUtil.parseInt(fields[3], 1);

		String filename = fields[4];

		return new TilesCacheLoader(tilesCacheId, originalCacheId_keepCase, cacheStartIndexInVRAM, filename, enable);
    }
}
