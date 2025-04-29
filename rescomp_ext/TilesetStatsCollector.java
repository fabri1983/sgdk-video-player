package sgdk.rescomp.tool;

import java.util.HashMap;
import java.util.Map;

public class TilesetStatsCollector {

	private static Map<String, Integer> minTilenumPerId = new HashMap<>();
	private static Map<String, Integer> maxTilenumPerId = new HashMap<>();
	private static Map<String, Integer> maxTilenumChunk1PerId = new HashMap<>();
	private static Map<String, Integer> maxTilenumChunk2PerId = new HashMap<>();
	private static Map<String, Integer> maxTilenumChunk3PerId = new HashMap<>();
	private static Map<String, Integer> minTotalTilenumPerId = new HashMap<>();
	private static Map<String, Integer> maxTotalTilenumPerId = new HashMap<>();

	public static boolean isOnlyChunk1Valid(String id){
		return (maxTilenumChunk2PerId.get(id) == null || maxTilenumChunk2PerId.get(id) == 0) 
				&& (maxTilenumChunk3PerId.get(id) == null || maxTilenumChunk3PerId.get(id) == 0);
	}

	public static void count1chunk(String id, int chunkSize1) {
		if (id == null || "".equals(id))
			return;
		counForMin(id, chunkSize1, minTilenumPerId);
		counForMax(id, chunkSize1, maxTilenumPerId);
		counForMax(id, chunkSize1, maxTilenumChunk1PerId);
		counForMax(id, 0, maxTilenumChunk2PerId);
		counForMax(id, 0, maxTilenumChunk3PerId);
		counForMin(id, chunkSize1, minTotalTilenumPerId);
		counForMax(id, chunkSize1, maxTotalTilenumPerId);
	}

	public static void count2chunks(String id, int chunkSize1, int chunkSize2) {
		if (id == null || "".equals(id))
			return;
		counForMin(id, chunkSize1, minTilenumPerId);
		counForMax(id, chunkSize1, maxTilenumPerId);
		counForMax(id, chunkSize1, maxTilenumChunk1PerId);
		counForMin(id, chunkSize2, minTilenumPerId);
		counForMax(id, chunkSize2, maxTilenumPerId);
		counForMax(id, chunkSize2, maxTilenumChunk2PerId);
		counForMax(id, 0, maxTilenumChunk3PerId);
		counForMin(id, chunkSize1 + chunkSize2, minTotalTilenumPerId);
		counForMax(id, chunkSize1 + chunkSize2, maxTotalTilenumPerId);
	}

	public static void count3chunks(String id, int chunkSize1, int chunkSize2, int chunkSize3) {
		if (id == null || "".equals(id))
			return;
		counForMin(id, chunkSize1, minTilenumPerId);
		counForMax(id, chunkSize1, maxTilenumPerId);
		counForMax(id, chunkSize1, maxTilenumChunk1PerId);
		counForMin(id, chunkSize2, minTilenumPerId);
		counForMax(id, chunkSize2, maxTilenumPerId);
		counForMax(id, chunkSize2, maxTilenumChunk2PerId);
		counForMin(id, chunkSize3, minTilenumPerId);
		counForMax(id, chunkSize3, maxTilenumPerId);
		counForMax(id, chunkSize3, maxTilenumChunk3PerId);
		counForMin(id, chunkSize1 + chunkSize2 + chunkSize3, minTotalTilenumPerId);
		counForMax(id, chunkSize1 + chunkSize2 + chunkSize3, maxTotalTilenumPerId);
	}

	private static void counForMin(String id, int chunkSize, Map<String, Integer> map) {
		if (id == null || "".equals(id))
			return;
		Integer min = map.get(id);
		if (min == null) {
			min = Integer.valueOf(chunkSize);
			map.put(id, min);
		}
		if (chunkSize < min.intValue())
			map.put(id, Integer.valueOf(chunkSize));
	}

	private static void counForMax(String id, int chunkSize, Map<String, Integer> map) {
		if (id == null || "".equals(id))
			return;
		Integer max = map.get(id);
		if (max == null) {
			max = Integer.valueOf(chunkSize);
			map.put(id, max);
		}
		if (chunkSize > max.intValue())
			map.put(id, Integer.valueOf(chunkSize));
	}

	public static Integer getMinTileNum(String id) {
		if (id == null || "".equals(id))
			return null;
		Integer min = minTilenumPerId.get(id);
		if (min == null)
			return null;
		return min.intValue();
	}

	public static Integer getMaxTileNum(String id) {
		if (id == null || "".equals(id))
			return null;
		Integer max = maxTilenumPerId.get(id);
		if (max == null)
			return null;
		return max.intValue();
	}

	public static Integer getMaxTileNumChunk1(String id) {
		if (id == null || "".equals(id))
			return null;
		Integer max = maxTilenumChunk1PerId.get(id);
		if (max == null)
			return null;
		return max.intValue();
	}

	public static Integer getMaxTileNumChunk2(String id) {
		if (id == null || "".equals(id))
			return null;
		Integer max = maxTilenumChunk2PerId.get(id);
		if (max == null)
			return null;
		return max.intValue();
	}

	public static Integer getMaxTileNumChunk3(String id) {
		if (id == null || "".equals(id))
			return null;
		Integer max = maxTilenumChunk3PerId.get(id);
		if (max == null)
			return null;
		return max.intValue();
	}

	public static Integer getMinTotalTileNum(String id) {
		if (id == null || "".equals(id))
			return null;
		Integer minTotal = minTotalTilenumPerId.get(id);
		if (minTotal == null)
			return null;
		return minTotal.intValue();
	}

	public static Integer getMaxTotalTileNum(String id) {
		if (id == null || "".equals(id))
			return null;
		Integer maxTotal = maxTotalTilenumPerId.get(id);
		if (maxTotal == null)
			return null;
		return maxTotal.intValue();
	}
}
