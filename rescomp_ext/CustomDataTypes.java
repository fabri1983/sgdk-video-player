package sgdk.rescomp.type;

import java.util.Arrays;
import java.util.Comparator;

public enum CustomDataTypes {

	// NOTE: ORDER IS IMPORTANT

	TileMapCustom("TileMapCustom"),
	ImageNoPals("ImageNoPals"),
	ImageNoPalsTilesetSplit21("ImageNoPalsTilesetSplit21"),
	ImageNoPalsTilesetSplit22("ImageNoPalsTilesetSplit22"),
	ImageNoPalsTilesetSplit31("ImageNoPalsTilesetSplit31"),
	ImageNoPalsTilesetSplit32("ImageNoPalsTilesetSplit32"),
	ImageNoPalsTilesetSplit33("ImageNoPalsTilesetSplit33"),
	Palette32AllStrips("Palette32AllStrips"),
	Palette32AllStripsSplit2("Palette32AllStripsSplit2"),
	Palette32AllStripsSplit3("Palette32AllStripsSplit3");

	private final String value;

	CustomDataTypes (String value) {
        this.value = value;
    }

	public String getValue() {
		return value;
	}

	public static CustomDataTypes from(String s) {
		for (CustomDataTypes myEnum : CustomDataTypes.values()) {
			if (myEnum.value.equalsIgnoreCase(s)) {
				return myEnum;
			}
		}
		throw new IllegalArgumentException("No enum constant with text: " + s);
	}

	public static CustomDataTypes[] parse(String listStr) {
		if (listStr == null || listStr.isEmpty()) {
			return new CustomDataTypes[0]; // Return an empty array if input is null or empty
		}
		String[] values = listStr.split(",");
		CustomDataTypes[] result = new CustomDataTypes[values.length];
		for (int i = 0; i < values.length; i++) {
			result[i] = from(values[i].trim());
		}
		// sort according ordinal in ascendant order
		Comparator<CustomDataTypes> comp = (cdt1, cdt2) -> cdt2.ordinal() - cdt1.ordinal(); 
		Arrays.sort(result, comp);
		return result;
	}

	public static String getDefinition (CustomDataTypes cdt) {
		switch (cdt) {
		case ImageNoPals:
			return "typedef struct\n"
					+ "{\n"
					+ "    TileSet *tileset;\n"
					+ "    TileMap *tilemap;\n"
					+ "} ImageNoPals;\n";
		case ImageNoPalsTilesetSplit21:
			return "typedef struct\n"
					+ "{\n"
					+ "    TileSet *tileset1;\n"
					+ "    TileSet *tileset2;\n"
					+ "    TileMapCustom *tilemap1;\n"
					+ "} ImageNoPalsTilesetSplit21;\n";
		case ImageNoPalsTilesetSplit22:
			return "typedef struct\n"
					+ "{\n"
					+ "    TileSet *tileset1;\n"
					+ "    TileSet *tileset2;\n"
					+ "    TileMapCustom *tilemap1;\n"
					+ "    TileMapCustom *tilemap2;\n"
					+ "} ImageNoPalsTilesetSplit22;\n";
		case ImageNoPalsTilesetSplit31:
			return "typedef struct\n"
					+ "{\n"
					+ "    TileSet *tileset1;\n"
					+ "    TileSet *tileset2;\n"
					+ "    TileSet *tileset3;\n"
					+ "    TileMapCustom *tilemap1;\n"
					+ "} ImageNoPalsTilesetSplit31;\n";
		case ImageNoPalsTilesetSplit32:
			return "typedef struct\n"
					+ "{\n"
					+ "    TileSet *tileset1;\n"
					+ "    TileSet *tileset2;\n"
					+ "    TileSet *tileset3;\n"
					+ "    TileMapCustom *tilemap1;\n"
					+ "    TileMapCustom *tilemap2;\n"
					+ "} ImageNoPalsTilesetSplit32;\n";
		case ImageNoPalsTilesetSplit33:
			return "typedef struct\n"
					+ "{\n"
					+ "    TileSet *tileset1;\n"
					+ "    TileSet *tileset2;\n"
					+ "    TileSet *tileset3;\n"
					+ "    TileMapCustom *tilemap1;\n"
					+ "    TileMapCustom *tilemap2;\n"
					+ "    TileMapCustom *tilemap3;\n"
					+ "} ImageNoPalsTilesetSplit33;\n";
		case Palette32AllStrips:
			return "typedef struct\n"
					+ "{\n"
					+ "    u16* data;\n"
					+ "} Palette32AllStrips;\n";
		case Palette32AllStripsSplit2:
			return "typedef struct\n"
					+ "{\n"
					+ "    u16* data1;\n"
					+ "    u16* data2;\n"
					+ "} Palette32AllStripsSplit2;\n";
		case Palette32AllStripsSplit3:
			return "typedef struct\n"
					+ "{\n"
					+ "    u16* data1;\n"
					+ "    u16* data2;\n"
					+ "    u16* data3;\n"
					+ "} Palette32AllStripsSplit3;\n";
		case TileMapCustom:
			return "typedef struct\n"
					+ "{\n"
					+ "    u16 *tilemap;\n"
					+ "} TileMapCustom;\n";
		default:
			return "";
		}
	}
}
