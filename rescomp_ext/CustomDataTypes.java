package sgdk.rescomp.type;

import java.util.Arrays;
import java.util.Comparator;

public enum CustomDataTypes {

	// NOTE: ORDER IS IMPORTANT
	TileSetOriginalCustom("TileSetOriginalCustom"),
	TileMapOriginalCustom("TileMapOriginalCustom"),
	TileMapCustom("TileMapCustom"),
	TileMapCustomCompField("TileMapCustomCompField"),
	ImageNoPals("ImageNoPals"),
	ImageNoPalsCompField("ImageNoPalsCompField"),
	ImageNoPalsSplit21("ImageNoPalsSplit21"),
	ImageNoPalsSplit22("ImageNoPalsSplit22"),
	ImageNoPalsSplit31("ImageNoPalsSplit31"),
	ImageNoPalsSplit32("ImageNoPalsSplit32"),
	ImageNoPalsSplit33("ImageNoPalsSplit33"),
	ImageNoPals21CompField("ImageNoPalsSplit21CompField"),
	ImageNoPalsSplit22CompField("ImageNoPalsSplit22CompField"),
	ImageNoPalsSplit31CompField("ImageNoPalsSplit31CompField"),
	ImageNoPalsSplit32CompField("ImageNoPalsSplit32CompField"),
	ImageNoPalsSplit33CompField("ImageNoPalsSplit33CompField"),
	Palette16("Palette16"),
	Palette32("Palette32"),
	Palette16AllStrips("Palette16AllStrips"),
	Palette16AllStripsSplit2("Palette16AllStripsSplit2"),
	Palette16AllStripsSplit3("Palette16AllStripsSplit3"),
	Palette16AllStripsCompField("Palette16AllStripsCompField"),
	Palette16AllStripsSplit2CompField("Palette16AllStripsSplit2CompField"),
	Palette16AllStripsSplit3CompField("Palette16AllStripsSplit3CompField"),
	Palette32AllStrips("Palette32AllStrips"),
	Palette32AllStripsSplit2("Palette32AllStripsSplit2"),
	Palette32AllStripsSplit3("Palette32AllStripsSplit3"),
	Palette32AllStripsCompField("Palette32AllStripsCompField"),
	Palette32AllStripsSplit2CompField("Palette32AllStripsSplit2CompField"),
	Palette32AllStripsSplit3CompField("Palette32AllStripsSplit3CompField");

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
		case TileSetOriginalCustom:
			return "typedef struct {\n"
					+ "    u16 numTile;\n"
					+ "    u32* tiles;\n"
					+ "} " + TileSetOriginalCustom.getValue() + ";\n";
		case TileMapOriginalCustom:
			return "typedef struct {\n"
					+ "    u16 w;\n"
					+ "    u16 h;\n"
					+ "    u16* tilemap;\n"
					+ "} " + TileMapOriginalCustom.getValue() + ";\n";
		case TileMapCustom:
			return "typedef struct {\n"
					+ "    u16* tilemap;\n"
					+ "} " + TileMapCustom.getValue() + ";\n";
		case TileMapCustomCompField:
			return "typedef struct {\n"
					+ "    u16 compression;\n"
					+ "    u16* tilemap;\n"
					+ "} " + TileMapCustomCompField.getValue() + ";\n";
		case ImageNoPals:
			return "typedef struct {\n"
					+ "    TileSetOriginalCustom* tileset;\n"
					+ "    TileMapOriginalCustom* tilemap;\n"
					+ "} " + ImageNoPals.getValue() + ";\n";
		case ImageNoPalsCompField:
			return "typedef struct {\n"
					+ "    TileSet* tileset;\n"
					+ "    TileMap* tilemap;\n"
					+ "} " + ImageNoPalsCompField.getValue() + ";\n";
		case ImageNoPalsSplit21:
			return "typedef struct {\n"
					+ "    TileSetOriginalCustom* tileset1;\n"
					+ "    TileSetOriginalCustom* tileset2;\n"
					+ "    TileMapCustom* tilemap1;\n"
					+ "} " + ImageNoPalsSplit21.getValue() + ";\n";
		case ImageNoPalsSplit22:
			return "typedef struct {\n"
					+ "    TileSet* tileset1;\n"
					+ "    TileSet* tileset2;\n"
					+ "    TileMapCustom* tilemap1;\n"
					+ "    TileMapCustom* tilemap2;\n"
					+ "} " + ImageNoPalsSplit22.getValue() + ";\n";
		case ImageNoPalsSplit31:
			return "typedef struct {\n"
					+ "    TileSetOriginalCustom* tileset1;\n"
					+ "    TileSetOriginalCustom* tileset2;\n"
					+ "    TileSetOriginalCustom* tileset3;\n"
					+ "    TileMapCustom* tilemap1;\n"
					+ "} " + ImageNoPalsSplit31.getValue() + ";\n";
		case ImageNoPalsSplit32:
			return "typedef struct {\n"
					+ "    TileSetOriginalCustom* tileset1;\n"
					+ "    TileSetOriginalCustom* tileset2;\n"
					+ "    TileSetOriginalCustom* tileset3;\n"
					+ "    TileMapCustom* tilemap1;\n"
					+ "    TileMapCustom* tilemap2;\n"
					+ "} " + ImageNoPalsSplit32.getValue() + ";\n";
		case ImageNoPalsSplit33:
			return "typedef struct {\n"
					+ "    TileSet* tileset1;\n"
					+ "    TileSet* tileset2;\n"
					+ "    TileSet* tileset3;\n"
					+ "    TileMapCustom* tilemap1;\n"
					+ "    TileMapCustom* tilemap2;\n"
					+ "    TileMapCustom* tilemap3;\n"
					+ "} " + ImageNoPalsSplit33.getValue() + ";\n";
		case ImageNoPals21CompField:
			return "typedef struct {\n"
					+ "    TileSet* tileset1;\n"
					+ "    TileSet* tileset2;\n"
					+ "    TileMapCustom* tilemap1;\n"
					+ "} " + ImageNoPals21CompField.getValue() + ";\n";
		case ImageNoPalsSplit22CompField:
			return "typedef struct {\n"
					+ "    TileSet* tileset1;\n"
					+ "    TileSet* tileset2;\n"
					+ "    TileMapCustomCompField* tilemap1;\n"
					+ "    TileMapCustomCompField* tilemap2;\n"
					+ "} " + ImageNoPalsSplit22CompField.getValue() + ";\n";
		case ImageNoPalsSplit31CompField:
			return "typedef struct {\n"
					+ "    TileSet* tileset1;\n"
					+ "    TileSet* tileset2;\n"
					+ "    TileSet* tileset3;\n"
					+ "    TileMapCustomCompField* tilemap1;\n"
					+ "} " + ImageNoPalsSplit31CompField.getValue() + ";\n";
		case ImageNoPalsSplit32CompField:
			return "typedef struct {\n"
					+ "    TileSet* tileset1;\n"
					+ "    TileSet* tileset2;\n"
					+ "    TileSet* tileset3;\n"
					+ "    TileMapCustomCompField* tilemap1;\n"
					+ "    TileMapCustomCompField* tilemap2;\n"
					+ "} " + ImageNoPalsSplit32CompField.getValue() + ";\n";
		case ImageNoPalsSplit33CompField:
			return "typedef struct {\n"
					+ "    TileSet* tileset1;\n"
					+ "    TileSet* tileset2;\n"
					+ "    TileSet* tileset3;\n"
					+ "    TileMapCustomCompField* tilemap1;\n"
					+ "    TileMapCustomCompField* tilemap2;\n"
					+ "    TileMapCustomCompField* tilemap3;\n"
					+ "} " + ImageNoPalsSplit33CompField.getValue() + ";\n";
		case Palette16:
			return "typedef struct {\n"
					+ "    u16* data;\n"
					+ "} " + Palette16.getValue() + ";\n";
		case Palette32:
			return "typedef struct {\n"
					+ "    u16* data;\n"
					+ "} " + Palette32.getValue() + ";\n";
		case Palette16AllStrips:
			return "typedef struct {\n"
					+ "    u16* data;\n"
					+ "} " + Palette16AllStrips.getValue() + ";\n";
		case Palette16AllStripsSplit2:
			return "typedef struct {\n"
					+ "    u16* data1;\n"
					+ "    u16* data2;\n"
					+ "} " + Palette16AllStripsSplit2.getValue() + ";\n";
		case Palette16AllStripsSplit3:
			return "typedef struct {\n"
					+ "    u16* data1;\n"
					+ "    u16* data2;\n"
					+ "    u16* data3;\n"
					+ "} " + Palette16AllStripsSplit3.getValue() + ";\n";
		case Palette16AllStripsCompField:
			return "typedef struct {\n"
					+ "    u16 compression;\n"
					+ "    u16* data;\n"
					+ "} " + Palette16AllStripsCompField.getValue() + ";\n";
		case Palette16AllStripsSplit2CompField:
			return "typedef struct {\n"
					+ "    u16 compression;\n"
					+ "    u16* data1;\n"
					+ "    u16* data2;\n"
					+ "} " + Palette16AllStripsSplit2CompField.getValue() + ";\n";
		case Palette16AllStripsSplit3CompField:
			return "typedef struct {\n"
					+ "    u16 compression;\n"
					+ "    u16* data1;\n"
					+ "    u16* data2;\n"
					+ "    u16* data3;\n"
					+ "} " + Palette16AllStripsSplit3CompField.getValue() + ";\n";
		case Palette32AllStrips:
			return "typedef struct {\n"
					+ "    u16* data;\n"
					+ "} " + Palette32AllStrips.getValue() + ";\n";
		case Palette32AllStripsSplit2:
			return "typedef struct {\n"
					+ "    u16* data1;\n"
					+ "    u16* data2;\n"
					+ "} " + Palette32AllStripsSplit2.getValue() + ";\n";
		case Palette32AllStripsSplit3:
			return "typedef struct {\n"
					+ "    u16* data1;\n"
					+ "    u16* data2;\n"
					+ "    u16* data3;\n"
					+ "} " + Palette32AllStripsSplit3.getValue() + ";\n";
		case Palette32AllStripsCompField:
			return "typedef struct {\n"
					+ "    u16 compression;\n"
					+ "    u16* data;\n"
					+ "} " + Palette32AllStripsCompField.getValue() + ";\n";
		case Palette32AllStripsSplit2CompField:
			return "typedef struct {\n"
					+ "    u16 compression;\n"
					+ "    u16* data1;\n"
					+ "    u16* data2;\n"
					+ "} " + Palette32AllStripsSplit2CompField.getValue() + ";\n";
		case Palette32AllStripsSplit3CompField:
			return "typedef struct {\n"
					+ "    u16 compression;\n"
					+ "    u16* data1;\n"
					+ "    u16* data2;\n"
					+ "    u16* data3;\n"
					+ "} " + Palette32AllStripsSplit3CompField.getValue() + ";\n";
		default:
			return "NO_DEFINITION";
		}
	}
}
