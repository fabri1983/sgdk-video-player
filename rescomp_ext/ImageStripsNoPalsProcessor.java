package sgdk.rescomp.processor;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import sgdk.rescomp.Compiler;
import sgdk.rescomp.Processor;
import sgdk.rescomp.Resource;
import sgdk.rescomp.resource.ImageStripsNoPals;
import sgdk.rescomp.resource.ImageStripsNoPalsTilesetSplit2;
import sgdk.rescomp.resource.ImageStripsNoPalsTilesetSplit3;
import sgdk.rescomp.tool.Util;
import sgdk.rescomp.type.Basics.Compression;
import sgdk.rescomp.type.Basics.TileOptimization;
import sgdk.tool.FileUtil;
import sgdk.tool.ImageUtil;
import sgdk.tool.ImageUtil.BasicImageInfo;
import sgdk.tool.StringUtil;

public class ImageStripsNoPalsProcessor implements Processor
{
	private static final String resId = "IMAGE_STRIPS_NO_PALS";

    @Override
    public String getId()
    {
        return resId;
    }

    @Override
    public Resource execute(String[] fields) throws Exception
    {
		if (fields.length < 4)
		{
			System.out.println("Wrong " + resId + " definition");
			System.out.println(resId + " name \"baseFile\" strips mergeOpts [compression [map_opt [map_base]]]");
			System.out.println("  name           Image variable name. Eg: frame_12");
			System.out.println("  baseFile       path of the first strip for input RGB image file with palettes (BMP or PNG image). Eg: \"res/rgb/frame_12_0.png\" or \"res/rgb/frame_12_0_RGB.png\"");
			System.out.println("  strips         how many strips is the final image composed of. Eg: 21. It means there are frame_12_0.png, frame_12_1.png, ... frame_12_20.png");
			System.out.println("  splitTileset   how many chunks is the tileset split. Default 1 (no split), otherwise 2 or 3.");
			System.out.println("  toggleMapTileBaseIndexFlag   -1 disable. 0 if first frame num is even, 1 if odd. This is for the video frame swap buffer mechanism.");
			System.out.println("  mapWidth64     extends the map width to 64 tiles (filled with 0s) so you can use VDP_setTileMapData() if your plane width is 64 tiles. TRUE or FALSE");
			System.out.println("  compression    compression type, accepted values:");
			System.out.println("                   -1 / BEST / AUTO = use best compression");
			System.out.println("                    0 / NONE        = no compression (default)");
			System.out.println("                    1 / APLIB       = aplib library (good compression ratio but slow)");
			System.out.println("                    2 / FAST / LZ4W = custom lz4 compression (average compression ratio but fast)");
			System.out.println("  map_opt        define the map optimisation level, accepted values:");
			System.out.println("                    0 / NONE        = no optimisation (each tile is unique)");
			System.out.println("                    1 / ALL         = find duplicate and flipped tile (default)");
			System.out.println("                    2 / DUPLICATE   = find duplicate tile only");
			System.out.println("  map_base       define the base tilemap value, useful to set a default priority, palette and base tile index offset.");
			return null;
		}

        // get resource id (name parameter)
        String name = fields[1];

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

        // get tileset split number
        int splitTileset = 1;
        if (fields.length >= 5) {
        	int value = StringUtil.parseInt(fields[4], 1);
			splitTileset = Math.min(3, Math.max(1, value)); // clamp(1, 3, value)
        }

        // get the value for toggling map tile base index for video frame swap buffer
        int toggleMapTileBaseIndexFlag = -1;
        if (fields.length >= 6) {
        	int value = StringUtil.parseInt(fields[5], 1);
        	if (value == 0 || value == 1)
        		toggleMapTileBaseIndexFlag = value;
        }

        // extend map width to 64 tiles
        boolean extendedMapWidth64 = false;
        if (fields.length >= 7) {
        	extendedMapWidth64 = Boolean.parseBoolean(fields[6]);
        }

        // get packed value
        Compression compression = Compression.NONE;
        if (fields.length >= 8)
            compression = Util.getCompression(fields[7]);

        // get map optimization value
        TileOptimization tileOpt = TileOptimization.ALL;
        if (fields.length >= 9)
            tileOpt = Util.getTileOpt(fields[8]);

        // get map base
        int mapBase = 0;
        if (fields.length >= 10)
            mapBase = Integer.parseInt(fields[9]);

        // generate the list of strip files
        List<String> stripsInList = generateFilesInForStrips(baseFileAbsPath, baseFileName, baseFileNameMatcher, strips);

        // add resource file (used for deps generation)
        // NOTE: missing split in 2 parts when splitTileset > 1
//		String baseFileIn = baseFileAbsPath + name + FileUtil.getFileExtension(baseFile, true);
//		saveFinalImage(baseFileIn, stripsInList, mergeOpt);
//		Compiler.addResourceFile(baseFileIn);

        if (splitTileset == 1)
        	return new ImageStripsNoPals(name, stripsInList, toggleMapTileBaseIndexFlag, extendedMapWidth64, compression, tileOpt, mapBase);
        else if (splitTileset == 2)
        	return new ImageStripsNoPalsTilesetSplit2(name, stripsInList, toggleMapTileBaseIndexFlag, extendedMapWidth64, compression, tileOpt, mapBase);
        else
        	return new ImageStripsNoPalsTilesetSplit3(name, stripsInList, toggleMapTileBaseIndexFlag, extendedMapWidth64, compression, tileOpt, mapBase);
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

	private void saveFinalImage(String finalImgPath, List<String> stripsInList) throws IOException
	{
        // get info from first strip
        BasicImageInfo strip0Info = ImageUtil.getBasicInfo(stripsInList.get(0));
        int w = strip0Info.w;
        int h = strip0Info.h;
        int hStrip = strip0Info.h;
        // true color / RGB image ? removes palettes
        int cropPalettesOffset = strip0Info.bpp > 8 ? 32 : 0;
        h -= cropPalettesOffset;
        hStrip -= cropPalettesOffset;
        h *= stripsInList.size();

        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        Rectangle rect = new Rectangle(0, cropPalettesOffset, w, hStrip);
        for (int i=0; i < stripsInList.size(); ++i) {
        	BufferedImage stripImg = ImageUtil.load(stripsInList.get(i));
        	ImageUtil.waitImageReady(stripImg); // be sure image data are ready
        	BufferedImage subStripImage = stripImg.getSubimage(rect.x, rect.y, rect.width, rect.height);
        	g2d.drawImage(subStripImage, 0, i * hStrip, null);
        }

        g2d.dispose();

        String extension = FileUtil.getFileExtension(finalImgPath, false);
        ImageUtil.save(image, extension, finalImgPath);
        image.flush();
	}

//	public static void main(String[] args) throws Exception
//	{
//		ImageStripsNoPalsProcessor p = new ImageStripsNoPalsProcessor();
//		String[] fields = {
//			resId, "mv_frame_47_0_RGB", "C:\\MyProjects\\VSCode\\sgdk\\sgdk-video-player-main\\res\\rgb\\frame_47_0_RGB.png", "22", 
//			"2", "1", "TRUE", "FAST", "ALL" 
//		};
//		p.execute(fields);
//	}
}
