package sgdk.rescomp.resource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import sgdk.rescomp.tool.CompressionCustomUsageTracker;
import sgdk.rescomp.tool.MdComp;
import sgdk.rescomp.tool.Util;
import sgdk.rescomp.type.Basics.Compression;
import sgdk.rescomp.type.Basics.PackedData;
import sgdk.rescomp.type.CompressionCustom;
import sgdk.rescomp.type.PackedDataCustom;
import sgdk.tool.ArrayUtil;

public class BinCustom extends Bin
{
	public CompressionCustom wantedCompressionCustom;
	public CompressionCustom doneCompressionCustom;

    public BinCustom(String id, byte[] data, int align, int sizeAlign, int fill, Compression compression, CompressionCustom compressionCustom, boolean far, boolean embedded)
    {
        super(id, data, align, sizeAlign, fill, compression, far, embedded);
        this.wantedCompressionCustom = compressionCustom;
    }

    public BinCustom(String id, byte[] data, int align, int sizeAlign, int fill, Compression compression, CompressionCustom compressionCustom, boolean far)
    {
        // consider embedded by default
        this(id, data, align, sizeAlign, fill, compression, compressionCustom, far, true);
    }

    public BinCustom(String id, byte[] data, int align, int sizeAlign, int fill, Compression compression, CompressionCustom compressionCustom)
    {
        this(id, data, align, sizeAlign, fill, compression, compressionCustom, true);
    }

    public BinCustom(String id, byte[] data, int align, int sizeAlign, int fill)
    {
        this(id, data, align, sizeAlign, fill, Compression.NONE, CompressionCustom.NONE);
    }

    public BinCustom(String id, byte[] data, int align, Compression compression, CompressionCustom compressionCustom)
    {
        this(id, data, align, 0, 0, compression, compressionCustom);
    }

    public BinCustom(String id, byte[] data, Compression compression, CompressionCustom compressionCustom)
    {
        this(id, data, 2, 0, 0, compression, compressionCustom);
    }

    public BinCustom(String id, short[] data, Compression compression, CompressionCustom compressionCustom, boolean far)
    {
        this(id, ArrayUtil.shortToByte(data), 2, 0, 0, compression, compressionCustom, far);
    }

    public BinCustom(String id, short[] data, Compression compression, CompressionCustom compressionCustom)
    {
        this(id, ArrayUtil.shortToByte(data), 2, 0, 0, compression, compressionCustom, true);
    }

    public BinCustom(String id, int[] data, Compression compression, CompressionCustom compressionCustom)
    {
        this(id, ArrayUtil.intToByte(data), 2, 0, 0, compression, compressionCustom);
    }

    @Override
    public void out(ByteArrayOutputStream outB, StringBuilder outS, StringBuilder outH) throws IOException
    {
        // do 'outB' align *before* doing compression (as LZ4W compression can use previous data block)
        Util.align(outB, align);

        // IMPORTANT: CompressionCustom option has priority over Compression option
        if (wantedCompressionCustom != CompressionCustom.NONE) {
        	PackedDataCustom packedDataCustom = MdComp.pack(data, id, wantedCompressionCustom);
        	packedData = (PackedData) packedDataCustom;
        	doneCompressionCustom = packedDataCustom.compressionCustom;
        	doneCompression = Compression.NONE;
        }
        else {
	        // pack data first if needed (force selected compression when not embedded resource)
	        packedData = Util.pack(data, wantedCompression, outB, !embedded);
	        doneCompression = packedData.compression;
	        doneCompressionCustom = CompressionCustom.NONE;
        }

        final int baseSize = data.length;
        final int packedSize = packedData.data.length;

        // data was custom compressed ?
        if (wantedCompressionCustom != CompressionCustom.NONE) {
            System.out.print("'" + id + "' ");

            switch (doneCompressionCustom)
            {
                case NONE:
                    System.out.println("not packed (size = " + baseSize + ")");
                    break;

                default: 
                    System.out.print("packed with " + doneCompressionCustom.getValue() + ", ");
                    break;
            }

            if (doneCompressionCustom != CompressionCustom.NONE) {
            	CompressionCustomUsageTracker.markUsed(doneCompressionCustom);
                System.out.println("size = " + packedSize + " (" + Math.round((packedSize * 100f) / baseSize) + "% - origin size = " + baseSize + ")");
            }
        }
        // data was compressed ?
        else if (wantedCompression != Compression.NONE)
        {
            System.out.print("'" + id + "' ");

            switch (doneCompression)
            {
                case NONE:
                    System.out.println("not packed (size = " + baseSize + ")");
                    break;

                case APLIB:
                	CompressionCustomUsageTracker.markUsed(CompressionCustom.APLIB);
                    System.out.print("packed with APLIB, ");
                    break;

                case LZ4W:
                	CompressionCustomUsageTracker.markUsed(CompressionCustom.LZ4W);
                    System.out.print("packed with LZ4W, ");
                    break;

                default: 
                    System.out.print("packed with UNKNOW, ");
                    break;
            }

            if (doneCompression != Compression.NONE)
                System.out.println("size = " + packedSize + " (" + Math.round((packedSize * 100f) / baseSize) + "% - origin size = " + baseSize + ")");
        }

        // output binary data (data alignment was done before)
        Util.outB(outB, packedData.data);

        // declare
        Util.declArray(outS, outH, "u8", id, packedData.data.length, align, global);
        // output data (compression information is stored in 'parent' resource when embedded)
        Util.outS(outS, packedData.data, 1);
        Util.declArrayEnd(outS, outH, "u8", id, packedData.data.length, align, global);
        outS.append("\n");
    }
}