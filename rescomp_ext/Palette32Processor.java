package sgdk.rescomp.processor;

import sgdk.rescomp.Compiler;
import sgdk.rescomp.Processor;
import sgdk.rescomp.Resource;
import sgdk.rescomp.resource.Palette32;
import sgdk.tool.FileUtil;

public class Palette32Processor implements Processor
{
	private static final String resId = "PALETTE_32_COLORS";

    @Override
    public String getId()
    {
        return resId;
    }

    @Override
    public Resource execute(String[] fields) throws Exception
    {
		if (fields.length < 3) {
			System.out.println("Wrong " + resId + " definition");
			System.out.println(resId + " name file");
			System.out.println("  name       Palette variable name");
			System.out.println("  file       path of the .pal or image file to convert to Palette structure (PAL file, BMP or PNG image file)");

			return null;
		}

		// get resource id
		String id = fields[1];
		// get input file
		String fileIn = FileUtil.adjustPath(Compiler.resDir, fields[2]);

		// add resource file (used for deps generation)
//		Compiler.addResourceFile(fileIn);

		return new Palette32(id, fileIn);
    }

//	public static void main(String[] args) throws Exception
//	{
//		Palette32Processor p = new Palette32Processor();
//		String[] fields = {
//			resId, "pal_frame_22_0", "C:\\MyProjects\\VSCode\\sgdk\\sgdk-video-player-main\\res\\rgb\\frame_22_0_RGB.png" 
//		};
//		p.execute(fields);
//	}
}
