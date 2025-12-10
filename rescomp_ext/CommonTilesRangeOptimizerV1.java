package sgdk.rescomp.tool;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import sgdk.rescomp.resource.TilesetOriginalCustom;
import sgdk.rescomp.type.Basics.Compression;
import sgdk.rescomp.type.Basics.TileEquality;
import sgdk.rescomp.type.Basics.TileOptimization;
import sgdk.rescomp.type.Basics.TileOrdering;
import sgdk.rescomp.type.CommonTilesRange;
import sgdk.rescomp.type.CompressionCustom;
import sgdk.rescomp.type.Tile;
import sgdk.tool.ImageUtil;
import sgdk.tool.ImageUtil.BasicImageInfo;

public class CommonTilesRangeOptimizerV1 {

	public static List<CommonTilesRange> generateOptimizedCommonTiles (List<List<String>> allStripsInList, String tilesCacheId,
			int minCommonTilesNum) throws InterruptedException, ExecutionException
	{
		// final int maxCommonTilesNum = ExtProperties.getInt(ExtProperties.MAX_TILESET_NUM_FOR_MAP_BASE_TILE_INDEX);
		// Min number of consecutive images we grab to analyze
		final int minRange = 3; // Use odd number >= 3 due to the nature of two-buffers swapping solution used by the video player
		// Max number of consecutive images we grab to analyze
		final int maxRange = 12;

		final int processors = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
		ExecutorService executor = Executors.newFixedThreadPool(processors);

		int taskCount = allStripsInList.size() - (minRange - 1);
		CompletionService<List<CommonTilesRange>> completionTasks = new ExecutorCompletionService<>(executor);

		System.out.println("Calculating common tiles between all possible [" + minRange + "," + maxRange
				+ "] consecutives images from a total of " + allStripsInList.size() + " images...");

		for (int i = 0; i < taskCount; i++) {
			final int startIdx = i;

			completionTasks.submit(() -> {
				final int finalIdx = Math.min(startIdx + maxRange - 1, allStripsInList.size() - 1);
				// System.out.println("Calculating common tiles for range [" + startIdx + ", " + finalIdx + "]");
				List<CommonTilesRange> localList = new ArrayList<>();

				// Extract the starting image number
				String startingImgPath = allStripsInList.get(startIdx).get(0);
				File baseStartingFileDesc = new File(startingImgPath);
		        String baseStartingFileName = baseStartingFileDesc.getName();
				Matcher startingImgNumMatcher = CommonTilesRangeManager.stripsBaseFileNamePattern.matcher(baseStartingFileName);
				startingImgNumMatcher.matches();
				int startingImgNumInName = Integer.parseInt(startingImgNumMatcher.group(1));

				for (int endIdx = startIdx + minRange - 1; endIdx <= finalIdx; endIdx++) {
					// Given the nature of two-buffers swapping solution for the video player we have to capture always 
					// an odd amount of consecutive images. Except when endIdx == finalIdx 
					if (endIdx != finalIdx && (endIdx - startIdx + 1) % 2 == 0)
						continue;

					// calculate common tiles between startingImgIdx and endingImgIdx (inclusive)
					List<Tile> tiles = getCommonTiles(startIdx, endIdx, allStripsInList, tilesCacheId, minCommonTilesNum);
					int commonTilesNum = tiles.size();
					if (commonTilesNum >= minCommonTilesNum) {

						// Extract the ending image number
						String endingImgPath = allStripsInList.get(endIdx).get(0);
						File baseEndingFileDesc = new File(endingImgPath);
				        String baseEndingFileName = baseEndingFileDesc.getName();
						Matcher endingImgNumMatcher = CommonTilesRangeManager.stripsBaseFileNamePattern.matcher(baseEndingFileName);
						endingImgNumMatcher.matches();
						int endingImgNumInName = Integer.parseInt(endingImgNumMatcher.group(1));

						localList.add(new CommonTilesRange(commonTilesNum, startIdx, endIdx, tiles, startingImgNumInName, endingImgNumInName));
					}
				}

				return localList;
			});
		}

		executor.shutdown();

		// Collect results in completion order while tracking progress
		List<CommonTilesRange> allCommonTilesRanges = new ArrayList<>();
		System.out.print("Progress:   0.0%");  // Initial display with padding
		System.out.flush();

		for (int i = 1; i <= taskCount; i++) {
			Future<List<CommonTilesRange>> completedFuture = completionTasks.take(); // waits for next completed
			allCommonTilesRanges.addAll(completedFuture.get());

		    double progress = (i * 100.0) / taskCount;
		    // \r brings cursor back to beginning of line. It doesn't work in Eclipse's Console view.
		    System.out.printf("\rProgress: %5.1f%%", progress);  // 5 characters wide, 1 decimal place
		    System.out.flush();
		}

		System.out.println(" Done.");

		// Sort by startingImgIdx in ascending order, then by getEndingImgIdx in ascending order
	    allCommonTilesRanges.sort((a, b) -> {
	        if (a.getStartingImgIdx() != b.getStartingImgIdx()) {
	            return Integer.compare(a.getStartingImgIdx(), b.getStartingImgIdx());
	        }
	        // For same starting index, prefer larger ranges (higher ending index)
	        return Integer.compare(a.getEndingImgIdx(), b.getEndingImgIdx());
	    });
	    //allCommonTilesRanges.forEach(System.out::println);

	    // Total ranges
	    System.out.println("Total number of ranges: " + allCommonTilesRanges.size());

		// Find Biggest numTiles
		allCommonTilesRanges.stream()
				.max(Comparator.comparingInt(CommonTilesRange::getNumTiles))
				.ifPresent(result -> {
					System.out.println("Biggest numTiles: " + result);
				});

		// Create an optimized list of CommonTilesInFrames such that it maximizes the number of common tiles
		System.out.println("Optimization of common tiles. Strategies: Weighted Interval Scheduling and Disjoint Points. Maximizes sum(numTiles) and longer or better ranges when equal score.");
		List<CommonTilesRange> optimizedRanges_strat_A = optimize_max_sum_numTile_strategy_A(allCommonTilesRanges);
		List<CommonTilesRange> optimizedRanges_strat_B = optimize_max_sum_numTile_strategy_B(allCommonTilesRanges, minRange, allStripsInList.size(), true);
		// sum(numTiles)
		int sum_numTiles_strat_A = optimizedRanges_strat_A.stream()
				.mapToInt(CommonTilesRange::getNumTiles)
				.sum();
		int sum_numTiles_strat_B = optimizedRanges_strat_B.stream()
				.mapToInt(CommonTilesRange::getNumTiles)
				.sum();
		// num of ranges
		int totalRanges_strat_A = optimizedRanges_strat_A.size();
		int totalRanges_strat_B = optimizedRanges_strat_B.size();
		// Choose the better solution
		List<CommonTilesRange> optimizedRanges = null;
		if (sum_numTiles_strat_A < sum_numTiles_strat_B)
			optimizedRanges = optimizedRanges_strat_A;
		else if (sum_numTiles_strat_A > sum_numTiles_strat_B)
			optimizedRanges = optimizedRanges_strat_B;
		else {
			if (totalRanges_strat_A <= totalRanges_strat_B)
				optimizedRanges = optimizedRanges_strat_A;
			else
				optimizedRanges = optimizedRanges_strat_B;
		}

		//System.out.println("Optimization of common tiles. Strategy: Longest Ranges.");
		//List<CommonTilesRange> optimizedRanges = optimize_longest_non_overlapping_ranges(allCommonTilesRanges);

		// Print the optimized result
		//optimizedRanges.forEach(System.out::println);

	    // Total ranges optimized
	    System.out.println("Total number of ranges: " + optimizedRanges.size());

		// sum(numTiles)
		optimizedRanges.stream()
				.mapToInt(CommonTilesRange::getNumTiles)
				.reduce( (a, b) -> a + b)
				.ifPresent(res -> {
					System.out.println("sum(numTiles): " + res);
				});

	    // Find longest range
		optimizedRanges.stream()
				.max(Comparator.comparingInt(t -> t.getEndingImgIdx() - t.getStartingImgIdx()))
				.ifPresent(res -> {
					System.out.println("Longest range: " + (res.getEndingImgIdx() - res.getStartingImgIdx() + 1) + " -> " + res);
				});

		return optimizedRanges;
	}

