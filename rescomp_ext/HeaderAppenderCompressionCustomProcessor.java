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
        if (fields.length != 2)
        {
            System.out.println("Wrong " + resId + " definition. Just use the resource id to include a list of #define for each CompressionCustom value.");
            System.out.println("  name        Just an internal name for this chunk of the header. Eg: compressionCustomHeader1");
            System.out.println(resId);
            return null;
        }

        // get resource id
        String id = fields[1];

        return new HeaderAppenderCompressionCustom(id);
    }
}
