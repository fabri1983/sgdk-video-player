package sgdk.rescomp.resource;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;

import sgdk.rescomp.Resource;
import sgdk.rescomp.tool.MdComp;
import sgdk.rescomp.tool.RLEWCompressor;
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
public class Palette16AllStripsSplit2 extends Resource
{
    final int hc;
    final boolean addCompressionField;

    private BinCustom bin1, bin2;

    public Palette16AllStripsSplit2(String id, List<String> stripsFileList, final PalettesPositionEnum palsPosition, final boolean togglePalsLocation, 
    		Compression compression, CompressionCustom compressionCustom, boolean addCompressionField) throws Exception
    {
        super(id);

        this.addCompressionField = addCompressionField;

        short[] palettesAll = new short[16 * stripsFileList.size()];
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
        		if (currentPalsPosition == PalettesPositionEnum.PAL0)
        			palette = Arrays.copyOfRange(palette, 0, 16);
        		else if (currentPalsPosition == PalettesPositionEnum.PAL1)
        			palette = Arrays.copyOfRange(palette, 16, 32);
        		else if (currentPalsPosition == PalettesPositionEnum.PAL1)
        			palette = Arrays.copyOfRange(palette, 32, 48);
        		else if (currentPalsPosition == PalettesPositionEnum.PAL3)
        			palette = Arrays.copyOfRange(palette, 48, 64);
        		if (togglePalsLocation)
        			currentPalsPosition = currentPalsPosition.next();
        	}
        	// palettes from indexed color image or .pal and more than 16 colors (but less than 64)
        	else if (palette.length > 16) {
        		// we keep PAL0 and PAL1
        		palette = Arrays.copyOfRange(palette, 0, 16);
        	}
        	// palettes from indexed color image or .pal and less than 16 colors
        	else if (palette.length < 16) {
        		// rangeCopy() will pad with 0
        		palette = Arrays.copyOfRange(palette, 0, 16);
        	}

        	// add current palette to the final palette
        	System.arraycopy(palette, 0, palettesAll, i * 16, 16);
        }

        int size1 = palettesAll.length / 2;
        int size2 = (palettesAll.length / 2) + (palettesAll.length % 2);
        short[] chunk1 = new short[size1];
        short[] chunk2 = new short[size2];
        System.arraycopy(palettesAll, 0, chunk1, 0, size1);
        System.arraycopy(palettesAll, size1, chunk2, 0, size2);

        // build BIN
        if (CompressionCustom.isOneOfSgdkCompression(compressionCustom)) {
        	compression = CompressionCustom.getSgdkCompression(compressionCustom);
        	compressionCustom = CompressionCustom.NONE;
        }
        // We allow each of them to go into near position in rom
        bin1 = (BinCustom) addInternalResource(new BinCustom(id + "_chunk1_data", chunk1, compression, compressionCustom, false));
        bin2 = (BinCustom) addInternalResource(new BinCustom(id + "_chunk2_data", chunk2, compression, compressionCustom, false));

        // set the amount of colors in words as a property so the compressor uses the correct settings
        if (compressionCustom == CompressionCustom.RLEW_A || compressionCustom == CompressionCustom.RLEW_B) {
        	System.setProperty(bin1.id + RLEWCompressor.RLE_PROPERTY_SUFFIX_WORDS_PER_ROW, String.valueOf(16));
        	System.setProperty(bin2.id + RLEWCompressor.RLE_PROPERTY_SUFFIX_WORDS_PER_ROW, String.valueOf(16));
        }

        // compute hash code
        hc = bin1.hashCode() ^ bin2.hashCode();
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
        if (obj instanceof Palette16AllStripsSplit2)
        {
            final Palette16AllStripsSplit2 palette = (Palette16AllStripsSplit2) obj;
            return bin1.equals(palette.bin1) && bin2.equals(palette.bin2);
        }

        return false;
    }

    @Override
    public List<Bin> getInternalBinResources()
    {
        return Arrays.asList(bin1, bin2);
    }

    @Override
    public int shallowSize()
    {
    	if (addCompressionField)
    		return 2 + 4 + 4;
    	else
    		return 4 + 4;
    }

    @Override
    public int totalSize()
    {
        return bin1.totalSize() + bin2.totalSize() + shallowSize();
    }

    @Override
    public void out(ByteArrayOutputStream outB, StringBuilder outS, StringBuilder outH)
    {
		// can't store pointer so we just reset binary stream here (used for compression only)
		outB.reset();

		if (!MdComp.checkAllSameCompression(bin1, bin2))
			throw new RuntimeException("Resource id " + id + " has different compression methods for bin1, and bin2");

		// declare
		if (addCompressionField)
			Util.decl(outS, outH, CustomDataTypes.Palette16AllStripsSplit2CompField.getValue(), id, 2, global);
		else
			Util.decl(outS, outH, CustomDataTypes.Palette16AllStripsSplit2.getValue(), id, 2, global);
		// set compression info (very important that binary data had already been exported at this point)
		if (addCompressionField) {
	        int compOrdinal = 0;
	        if (bin1.doneCompression != Compression.NONE)
	        	compOrdinal = bin1.doneCompression.ordinal() - 1;
	        else if (bin1.doneCompressionCustom != CompressionCustom.NONE)
	        	compOrdinal = bin1.doneCompressionCustom.getDefineValue();
			outS.append("    dc.w    " + compOrdinal + "\n");
		}
		// set palette data pointer
		outS.append("    dc.l    " + bin1.id + "\n");
		outS.append("    dc.l    " + bin2.id + "\n");
		outS.append("\n");
    }
}
