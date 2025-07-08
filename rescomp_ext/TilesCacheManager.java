package sgdk.rescomp.tool;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import sgdk.rescomp.type.Basics.TileEquality;
import sgdk.rescomp.type.Tile;
import sgdk.rescomp.type.TileCacheMatch;

public class TilesCacheManager {

	private static String rescomp_ext_jar_path = new File(MdComp.class.getProtectionDomain().getCodeSource().getLocation().getPath())
			.getParentFile().getAbsolutePath();

	private static Map<String, List<Tile>> cachedTilesByCacheId = new HashMap<>();

	private static Map<String, Boolean> statsEnabledById = new HashMap<>();
	/**
	 * Counts in how many resources, under the same tile cache id, a tile appears (not the total count of the tile in all resources).
	 */
	private static Map<String, Map<Integer, AtomicInteger>> statsCacheResourcesPerTileByCacheId = new HashMap<>();

	/**
	 * Counts how many times a tile appears along all the resources under the same tile cache id.
	 */
	private static Map<String, Map<Integer, AtomicInteger>> statsCacheTotalOccurrsByCacheId = new HashMap<>();

	/*
	 * This used only when collecting the stats.
	 */
	private static Map<String, Map<Integer, Tile>> tileByHashCodeByCacheId = new HashMap<>();

	private static Map<String, Integer> minTilesetSizeForStatsByCacheId = new HashMap<>();

	private static Map<String, Integer> cacheStartIndexInVRAM_var_ById = new HashMap<>();
	private static Map<String, Integer> cacheTilesNum_var_ById = new HashMap<>();
	private static Map<String, List<Entry<Integer,Integer>>> cacheRangesInVRAM_fixed_ById = new HashMap<>();

	public static void setMinTilesetSizeForStatsFor (String cacheId, int minTilesetSize) {
		minTilesetSizeForStatsByCacheId.put(cacheId, Integer.valueOf(minTilesetSize));
	}

	public static int getMinTilesetSizeForStatsFor (String cacheId) {
		Integer value = minTilesetSizeForStatsByCacheId.get(cacheId);
		if (value == null)
			return 0;
		return value.intValue();
	}

	public static void setStartIndexInVRAM_var (String cacheId, int cacheStartIndexInVRAM_1) {
		cacheStartIndexInVRAM_var_ById.put(cacheId, Integer.valueOf(cacheStartIndexInVRAM_1));
	}
	
	public static void setCacheTilesNum_var (String cacheId, int cacheVarTilesNum) {
		cacheTilesNum_var_ById.put(cacheId, Integer.valueOf(cacheVarTilesNum));
	}

	public static void setRangesInVRAM_fixed (String cacheId, List<Entry<Integer,Integer>> cacheRangesInVRAM_fixed) {
		cacheRangesInVRAM_fixed_ById.put(cacheId, cacheRangesInVRAM_fixed);
	}

	public static int getStartIndexInVRAM_var (String cacheId) {
		Integer value = cacheStartIndexInVRAM_var_ById.get(cacheId);
		if (value == null)
			return 0;
		return value.intValue();
	}

	public static int getCacheTilesNum_var (String cacheId) {
		Integer value = cacheTilesNum_var_ById.get(cacheId);
		if (value == null)
			return 0;
		return value.intValue();
	}

	public static List<Entry<Integer,Integer>> getRangesInVRAM_fixed (String cacheId) {
		List<Entry<Integer,Integer>> list = cacheRangesInVRAM_fixed_ById.get(cacheId);
		if (list == null)
			return Collections.emptyList();
		return list;
	}

	public static int getCacheFixedTOTALTilesNum (String cacheId) {
		List<Entry<Integer,Integer>> list = cacheRangesInVRAM_fixed_ById.get(cacheId);
		if (list == null)
			return 0;
		return list.stream()
                .mapToInt(Map.Entry::getValue)
                .sum();
	}

	public static int getCacheFixedEndIndex (String cacheId) {
		List<Entry<Integer,Integer>> list = cacheRangesInVRAM_fixed_ById.get(cacheId);
		if (list == null)
			return 0;
		Entry<Integer,Integer> lastEntry = list.get(list.size() - 1);
		return lastEntry.getKey() + lastEntry.getValue() - 1;
	}

