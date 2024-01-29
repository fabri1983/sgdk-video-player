package sgdk.rescomp.type;

public enum CompressionCustom {

	NONE("NONE", "", 0), // same than Compression.NONE.ordinal() - 1 (because first element is AUTO)
	COMPER("COMPER", "compcmp", 5),
	COMPERX("COMPERX", "comperx", 6),
	KOSINSKI("KOSINSKI", "koscmp", 7),
	KOSINSKI_PLUS("KOSINSKI_PLUS", "kosplus", 8),
	LZKN1("LZKN1", "lzkn1cmp", 9),
	ROCKET("ROCKET", "rockcmp", 10),
	SAXMAN("SAXMAN", "saxcmp", 11),
	SAXMAN2("SAXMAN2", "saxcmp", 12),
	SNKRLE("SNKRLE", "snkcmp", 13),
	UFTC("UFTC", "uftc", 14),
	UFTC_15("UFTC_15", "uftc", 15),
	UNAPLIB("UNAPLIB", "", 16);

    private final String value;
    private final String exeName;
    private final int defineValue;

    CompressionCustom (String value, String exeName, int defineValue) {
        this.value = value;
        this.exeName = exeName;
        this.defineValue = defineValue;
    }

    public String getValue() {
        return value;
    }

	public String getExeName() {
		return exeName;
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
