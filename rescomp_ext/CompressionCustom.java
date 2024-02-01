package sgdk.rescomp.type;

public enum CompressionCustom {

	NONE(			"NONE",				"",					0), // same than Compression.NONE.ordinal() - 1 (because first element is AUTO)
	COMPER(			"COMPER",			"compcmp",			5),
	COMPERX(		"COMPERX",			"comperx",			6),
	KOSINSKI(		"KOSINSKI",			"koscmp",			7),
	KOSINSKI_PLUS(	"KOSINSKI_PLUS",	"kosplus",			8),
	LZ4(			"LZ4", 				"lz4",				9),
	LZKN1(			"LZKN1", 			"lzkn1cmp",			10),
	RNC_1(			"RNC_1",			"rnc_propack_x64",	11),
	RNC_2(			"RNC_2",			"rnc_propack_x64",	12),
	ROCKET(			"ROCKET",			"rockcmp",			13),
	SAXMAN(			"SAXMAN",			"saxcmp",			14),
	SAXMAN2(		"SAXMAN2",			"saxcmp",			15),
	SNKRLE(			"SNKRLE",			"snkcmp",			16),
	UFTC(			"UFTC",				"uftc",				17),
	UFTC_15(		"UFTC_15",			"uftc",				18),
	UNAPLIB(		"UNAPLIB",			"",					19);

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
