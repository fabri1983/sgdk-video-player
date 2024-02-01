package sgdk.rescomp.tool;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import sgdk.rescomp.type.CompressionCustom;
import sgdk.rescomp.type.PackedDataCustom;

public class MdComp {

	private static boolean isWindows = System.getProperty("os.name").toLowerCase().indexOf("win") >= 0;
	private static String rescomp_ext_jar_path = new File(MdComp.class.getProtectionDomain().getCodeSource().getLocation().getPath())
			.getParentFile().getAbsolutePath();
	
	public static PackedDataCustom pack(byte[] data, CompressionCustom compression) {
		// nothing to do
		if (compression == CompressionCustom.NONE)
			return new PackedDataCustom(data, CompressionCustom.NONE);

		byte[] result = data;
		result = compress(data, compression);
		// no good compression ? return origin data
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

	private static byte[] compress(byte[] data, CompressionCustom compression) {
		if (compression == CompressionCustom.UNAPLIB) {
			byte[] result = Util.appack(data);
			return result;
		}

		//countUnique(data);

		long suffix = System.currentTimeMillis();
		String infile = rescomp_ext_jar_path + File.separator + compression.getValue() + "_tmp_tiles_IN_" + suffix + ".bin";
		String outfile = rescomp_ext_jar_path + File.separator + compression.getValue() + "_tmp_tiles_OUT_" + suffix + ".bin";

		try {
			writeBytesToFile(data, infile);
			if (compression == CompressionCustom.RNC_1 || compression == CompressionCustom.RNC_2) {
				String m_flag = compression == CompressionCustom.RNC_1 ? "-m=1" : "-m=2";
				if (isWindows)
					callProgram("cmd.exe", "/c", compression.getExeName(), "p", infile, outfile, m_flag);
				else
					callProgram("bash", "-c", compression.getExeName(), "p", infile, outfile, m_flag);
			}
			else if (compression == CompressionCustom.UFTC || compression == CompressionCustom.UFTC_15) {
				String utfc_flag = compression == CompressionCustom.UFTC_15 ? "-15 -c" : "-c";
				if (isWindows)
					callProgram("cmd.exe", "/c", compression.getExeName(), utfc_flag, infile, outfile);
				else
					callProgram("bash", "-c", compression.getExeName(), utfc_flag, infile, outfile);
			}
			else if (compression == CompressionCustom.LZ4) {
				if (isWindows)
					callProgram("cmd.exe", "/c", compression.getExeName(), "-9", "--favor-decSpeed", "--no-frame-crc", infile, outfile);
				else
					callProgram("bash", "-c", compression.getExeName(), "-9", "--favor-decSpeed", "--no-frame-crc", infile, outfile);
			}
			else {
				if (isWindows)
					callProgram("cmd.exe", "/c", compression.getExeName(), infile, outfile);
				else
					callProgram("bash", "-c", compression.getExeName(), infile, outfile);
			}

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
	 * This only needed for decompressors that work on a word basis, as LZ4W does.
	 * @param byteArray
	 * @return
	 */
	private static byte[] convertToLE(byte[] byteArray) {
		ByteBuffer bb = ByteBuffer.wrap(byteArray);
		bb.order( ByteOrder.LITTLE_ENDIAN);
		
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

	private static void callProgram(String... commands) throws InterruptedException, IOException {
		ProcessBuilder pb = new ProcessBuilder(commands);
		pb.redirectErrorStream(true);
		pb.directory(new File(rescomp_ext_jar_path + File.separator + "compressors"));
		Process process = pb.start();
		process.waitFor();
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
}
