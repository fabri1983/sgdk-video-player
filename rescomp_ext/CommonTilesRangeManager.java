package sgdk.rescomp.tool;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import sgdk.rescomp.type.CommonTilesRange;

public class CommonTilesRangeManager {

	public static final Pattern stripsBaseFileNamePattern = Pattern.compile("^[A-Za-z_]+_(\\d+)_(\\d+)(_RGB)?\\.(png|bmp)$", Pattern.CASE_INSENSITIVE);

	private static Map<String, List<CommonTilesRange>> optimizedRangesByResId_map = new HashMap<>();

	public static void saveForResId (String resId, List<CommonTilesRange> optimizedCommonTiles) {
		if (resId != null && !resId.isBlank()) {
			if (optimizedCommonTiles != null && !optimizedCommonTiles.isEmpty()) {
				// Warn in case the key already exists
				if (optimizedRangesByResId_map.containsKey(resId)) {
					System.out.println("[WARNING] There is already a key with name " + resId + ". At class " + CommonTilesRangeManager.class.getSimpleName());
				}
				optimizedRangesByResId_map.put(resId, optimizedCommonTiles);
			}
		}
	}

	public static List<CommonTilesRange> getFromResId (String resId) {
		if (resId != null && !resId.isBlank()) {
			if (optimizedRangesByResId_map.containsKey(resId)) {
				return optimizedRangesByResId_map.get(resId);
			}
		}
		return Collections.emptyList();
	}

	

	public static CommonTilesRange findRangeForImageIdx (List<CommonTilesRange> rangeList, int imgIdx) {
		// commonTiles should be already sorted by startingImgIdx

		int low = 0;
		int high = rangeList.size() - 1;

		while (low <= high) {
			int mid = (low + high) >>> 1; // fast divide-by-2
			CommonTilesRange ct = rangeList.get(mid);

			if (imgIdx < ct.getStartingImgIdx()) {
				high = mid - 1;
			} else if (imgIdx > ct.getEndingImgIdx()) {
				low = mid + 1;
			} else {
				// FOUND: imageNum is within [startingImgIdx, endingImgIdx]
				return ct;
			}
		}

		return null; // not found
	}

	public static CommonTilesRange findRangeForImageNum (List<CommonTilesRange> rangeList, int imgNum) {
		// commonTiles should be already sorted by startingImgNumInName

		int low = 0;
		int high = rangeList.size() - 1;

		while (low <= high) {
			int mid = (low + high) >>> 1; // fast divide-by-2
			CommonTilesRange ct = rangeList.get(mid);

			if (imgNum < ct.getStartingImgNumInName()) {
				high = mid - 1;
			} else if (imgNum > ct.getEndingImgNumInName()) {
				low = mid + 1;
			} else {
				// FOUND: imageNum is within [startingImgNumInName, endingImgNumInName]
				return ct;
			}
		}

		return null; // not found
	}

}
