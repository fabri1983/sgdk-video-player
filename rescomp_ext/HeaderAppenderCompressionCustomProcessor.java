package sgdk.rescomp.processor;

import sgdk.rescomp.Processor;
import sgdk.rescomp.Resource;
import sgdk.rescomp.resource.HeaderAppenderCompressionCustom;

public class HeaderAppenderCompressionCustomProcessor implements Processor
{
	private static final String resId = "HEADER_APPENDER_COMPRESSION_CUSTOM";

    @Override
    public String getId()
    {
        return resId;
    }

    @Override
    public Resource execute(String[] fields) throws Exception
    {
        if (fields.length < 2)
        {
            System.out.println("Wrong " + resId + " definition.");
            System.out.println("This processor includes a list of #define delcarations, one per each CompressionCustom value.");
            System.out.println(resId + " name");
            System.out.println("  name        Just an internal name for this chunk of the header. Eg: compressionCustomHeader1");
            return null;
        }

        // get resource id
        String id = fields[1];

        return new HeaderAppenderCompressionCustom(id);
    }
}
