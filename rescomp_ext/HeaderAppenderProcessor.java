package sgdk.rescomp.processor;

import sgdk.rescomp.Processor;
import sgdk.rescomp.Resource;
import sgdk.rescomp.resource.HeaderAppender;

public class HeaderAppenderProcessor implements Processor
{
	private static final String resId = "HEADER_APPENDER";

    @Override
    public String getId()
    {
        return resId;
    }

    @Override
    public Resource execute(String[] fields) throws Exception
    {
        if (fields.length < 3)
        {
            System.out.println("Wrong " + resId + " definition");
            System.out.println(resId + " name contentString");
            System.out.println("  name              Just an internal name for this chunk of the header. Eg: headerForMovie_1");
            System.out.println("  contentString     Define your header content in a string. Eg: #define MAX_VALE 32\n\ntypedef struct {\n...\n} CustomStructTypeDef;\n");
            return null;
        }

        // get resource id
        String id = fields[1];
        // get resource id
        String contentString = fields[2];

        return new HeaderAppender(id, contentString);
    }

//	public static void main(String[] args) throws Exception
//	{
//		HeaderAppenderProcessor p = new HeaderAppenderProcessor();
//		String content = "\n"
//				+ "typedef struct\n"
//				+ "{\n"
//				+ "    TileSet *tileset;\n"
//				+ "    TileMap *tilemap;\n"
//				+ "} ImageNoPals;\n";
//
//		String[] fields = {
//			resId, "headerForMovie_1", content
//		};
//		p.execute(fields);
//	}
}
