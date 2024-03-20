package sgdk.rescomp.resource;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;

import sgdk.rescomp.Resource;
import sgdk.rescomp.tool.Util;
import sgdk.rescomp.type.Basics.Compression;
import sgdk.rescomp.type.CustomDataTypes;
import sgdk.rescomp.type.TSX;
import sgdk.tool.FileUtil;
import sgdk.tool.ImageUtil;
import sgdk.tool.ImageUtil.BasicImageInfo;

/**
 * Palette with 16 colors.
 */
public class Palette16 extends Resource
{
    final int hc;

    public Bin bin;

    public Palette16(String id, String fileIn) throws Exception
    {
        super(id);

        String file = fileIn;
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
	        if (!allSameColor(palette, 0, 16))
	        	palette = Arrays.copyOfRange(palette, 0, 16);
	        else if (!allSameColor(palette, 16, 32))
	        	palette = Arrays.copyOfRange(palette, 16, 32);
	        else if (!allSameColor(palette, 32, 48))
	        	palette = Arrays.copyOfRange(palette, 32, 48);
	        else if (!allSameColor(palette, 48, 64))
	        	palette = Arrays.copyOfRange(palette, 48, 64);
	        else
	        	palette = Arrays.copyOfRange(palette, 0, 16);
        }
        // palettes from indexed color image or .pal and more than 16 colors
        else if (palette.length > 16) {
        	// we keep PAL0 and PAL1
        	palette = Arrays.copyOfRange(palette, 0, 16);
        }
        // palettes from indexed color image or .pal and less than 16 colors
        else if (palette.length < 16) {
        	// rangeCopy() will pad with 0
        	palette = Arrays.copyOfRange(palette, 0, 16);
        }

        // build BIN (we never compress palette)
        bin = (Bin) addInternalResource(new Bin(id + "_data", palette, Compression.NONE, false));

        // compute hash code
        hc = bin.hashCode();
    }

    private boolean allSameColor (short[] palette, int i, int j) {
    	short c1 = palette[i];
		for (; i < j; ++i) {
			short c2 = palette[i];
			if (c1 != c2)
				return false;
			c1 = c2;
		}
		return true;
	}

	@Override
    public int internalHashCode()
    {
        return hc;
    }

    @Override
    public boolean internalEquals(Object obj)
    {
        if (obj instanceof Palette16)
        {
            final Palette16 palette = (Palette16) obj;
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
        Util.decl(outS, outH, CustomDataTypes.Palette16.getValue(), id, 2, global);
        // first palette size
        //outS.append("    dc.w    " + (bin.data.length / 2) + "\n");
        // set palette data pointer
        outS.append("    dc.l    " + bin.id + "\n");
        outS.append("\n");
    }
}