    private static List<Tile> getCommonTiles (int startingImgIdx, int endingImgIdx, List<List<String>> allStripsInList, 
    		String tilesCacheId, int minCommonTilesNum) throws Exception
    {
    	// Step 1: Create all the tilesets in the given range

    	List<TilesetOriginalCustom> tilesetsList = new ArrayList<>((endingImgIdx - startingImgIdx) + 1);

    	for (int i = startingImgIdx; i <= endingImgIdx; i++) {
    		List<String> stripsFileList = allStripsInList.get(i);

    		File baseFileDesc = new File(stripsFileList.get(0));
            String baseFileName = baseFileDesc.getName();
    		String thisImageName = baseFileName.substring(0, baseFileName.lastIndexOf('.'));

			// finalImageData has no more palette definitions at the top
			byte[] finalImageData = mergeAllStrips(stripsFileList);
			// get info from first strip
			BasicImageInfo strip0Info = ImageUtil.getBasicInfo(stripsFileList.get(0));
			// width and height in pixels
			int w = strip0Info.w;
			// we determine 'h' from data length and 'w' as we can crop image vertically to remove palette data
			int h = finalImageData.length / w;
			// get size in tile
			int wt = w / 8;
			int ht = h / 8;

            // Create a tileset with final tiles: no plain tiles and no cached tiles
			boolean isTempTileset = true;
			TilesetOriginalCustom tilesetTemp = new TilesetOriginalCustom(thisImageName + "_tileset", finalImageData, w, h, 0, 0, wt, ht, 
					TileOptimization.NONE, Compression.NONE, CompressionCustom.NONE, false, isTempTileset, TileOrdering.ROW, tilesCacheId, 
					false, null);

			// If a tileset has less than the minimum tiles expected then we just ignore it
			if (tilesetTemp.getNumTile() < minCommonTilesNum)
				//return Collections.emptyList();
				continue;

			tilesetsList.add(tilesetTemp);
    	}

    	// Step 2: Calculate how many common tiles exist between all the tilesets in tilesetsList, ie: how many tiles intersect.

		Optional<TilesetOriginalCustom> optionalSmallerTileset = tilesetsList.stream()
				.min(Comparator.comparingInt(TilesetOriginalCustom::getNumTile));

		if (optionalSmallerTileset.isEmpty())
			return Collections.emptyList();

		TilesetOriginalCustom smallerTileset = optionalSmallerTileset.get();

		boolean[] isCommonToAll = new boolean[smallerTileset.getNumTile()];
		Arrays.fill(isCommonToAll, true);

		// Check each tile from the first tileset against all other tilesets
		for (int tileIdx = 0; tileIdx < smallerTileset.getNumTile(); tileIdx++) {
			Tile referenceTile = smallerTileset.get(tileIdx);

			// Check if referenceTile exists in every other tileset
			for (int tilesetIdx = 1; tilesetIdx < tilesetsList.size(); tilesetIdx++) {
				TilesetOriginalCustom currentTileset = tilesetsList.get(tilesetIdx);
				List<Tile> currentTiles = currentTileset.tiles;

				boolean foundInCurrentTileset = false;

				// Search for referenceTile in the current tileset
				for (Tile currentTile : currentTiles) {
					if (referenceTile.getEquality(currentTile) != TileEquality.NONE) {
						foundInCurrentTileset = true;
						break;
					}
				}

				// If not found in any tileset, mark as not common
				if (!foundInCurrentTileset) {
					isCommonToAll[tileIdx] = false;
					break;
				}
			}
		}

		// Save every common Tile
		List<Tile> currentTiles = smallerTileset.tiles;
		List<Tile> commonTiles = new ArrayList<>();
		for (int i=0; i < currentTiles.size(); ++i) {
			if (isCommonToAll[i]) {
				commonTiles.add(currentTiles.get(i));
			}
		}

		return commonTiles;
	}

