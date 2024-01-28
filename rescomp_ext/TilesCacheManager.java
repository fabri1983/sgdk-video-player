package sgdk.rescomp.tool;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

	public static void setMinTilesetSizeForStatsFor(String cacheId, int minTilesetSize) {
		minTilesetSizeForStatsByCacheId.put(cacheId, Integer.valueOf(minTilesetSize));
	}

	public static int getMinTilesetSizeForStatsFor(String cacheId) {
		Integer value = minTilesetSizeForStatsByCacheId.get(cacheId);
		if (value == null)
			return 0;
		return value.intValue();
	}

	public static List<Tile> loadCacheFromFile (String cacheId, String filename) {
		File file = new File(rescomp_ext_jar_path + File.separator + filename);
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
            	System.out.println("ERROR! provided cacheId is different from cacheId in file " + filename);
    			return Collections.emptyList();
            }

            List<Tile> tiles = new ArrayList<>(80);
            // Read the remaining line as comma-separated integers each
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
            
            cachedTilesByCacheId.put(cacheId, tiles);
            return tiles;

        } catch (FileNotFoundException e) {
            System.out.println("ERROR! " + e.getMessage());
            return Collections.emptyList();
        }
	}

	/**
	 * Check if the parameter tile exist in the cache. The search uses TileEquality to consider H/V flip cases.
	 */
	public static TileCacheMatch getCachedTile(String cacheId, Tile tile) {
		if (!cachedTilesByCacheId.containsKey(cacheId))
			return null;

		List<Tile> tiles = cachedTilesByCacheId.get(cacheId);
		if (tiles == null)
			return null;

		Tile tileFound = null;
		int indexInCache = 0;
		// we have to search over all entries since the tile might be flipped.
		for (Tile t : tiles) {
			if (tile.getEquality(t) != TileEquality.NONE) {
				tileFound = t;
				break;
			}
			++indexInCache;
		}

		if (tileFound == null)
			return null;
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

	public static void createCacheIfNotExist(String cacheId) {
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
	public static void countResourcesPerTile(String cacheId, List<Tile> tiles) {
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
	 * This counts the toal times a tile appears in the passes tiles list.
	 * @param cacheId
	 * @param tiles
	 */
	public static void countTotalTiles(String cacheId, List<Tile> tiles) {
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

	public static String getStats(String cacheId) {
		if (!statsCacheResourcesPerTileByCacheId.containsKey(cacheId) && !statsCacheTotalOccurrsByCacheId.containsKey(cacheId))
			return "WARNING! Stats Cache for id " + cacheId + " don't exist.";

		Map<Integer, AtomicInteger> resourcesPerTile = statsCacheResourcesPerTileByCacheId.get(cacheId);
		Map<Integer, AtomicInteger> occurrencesPerTile = statsCacheTotalOccurrsByCacheId.get(cacheId);
		Map<Integer, Tile> tileByHashCode = tileByHashCodeByCacheId.get(cacheId);

		StringBuilder sb = new StringBuilder(42000); // 42k chars (bytes)
		int topNusedTiles = ExtProperties.getInt(ExtProperties.TOP_N_USED_TILES);

		sb.append("##-----------------------------------------------------------------------------##").append(System.lineSeparator());
		sb.append("##  [TOP ").append(topNusedTiles).append("] Tiles in Resources under cache id: ").append(cacheId).append(System.lineSeparator());
		sb.append("##-----------------------------------------------------------------------------##").append(System.lineSeparator());
		List<Entry<Integer, AtomicInteger>> sortedTopNresourcesPerTile = resourcesPerTile.entrySet()
                .stream()
                .sorted((entry1, entry2) -> entry2.getValue().get() - entry1.getValue().get())
                .limit(topNusedTiles)
                .collect(Collectors.toList());
		for (Entry<Integer, AtomicInteger> entry : sortedTopNresourcesPerTile) {
			String tileDataArrayStr = tileDataToStr(tileByHashCode.get(entry.getKey()));
			String dataStr = String.format("%-12d %4d  %s", entry.getKey().intValue(), entry.getValue().intValue(), tileDataArrayStr);
			sb.append(dataStr).append(System.lineSeparator());
		}

		sb.append("##-----------------------------------------------------------------------------##").append(System.lineSeparator());
		sb.append("##  [TOP ").append(topNusedTiles).append("] Tiles total in all Resources under cache id: ").append(cacheId).append(System.lineSeparator());
		sb.append("##-----------------------------------------------------------------------------##").append(System.lineSeparator());
		List<Entry<Integer, AtomicInteger>> sortedTopNoccursPerTile = occurrencesPerTile.entrySet()
                .stream()
                .sorted((entry1, entry2) -> entry2.getValue().get() - entry1.getValue().get())
                .limit(topNusedTiles)
                .collect(Collectors.toList());
		for (Entry<Integer, AtomicInteger> entry : sortedTopNoccursPerTile) {
			String tileDataArrayStr = tileDataToStr(tileByHashCode.get(entry.getKey()));
			String dataStr = String.format("%-12d %4d  %s", entry.getKey().intValue(), entry.getValue().intValue(), tileDataArrayStr);
			sb.append(dataStr).append(System.lineSeparator());
		}

//		sb.append("##-----------------------------------------------------------------##").append(System.lineSeparator());
//		sb.append("##  Tiles in Resources under cache id: ").append(cacheId).append(System.lineSeparator());
//		sb.append("##-----------------------------------------------------------------##").append(System.lineSeparator());
//		for (Entry<Integer, AtomicInteger> entry : resourcesPerTile.entrySet()) {
//			String dataStr = String.format("%-12d %4d", entry.getKey().intValue(), entry.getValue().get());
//			sb.append(dataStr).append(System.lineSeparator());
//		}
//
//		sb.append("##-----------------------------------------------------------------##").append(System.lineSeparator());
//		sb.append("##  Tiles total in all Resources under cache id: ").append(cacheId).append(System.lineSeparator());
//		sb.append("##-----------------------------------------------------------------##").append(System.lineSeparator());
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
		} catch (IOException e) {
			System.out.println("ERROR! Couldn't save file " + fileDest + ". " + e.getMessage());
			return null;
		}
	}

	private static void writeBytesToFile(byte[] data, String fileName) throws IOException {
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
		} catch (IOException e) {
		}
	}
}
