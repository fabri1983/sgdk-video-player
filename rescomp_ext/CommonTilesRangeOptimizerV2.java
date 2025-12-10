package sgdk.rescomp.tool;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;

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

public class CommonTilesRangeOptimizerV2 {

	/**
	 * Computes the ranges with most common tiles.
	 * 
	 * @param allStripsInList
	 * @param tilesCacheId
	 * @param minCommonTilesNum
	 * @return
	 * @throws Exception
	 */
	public static List<CommonTilesRange> generateOptimizedCommonTiles (List<List<String>> allStripsInList, String tilesCacheId,
			int minCommonTilesNum) throws Exception
	{
		final int minRange = 3; // Use odd number >= 3 due to the nature of two-buffers swapping solution used by the video player
		
		List<TilesetOriginalCustom> tilesetsList = new ArrayList<>(allStripsInList.size());

		System.out.println("Calculating common tiles between images ranges with a min size of " + minRange 
				+ " consecutives images from a total of " + allStripsInList.size() + " images.");

		for (List<String> stripsFileList : allStripsInList) {

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

		System.out.println("Optimization of common tiles. Strategies: Maximum Tile Reuse.");
		// Compute the optimized ranges
		List<CommonTilesRange> optimizedRanges = computeOptimzalRanges(tilesetsList, minRange, minCommonTilesNum);

		// Print the optimized result
		optimizedRanges.forEach(System.out::println);

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

	private static List<CommonTilesRange> computeOptimzalRanges (List<TilesetOriginalCustom> tilesetsList, int minRange, int minCommonTilesNum)
	{
		int n = tilesetsList.size();
		if (n == 0)
			return Collections.emptyList();

		// Step 1: Enumerate all valid ranges
		List<CommonTilesRange> allRanges = new ArrayList<>();

		for (int startIdx = 0; startIdx < n; startIdx++) {
			List<Tile> common = new ArrayList<>(tilesetsList.get(startIdx).tiles);

			Matcher startMatcher = TilesetOriginalCustom.imageNameStripsFromResIdPattern
					.matcher(tilesetsList.get(startIdx).id);
			startMatcher.matches();
			int startingImgNum = Integer.parseInt(startMatcher.group(1));

			for (int endIdx = startIdx; endIdx < n; endIdx++) {
				if (endIdx > startIdx) {
					common = intersectTiles(common, tilesetsList.get(endIdx).tiles);
					if (common.isEmpty())
						break;
				}

				int frames = endIdx - startIdx + 1;

				if (frames >= minRange && common.size() >= minCommonTilesNum) {
					Matcher endMatcher = TilesetOriginalCustom.imageNameStripsFromResIdPattern
							.matcher(tilesetsList.get(endIdx).id);
					endMatcher.matches();
					int endingImgNum = Integer.parseInt(endMatcher.group(1));

					allRanges.add(new CommonTilesRange(common.size(), startIdx, endIdx, new ArrayList<>(common),
							startingImgNum, endingImgNum));
				}
			}
		}

		if (allRanges.isEmpty())
			return Collections.emptyList();

		// Sort by ending index (important for DP)
		allRanges.sort(Comparator.comparingInt(CommonTilesRange::getEndingImgIdx)
				.thenComparingInt(CommonTilesRange::getStartingImgIdx));

		// Step 2: DP Arrays
		long[] dp = new long[n]; // best score ending at frame i
		int[] choice = new int[n]; // index of chosen range in allRanges
		int[] prev = new int[n]; // previous frame pointer

		Arrays.fill(choice, -1);
		Arrays.fill(prev, -1);

		int rangeIndex = 0;

		for (int i = 0; i < n; i++) {
			// By default carry previous DP state
			dp[i] = (i > 0) ? dp[i - 1] : 0;
			prev[i] = i - 1;

			// Consider all ranges ending at i
			while (rangeIndex < allRanges.size() && allRanges.get(rangeIndex).getEndingImgIdx() == i) {

				CommonTilesRange r = allRanges.get(rangeIndex);
				int s = r.getStartingImgIdx();

				int frames = r.getEndingImgIdx() - r.getStartingImgIdx() + 1;
				long score = frames * (long) r.getNumTiles();

				long candidate = score + (s > 0 ? dp[s - 1] : 0);

				if (candidate > dp[i]) {
					dp[i] = candidate;
					choice[i] = rangeIndex;
					prev[i] = s - 1;
				}

				rangeIndex++;
			}
		}

		// Step 3: Backtrack reconstruction
		List<CommonTilesRange> result = new ArrayList<>();
		int idx = n - 1;

		while (idx >= 0) {
			if (choice[idx] == -1) {
				idx = prev[idx];
				continue;
			}

			CommonTilesRange r = allRanges.get(choice[idx]);
			result.add(r);
			idx = prev[idx];
		}

		Collections.reverse(result);
		return result;
	}

	/**
	 * Compute intersection between lists of tiles. Preserves order from listA.
	 */
	private static List<Tile> intersectTiles (List<Tile> listA, List<Tile> listB)
	{
		List<Tile> result = new ArrayList<>();
		for (Tile a : listA) {
			for (Tile b : listB) {
				if (a.getEquality(b) != TileEquality.NONE) {
					result.add(a);
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
