package sgdk.rescomp.type;

public enum CompressionCustom {

	NONE(					"NONE",					"",						0), // same than Compression.NONE.ordinal() - 1 (because first element is AUTO)
	BYTEKILLER(				"BYTEKILLER",			"bytekiller",			10),	
	COMPER(					"COMPER",				"compcmp",				11),
	COMPERX(				"COMPERX",				"comperx",				12),
	COMPERXM(				"COMPERXM",				"comperx",				13),
	ELEKTRO(				"ELEKTRO",				"elektro",				14),
	ENIGMA(					"ENIGMA",				"enicmp",				15),
	FC8(					"FC8",					"fc8",					16),
	KOSINSKI(				"KOSINSKI",				"koscmp",				17),
	KOSINSKI_PLUS(			"KOSINSKI_PLUS",		"kosplus",				18),
	LZ4(					"LZ4", 					"lz4",					19),
	LZKN1_MDCOMP(			"LZKN1_MDCOMP", 		"lzkn1cmp",				20),
	LZKN1_R57SHELL(			"LZKN1_R57SHELL", 		"lzkn1_r57shell",		21),
	LZKN1_VLADIKCOMPER(		"LZKN1_VLADIKCOMPER", 	"lzkn1_vladikcomper",	22),
	MEGAPACK(				"MEGAPACK", 			"megapack",				23),
	NEMESIS(				"NEMESIS",				"nemcmp",				24),
	NIBBLER(				"NIBBLER",				"Nibble",				25),
	PACKFIRE(				"PACKFIRE",				"packfire",				26),
	RNC1(					"RNC1",					"rnc_propack_x64",		27),
	RNC2(					"RNC2",					"rnc_propack_x64",		28),
	ROCKET(					"ROCKET",				"rockcmp",				29),
	SAXMAN(					"SAXMAN",				"saxcmp",				30),
	SBZ(					"SBZ",					"sbz",					31),
	SNKRLE(					"SNKRLE",				"snkcmp",				32),
	UFTC(					"UFTC",					"uftc",					33),
	UFTC15(					"UFTC15",				"uftc",					34),
	UNAPLIB(				"UNAPLIB",				"",						35);

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
