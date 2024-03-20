package sgdk.rescomp.processor;

import sgdk.rescomp.Processor;
import sgdk.rescomp.Resource;
import sgdk.rescomp.resource.HeaderAppenderCompressionCustomTracker;

public class HeaderAppenderCompressionCustomTrackerProcessor implements Processor
{
	private static final String resId = "HEADER_APPENDER_COMPRESSION_CUSTOM_TRACKER";

    @Override
    public String getId()
    {
        return resId;
    }

    @Override
    public Resource execute(String[] fields) throws Exception
    {
    	String headerFileName = "compressionTypesTracker.h";

        if (fields.length < 2)
        {
            System.out.println("Wrong " + resId + " definition.");
            System.out.println("This processor enables the tracking of the CompressionCustom used in this resource file.");
            System.out.println("Add it at the end of all resource files you use any of CompressionCustom algorithms.");
            System.out.println("It outputs file " + headerFileName + " at rescomp_ext.jar location, containig all the resources's CompressionCustom algorithm used.");
            System.out.println(resId + " name");
            System.out.println("  name        Just an internal name for this chunk of the header. Eg: compressionCustomTrackerHeader1");
            return null;
        }

        // get resource id
        String id = fields[1];

        return new HeaderAppenderCompressionCustomTracker(id, headerFileName);
    }
}
