package sgdk.rescomp.processor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import sgdk.rescomp.Compiler;
import sgdk.rescomp.Processor;
import sgdk.rescomp.Resource;
import sgdk.rescomp.resource.Palette32AllStrips;
import sgdk.rescomp.resource.Palette32AllStripsSplit2;
import sgdk.rescomp.resource.Palette32AllStripsSplit3;
import sgdk.rescomp.tool.Util;
import sgdk.rescomp.type.Basics.Compression;
import sgdk.rescomp.type.CompressionCustom;
import sgdk.rescomp.type.PalettesPositionEnum;
import sgdk.tool.FileUtil;
import sgdk.tool.StringUtil;

public class Palette32AllStripsProcessor implements Processor
{
	private static final String resId = "PALETTE_32_COLORS_ALL_STRIPS";

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
            System.out.println(resId + " name baseFile strips palsPosition palsPosition");
            System.out.println("  name                Palette variable name (with all the colors per strip). Eg: pal_frame_22");
            System.out.println("  baseFile            Path of the first strip for input RGB image file with palettes (BMP or PNG image). Eg: \"res/rgb/frame_12_0.png\" or \"res/rgb/frame_12_0_RGB.png\"");
            System.out.println("  strips              How many strips is the final image composed of. Eg: 21. It means there are frame_12_0.png, frame_12_1.png, ... frame_12_20.png");
            System.out.println("  splitChunks         How many chunks is the final palette split into. Default 1 (no split), otherwise 2 or 3.");
            System.out.println("  palsPosition        Two possible values: " + PalettesPositionEnum.PAL0PAL1.getValue() + " or " + PalettesPositionEnum.PAL2PAL3.getValue() + " . It marks the 2 palettes location in the image.");
            System.out.println("  togglePalsLocation  If TRUE then every other strip the palettes are grabbed changing locations. If FALSE then it grabd them at fixed position.");
            System.out.println("  compression         compression type, accepted values:");
			System.out.println("                       0 / NONE        = no compression (default)");
			System.out.println("                       2 / FAST / LZ4W = custom lz4 compression (average compression ratio but fast)");
			System.out.println("  compressionCustom   overrides the compression parameter. Accepted values:");
			System.out.println("                       " + CompressionCustom.NONE.getValue());
            return null;
        }

        // get resource id
        String id = fields[1];
        
        // get input base file
        String baseFile = FileUtil.adjustPath(Compiler.resDir, fields[2]);
        File baseFileDesc = new File(baseFile);
        String baseFileName = baseFileDesc.getName();
        String baseFileAbsPath = baseFileDesc.getAbsolutePath().replace(baseFileName, "");
        Pattern baseFileNamePattern = Pattern.compile("^\\w+_(\\d+)_(\\d+)(_RGB)?\\.(png|bmp)$", Pattern.CASE_INSENSITIVE);
        Matcher baseFileNameMatcher = baseFileNamePattern.matcher(baseFileName);
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
		// Only Compression.NONE or Compression.LZW4 (FAST)
		if (compression == Compression.APLIB)
			compression = Compression.LZ4W;

		// get custom compression value
        CompressionCustom compressionCustom = CompressionCustom.NONE;
        if (fields.length >= 9)
        	compressionCustom = CompressionCustom.from(fields[8]);

		// add resource file (used for deps generation)
//		Compiler.addResourceFile(fileIn);

		if (splitChunks == 1)
			return new Palette32AllStrips(id, stripsInList, palsPosition, togglePalsLocation, compression, compressionCustom);
		else if (splitChunks == 2)
			return new Palette32AllStripsSplit2(id, stripsInList, palsPosition, togglePalsLocation, compression, compressionCustom);
		else
			return new Palette32AllStripsSplit3(id, stripsInList, palsPosition, togglePalsLocation, compression, compressionCustom);
    }

	private List<String> generateFilesInForStrips(String absPath, String baseFileName, Matcher baseFileNameMatcher, int strips)
	{
        String prefix = baseFileName.substring(0, baseFileNameMatcher.start(2)); // Extract the prefix part
        //String middle = baseFile.substring(baseFileNameMatcher.start(2), baseMatcher.end(2)); // Extract the middle number part
        String suffix = baseFileName.substring(baseFileNameMatcher.end(2), baseFileName.length()); // Extract the suffix (file extension) part
        int startingStrip = Integer.parseInt(baseFileNameMatcher.group(2));
        int length = startingStrip + strips;

        List<String> sortedFiles = new ArrayList<>(strips);
        for (int i = startingStrip; i < length; i++) {
            String newFilename = absPath + prefix + String.format("%d", i) + suffix;
            sortedFiles.add(newFilename);
        }

		return sortedFiles;
	}

//	public static void main(String[] args) throws Exception
//	{
//		Palette32AllStripsProcessor p = new Palette32AllStripsProcessor();
//		String[] fields = {
//			resId, "pal_frame_47", "C:\\MyProjects\\VSCode\\sgdk\\sgdk-video-player-main\\res\\rgb\\frame_47_0_RGB.png", 
//			"22", "1", PalettesPositionEnum.PAL0PAL1.getValue(), "TRUE", "FAST", "NONE"
//		};
//		p.execute(fields);
//	}
}