	public static List<Tile> loadCacheFromFile (String cacheId, String filename) {
		boolean isAbsolutePath = false; // use this variable for debugging purpose
		String location = isAbsolutePath ? "" : rescomp_ext_jar_path;
		File file = new File(location + File.separator + filename);

		try (Scanner scanner = new Scanner(file)) {
			if (!file.exists()) {
				System.out.println("ERROR! Couldn't load file " + filename);
				return Collections.emptyList();
			}

			// Read the first line as the id
			String id = null;
			if (scanner.hasNextLine()) {
				id = scanner.nextLine();
			}
			if (!cacheId.equals(id)) {
            	System.out.println("ERROR! " + TilesCacheManager.class.getSimpleName() + ": provided cacheId is different from cacheId in file " + filename);
				return Collections.emptyList();
			}

			List<Tile> tiles = new ArrayList<>(256); // initial capacity
			// Read the remaining lines as comma-separated integers each
			while (scanner.hasNextLine()) {
				String[] values = scanner.nextLine().split(",");
				if (values == null || values.length != 8)
					continue;
				int[] data = new int[8];
				for (int i = 0; i < values.length && i < 8; i++) {
					data[i] = Integer.parseInt(values[i].trim());
				}
				Tile tile = new Tile(data, 8, 0, false, 0);
				tiles.add(tile);
			}

			System.out.println(cacheId + ": Loaded tiles: " + tiles.size());
			cachedTilesByCacheId.put(cacheId, tiles);
			return tiles;

		} catch (FileNotFoundException e) {
			System.out.println("ERROR! " + TilesCacheManager.class.getSimpleName() + ": " + e.getMessage());
			return Collections.emptyList();
		}
	}

	private static List<Map.Entry<Integer, Integer>> calculateGaps (List<Map.Entry<Integer, Integer>> rangesInVRAM_fixed) {
		if (rangesInVRAM_fixed.isEmpty())
			return Collections.emptyList();

        List<Map.Entry<Integer, Integer>> result = new ArrayList<>(rangesInVRAM_fixed.size());

        for (int i = 0; i < rangesInVRAM_fixed.size(); i++) {
            Map.Entry<Integer, Integer> currentRange = rangesInVRAM_fixed.get(i);
            // calculate the end index of the range
            int endIndex = currentRange.getKey() + currentRange.getValue() - 1;
            // calculate the gap between the end index and the next range's start
            int gapToNextRange = 0;
            if (i < rangesInVRAM_fixed.size() - 1) {
                Map.Entry<Integer, Integer> nextRange = rangesInVRAM_fixed.get(i + 1);
                gapToNextRange = nextRange.getKey() - endIndex;
            }

            // Add the new entry to the result list
            result.add(new AbstractMap.SimpleEntry<>(endIndex, gapToNextRange));
        }

        return result;
    }

