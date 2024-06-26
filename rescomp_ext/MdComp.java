package sgdk.rescomp.tool;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import sgdk.rescomp.resource.BinCustom;
import sgdk.rescomp.type.CompressionCustom;
import sgdk.rescomp.type.PackedDataCustom;
import sgdk.tool.SystemUtil;

public class MdComp {

	private static boolean isWindows = System.getProperty("os.name").toLowerCase().indexOf("win") >= 0;
	private static final String rescomp_ext_jar_path = new File(MdComp.class.getProtectionDomain().getCodeSource().getLocation().getPath())
			.getParentFile().getAbsolutePath();
	private static final String tmdDir = SystemUtil.getProperty("java.io.tmpdir");

	public static PackedDataCustom pack(byte[] data, String binId, CompressionCustom compression) {
		// nothing to do
		if (compression == CompressionCustom.NONE)
			return new PackedDataCustom(data, CompressionCustom.NONE);

		byte[] result = data;
		result = compress(data, binId, compression);
		// no good compression? return origin data
        if (!isCompressionValuable(result.length, data.length))
            return new PackedDataCustom(data, CompressionCustom.NONE);
        else
        	return new PackedDataCustom(result, compression);
	}

    private static boolean isCompressionValuable(int compressedSize, int uncompressedSize)
    {
        final int minDiff = 60;
        if ((uncompressedSize - compressedSize) <= minDiff)
            return false;

        final double maxPourcentage =  95;
        return Math.round((compressedSize * 100f) / uncompressedSize) <= maxPourcentage;
    }

	private static byte[] compress(byte[] data, String binId, CompressionCustom compression) {

		//countUnique(data);

		if (compression == CompressionCustom.UNAPLIB) {
			byte[] result = Util.appack(data);
			return result;
		}
		else if (compression == CompressionCustom.RLEW_A) {
			byte[] result = RLEWCompressor.compress_A(data, binId);
			return result;
		}
		else if (compression == CompressionCustom.RLEW_B) {
			byte[] result = RLEWCompressor.compress_B(data, binId);
			return result;
		}

		long suffix = System.currentTimeMillis();
		String infile = tmdDir + File.separator + compression.getValue() + "_" + binId + "_IN_" + suffix + ".bin";
		String outfile = tmdDir + File.separator + compression.getValue() + "_" + binId + "_OUT_" + suffix + ".bin";

		try {
			writeBytesToFile(data, infile);
			List<String> flags;

			if (compression == CompressionCustom.CLOWNNEMESIS) {
				flags = Arrays.asList(compression.getExeName(), "-c", infile, outfile);
			}
			else if (compression == CompressionCustom.COMPERXM) {
				flags = Arrays.asList(compression.getExeName(), "-m", infile, outfile);
			}
			else if (compression == CompressionCustom.ELEKTRO) {
				// byte aligned: 2; slightly fast compression: 3
				flags = Arrays.asList(compression.getExeName(), infile, outfile, "2", "3");
			}
			else if (compression == CompressionCustom.LZ4) {
				// -f: force overwrite; -9: best compression (slow) 
				flags = Arrays.asList(compression.getExeName(), "-f", "-9", "--favor-decSpeed", "--no-frame-crc", infile, outfile);
			}
			else if (compression == CompressionCustom.MEGAPACK) {
				flags = Arrays.asList(compression.getExeName(), infile, outfile, "c");
			}
			else if (compression == CompressionCustom.NIBBLER) {
				flags = Arrays.asList("vamos", compression.getExeName(), infile, outfile);
			}
			else if (compression == CompressionCustom.PACKFIRE) {
				// -b: binary output; -l: generates large model
				flags = Arrays.asList(compression.getExeName(), "-b", "-l", infile, outfile);
			}
			else if (compression == CompressionCustom.RNC1 || compression == CompressionCustom.RNC2) {
				if (compression == CompressionCustom.RNC1)
					flags = Arrays.asList(compression.getExeName(), "p", infile, outfile, "-m=1");
				else
					flags = Arrays.asList(compression.getExeName(), "p", infile, outfile, "-m=2");
			}
			else if (compression == CompressionCustom.UFTC || compression == CompressionCustom.UFTC15) {
				if (compression == CompressionCustom.UFTC15)
					flags = Arrays.asList(compression.getExeName(), "-15", "-c", infile, outfile);
				else
					flags = Arrays.asList(compression.getExeName(), "-c", infile, outfile);
			}
			else {
				flags = Arrays.asList(compression.getExeName(), infile, outfile);
			}

			List<String> all = new ArrayList<>(10);
			if (isWindows) {
				all.add("cmd.exe");
				all.add("/c");
			} else {
				all.add("bash");
				all.add("-c");
			}
			all.addAll(flags);

			callProgram(all);

			byte[] result = readFileAsByteArray(outfile);
			//printByteArray(result);
			//result = convertToLE(result); // convert each word into Little Endian order
			return result;
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException(e);
		} finally {
			deleteFile(infile);
			deleteFile(outfile);
		}
	}

