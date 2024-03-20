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
	ImageNoPalsTilesetSplit21("ImageNoPalsTilesetSplit21"),
	ImageNoPalsTilesetSplit22("ImageNoPalsTilesetSplit22"),
	ImageNoPalsTilesetSplit31("ImageNoPalsTilesetSplit31"),
	ImageNoPalsTilesetSplit32("ImageNoPalsTilesetSplit32"),
	ImageNoPalsTilesetSplit33("ImageNoPalsTilesetSplit33"),
	ImageNoPalsTilesetSplit21CompField("ImageNoPalsTilesetSplit21CompField"),
	ImageNoPalsTilesetSplit22CompField("ImageNoPalsTilesetSplit22CompField"),
	ImageNoPalsTilesetSplit31CompField("ImageNoPalsTilesetSplit31CompField"),
	ImageNoPalsTilesetSplit32CompField("ImageNoPalsTilesetSplit32CompField"),
	ImageNoPalsTilesetSplit33CompField("ImageNoPalsTilesetSplit33CompField"),
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
					+ "} TileSetOriginalCustom;\n";
		case TileMapOriginalCustom:
			return "typedef struct {\n"
					+ "    u16 w;\n"
					+ "    u16 h;\n"
					+ "    u16* tilemap;\n"
					+ "} TileMapOriginalCustom;\n";
		case TileMapCustom:
			return "typedef struct {\n"
					+ "    u16* tilemap;\n"
					+ "} TileMapCustom;\n";
		case TileMapCustomCompField:
			return "typedef struct {\n"
					+ "    u16 compression;\n"
					+ "    u16* tilemap;\n"
					+ "} TileMapCustomCompField;\n";
		case ImageNoPals:
			return "typedef struct {\n"
					+ "    TileSetOriginalCustom* tileset;\n"
					+ "    TileMapOriginalCustom* tilemap;\n"
					+ "} ImageNoPals;\n";
		case ImageNoPalsCompField:
			return "typedef struct {\n"
					+ "    TileSet* tileset;\n"
					+ "    TileMap* tilemap;\n"
					+ "} ImageNoPalsCompField;\n";
		case ImageNoPalsTilesetSplit21:
			return "typedef struct {\n"
					+ "    TileSetOriginalCustom* tileset1;\n"
					+ "    TileSetOriginalCustom* tileset2;\n"
					+ "    TileMapCustom* tilemap1;\n"
					+ "} ImageNoPalsTilesetSplit21;\n";
		case ImageNoPalsTilesetSplit22:
			return "typedef struct {\n"
					+ "    TileSet* tileset1;\n"
					+ "    TileSet* tileset2;\n"
					+ "    TileMapCustom* tilemap1;\n"
					+ "    TileMapCustom* tilemap2;\n"
					+ "} ImageNoPalsTilesetSplit22;\n";
		case ImageNoPalsTilesetSplit31:
			return "typedef struct {\n"
					+ "    TileSetOriginalCustom* tileset1;\n"
					+ "    TileSetOriginalCustom* tileset2;\n"
					+ "    TileSetOriginalCustom* tileset3;\n"
					+ "    TileMapCustom* tilemap1;\n"
					+ "} ImageNoPalsTilesetSplit31;\n";
		case ImageNoPalsTilesetSplit32:
			return "typedef struct {\n"
					+ "    TileSetOriginalCustom* tileset1;\n"
					+ "    TileSetOriginalCustom* tileset2;\n"
					+ "    TileSetOriginalCustom* tileset3;\n"
					+ "    TileMapCustom* tilemap1;\n"
					+ "    TileMapCustom* tilemap2;\n"
					+ "} ImageNoPalsTilesetSplit32;\n";
		case ImageNoPalsTilesetSplit33:
			return "typedef struct {\n"
					+ "    TileSet* tileset1;\n"
					+ "    TileSet* tileset2;\n"
					+ "    TileSet* tileset3;\n"
					+ "    TileMapCustom* tilemap1;\n"
					+ "    TileMapCustom* tilemap2;\n"
					+ "    TileMapCustom* tilemap3;\n"
					+ "} ImageNoPalsTilesetSplit33;\n";
		case ImageNoPalsTilesetSplit21CompField:
			return "typedef struct {\n"
					+ "    TileSet* tileset1;\n"
					+ "    TileSet* tileset2;\n"
					+ "    TileMapCustom* tilemap1;\n"
					+ "} ImageNoPalsTilesetSplit21CompField;\n";
		case ImageNoPalsTilesetSplit22CompField:
			return "typedef struct {\n"
					+ "    TileSet* tileset1;\n"
					+ "    TileSet* tileset2;\n"
					+ "    TileMapCustomCompField* tilemap1;\n"
					+ "    TileMapCustomCompField* tilemap2;\n"
					+ "} ImageNoPalsTilesetSplit22CompField;\n";
		case ImageNoPalsTilesetSplit31CompField:
			return "typedef struct {\n"
					+ "    TileSet* tileset1;\n"
					+ "    TileSet* tileset2;\n"
					+ "    TileSet* tileset3;\n"
					+ "    TileMapCustomCompField* tilemap1;\n"
					+ "} ImageNoPalsTilesetSplit31CompField;\n";
		case ImageNoPalsTilesetSplit32CompField:
			return "typedef struct {\n"
					+ "    TileSet* tileset1;\n"
					+ "    TileSet* tileset2;\n"
					+ "    TileSet* tileset3;\n"
					+ "    TileMapCustomCompField* tilemap1;\n"
					+ "    TileMapCustomCompField* tilemap2;\n"
					+ "} ImageNoPalsTilesetSplit32CompField;\n";
		case ImageNoPalsTilesetSplit33CompField:
			return "typedef struct {\n"
					+ "    TileSet* tileset1;\n"
					+ "    TileSet* tileset2;\n"
					+ "    TileSet* tileset3;\n"
					+ "    TileMapCustomCompField* tilemap1;\n"
					+ "    TileMapCustomCompField* tilemap2;\n"
					+ "    TileMapCustomCompField* tilemap3;\n"
					+ "} ImageNoPalsTilesetSplit33CompField;\n";
		case Palette16:
			return "typedef struct {\n"
					+ "    u16* data;\n"
					+ "} Palette16;\n";
		case Palette32:
			return "typedef struct {\n"
					+ "    u16* data;\n"
					+ "} Palette32;\n";
		case Palette16AllStrips:
			return "typedef struct {\n"
					+ "    u16* data;\n"
					+ "} Palette16AllStrips;\n";
		case Palette16AllStripsSplit2:
			return "typedef struct {\n"
					+ "    u16* data1;\n"
					+ "    u16* data2;\n"
					+ "} Palette16AllStripsSplit2;\n";
		case Palette16AllStripsSplit3:
			return "typedef struct {\n"
					+ "    u16* data1;\n"
					+ "    u16* data2;\n"
					+ "    u16* data3;\n"
					+ "} Palette16AllStripsSplit3;\n";
		case Palette16AllStripsCompField:
			return "typedef struct {\n"
					+ "    u16 compression;\n"
					+ "    u16* data;\n"
					+ "} Palette16AllStripsCompField;\n";
		case Palette16AllStripsSplit2CompField:
			return "typedef struct {\n"
					+ "    u16 compression;\n"
					+ "    u16* data1;\n"
					+ "    u16* data2;\n"
					+ "} Palette16AllStripsSplit2CompField;\n";
		case Palette16AllStripsSplit3CompField:
			return "typedef struct {\n"
					+ "    u16 compression;\n"
					+ "    u16* data1;\n"
					+ "    u16* data2;\n"
					+ "    u16* data3;\n"
					+ "} Palette16AllStripsSplit3CompField;\n";
		case Palette32AllStrips:
			return "typedef struct {\n"
					+ "    u16* data;\n"
					+ "} Palette32AllStrips;\n";
		case Palette32AllStripsSplit2:
			return "typedef struct {\n"
					+ "    u16* data1;\n"
					+ "    u16* data2;\n"
					+ "} Palette32AllStripsSplit2;\n";
		case Palette32AllStripsSplit3:
			return "typedef struct {\n"
					+ "    u16* data1;\n"
					+ "    u16* data2;\n"
					+ "    u16* data3;\n"
					+ "} Palette32AllStripsSplit3;\n";
		case Palette32AllStripsCompField:
			return "typedef struct {\n"
					+ "    u16 compression;\n"
					+ "    u16* data;\n"
					+ "} Palette32AllStripsCompField;\n";
		case Palette32AllStripsSplit2CompField:
			return "typedef struct {\n"
					+ "    u16 compression;\n"
					+ "    u16* data1;\n"
					+ "    u16* data2;\n"
					+ "} Palette32AllStripsSplit2CompField;\n";
		case Palette32AllStripsSplit3CompField:
			return "typedef struct {\n"
					+ "    u16 compression;\n"
					+ "    u16* data1;\n"
					+ "    u16* data2;\n"
					+ "    u16* data3;\n"
					+ "} Palette32AllStripsSplit3CompField;\n";
		default:
			return "NO_DEFINITION";
		}
	}
}