    /**
     * Given ranges with same startingImgIdx, choose the range with the bigger endingImgIdx. No overlapping ranges.</br>
     * This strategy does not maximize the sum of numTiles. It simply chooses the longest possible range starting from each image.</br>
     * @param rangesList
     * @return
     */
    private static List<CommonTilesRange> optimize_longest_non_overlapping_ranges (List<CommonTilesRange> rangesList)
    {
		// group by starting index and keep the one with max ending index
		Map<Integer, CommonTilesRange> longestPerStart = rangesList.stream()
				.collect(Collectors.toMap(CommonTilesRange::getStartingImgIdx, r -> r,
						(r1, r2) -> r1.getEndingImgIdx() >= r2.getEndingImgIdx() ? r1 : r2));

		List<CommonTilesRange> ranges = new ArrayList<>(longestPerStart.values());

		// Sort by end index
		ranges.sort(Comparator.comparingInt(CommonTilesRange::getEndingImgIdx));

		int n = ranges.size();
		int[] dp = new int[n];
		int[] parent = new int[n];

		// Precompute p(i): rightmost interval that ends before ranges[i] starts
		int[] p = new int[n];
		for (int i = 0; i < n; i++) {
			p[i] = -1;
			for (int j = i - 1; j >= 0; j--) {
				if (ranges.get(j).getEndingImgIdx() < ranges.get(i).getStartingImgIdx()) {
					p[i] = j;
					break;
				}
			}
		}

		// DP to maximize total length
		for (int i = 0; i < n; i++) {
			int length = ranges.get(i).getEndingImgIdx() - ranges.get(i).getStartingImgIdx() + 1;

			int include = length + (p[i] != -1 ? dp[p[i]] : 0);
			int exclude = (i > 0 ? dp[i - 1] : 0);

			if (include > exclude) {
				dp[i] = include;
				parent[i] = p[i];
			} else {
				dp[i] = exclude;
				parent[i] = (i > 0 ? i - 1 : -1);
			}
		}

		// Backtrack to recover chosen intervals
		List<CommonTilesRange> result = new ArrayList<>();
		int i = n - 1;
		while (i >= 0) {
			int length = ranges.get(i).getEndingImgIdx() - ranges.get(i).getStartingImgIdx() + 1;
			int include = length + (p[i] != -1 ? dp[p[i]] : 0);
			int exclude = (i > 0 ? dp[i - 1] : 0);

			if (include >= exclude) {
				result.add(ranges.get(i));
				i = p[i];
			} else {
				i = i - 1;
			}
		}

		Collections.reverse(result);
		return result;
    }

