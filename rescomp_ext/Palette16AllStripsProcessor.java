package sgdk.rescomp.processor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import sgdk.rescomp.Compiler;
import sgdk.rescomp.Processor;
import sgdk.rescomp.Resource;
import sgdk.rescomp.resource.Palette16AllStrips;
import sgdk.rescomp.resource.Palette16AllStripsSplit2;
import sgdk.rescomp.resource.Palette16AllStripsSplit3;
import sgdk.rescomp.tool.CommonTilesRangeManager;
import sgdk.rescomp.tool.Util;
import sgdk.rescomp.type.Basics.Compression;
import sgdk.rescomp.type.CompressionCustom;
import sgdk.rescomp.type.PalettesPositionEnum;
import sgdk.tool.FileUtil;
import sgdk.tool.StringUtil;

public class Palette16AllStripsProcessor implements Processor
{
	private static final String resId = "PALETTE_16_COLORS_ALL_STRIPS";

	@Override
    public String getId()
    {
        return resId;
    }

    @Override
    public Resource execute(String[] fields) throws Exception
    {
        if (fields.length < 6)
        {
            System.out.println("Wrong " + resId + " definition");
            System.out.println(resId + " name baseFile strips palsPosition palsPosition addCompressionField compression compressionCustom addCompressionField");
            System.out.println("  name                 Palette variable name (with all the colors per strip). Eg: pal_frame_22");
            System.out.println("  baseFile             Path of the first strip for input RGB image file with palettes (BMP or PNG image). Eg: \"res/rgb/frame_12_0.png\" or \"res/rgb/frame_12_0_RGB.png\"");
            System.out.println("  strips               How many strips is the final image composed of. Eg: 21. It means there are frame_12_0.png, frame_12_1.png, ... frame_12_20.png");
            System.out.println("  splitChunks          How many chunks is the final palette split into. Default 1 (no split), otherwise 2 or 3.");
            System.out.println("  palsPosition         Possible values (use only one): "
            		+ PalettesPositionEnum.PAL0.getValue() + ", "
            		+ PalettesPositionEnum.PAL1.getValue() + ", "
            		+ PalettesPositionEnum.PAL2.getValue() + ", "
                    + PalettesPositionEnum.PAL3.getValue() + " . It marks the palette location in the image.");
            System.out.println("  togglePalsLocation   If TRUE then every other strip the palettes are grabbed changing locations. If FALSE then it grabs them at fixed position.");
            System.out.println("  compression          Compression type. Accepted values:");
			System.out.println("                         -1 / BEST / AUTO = use best compression");
			System.out.println("                         0 / NONE        = no compression (default)");
			System.out.println("                         1 / APLIB       = aplib library (good compression ratio but slow)");
			System.out.println("                         2 / FAST / LZ4W = custom lz4 compression (average compression ratio but fast)");
			System.out.println("  compressionCustom    Overrides the other compression parameter. Accepted values:");
			for (CompressionCustom cc : CompressionCustom.values())
				System.out.println("                         " + cc.getValue());
			System.out.println("  addCompressionField  Include or exclude the compression field. TRUE or FALSE (default)");
            return null;
        }

        // get resource id
        String id = fields[1];
        
        // get input base file
        String baseFile = FileUtil.adjustPath(Compiler.resDir, fields[2]);
        File baseFileDesc = new File(baseFile);
        String baseFileName = baseFileDesc.getName();
        String baseFileAbsPath = baseFileDesc.getAbsolutePath().replace(baseFileName, "");
        Matcher baseFileNameMatcher = CommonTilesRangeManager.stripsBaseFileNamePattern.matcher(baseFileName);
		if (!baseFileNameMatcher.matches()) {
			throw new IllegalArgumentException("baseFile doesn't match expected pattern.");
		}

		// get number of strips
        int strips = StringUtil.parseInt(fields[3], 0);

        // generate the list of strip files
        List<String> stripsInList = generateFilesInForStrips(baseFileAbsPath, baseFileName, baseFileNameMatcher, strips);

        // get number for split chunks
        int splitChunks = StringUtil.parseInt(fields[4], 1);

        // get palsPosition value
        PalettesPositionEnum palsPosition = PalettesPositionEnum.from(fields[5]);

        // get toggleEvery2Pals value
        boolean togglePalsLocation = Boolean.parseBoolean(fields[6]);

		// get packed value
		Compression compression = Compression.NONE;
		if (fields.length >= 8)
			compression = Util.getCompression(fields[7]);

		// get custom compression value
        CompressionCustom compressionCustom = CompressionCustom.NONE;
        if (fields.length >= 9)
        	compressionCustom = CompressionCustom.from(fields[8]);

        boolean addCompressionField = false;
        if (fields.length >= 10)
        	addCompressionField = Boolean.parseBoolean(fields[9]);

		// add resource file (used for deps generation)
//		Compiler.addResourceFile(fileIn);

		if (splitChunks == 1)
			return new Palette16AllStrips(id, stripsInList, palsPosition, togglePalsLocation, compression, compressionCustom, addCompressionField);
		else if (splitChunks == 2)
			return new Palette16AllStripsSplit2(id, stripsInList, palsPosition, togglePalsLocation, compression, compressionCustom, addCompressionField);
		else
			return new Palette16AllStripsSplit3(id, stripsInList, palsPosition, togglePalsLocation, compression, compressionCustom, addCompressionField);
    }

	private List<String> generateFilesInForStrips(String absPath, String baseFileName, Matcher baseFileNameMatcher, int strips)
	{
		// Eg: mv_frame_47_0_RGB.png
        String baseName = baseFileName.substring(0, baseFileNameMatcher.start(2)); // mv_frame_47_
        String middle = baseFileNameMatcher.group(2); // 0
        String ending = baseFileName.substring(baseFileNameMatcher.end(2), baseFileName.length()); // _RGB.png
        int startingStrip = Integer.parseInt(middle);
        int length = startingStrip + strips;

        List<String> sortedFiles = new ArrayList<>(strips);
        for (int i = startingStrip; i < length; i++) {
            String newFilename = absPath + baseName + String.format("%d", i) + ending;
            sortedFiles.add(newFilename);
        }

		return sortedFiles;
	}
}
