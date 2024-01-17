package sgdk.rescomp.resource;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;

import sgdk.rescomp.Resource;
import sgdk.rescomp.tool.Util;
import sgdk.rescomp.type.Basics.Compression;
import sgdk.rescomp.type.CompressionCustom;
import sgdk.rescomp.type.PalettesPositionEnum;
import sgdk.rescomp.type.TSX;
import sgdk.tool.FileUtil;
import sgdk.tool.ImageUtil;
import sgdk.tool.ImageUtil.BasicImageInfo;

/**
 * Palette with all the colors of every strip's palette.
 */
public class Palette32AllStripsSplit3 extends Resource
{
    final int hc;

    private BinCustom bin1, bin2, bin3;

    public Palette32AllStripsSplit3(String id, List<String> stripsFileList, final PalettesPositionEnum palsPosition, final boolean togglePalsLocation, 
    		Compression compression, CompressionCustom compressionCustom) throws Exception
    {
        super(id);

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

        int size1 = palettesAll.length / 3;
        int size2 = palettesAll.length / 3;
        int size3 = (palettesAll.length / 3) + (palettesAll.length % 3);
        short[] chunk1 = new short[size1];
        short[] chunk2 = new short[size2];
        short[] chunk3 = new short[size3];
        System.arraycopy(palettesAll, 0, chunk1, 0, size1);
        System.arraycopy(palettesAll, size1, chunk2, 0, size2);
        System.arraycopy(palettesAll, size1 + size2, chunk3, 0, size3);

        // build BIN
        bin1 = (BinCustom) addInternalResource(new BinCustom(id + "_chunk1_data", chunk1, compression, compressionCustom, false));
        bin2 = (BinCustom) addInternalResource(new BinCustom(id + "_chunk2_data", chunk2, compression, compressionCustom, false));
        bin3 = (BinCustom) addInternalResource(new BinCustom(id + "_chunk3_data", chunk3, compression, compressionCustom, false));

        // compute hash code
        hc = bin1.hashCode() ^ bin2.hashCode() ^ bin3.hashCode();
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
        if (obj instanceof Palette32AllStripsSplit3)
        {
            final Palette32AllStripsSplit3 palette = (Palette32AllStripsSplit3) obj;
            return bin1.equals(palette.bin1) && bin2.equals(palette.bin2) && bin3.equals(palette.bin3);
        }

        return false;
    }

    @Override
    public List<Bin> getInternalBinResources()
    {
        return Arrays.asList(bin1, bin2, bin3);
    }

    @Override
    public int shallowSize()
    {
        return 4 + 4 + 4;
    }

    @Override
    public int totalSize()
    {
        return bin1.totalSize() + bin2.totalSize() + bin3.totalSize() + shallowSize();
    }

    @Override
    public void out(ByteArrayOutputStream outB, StringBuilder outS, StringBuilder outH)
    {
		// can't store pointer so we just reset binary stream here (used for compression only)
		outB.reset();

		// declare
		Util.decl(outS, outH, "Palette32AllStripsSplit3", id, 2, global);
		// set compression info (very important that binary data had already been exported at this point)
		//outS.append("    dc.w    " + (bin1.doneCompression.ordinal() - 1) + "\n");
		// set palette data pointer
		outS.append("    dc.l    " + bin1.id + "\n");
		outS.append("    dc.l    " + bin2.id + "\n");
		outS.append("    dc.l    " + bin3.id + "\n");
		outS.append("\n");
    }
}
