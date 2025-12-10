package sgdk.rescomp.type;

public enum TilesetSplitStrategyEnum {

	SPLIT_NONE("SPLIT_NONE"),
	SPLIT_NORMAL("SPLIT_NORMAL"),
	SPLIT_MAX_CAPACITY_FIRST("SPLIT_MAX_CAPACITY_FIRST"),
	SPLIT_EVENLY("SPLIT_EVENLY");

    private final String value;

    TilesetSplitStrategyEnum (String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static TilesetSplitStrategyEnum from (String s) {
        for (TilesetSplitStrategyEnum myEnum : TilesetSplitStrategyEnum.values()) {
            if (myEnum.value.equalsIgnoreCase(s)) {
                return myEnum;
            }
        }
        throw new IllegalArgumentException("No enum constant with text: " + s);
    }
}
