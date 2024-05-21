package sgdk.rescomp.resource;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;

import sgdk.rescomp.Resource;
import sgdk.rescomp.tool.Util;
import sgdk.rescomp.type.Basics.Compression;
import sgdk.rescomp.type.CompressionCustom;
import sgdk.rescomp.type.CustomDataTypes;
import sgdk.rescomp.type.PalettesPositionEnum;
import sgdk.rescomp.type.TSX;
import sgdk.tool.FileUtil;
import sgdk.tool.ImageUtil;
import sgdk.tool.ImageUtil.BasicImageInfo;

/**
 * Palette with all the colors of every strip's palette.
 */
public class Palette32AllStrips extends Resource
{
	final boolean addCompressionField;
    final int hc;

    public BinCustom bin;

    public Palette32AllStrips(String id, List<String> stripsFileList, final PalettesPositionEnum palsPosition, final boolean togglePalsLocation, 
    		Compression compression, CompressionCustom compressionCustom, boolean addCompressionField) throws Exception
    {
        super(id);

        this.addCompressionField = addCompressionField;

        short[] palettesAll = new short[32 * stripsFileList.size()];
        PalettesPositionEnum currentPalsPosition = palsPosition;

        for (int i=0; i < stripsFileList.size(); ++i)
        {
        	String file = stripsFileList.get(i);
        	short[] palette;

        	// PAL file ?
        	if (FileUtil.getFileExtension(file, false).equalsIgnoreCase("pal"))
        	{
        		// get palette raw data
        		palette = ImageUtil.getRGBA4444PaletteFromPALFile(file, 0x0EEE);
        	}
        	else
        	{
        		// TSX file ? --> get image file name
        		if (FileUtil.getFileExtension(file, false).equalsIgnoreCase("tsx"))
        			file = TSX.getTSXTilesetPath(file);
        		
        		// retrieve basic info about the image
        		final BasicImageInfo imgInfo = ImageUtil.getBasicInfo(file);
        		
        		// true color / RGB image
        		if (imgInfo.bpp > 8)
        		{
        			palette = ImageUtil.getRGBA4444PaletteFromTiles(file, 0x0EEE);
        			// cannot found palette in RGB image
        			if (palette == null)
        				throw new IllegalArgumentException(
        						"RGB image '" + file + "' does not contains palette data (see 'Important note about image format' in the rescomp.txt file");
        		}
        		else
        			// get palette from indexed image
        			palette = ImageUtil.getRGBA4444PaletteFromIndColImage(file, 0x0EEE);
        	}
        	
        	// palettes extracted from RGB images containing the palettes definition in first rows will always output 64 colors
        	if (palette.length == 64) {
        		if (currentPalsPosition == PalettesPositionEnum.PAL0PAL1)
        			palette = Arrays.copyOfRange(palette, 0, 32);
        		else if (currentPalsPosition == PalettesPositionEnum.PAL2PAL3)
        			palette = Arrays.copyOfRange(palette, 32, 64);
        		if (togglePalsLocation)
        			currentPalsPosition = currentPalsPosition.next();
        	}
        	// palettes from indexed color image or .pal and more than 32 colors (but less than 64)
        	else if (palette.length > 32) {
        		// we keep PAL0 and PAL1
        		palette = Arrays.copyOfRange(palette, 0, 32);
        	}
        	// palettes from indexed color image or .pal and less than 32 colors
        	else if (palette.length < 32) {
        		// rangeCopy() will pad with 0
        		palette = Arrays.copyOfRange(palette, 0, 32);
        	}

        	// add current palette to the final palette
        	System.arraycopy(palette, 0, palettesAll, i * 32, 32);
        }

        // build BIN (we never compress palette)
        if (CompressionCustom.isOneOfSgdkCompression(compressionCustom)) {
        	compression = CompressionCustom.getSgdkCompression(compressionCustom);
        	compressionCustom = CompressionCustom.NONE;
        }
        bin = (BinCustom) addInternalResource(new BinCustom(id + "_data", palettesAll, compression, compressionCustom, false));

        // compute hash code
        hc = bin.hashCode();
    }

//    private boolean allSameColor (short[] palette, int i, int j) {
//    	short c1 = palette[i];
//		for (; i < j; ++i) {
//			short c2 = palette[i];
//			if (c1 != c2)
//				return false;
//			c1 = c2;
//		}
//		return true;
//	}

	@Override
    public int internalHashCode()
    {
        return hc;
    }

    @Override
    public boolean internalEquals(Object obj)
    {
        if (obj instanceof Palette32AllStrips)
        {
            final Palette32AllStrips palette = (Palette32AllStrips) obj;
            return bin.equals(palette.bin);
        }

        return false;
    }

    @Override
    public List<Bin> getInternalBinResources()
    {
        return Arrays.asList(bin);
    }

    @Override
    public int shallowSize()
    {
    	if (addCompressionField)
    		return 2 + 4;
    	else
    		return 4;
    }

    @Override
    public int totalSize()
    {
        return bin.totalSize() + shallowSize();
    }

    @Override
    public void out(ByteArrayOutputStream outB, StringBuilder outS, StringBuilder outH)
    {
		// can't store pointer so we just reset binary stream here (used for compression only)
		outB.reset();

		// declare
		if (addCompressionField)
			Util.decl(outS, outH, CustomDataTypes.Palette32AllStripsCompField.getValue(), id, 2, global);
		else
			Util.decl(outS, outH, CustomDataTypes.Palette32AllStrips.getValue(), id, 2, global);
		// set compression info (very important that binary data had already been exported at this point)
		if (addCompressionField) {
	        int compOrdinal = 0;
	        if (bin.doneCompression != Compression.NONE)
	        	compOrdinal = bin.doneCompression.ordinal() - 1;
	        else if (bin.doneCompressionCustom != CompressionCustom.NONE)
	        	compOrdinal = bin.doneCompressionCustom.getDefineValue();
			outS.append("    dc.w    " + compOrdinal + "\n");
		}
		// set palette data pointer
		outS.append("    dc.l    " + bin.id + "\n");
		outS.append("\n");
    }
}
