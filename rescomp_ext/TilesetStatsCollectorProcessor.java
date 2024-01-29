package sgdk.rescomp.processor;

import sgdk.rescomp.Processor;
import sgdk.rescomp.Resource;
import sgdk.rescomp.resource.TilesetStatsCollectorPrinter;

public class TilesetStatsCollectorProcessor implements Processor
{
	public static final String resId = "TILESET_STATS_COLLECTOR";

	@Override
    public String getId()
    {
        return resId;
    }

    @Override
    public Resource execute(String[] fields) throws Exception
    {
        if (fields.length < 2)
        {
            System.out.println("Wrong " + resId + " definition");
            System.out.println(resId + " tilesetStatsCollectorId");
            System.out.println("  tilesetStatsCollectorId      The same id you used in other resources to match this collector id.");
            return null;
        }

        // get the tilesetStatsCollectorId as the resource id
        String tilesetStatsCollectorId = fields[1].toUpperCase();
        if (tilesetStatsCollectorId == null || tilesetStatsCollectorId.isEmpty() || tilesetStatsCollectorId.isBlank())
        	throw new IllegalArgumentException("tilesetStatsCollectorId is invalid");

        return new TilesetStatsCollectorPrinter(tilesetStatsCollectorId);
    }
}