	/**
	 * Check if the parameter tile exist in the cache. The search uses TileEquality to consider H/V flip cases.
	 */
	public static TileCacheMatch getCachedTile (String cacheId, Tile tile) {
		if (!cachedTilesByCacheId.containsKey(cacheId))
			return null;

		List<Tile> tiles = cachedTilesByCacheId.get(cacheId);
		if (tiles == null || tiles.isEmpty())
			return null;

		final int startIndexInVRAM_var = getStartIndexInVRAM_var(cacheId);
		final int cacheTilesNum_var = getCacheTilesNum_var(cacheId);
		final int endIndexInVRAM_var = startIndexInVRAM_var + cacheTilesNum_var - 1;
		final List<Entry<Integer,Integer>> rangesInVRAM_fixed = getRangesInVRAM_fixed(cacheId);
		final int endIndexInVRAM_fixed = getCacheFixedEndIndex(cacheId);
		
		// Start assigning an index at the the beginning of fixed VRAM. If not fixed VRAM then use variable VRAM starting index
		final int startIndexInVRAM = rangesInVRAM_fixed.isEmpty() ? startIndexInVRAM_var : rangesInVRAM_fixed.get(0).getKey();
		// End index
		final int endIndexInVRAM = rangesInVRAM_fixed.isEmpty() ? endIndexInVRAM_var : endIndexInVRAM_fixed;

		// Only when a range of fixed VRAM is set, we need to use the ranges list to know the gaps the indexInCache has to add to it self
		List<Entry<Integer,Integer>> rangesForGaps = calculateGaps(rangesInVRAM_fixed);

		Tile tileFound = null;
		int indexInCache = startIndexInVRAM;
		// we have to search over all entries since the tile might be flipped.
		for (Tile t : tiles) {
			if (tile.getEquality(t) != TileEquality.NONE) {
				tileFound = t;
				break;
			}
			++indexInCache; // stepping forward in VRAM

			// Ensure the stepping in VRAM is along the fixed VRAM regions 
			if (!rangesForGaps.isEmpty()) {
				for (int i=0; i < rangesForGaps.size(); ++i) {
					Entry<Integer, Integer> range = rangesForGaps.get(i);
					if (indexInCache > range.getKey()) {
						// apply the gap so it continues into next range
						indexInCache += range.getValue();
						// remove the gap
						rangesForGaps.remove(i);
						break;
					}
				}
			}
			
			// Once the rangesForGaps is empty it means we have not found the tile among the fixed VRAM indexes,
			// so we continue normally without gaps into variable VRAM indexes.
		}

		if (tileFound == null)
			return null;

		// THE NEXT VALIDATES FOR THE SCENARIO WHEN ONLY ONE SETTING WAS SET WHETHER FIXED OR VAR VRAM CACHE.
		// If indexInCache exceeds endIndexInVRAM_fixed it means we exhausted the fixed VRAM, 
		// then we need to set indexInCache at variable VRAM startIndexInVRAM_var (if available).
		if (indexInCache > endIndexInVRAM) {
			// No fixed cache was set? Then we have exceeded the variable VRAM
			if (rangesInVRAM_fixed.isEmpty())
				throw new RuntimeException("indexInCache > endIndexInVRAM_var: " + indexInCache + " > " + endIndexInVRAM_var);
			// Fixed cache was set and exhausted. Ensure we have variable VRAM to be used. If not then throw exception
			else if (cacheTilesNum_var == 0)
				throw new RuntimeException("Fixed VRAM for cache " + cacheId + " exhausted and there is no Variable VRAM to occupy.");

			// At this point we have exhausted the fixed VRAM and we need to check if we haven't exhausted variable VRAM
			final int exceeded = indexInCache - endIndexInVRAM;
			indexInCache = startIndexInVRAM_var + exceeded - 1;
			// Ensure indexInCache doesn't exceed variable VRAM
			if (indexInCache > endIndexInVRAM_var)
				throw new RuntimeException("indexInCache > endIndexInVRAM_var: " + indexInCache + " > " + endIndexInVRAM_var);
		}
	
		return new TileCacheMatch(tileFound, indexInCache);
	}

	public static void enableStatsFor (String cacheId) {
		statsEnabledById.put(cacheId, Boolean.TRUE);
	}

	public static boolean isStatsEnabledFor (String cacheId) {
		if (cacheId == null)
			return false;
		return statsEnabledById.get(cacheId) == Boolean.TRUE;
	}

	public static void createCacheIfNotExist (String cacheId) {
		if (!statsCacheTotalOccurrsByCacheId.containsKey(cacheId)) {
			Map<Integer, AtomicInteger> occurrencesPerTile = new HashMap<>((int)(2048 / 0.75) + 1);
			statsCacheTotalOccurrsByCacheId.put(cacheId, occurrencesPerTile);
		}
		
		if (!statsCacheResourcesPerTileByCacheId.containsKey(cacheId)) {
			Map<Integer, AtomicInteger> occurrencesPerTile = new HashMap<>((int)(2048 / 0.75) + 1);
			statsCacheResourcesPerTileByCacheId.put(cacheId, occurrencesPerTile);
		}
	}

