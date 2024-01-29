package sgdk.rescomp.tool;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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

		long suffix = System.currentTimeMillis();
		String infile = rescomp_ext_jar_path + File.separator + compression.getValue() + "_tmp_IN_" + suffix + ".bin";
		String outfile = rescomp_ext_jar_path + File.separator + compression.getValue() + "_tmp_OUT_" + suffix + ".bin";

		try {
			writeBytesToFile(data, infile);
			if (compression == CompressionCustom.UFTC || compression == CompressionCustom.UFTC_15) {
				String utfc_flag = compression == CompressionCustom.UFTC_15 ? "-15 -c" : "-c";
				if (isWindows)
					callProgram("cmd.exe", "/c", compression.getExeName(), utfc_flag, infile, outfile);
				else
					callProgram("bash", "-c", compression.getExeName(), utfc_flag, infile, outfile);
			}
			else {
				if (isWindows)
					callProgram("cmd.exe", "/c", compression.getExeName(), infile, outfile);
				else
					callProgram("bash", "-c", compression.getExeName(), infile, outfile);
			}

			byte[] result = readFileAsByteArray(outfile);
			return result;
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException(e);
		} finally {
			deleteFile(infile);
			deleteFile(outfile);
		}
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
