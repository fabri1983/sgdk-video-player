package sgdk.rescomp.processor;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

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
            System.out.println(resId + " tilesCacheId enable cacheStartIndexInVRAM_var cacheVarTilesNum cacheStartIndexInVRAM_fixed cacheFixedTilesNum filename [compression compressionCustom]");
            System.out.println("  tilesCacheId                  The same id you used in other resources to match this cache.");
            System.out.println("  enable                        Enables or disables the use of this cache. TRUE or FALSE.");
            System.out.println("  cacheStartIndexInVRAM_var     Starting index of the tiles cache in VRAM variable region.");
            System.out.println("  cacheTilesNum_var             Number of tiles that goes into VRAM variable region.");
            System.out.println("                                Use NULL or 0 to disable it.");
            System.out.println("  cacheRangesInVRAM_fixed       Comma separated values with the starting index in VRAM and number of tiles. Eg: 792-10,912-32.");
            System.out.println("                                Use NULL or 0-0 to disable it.");
            System.out.println("  filename                      File containing the definition for cached tiles. Relative to res folder.");
			System.out.println("  compression                   Compression type. Accepted values:");
			System.out.println("                                 -1 / BEST / AUTO = use best compression");
			System.out.println("                                  0 / NONE        = no compression (default)");
			System.out.println("                                  1 / APLIB       = aplib library (good compression ratio but slow)");
			System.out.println("                                  2 / FAST / LZ4W = custom lz4 compression (average compression ratio but fast)");
			System.out.println("  compressionCustom             Overrides the compression parameter. Accepted values:");
			for (CompressionCustom cc : CompressionCustom.values())
				System.out.println("                            " + cc.getValue());
            return null;
        }

        // get the tilesCacheId as the resource id
        String originalCacheId_keepCase = fields[1];
        if (originalCacheId_keepCase == null || originalCacheId_keepCase.isEmpty() || originalCacheId_keepCase.isBlank())
        	throw new IllegalArgumentException("tilesCacheId is invalid");
        String tilesCacheId = originalCacheId_keepCase.toUpperCase();

        // enable value
		boolean enable = "TRUE".equals(fields[2].toUpperCase());

		int cacheStartIndexInVRAM_var = StringUtil.parseInt(fields[3], 1);
		int cacheTilesNum_var = StringUtil.parseInt(fields[4], 0);
		List<Entry<Integer,Integer>> cacheRangesInVRAM_fixed = parseCacheFixedPairs(fields[5]);

		String filename = fields[6];

        // get packed value
        Compression compression = Compression.NONE;
        if (fields.length >= 8)
            compression = Util.getCompression(fields[7]);

        // get custom compression value
        CompressionCustom compressionCustom = CompressionCustom.NONE;
        if (fields.length >= 9)
        	compressionCustom = CompressionCustom.from(fields[8]);

		return new TilesCacheLoader(tilesCacheId, originalCacheId_keepCase, cacheStartIndexInVRAM_var, cacheTilesNum_var, cacheRangesInVRAM_fixed, 
				filename, enable, compression, compressionCustom);
    }

    public static List<Map.Entry<Integer, Integer>> parseCacheFixedPairs(String input) {
        // Handle null or empty input
        if (input == null || input.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // Split the input by commas
        String[] pairs = input.split(",");
        List<Map.Entry<Integer, Integer>> result = new ArrayList<>();

        for (String pair : pairs) {
            // Split each pair by the hyphen
            String[] values = pair.split("-");

            // Validate the pair format
            if (values.length != 2) {
                throw new RuntimeException("Invalid pair format: " + pair);
            }

            try {
                // Parse the integers
                int first = Integer.parseInt(values[0].trim());
                int second = Integer.parseInt(values[1].trim());

                // Add the pair to the result list
                result.add(new AbstractMap.SimpleEntry<>(first, second));
            } catch (NumberFormatException e) {
                throw new RuntimeException("Invalid integer in pair: " + pair, e);
            }
        }

        // Sort the list by Entry.key in ascending order
        return result.stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .collect(Collectors.toList());
    }

//    public static void main(String[] args) throws Exception {
//    	TilesCacheLoaderProcessor p = new TilesCacheLoaderProcessor();
//    	String cacheStartIndexInVRAM_var = String.valueOf(1792-216);
//    	String cacheTilesNum_var = "216";
//    	String cacheRangesInVRAM_fixed = "1921-127";
//		String[] fields_test_movie = {
//				resId, "tilesCache_movie1", "TRUE", cacheStartIndexInVRAM_var, cacheTilesNum_var, cacheRangesInVRAM_fixed, 
//				"C:\\MyProjects\\VSCode\\sgdk\\sgdk-video-player-main\\res\\tilesCache_movie1.txt",	"APLIB", "NONE" 
//			};
//		p.execute(fields_test_movie);
//    }
}
