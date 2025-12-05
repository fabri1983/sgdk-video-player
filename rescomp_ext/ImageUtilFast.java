package sgdk.rescomp.tool;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.Arrays;

import sgdk.tool.ImageUtil;
import sgdk.tool.ImageUtil.BasicImageInfo;

public class ImageUtilFast {

    public static byte[] getImageAs8bpp(String imgFile, boolean checkTileAligned, boolean removeRGBPalette) throws Exception
    {
        // retrieve basic infos about the image
        final BasicImageInfo imgInfo = ImageUtil.getBasicInfo(imgFile);

        // check size is correct
        if (checkTileAligned)
        {
            if ((imgInfo.w & 7) != 0)
                throw new IllegalArgumentException("'" + imgFile + "' width is '" + imgInfo.w + ", should be a multiple of 8.");
            if ((imgInfo.h & 7) != 0)
                throw new IllegalArgumentException("'" + imgFile + "' height is '" + imgInfo.h + ", should be a multiple of 8.");
        }

        // true color / RGB image ?
        if (imgInfo.bpp > 8)
            return convertRGBTo8bpp(imgFile, removeRGBPalette);

        // get image data
        final byte[] data = ImageUtil.getIndexedPixels(imgFile);
        // convert to 8 bpp
        return ImageUtil.convertTo8bpp(data, imgInfo.bpp);
    }

    /**
     * Faster implementation than {@link ImageUtil#convertRGBTo8bpp}.
     * Convert an (A)RGB image to 8bpp image using the palette information
     * found in the image (4 lines of 8x8 tiles giving the 16 colors for each palette)
     * 
     * @param filename
     * @param cropPalette
     *        remove palette tiles from result image
     * @return null if not palette data found
     * @throws Exception
     */
    public static byte[] convertRGBTo8bpp(String filename, boolean cropPalette) throws Exception {

        final BasicImageInfo imageInfo = ImageUtil.getBasicInfo(filename);
        final int w = imageInfo.w;
        final int h = imageInfo.h;

        if ((w & 7) != 0)
            throw new IllegalArgumentException("'" + filename + "' width must be multiple of 8.");
        if ((h & 7) != 0)
            throw new IllegalArgumentException("'" + filename + "' height must be multiple of 8.");

        // load ARGB pixels
        final int[] argb = ImageUtil.getARGBPixels(filename);

        // load palette
        final int[] palette = ImageUtil.getRGBA8888PaletteFromTiles(argb, w, h);
        if (palette == null)
            return null;

        final int[] palARGB = new int[64];
        for (int i = 0; i < 64; i++)
            palARGB[i] = ImageUtil.ABGRtoARGB(palette[i]);

        // -----------------------------------------------------
        // FAST COLOR MAP (ARGB -> palette indices)
        // -----------------------------------------------------
        // typical count ~64 unique palette colors
        // so probed-limited hash table is extremely fast

        final int CAP = 128;
        final int EMPTY = 0x7FFFFFFF;

        final int[] keyTable = new int[CAP];
        final int[][] valueTable = new int[CAP][]; // each entry is palette slot list

        Arrays.fill(keyTable, EMPTY);

        for (int i = 0; i < 64; i++) {
            int c = palARGB[i];

            int slot = (c * 0x9E3779B9) >>> 25;  // good avalanching mix
            while (keyTable[slot] != EMPTY && keyTable[slot] != c)
                slot = (slot + 1) % CAP;

            if (keyTable[slot] == EMPTY) {
                keyTable[slot] = c;
                valueTable[slot] = new int[]{i};
            } else {
                // append palette index
                int[] old = valueTable[slot];
                int[] nw = Arrays.copyOf(old, old.length + 1);
                nw[nw.length - 1] = i;
                valueTable[slot] = nw;
            }
        }

        // rapid lookup
        final java.util.function.IntFunction<int[]> findIndexes = (col) -> {
            int slot = (col * 0x9E3779B9) >>> 25;
            while (true) {
                int k = keyTable[slot];
                if (k == EMPTY) return null;
                if (k == col) return valueTable[slot];
                slot = (slot + 1) % CAP;
            }
        };

        final int wt = w / 8;
        final int ht = h / 8;
        final byte[] result = new byte[w * h];

        // ------------------------------------------------------------------
        // INITIAL WRITE OF PALETTE TILES
        // ------------------------------------------------------------------
        for (int yt = 0; yt < 4; yt++) {
            for (int xt = 0; xt < wt; xt++) {

                int palIndex = (xt < 16) ? (yt * 16 + xt) : 0;

                int off = (yt * 8) * w + xt * 8;

                for (int y = 0; y < 8; y++) {
                    for (int x = 0; x < 8; x++)
                        result[off++] = (byte) palIndex;
                    off += w - 8;
                }
            }
        }

        // ------------------------------------------------------------------
        // PROCESS ALL IMAGE TILES
        // ------------------------------------------------------------------
        for (int yt = 0; yt < ht; yt++) {
            for (int xt = 0; xt < wt; xt++) {

                // skip palette tiles
                if (yt < 4 && xt < 16)
                    continue;

                int baseOff = (yt * 8) * w + xt * 8;

                // special case (yt==0,xt==16)
                if (yt == 0 && xt == 16) {
                    int off = baseOff;
                    for (int y = 0; y < 8; y++) {
                        Arrays.fill(result, off, off + 8, (byte)0);
                        off += w;
                    }
                    continue;
                }

                // ----------------------------------------------------------
                // Determine palette (bitmask 16 bits = 16 palettes)
                // ----------------------------------------------------------
                int off = baseOff;
                int col0 = argb[off];
                int[] idx0 = findIndexes.apply(col0);
                if (idx0 == null)
                    throw new Exception(filename + ": bad pixel at [" + (xt*8) + "," + (yt*8)+ "]");

                int mask = 0;
                for (int idx : idx0)
                    mask |= 1 << ((idx >>> 4) & 15);

                for (int y = 0; y < 8; y++) {
                    for (int x = 0; x < 8; x++) {
                        int[] idxs = findIndexes.apply(argb[off]);
                        if (idxs == null)
                            throw new Exception(filename + ": bad pixel at [" + (xt*8+x) + "," + (yt*8+y)+ "]");

                        int cm = 0;
                        for (int i : idxs)
                            cm |= 1 << ((i >>> 4) & 15);

                        mask &= cm;

                        if (mask == 0)
                            throw new Exception(filename + ": tile ["+xt+","+yt+"] mixes palettes");

                        off++;
                    }
                    off += w - 8;
                }

                // get palette ID
                int pal = Integer.numberOfTrailingZeros(mask) << 4;

                // ----------------------------------------------------------
                // final write
                // ----------------------------------------------------------
                off = baseOff;
                for (int y = 0; y < 8; y++) {
                    for (int x = 0; x < 8; x++) {
                        int[] idxs = findIndexes.apply(argb[off]);
                        int finalIndex = -1;

                        for (int ci : idxs)
                            if ((ci & 0xF0) == pal) {
                                finalIndex = ci;
                                break;
                            }

                        if (finalIndex < 0)
                            throw new Exception(filename+": palette mismatch at tile ["+xt+","+yt+"]");

                        result[off] = (byte) finalIndex;
                        off++;
                    }
                    off += w - 8;
                }
            }
        }

        if (cropPalette)
			return ImageUtil.getSubImage(result, new Dimension(w, h), new Rectangle(0, 32, w, h - 32));

        return result;
    }

}
