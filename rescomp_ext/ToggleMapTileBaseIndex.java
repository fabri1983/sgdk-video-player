package sgdk.rescomp.type;

public enum ToggleMapTileBaseIndex {

    NONE("NONE"),
    EVEN("EVEN"),
	ODD("ODD");

    private final String value;

    ToggleMapTileBaseIndex (String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ToggleMapTileBaseIndex from (String s) {
        for (ToggleMapTileBaseIndex myEnum : ToggleMapTileBaseIndex.values()) {
            if (myEnum.value.equalsIgnoreCase(s)) {
                return myEnum;
            }
        }
        throw new IllegalArgumentException("No enum constant with text: " + s);
    }
}
