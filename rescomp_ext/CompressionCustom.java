package sgdk.rescomp.type;

import sgdk.rescomp.type.Basics.Compression;

public enum CompressionCustom {

	AUTO(					"AUTO",					"",						0),
	NONE(					"NONE",					"",						0),
	FAST(					"FAST",					"",						0),
	LZ4W(					"LZ4W",					"",						0),
	APLIB(					"APLIB",				"",						0),
	BEST(					"BEST",					"",						0),
	BYTEKILLER(				"BYTEKILLER",			"bytekiller",			10),
	CLOWNNEMESIS(			"CLOWNNEMESIS",			"clownnemesis",			11),
	COMPER(					"COMPER",				"compcmp",				12),
	COMPERX(				"COMPERX",				"comperx",				13),
	COMPERXM(				"COMPERXM",				"comperx",				14),
	ELEKTRO(				"ELEKTRO",				"elektro",				15),
	ENIGMA(					"ENIGMA",				"enicmp",				16),
	FC8(					"FC8",					"fc8",					17),
	KOSINSKI(				"KOSINSKI",				"koscmp",				18),
	KOSINSKI_PLUS(			"KOSINSKI_PLUS",		"kosplus",				19),
	LZ4(					"LZ4", 					"lz4",					20),
	LZKN1_MDCOMP(			"LZKN1_MDCOMP", 		"lzkn1cmp",				21),
	LZKN1_R57SHELL(			"LZKN1_R57SHELL", 		"lzkn1_r57shell",		22),
	LZKN1_VLADIKCOMPER(		"LZKN1_VLADIKCOMPER", 	"lzkn1_vladikcomper",	23),
	MEGAPACK(				"MEGAPACK", 			"megapack",				24),
	NEMESIS(				"NEMESIS",				"nemcmp",				25),
	NIBBLER(				"NIBBLER",				"Nibble",				26),
	PACKFIRE(				"PACKFIRE",				"packfire",				27),
	RNC1(					"RNC1",					"rnc_propack_x64",		28),
	RNC2(					"RNC2",					"rnc_propack_x64",		29),
	ROCKET(					"ROCKET",				"rockcmp",				30),
	SAXMAN(					"SAXMAN",				"saxcmp",				31),
	SBZ(					"SBZ",					"sbz",					32),
	SNKRLE(					"SNKRLE",				"snkcmp",				33),
	UFTC(					"UFTC",					"uftc",					34),
	UFTC15(					"UFTC15",				"uftc",					35),
	UNAPLIB(				"UNAPLIB",				"",						36),
	ZX0(					"ZX0",					"salvador",				37);

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

    /**
     * If one of the SGDK compression enum values? Except NONE.
     * @param cc
     * @return
     */
    public static boolean isOneOfSgdkCompression (CompressionCustom cc) {
    	switch (cc) {
    	case AUTO:
    	case FAST:
    	case LZ4W:
    	case APLIB:
    	case BEST:
    		return true;
    	default: break;
    	}
    	return false;
    }

    public static Compression getSgdkCompression (CompressionCustom cc) {
    	switch (cc) {
    	case AUTO: return Compression.AUTO;
    	case FAST: return Compression.LZ4W;
    	case LZ4W: return Compression.LZ4W;
    	case APLIB: return Compression.APLIB;
    	case BEST: return Compression.AUTO;
    	default: break;
    	}
    	return Compression.NONE;
    }

}
