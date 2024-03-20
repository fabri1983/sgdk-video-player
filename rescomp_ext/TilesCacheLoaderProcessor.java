package sgdk.rescomp.processor;

import sgdk.rescomp.Processor;
import sgdk.rescomp.Resource;
import sgdk.rescomp.resource.TilesCacheLoader;
import sgdk.rescomp.tool.Util;
import sgdk.rescomp.type.Basics.Compression;
import sgdk.rescomp.type.CompressionCustom;
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
            System.out.println(resId + " tilesCacheId enable cacheStartIndexInVRAM filename compression compressionCustom");
            System.out.println("  tilesCacheId            The same id you used in other resources to match this cache.");
            System.out.println("  enable                  TRUE or FALSE.");
            System.out.println("  cacheStartIndexInVRAM   Starting index of the tiles cache in VRAM. SGDK places tiles from VRAM address 0.");
            System.out.println("  filename                File containing the definition for cached tiles. Relative to res folder.");
			System.out.println("  compression             Compression type. Accepted values:");
			System.out.println("                            -1 / BEST / AUTO = use best compression");
			System.out.println("                            0 / NONE        = no compression (default)");
			System.out.println("                            1 / APLIB       = aplib library (good compression ratio but slow)");
			System.out.println("                            2 / FAST / LZ4W = custom lz4 compression (average compression ratio but fast)");
			System.out.println("  compressionCustom       Overrides the compression parameter. Accepted values:");
			for (CompressionCustom cc : CompressionCustom.values())
				System.out.println("                            " + cc.getValue());
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

        // get packed value
        Compression compression = Compression.NONE;
        if (fields.length >= 6)
            compression = Util.getCompression(fields[5]);

        // get custom compression value
        CompressionCustom compressionCustom = CompressionCustom.NONE;
        if (fields.length >= 7)
        	compressionCustom = CompressionCustom.from(fields[6]);

		return new TilesCacheLoader(tilesCacheId, originalCacheId_keepCase, cacheStartIndexInVRAM, filename, enable, 
				compression, compressionCustom);
    }
}