	/**
	 * This only counts the resources that a tile appears (not the total count of a tile in the resource).
	 * @param cacheId
	 * @param tiles
	 */
	public static void countResourcesPerTile (String cacheId, List<Tile> tiles) {
		if (cacheId == null)
			return;
		if (!statsCacheResourcesPerTileByCacheId.containsKey(cacheId))
			return;

		Map<Integer, Tile> tileByHashCode = tileByHashCodeByCacheId.get(cacheId);
		if (tileByHashCode == null) {
			tileByHashCode = new HashMap<>();
			tileByHashCodeByCacheId.put(cacheId, tileByHashCode);
		}

		Map<Integer, AtomicInteger> resourcesPerTile = statsCacheResourcesPerTileByCacheId.get(cacheId);
		HashSet<Integer> alreadyProcessed = new HashSet<>((int)(tiles.size() / 0.75) + 1);
		for (Tile tile : tiles) {
			// we don't process the tile that is black because that's the first SGDK tile reserved in VRAM at address 0
			if (tile.getPlainValue() == 0)
				continue;
			int key = tile.hashCode();//Arrays.hashCode(tile.data);
			if (alreadyProcessed.contains(key))
				continue;
			if (!resourcesPerTile.containsKey(key))
				resourcesPerTile.put(key, new AtomicInteger(1));
			else {
				AtomicInteger currentCount = resourcesPerTile.get(key);
				currentCount.incrementAndGet();
			}
			alreadyProcessed.add(key);
			tileByHashCode.put(tile.hashCode(), tile);
		}
	}

	/**
	 * This counts the toal times a tile appears in the list of passes by tile.
	 * @param cacheId
	 * @param tiles
	 */
	public static void countTotalTiles (String cacheId, List<Tile> tiles) {
		if (cacheId == null)
			return;
		if (!statsCacheTotalOccurrsByCacheId.containsKey(cacheId))
			return;

		Map<Integer, Tile> tileByHashCode = tileByHashCodeByCacheId.get(cacheId);
		if (tileByHashCode == null) {
			tileByHashCode = new HashMap<>();
			tileByHashCodeByCacheId.put(cacheId, tileByHashCode);
		}

		Map<Integer, AtomicInteger> occurrencesPerTile = statsCacheTotalOccurrsByCacheId.get(cacheId);
		for (Tile tile : tiles) {
			// we don't process the tile that is black because that's the first tile SGDK reserves in VRAM at address 0
			if (tile.getPlainValue() == 0)
				continue;
			int key = tile.hashCode();//Arrays.hashCode(tile.data);
			if (!occurrencesPerTile.containsKey(key))
				occurrencesPerTile.put(key, new AtomicInteger(1));
			else {
				AtomicInteger currentCount = occurrencesPerTile.get(key);
				currentCount.incrementAndGet();
			}
			tileByHashCode.put(tile.hashCode(), tile);
		}
	}

