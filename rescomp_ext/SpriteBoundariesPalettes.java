package sgdk.rescomp.tool;

import java.awt.Rectangle;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Scanner;

import sgdk.rescomp.type.SpriteCell;

public class SpriteBoundariesPalettes {

	private static String rescomp_ext_jar_path = new File(MdComp.class.getProtectionDomain().getCodeSource().getLocation().getPath())
			.getParentFile().getAbsolutePath();

	private Map<Rectangle, Integer> palByBoundary;

	public static SpriteBoundariesPalettes from (String boundariesFile) {
		boolean isAbsolutePath = false; // use this variable for debugging purpose
		String location = isAbsolutePath ? "" : rescomp_ext_jar_path;
		File file = new File(location + File.separator + boundariesFile);
		
		try (Scanner scanner = new Scanner(file)) {
			if (!file.exists()) {
				System.out.println("ERROR! Couldn't load file " + boundariesFile);
				return null;
			}

			SpriteBoundariesPalettes newObj = new SpriteBoundariesPalettes();
			newObj.palByBoundary = new HashMap<>();

			// Read the lines as comma-separated integers each
			while (scanner.hasNextLine()) {
				String[] values = scanner.nextLine().split(",");
				if (values == null || values.length != 5)
					continue;
				int[] data = new int[5];
				for (int i = 0; i < values.length; i++) {
					data[i] = Integer.parseInt(values[i].trim());
				}
				Rectangle boundary = new Rectangle(data[0], data[1], data[2], data[3]);
				Integer palId = data[4];
				newObj.palByBoundary.put(boundary, palId);
			}

			System.out.println(boundariesFile + ": Loaded " + newObj.palByBoundary.size() + " boundaries palettes");
			return newObj;

		} catch (FileNotFoundException e) {
			System.out.println("ERROR! " + TilesCacheManager.class.getSimpleName() + ": " + e.getMessage());
			return null;
		}
	}

	public int getPalId (SpriteCell sprite) {
		if (palByBoundary.isEmpty())
			return 0;

		Optional<Entry<Rectangle, Integer>> found = palByBoundary.entrySet().stream()
			.filter(entry -> {
				Rectangle rect = entry.getKey();
				return (sprite.x == rect.x) && (sprite.y == rect.y) && (sprite.width == rect.width) && (sprite.height == rect.height);
			})
			.findFirst();

		if (found.isPresent())
			return found.get().getValue();
		
		return 0;
	}

}