	/**
	 * Maximizes the use of numTiles (common tiles in a range) along the different ranges.</br>
	 * It might contain objects whose range overlaps fully or partially with startingImgIdx and endingImgIdx in other object's range, 
	 * or might be completely inside another bigger object's range, so in that situation it may be better to modify the ranges as long as 
	 * the numTiles are kept maximum.</br>
	 * This function produces an optimal subset of non-overlapping ranges that maximizes sum(numTiles) by using Weighted Interval Scheduling (Dynamic Programming).</br>
	 * - Removes dominated intervals inside larger ones</br>
	 * - Avoids partially overlapping intervals unless beneficial</br>
	 * - Always selects the globally best combination</br>
	 * - Equivalent to finding best scene-reuse chains without double-counting</br>
	 * @param rangesList
	 * @return
	 */
	private static List<CommonTilesRange> optimize_max_sum_numTile_strategy_A (List<CommonTilesRange> rangesList)
	{
		if (rangesList == null || rangesList.isEmpty()) {
			return Collections.emptyList();
		}

		/*
		Using weighted interval scheduling (dynamic programming).
		This solves the requirement:
			- Select ranges (CommonTilesInFrames)
			- Avoid overlapping (unless the user prefers otherwise)
			- Maximize total numTiles
			- Works even when some ranges are inside others
			- Produces an optimal subset (no redundant or dominated ranges)
		If your intended meaning of “maximize use of tiles” is select non-overlapping ranges whose total numTiles is maximum, this is the right algorithm.
		*/

		// 1. Sort by ending index (required for weighted interval scheduling)
		List<CommonTilesRange> intervals = new ArrayList<>(rangesList);
		intervals.sort(Comparator.comparingInt(CommonTilesRange::getEndingImgIdx));

		int n = intervals.size();

		// 2. p(i): For each interval i, find the rightmost interval j < i that does not overlap i
		int[] p = new int[n];
		for (int i = 0; i < n; i++) {
			CommonTilesRange current = intervals.get(i);
			int compatible = -1;

			// binary search possible, but linear is fine for moderate sizes
			for (int j = i - 1; j >= 0; j--) {
				if (intervals.get(j).getEndingImgIdx() < current.getStartingImgIdx()) {
					compatible = j;
					break;
				}
			}
			p[i] = compatible;
		}

		// 3. DP table: dp[i] = max weight using intervals[0..i]
		int[] dp = new int[n];

		for (int i = 0; i < n; i++) {
			int includeWeight = intervals.get(i).getNumTiles();
			if (p[i] != -1) {
				includeWeight += dp[p[i]];
			}
			int excludeWeight = (i == 0 ? 0 : dp[i - 1]);
			dp[i] = Math.max(includeWeight, excludeWeight);
		}

		// 4. Recover the selected intervals
		List<CommonTilesRange> result = new ArrayList<>();
		int i = n - 1;

		while (i >= 0) {
			int includeWeight = intervals.get(i).getNumTiles() + (p[i] != -1 ? dp[p[i]] : 0);
			int excludeWeight = (i == 0 ? 0 : dp[i - 1]);

			if (includeWeight >= excludeWeight) {
				// include interval i
				result.add(intervals.get(i));
				i = p[i]; // jump to its predecessor
			} else {
				i -= 1; // skip interval i
			}
		}

		// 5. Result is reversed due to backtracking
		Collections.reverse(result);

		return result;
	}

