package sgdk.rescomp.tool;

import java.util.HashMap;
import java.util.Map;

public class TilesetStatsCollector {

	private static Map<String, Integer> minTilenumPerId = new HashMap<>();
	private static Map<String, Integer> maxTilenumPerId = new HashMap<>();
	private static Map<String, Integer> minTotalTilenumPerId = new HashMap<>();
	private static Map<String, Integer> maxTotalTilenumPerId = new HashMap<>();

	public static void count1chunk(String id, int chunk1) {
		counForMin(id, chunk1);
		counForMax(id, chunk1);
		counForMinTotal(id, chunk1);
		counForMaxTotal(id, chunk1);
	}

	public static void count2chunks(String id, int chunk1, int chunk2) {
		counForMin(id, chunk1);
		counForMax(id, chunk1);
		counForMin(id, chunk2);
		counForMax(id, chunk2);
		counForMinTotal(id, chunk1 + chunk2);
		counForMaxTotal(id, chunk1 + chunk2);
	}

	public static void count3chunks(String id, int chunk1, int chunk2, int chunk3) {
		counForMin(id, chunk1);
		counForMax(id, chunk1);
		counForMin(id, chunk2);
		counForMax(id, chunk2);
		counForMin(id, chunk3);
		counForMax(id, chunk3);
		counForMinTotal(id, chunk1 + chunk2 + chunk3);
		counForMaxTotal(id, chunk1 + chunk2 + chunk3);
	}

	private static void counForMin(String id, int chunk1) {
		Integer min = minTilenumPerId.get(id);
		if (min == null) {
			min = Integer.valueOf(chunk1);
			minTilenumPerId.put(id, min);
		}
		if (chunk1 < min.intValue())
			minTilenumPerId.put(id, Integer.valueOf(chunk1));
	}

	private static void counForMax(String id, int chunk1) {
		Integer max = maxTilenumPerId.get(id);
		if (max == null) {
			max = Integer.valueOf(chunk1);
			maxTilenumPerId.put(id, max);
		}
		if (chunk1 > max.intValue())
			maxTilenumPerId.put(id, Integer.valueOf(chunk1));
	}

	private static void counForMinTotal(String id, int total) {
		Integer min = minTotalTilenumPerId.get(id);
		if (min == null) {
			min = Integer.valueOf(total);
			minTotalTilenumPerId.put(id, min);
		}
		if (total < min.intValue())
			minTotalTilenumPerId.put(id, Integer.valueOf(total));
	}

	private static void counForMaxTotal(String id, int total) {
		Integer max = maxTotalTilenumPerId.get(id);
		if (max == null) {
			max = Integer.valueOf(total);
			maxTotalTilenumPerId.put(id, max);
		}
		if (total > max.intValue())
			maxTotalTilenumPerId.put(id, Integer.valueOf(total));
	}

	public static Integer getMinTileNum(String id) {
		Integer min = minTilenumPerId.get(id);
		if (min == null)
			return null;
		return min.intValue();
	}

	public static Integer getMaxTileNum(String id) {
		Integer max = maxTilenumPerId.get(id);
		if (max == null)
			return null;
		return max.intValue();
	}

	public static Integer getMinTotalTileNum(String id) {
		Integer minTotal = minTotalTilenumPerId.get(id);
		if (minTotal == null)
			return null;
		return minTotal.intValue();
	}

	public static Integer getMaxTotalTileNum(String id) {
		Integer maxTotal = maxTotalTilenumPerId.get(id);
		if (maxTotal == null)
			return null;
		return maxTotal.intValue();
	}
}
