package sgdk.rescomp.resource;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import sgdk.rescomp.Resource;
import sgdk.rescomp.tool.CompressionCustomUsageTracker;
import sgdk.rescomp.tool.MdComp;
import sgdk.rescomp.type.CompressionCustom;

/**
 * Just add a custom content to the auto generated header file.
 */
public class HeaderAppenderCompressionCustomTracker extends Resource
{
	private static final String rescomp_ext_jar_path = new File(MdComp.class.getProtectionDomain().getCodeSource().getLocation().getPath())
			.getParentFile().getAbsolutePath();

    final int hc;
    final String headerFileName;

    public HeaderAppenderCompressionCustomTracker(String id, String headerFileName) throws Exception
    {
        super(id);

        this.headerFileName = headerFileName;

        // compute hash code
        hc = id.hashCode() ^ HeaderAppenderCompressionCustomTracker.class.getSimpleName().hashCode();
    }

	@Override
    public int internalHashCode()
    {
        return hc;
    }

    @Override
    public boolean internalEquals(Object obj)
    {
        if (obj instanceof HeaderAppenderCompressionCustomTracker)
        {
            final HeaderAppenderCompressionCustomTracker other = (HeaderAppenderCompressionCustomTracker) obj;
            return id.equals(other.id);
        }

        return false;
    }

    @Override
    public List<Bin> getInternalBinResources()
    {
        return Collections.emptyList();
    }

    @Override
    public int shallowSize()
    {
        return 0;
    }

    @Override
    public int totalSize()
    {
        return 0;
    }

    @Override
    public void out(ByteArrayOutputStream outB, StringBuilder outS, StringBuilder outH)
    {
    	Set<CompressionCustom> used = CompressionCustomUsageTracker.getUsed();

		try {
			String headerFilePath = rescomp_ext_jar_path + File.separator + headerFileName;
			Path filePath = Path.of(headerFilePath);
			List<String> existingLines = Collections.emptyList();
			
			// if the file doesn't exist, create it
			if (!Files.exists(filePath)) {
				// but wait, check first if we have something to add into it
				if (used.isEmpty())
					return;
				// otherwise create it
				Files.createFile(filePath);
			}
			else {
				long totalLines = Files.lines(filePath).count();
				// if file somehow contains less than expected lines it means it has no CompressionCustom entries and just the #ifndef ... #endif
				if (totalLines <= 5)
					existingLines = Collections.emptyList();
				else {
					// read all lines except first 2 and last one. They are #define and #endif declarations
					existingLines = Files.lines(filePath, StandardCharsets.UTF_8)
							.skip(2)
							.limit(totalLines - 3)
							.filter(line -> !line.trim().isEmpty())
							.filter(line -> !line.trim().isBlank())
							.collect(Collectors.toList());
				}
			}

			String startSectionToken = "// start " + id;
			String endSectionToken = "// end " + id;

			// seek delimiters for starting and ending section indexes
			int startSectionIndex = existingLines.indexOf(startSectionToken);
			int endSectionIndex = existingLines.indexOf(endSectionToken);
			// remove the section delimiters and their content so we can re add them with newest compression types (if any)
			if (startSectionIndex >= 0 && endSectionIndex > startSectionIndex) {
				existingLines.subList(startSectionIndex, endSectionIndex + 1).clear();
			}
			
			List<String> newLines = new ArrayList<>();
			used.forEach(cc -> {
				String usedCc = "#ifndef USING_" + cc.getValue() + "\n#define USING_" + cc.getValue() + "\n#endif";
				newLines.add(usedCc);
			});
			
			List<String> completeLines = new ArrayList<>();
			completeLines.add("#ifndef _COMPRESSION_TYPES_TRACKER_H");
			completeLines.add("#define _COMPRESSION_TYPES_TRACKER_H");
			if (!existingLines.isEmpty()) {
				completeLines.add("");
				completeLines.addAll(existingLines);
			}
			if (!newLines.isEmpty()) {
				completeLines.add("");
				completeLines.add(startSectionToken);
				completeLines.addAll(newLines);
				completeLines.add(endSectionToken);
			}
			completeLines.add("");

			// Write the modified lines back to the file, overwriting its content
			Files.write(filePath, completeLines, StandardCharsets.UTF_8);
			Files.writeString(filePath, "#endif // _COMPRESSION_TYPES_TRACKER_H", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
    }
}