	/**
	 * Maximizes the use of numTiles (common tiles in a range) along the different ranges.</br>
	 * It might contain objects whose range overlaps fully or partially with startingImgIdx and endingImgIdx in other object's range, 
	 * or might be completely inside another bigger object's range, so in that situation it may be better to modify the ranges as long as 
	 * the numTiles are kept maximum.</br>
	 * This function produces an optimal subset of non-overlapping ranges by using Disjoint Ranges that maximize sum(numTiles) (Dynamic Programming) 
	 * and prefer longer or better ranges when equal score with Tie-Breakers constraint.</br>
     * @param rangesList       Input list of all candidate ranges
     * @param numberOfImages   Total number of images
     * @param minRange         Minimum allowed range length
     * @param useTieBreakers   Whether to use extended tie-breaking logic
	 * @return
	 */
	private static List<CommonTilesRange> optimize_max_sum_numTile_strategy_B (List<CommonTilesRange> rangesList, int minRange, 
			int numberOfImages, boolean useTieBreakers)
	{
		if (rangesList == null || rangesList.isEmpty()) {
			return Collections.emptyList();
		}

		// --- 1) Build lookup matrix: rangeLookup[start][end] = CommonTilesRange ---
		CommonTilesRange[][] rangeLookup = new CommonTilesRange[numberOfImages][numberOfImages];

		for (CommonTilesRange r : rangesList) {
			int s = r.getStartingImgIdx();
			int e = r.getEndingImgIdx();

			// must satisfy minimum range length
			if ((e - s + 1) >= minRange) {
				rangeLookup[s][e] = r;
			}
		}

		// --- 2) DP arrays ---
		long[] best = new long[numberOfImages];
		int[] choiceStart = new int[numberOfImages];
		Arrays.fill(choiceStart, -1);

		// --- 3) DP ---
		for (int i = 0; i < numberOfImages; i++) {

			long bestVal = (i == 0) ? 0L : best[i - 1];
			int bestStart = -1;

			for (int s = 0; s <= i; s++) {
				CommonTilesRange r = rangeLookup[s][i];
				if (r == null)
					continue;

				long before = (s > 0) ? best[s - 1] : 0L;
				long candidate = before + r.getNumTiles();

				if (candidate > bestVal) {
					// normal improvement
					bestVal = candidate;
					bestStart = s;

				}
				else if (useTieBreakers && candidate == bestVal && bestStart != -1) {
					// --- TIE BREAKERS ---

					CommonTilesRange existing = rangeLookup[bestStart][i];

					int lenCandidate = i - s;
					int lenExisting = i - bestStart;

					// Rule 1: prefer longer interval
					if (lenCandidate > lenExisting) {
						bestStart = s;
					}
					// Rule 2: if same length, prefer more tiles
					else if (lenCandidate == lenExisting) {
						if (r.getNumTiles() > existing.getNumTiles()) {
							bestStart = s;
						}
						// Rule 3: if still tied, earlier start wins
						else if (r.getNumTiles() == existing.getNumTiles() && s < bestStart) {
							bestStart = s;
						}
					}
				}
			}

			best[i] = bestVal;
			choiceStart[i] = bestStart;
		}

		// --- 4) Reconstruction ---
		List<CommonTilesRange> result = new ArrayList<>();
		int i = numberOfImages - 1;

		while (i >= 0) {
			int s = choiceStart[i];
			if (s == -1) {
				i--;
			} else {
				result.add(rangeLookup[s][i]);
				i = s - 1;
			}
		}

		Collections.reverse(result);

		// --- 5) Post-processing merge of adjacent ranges ---
		boolean improved = true;
		while (improved) {
			improved = false;

			for (int idx = 0; idx < result.size() - 1; idx++) {
				CommonTilesRange r1 = result.get(idx);
				CommonTilesRange r2 = result.get(idx + 1);

				if (r1.getEndingImgIdx() + 1 != r2.getStartingImgIdx())
					continue;

				int mergedStart = r1.getStartingImgIdx();
				int mergedEnd = r2.getEndingImgIdx();

				CommonTilesRange merged = rangeLookup[mergedStart][mergedEnd];
				if (merged == null)
					continue;

				int mergedTiles = merged.getNumTiles();
				int sumTiles = r1.getNumTiles() + r2.getNumTiles();

				if (mergedTiles > sumTiles) {
					result.remove(idx + 1);
					result.set(idx, merged);
					improved = true;
					break;
				}
			}
		}

		return result;
	}

