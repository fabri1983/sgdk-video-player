package sgdk.rescomp.processor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import sgdk.rescomp.Compiler;
import sgdk.rescomp.Processor;
import sgdk.rescomp.Resource;
import sgdk.rescomp.resource.ImageStripsCommonTilesRange;
import sgdk.rescomp.tool.CommonTilesRangeManager;
import sgdk.rescomp.tool.TilesCacheManager;
import sgdk.rescomp.type.CompressionCustom;
import sgdk.tool.FileUtil;
import sgdk.tool.StringUtil;

public class ImageStripsCommonTilesRangeProcessor implements Processor
{
	public static final String resId = "IMAGE_STRIPS_COMMON_TILES_RANGE";

    @Override
    public String getId()
    {
        return resId;
    }

    @Override
    public Resource execute(String[] fields) throws Exception
    {
		if (fields.length < 7)
		{
			System.out.println("Wrong " + resId + " definition");
			System.out.println(resId + " name enable \"baseFile\" stripsPerImg baseImgsNum compressionCustom [tilesCacheId minCommonTilesNum]");
			System.out.println("  commonTilesId      Resource id, used in other processors to indicate the use of this outcomming. Eg: commonTiles_movie1");
			System.out.println("  enable             Enables or disables the use of this processor. TRUE or FALSE.");
			System.out.println("  baseFile           Path of the first strip for input RGB image file with palettes (BMP or PNG image). Eg: \"res/rgb/frame_12_0.png\" or \"res/rgb/frame_12_0_RGB.png\"");
			System.out.println("  stripsPerImg       How many strips is the final image composed of. Eg: 21. It means there are frame_12_0.png, frame_12_1.png, ... frame_12_20.png");
			System.out.println("  baseImgsNum        How many images with same base name Eg: 100. It means there are 100 frame_<...>.png images (without counting the strips).");
			System.out.println("  compressionCustom  Compression parameter. Accepted values:");
			for (CompressionCustom cc : CompressionCustom.values())
				System.out.println("                       " + cc.getValue());
			System.out.println("  tilesCacheId       Set an id (case insensitive) to identify and re use the same cache of tiles along other resources.");
			System.out.println("                       Use NONE or NULL to disable the use of the tile cache.");
			System.out.println("                       Use " + TilesCacheStatsEnablerProcessor.resId + " and " + TilesCacheStatsPrinterProcessor.resId + " with the same id to collect data and print them (file or console).");
			System.out.println("                       You need to collect the stats in order to create the cache file and use it in a future run to effectivelly let the tilemaps use the cache.");
			System.out.println("  minCommonTilesNum  Min amount of common tiles considered to extract when comparing contigous frames.");
			return null;
		}

        // get the commonTilesId as the resource id
        String originalCommonTilesId_keepCase = fields[1];
        if (originalCommonTilesId_keepCase == null || originalCommonTilesId_keepCase.isEmpty() || originalCommonTilesId_keepCase.isBlank())
        	throw new IllegalArgumentException("commonTilesId is invalid");
        String commonTilesRangeId = originalCommonTilesId_keepCase.toUpperCase();

        // enable value
 		boolean enable = "TRUE".equals(fields[2].toUpperCase());

        // get input base file
        String baseFile = FileUtil.adjustPath(Compiler.resDir, fields[3]);
        File baseFileDesc = new File(baseFile);
        String baseFileName = baseFileDesc.getName();
        String baseFileAbsPath = baseFileDesc.getAbsolutePath().replace(baseFileName, "");
        Matcher baseFileNameMatcher = CommonTilesRangeManager.stripsBaseFileNamePattern.matcher(baseFileName);
		if (!baseFileNameMatcher.matches()) {
			throw new IllegalArgumentException("baseFile doesn't match expected pattern.");
		}

		// get number of strips
        int stripsPerImg = StringUtil.parseInt(fields[4], 0);

		// get number of base images
        int baseImgsNum = StringUtil.parseInt(fields[5], 0);

        // get custom compression value for tileset
        CompressionCustom compressionCustom = CompressionCustom.from(fields[6]);

        // tileCacheId
        String tilesCacheId = null; // null or empty string is considered as an invalid tile cache id
        if (fields.length >= 8) {
        	String valueId = fields[7].toUpperCase();
        	if (!"NONE".equals(valueId) && !"NULL".equals(valueId)) {
        		tilesCacheId = valueId;
        		TilesCacheManager.createStatsCacheIfNotExist(tilesCacheId);
        	}
        }

        // get minCommonTilesNum value
        int minCommonTilesNum = 10;
        if (fields.length >= 9) {
        	minCommonTilesNum = StringUtil.parseInt(fields[8], 1);
        }

        // generate the list of strip files
        List<List<String>> allStripsInList = generateAllFilesInForStrips(baseFileAbsPath, baseFileName, baseFileNameMatcher, stripsPerImg, baseImgsNum);

        return new ImageStripsCommonTilesRange(commonTilesRangeId, allStripsInList, tilesCacheId, minCommonTilesNum, enable, compressionCustom);
    }

	private List<List<String>> generateAllFilesInForStrips(String absPath, String baseFileName, Matcher baseFileNameMatcher, int stripsPerImg, int baseImgsNum)
	{
		// Eg: mv_frame_47_0_RGB.png
        String baseName = baseFileName.substring(0, baseFileNameMatcher.start(1)); // mv_frame_
        String fileNum = baseFileNameMatcher.group(1); // 47
        String stripNum = baseFileNameMatcher.group(2); // 0
        String ending = baseFileName.substring(baseFileNameMatcher.end(2), baseFileName.length()); //_RGB.png

        List<List<String>> allLists = new ArrayList<>(baseImgsNum);
        int startingFileNum = Integer.parseInt(fileNum);
        int endinFileNum = startingFileNum + baseImgsNum;
        for (int k = startingFileNum; k < endinFileNum; k++) {
	        int startingStrip = Integer.parseInt(stripNum);
	        int endingStrip = startingStrip + stripsPerImg;
	        List<String> sortedFiles = new ArrayList<>(stripsPerImg);
	        for (int i = startingStrip; i < endingStrip; i++) {
	            String newFilename = absPath + baseName + String.format("%d", k) + "_" + String.format("%d", i) + ending;
	            sortedFiles.add(newFilename);
	        }

	        allLists.add(sortedFiles);
        }

        return allLists;
	}

//	public static void main(String[] args) throws Exception
//	{
//		ImageStripsCommonTilesRangeProcessor p = new ImageStripsCommonTilesRangeProcessor();
//		String[] fields_test_A = {
//				resId, "mv_frames_common_tiles", "TRUE", "C:\\MyProjects\\VSCode\\sgdk\\sgdk-video-player-main\\res\\rgb\\frame_1_0_RGB.png", 
//				"24", "254", "LZ4W", "TilesCache_Movie1", "10"
//			};
//		p.execute(fields_test_A);
//	}
}
