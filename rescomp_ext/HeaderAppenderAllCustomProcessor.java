package sgdk.rescomp.processor;

import sgdk.rescomp.Processor;
import sgdk.rescomp.Resource;
import sgdk.rescomp.resource.HeaderAppenderAllCustomResource;
import sgdk.rescomp.type.CustomDataTypes;

public class HeaderAppenderAllCustomProcessor implements Processor
{
	private static final String resId = "HEADER_APPENDER_ALL_CUSTOM";

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
            System.out.println("This processor adds to the generated C header file all or selected data types used by custom classes.");
            System.out.println(resId + " name [\"list\"]");
            System.out.println("  name        Just an internal name for this chunk of the header. Eg: header_customDataTypeStructs");
            System.out.println("  list        Optional. Comma separated list of custom data types. Possible values are:");
            for (CustomDataTypes cdt : CustomDataTypes.values())
            	System.out.println("                " + cdt.getValue());
            return null;
        }

        // get resource id
        String id = fields[1];

        CustomDataTypes[] selection = CustomDataTypes.values(); // by default: all custom values
        if (fields.length >= 3) {
        	selection = CustomDataTypes.parse(fields[2]);	
        }

        return new HeaderAppenderAllCustomResource(id, selection);
    }
}