	private static byte[] mergeAllStrips (List<String> stripsFileList) throws Exception
    {
		// get tile data per pixel (color position in palette), check image dimension is aligned to tile, remove palette info if any
		byte[] image0 = ImageUtilFast.getImageAs8bpp(stripsFileList.get(0), true, true);
		int stripLength = image0.length;
		// allocate space for bigger image
		final byte[] finalImage = new byte[stripLength * stripsFileList.size()];

		// copy all the strips into finalImage
		for (int i = 0; i < stripsFileList.size(); ++i) {
			String imgFile = stripsFileList.get(i);
			byte[] image = ImageUtilFast.getImageAs8bpp(imgFile, true, true);
			checkImageNotNull(imgFile, image);
			checkImageColorByte(imgFile, image);
			System.arraycopy(image, 0, finalImage, i * stripLength, stripLength);
		}

		return finalImage;
	}

	/**
	 * Happen when we couldn't retrieve palette data from RGB image
	 * 
	 * @param imgFile
	 * @param image
	 */
	private static void checkImageNotNull(String imgFile, byte[] image) {
		if (image == null)
			throw new IllegalArgumentException("RGB image '" + imgFile
					+ "' does not contains palette data (see 'Important note about image format' in the rescomp.txt file");
	}

	/**
	 * b0-b3 = pixel data; b4-b5 = palette index; b7 = priority bit check if image
	 * try to use bit 6 (probably mean that we have too much colors in our image)
	 * 
	 * @param imgFile
	 * @param image
	 */
	private static void checkImageColorByte(String imgFile, byte[] image) {
		for (byte d : image) {
			// bit 6 used ?
			if ((d & 0x40) != 0)
				throw new IllegalArgumentException("'" + imgFile
						+ "' has color index in [64..127] range, IMAGE resource requires image with a maximum of 64 colors");
		}
	}

}
