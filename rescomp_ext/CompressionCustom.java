package sgdk.rescomp.type;

public enum CompressionCustom {

	NONE("NONE", 0), // same than Compression.NONE.ordinal() - 1
	COMPER("COMPER", 4),
	KOSINSKI("KOSINSKI", 5),
	KOSINSKI_PLUS("KOSINSKI_PLUS", 6);
	
    private final String value;
    private final int defineValue;

    CompressionCustom (String value, int defineValue) {
        this.value = value;
        this.defineValue = defineValue;
    }

    public String getValue() {
        return value;
    }

    public int getDefineValue() {
        return defineValue;
    }

    public static CompressionCustom from (String s) {
        for (CompressionCustom myEnum : CompressionCustom.values()) {
            if (myEnum.value.equalsIgnoreCase(s)) {
                return myEnum;
            }
        }
        throw new IllegalArgumentException("No enum constant with text: " + s);
    }

}