	public static String getStats (String cacheId) {
		if (!statsCacheResourcesPerTileByCacheId.containsKey(cacheId) && !statsCacheTotalOccurrsByCacheId.containsKey(cacheId))
			return "WARNING! Stats Cache for id " + cacheId + " don't exist.";

		Map<Integer, AtomicInteger> resourcesPerTile = statsCacheResourcesPerTileByCacheId.get(cacheId);
		Map<Integer, AtomicInteger> occurrencesPerTile = statsCacheTotalOccurrsByCacheId.get(cacheId);
		Map<Integer, Tile> tileByHashCode = tileByHashCodeByCacheId.get(cacheId);

		StringBuilder sb = new StringBuilder(50000); // 50k chars (bytes)
		int topNusedTiles = ExtProperties.getInt(ExtProperties.TOP_N_USED_TILES);

		List<Entry<Integer, AtomicInteger>> sortedTopNresourcesPerTile = resourcesPerTile.entrySet()
                .stream()
                .sorted((entry1, entry2) -> entry2.getValue().get() - entry1.getValue().get())
                .limit(topNusedTiles)
                .collect(Collectors.toList());
		sb.append("##-----------------------------------------------------------------------------##").append(System.lineSeparator());
		sb.append("##  [TOP ").append(sortedTopNresourcesPerTile.size()).append("] Tiles in Resources under cache id: ").append(cacheId).append(System.lineSeparator());
		sb.append("##-----------------------------------------------------------------------------##").append(System.lineSeparator());
		sb.append(String.format("%-12s %4s  %s", "hash", "#", "data")).append(System.lineSeparator());
		for (Entry<Integer, AtomicInteger> entry : sortedTopNresourcesPerTile) {
			String tileDataArrayStr = tileDataToStr(tileByHashCode.get(entry.getKey()));
			String dataStr = String.format("%-12d %4d  %s", entry.getKey().intValue(), entry.getValue().intValue(), tileDataArrayStr);
			sb.append(dataStr).append(System.lineSeparator());
		}

		List<Entry<Integer, AtomicInteger>> sortedTopNoccursPerTile = occurrencesPerTile.entrySet()
                .stream()
                .sorted((entry1, entry2) -> entry2.getValue().get() - entry1.getValue().get())
                .limit(topNusedTiles)
                .collect(Collectors.toList());
		sb.append("##-----------------------------------------------------------------------------##").append(System.lineSeparator());
		sb.append("##  [TOP ").append(sortedTopNoccursPerTile.size()).append("] Tiles total in all Resources under cache id: ").append(cacheId).append(System.lineSeparator());
		sb.append("##-----------------------------------------------------------------------------##").append(System.lineSeparator());
		sb.append(String.format("%-12s %4s  %s", "hash", "#", "data")).append(System.lineSeparator());
		for (Entry<Integer, AtomicInteger> entry : sortedTopNoccursPerTile) {
			String tileDataArrayStr = tileDataToStr(tileByHashCode.get(entry.getKey()));
			String dataStr = String.format("%-12d %4d  %s", entry.getKey().intValue(), entry.getValue().intValue(), tileDataArrayStr);
			sb.append(dataStr).append(System.lineSeparator());
		}

//		sb.append("##-----------------------------------------------------------------##").append(System.lineSeparator());
//		sb.append("##  Tiles in Resources under cache id: ").append(cacheId).append(System.lineSeparator());
//		sb.append("##-----------------------------------------------------------------##").append(System.lineSeparator());
//		sb.append(String.format("%-12s %4s", "hash", "#")).append(System.lineSeparator());
//		for (Entry<Integer, AtomicInteger> entry : resourcesPerTile.entrySet()) {
//			String dataStr = String.format("%-12d %4d", entry.getKey().intValue(), entry.getValue().get());
//			sb.append(dataStr).append(System.lineSeparator());
//		}
//
//		sb.append("##-----------------------------------------------------------------##").append(System.lineSeparator());
//		sb.append("##  Tiles total in all Resources under cache id: ").append(cacheId).append(System.lineSeparator());
//		sb.append("##-----------------------------------------------------------------##").append(System.lineSeparator());
//		sb.append(String.format("%-12s %4s", "hash", "#")).append(System.lineSeparator());
//		for (Entry<Integer, AtomicInteger> entry : occurrencesPerTile.entrySet()) {
//			String dataStr = String.format("%-12d %4d", entry.getKey().intValue(), entry.getValue().get());
//			sb.append(dataStr).append(System.lineSeparator());
//		}

		return sb.toString();
	}

	private static String tileDataToStr (Tile tile) {
		return Arrays.stream(tile.data)
		        .boxed()
				.map(i -> String.valueOf(i))
				.collect(Collectors.joining(","));
	}

	public static String saveStatsToFile (String cacheId) {
		String fileDest = rescomp_ext_jar_path + File.separator + cacheId + "_stats.txt";
		deleteFile(fileDest);
		String stats = getStats(cacheId);
		try {
			writeBytesToFile(stats.getBytes(), fileDest);
			return fileDest;
		}
		catch (IOException e) {
			System.out.println("ERROR! Couldn't save file " + fileDest + ". " + e.getMessage());
			return null;
		}
	}

	private static void writeBytesToFile (byte[] data, String fileName) throws IOException {
		try (FileOutputStream fos = new FileOutputStream(fileName)) {
			fos.write(data);
			fos.flush();
			fos.close();
		}
	}

	private static void deleteFile (String fileName) {
		try {
			Path path = Paths.get(fileName);
			Files.deleteIfExists(path);
		}
		catch (IOException e) {}
	}

}
