package sgdk.rescomp.processor;

import sgdk.rescomp.Processor;
import sgdk.rescomp.Resource;
import sgdk.rescomp.resource.TilesCacheStatsEnabler;
import sgdk.tool.StringUtil;

public class TilesCacheStatsEnablerProcessor implements Processor
{
	public static final String resId = "TILES_CACHE_STATS_ENABLER";

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
            System.out.println(resId + " tilesCacheId enable minTilesetSize");
            System.out.println("  tilesCacheId      The same id you used in other resources to match this cache.");
            System.out.println("  enable            TRUE or FALSE.");
            System.out.println("  minTilesetSize    Min tileset size (wihtout duplicates) to allow a tileset for stats. 0 allows all tilesets.");
            return null;
        }

        // get the tilesCacheId as the resource id
        String tilesCacheId = fields[1].toUpperCase();
        if (tilesCacheId == null || tilesCacheId.isEmpty() || tilesCacheId.isBlank())
        	throw new IllegalArgumentException("tilesCacheId is invalid");

        boolean enable = "TRUE".equals(fields[2].toUpperCase());

        int minTilesetSize = StringUtil.parseInt(fields[3], 0);
        
        return new TilesCacheStatsEnabler(tilesCacheId, enable, minTilesetSize);
    }
}