	private static void printByteArray(byte[] data) {
		ByteBuffer bb = ByteBuffer.wrap(data);
		System.out.println(data.length);
		while( bb.hasRemaining()) {
			System.out.print(String.format("0x%02X,", bb.get()));
		}
		System.out.println();
	}

	private static void countUnique(byte[] data) {
		ByteBuffer bb = ByteBuffer.wrap(data);
		Set<Byte> uniqueBytes = new HashSet<>();
		Set<Short> uniqueShorts = new HashSet<>();
		while( bb.hasRemaining()) {
			short s = bb.getShort();
			uniqueBytes.add((byte) (s & 0xFF));
			uniqueBytes.add((byte) ((s >>> 8) & 0xFF));
			uniqueShorts.add(s);
		}
		System.out.println("Unique bytes in data[]: " + uniqueBytes.size());
		System.out.println("Unique words in data[]: " + uniqueShorts.size());
	}

	/**
	 * Converts the byte array to LittleEndian order on a word (16 bits) basis.
	 * This only needed if the byte array isn't generated with little endian order in a per word basis.
	 * @param byteArray
	 * @return
	 */
	private static byte[] convertToLE(byte[] byteArray) {
		ByteBuffer bb = ByteBuffer.wrap(byteArray);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		
		byte[] result = new byte[byteArray.length];
		int offset = 0;
		while( bb.hasRemaining()) {
		   short v = bb.getShort();
		   result[offset] = (byte) ((v >> 8) & 0xFF);
		   if (offset + 1 < result.length)
			   result[offset + 1] = (byte) (v & 0xFF);
		}
		
		return result;
	}

	private static void writeBytesToFile(byte[] data, String fileName) throws IOException {
		try (FileOutputStream fos = new FileOutputStream(fileName)) {
			fos.write(data);
			fos.flush();
			fos.close();
		}
	}

	private static void callProgram(List<String> commands) throws InterruptedException, IOException {
		ProcessBuilder pb = new ProcessBuilder(commands);
		pb.redirectErrorStream(true);
		pb.directory(new File(rescomp_ext_jar_path + File.separator + "compressors"));
		Process process = pb.start();
		int terminationCode = process.waitFor();
		if (terminationCode != 0)
			throw new RuntimeException("ERROR! Compressor program returned value != 0");
	}

	private static byte[] readFileAsByteArray(String fileName) throws IOException {
		Path path = Paths.get(fileName);
		return Files.readAllBytes(path);
	}

	private static void deleteFile(String fileName) {
		try {
			Path path = Paths.get(fileName);
			Files.deleteIfExists(path);
		} catch (IOException e) {
			System.out.println(" WARNING! Couldn't delete file " + fileName);
		}
	}

	public static boolean checkAllSameCompression(BinCustom bin1, BinCustom bin2) {
		if (bin1.doneCompression != bin2.doneCompression)
			return false;
		if (bin1.doneCompressionCustom != bin2.doneCompressionCustom)
			return false;
		return true;
	}

	public static boolean checkAllSameCompression(BinCustom bin1, BinCustom bin2, BinCustom bin3) {
		if (bin1.doneCompression != bin2.doneCompression)
			return false;
		if (bin1.doneCompression != bin3.doneCompression)
			return false;
		if (bin2.doneCompression != bin3.doneCompression)
			return false;
		if (bin1.doneCompressionCustom != bin2.doneCompressionCustom)
			return false;
		if (bin1.doneCompressionCustom != bin3.doneCompressionCustom)
			return false;
		if (bin2.doneCompressionCustom != bin3.doneCompressionCustom)
			return false;
		return true;	
	}
}
