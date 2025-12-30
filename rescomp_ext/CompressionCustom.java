package sgdk.rescomp.type;

import sgdk.rescomp.type.Basics.Compression;

public enum CompressionCustom {

	AUTO(					"AUTO",					"",						0), // just a place holder, BinCustom changes the type to Compress before creating the bin
	NONE(					"NONE",					"",						0), // just a place holder, BinCustom changes the type to Compress before creating the bin
	FAST(					"FAST",					"",						0), // just a place holder, BinCustom changes the type to Compress before creating the bin
	LZ4W(					"LZ4W",					"",						0), // just a place holder, BinCustom changes the type to Compress before creating the bin
	APLIB(					"APLIB",				"",						0), // just a place holder, BinCustom changes the type to Compress before creating the bin
	BEST(					"BEST",					"",						0), // just a place holder, BinCustom changes the type to Compress before creating the bin
	BYTEKILLER(				"BYTEKILLER",			"bytekiller",			10),
	CLOWNNEMESIS(			"CLOWNNEMESIS",			"clownnemesis",			11),
	COMPER(					"COMPER",				"compcmp",				12),
	COMPERX(				"COMPERX",				"comperx",				13),
	COMPERXM(				"COMPERXM",				"comperx",				14),
	ELEKTRO(				"ELEKTRO",				"elektro",				15),
	ENIGMA(					"ENIGMA",				"enicmp",				16),
	FC8(					"FC8",					"fc8",					17),
	HIVERLE(				"HIVERLE",				"hiverle",				18),
	KOSINSKI(				"KOSINSKI",				"koscmp",				19),
	KOSINSKI_PLUS(			"KOSINSKI_PLUS",		"kosplus",				20),
	LZ4(					"LZ4", 					"lz4",					21),
	LZ4_SMALL(				"LZ4_SMALL",			"smallz4-v1.5",			22),
	LZ4X(					"LZ4X",					"lz4x_v1.60",			23),
	LZKN1_MDCOMP(			"LZKN1_MDCOMP", 		"lzkn1cmp",				24),
	LZKN1_R57SHELL(			"LZKN1_R57SHELL", 		"lzkn1_r57shell",		25),
	LZKN1_VLADIKCOMPER(		"LZKN1_VLADIKCOMPER", 	"lzkn1_vladikcomper",	26),
	MEGAPACK(				"MEGAPACK", 			"megapack",				27),
	NEMESIS(				"NEMESIS",				"nemcmp",				28),
	NIBBLER(				"NIBBLER",				"Nibble",				29), // keep it as Nibble because it is executed by vamos (virtual amiga os)
	PACKFIRE_LARGE(			"PACKFIRE_LARGE",		"packfire",				30),
	PACKFIRE_TINY(			"PACKFIRE_TINY",		"packfire",				31),
	RLEW_A(					"RLEW_A",				"",						32),
	RLEW_B(					"RLEW_B",				"",						33),
	RNC1(					"RNC1",					"rnc_propack_x64",		34),
	RNC2(					"RNC2",					"rnc_propack_x64",		35),
	ROCKET(					"ROCKET",				"rockcmp",				36),
	SAXMAN(					"SAXMAN",				"saxcmp",				37),
	SBZ(					"SBZ",					"sbz",					38),
	SHRINKLER(				"SHRINKLER",			"shrinkler",			39),
	SNKRLE(					"SNKRLE",				"snkcmp",				40),
	TWIZZLER(				"TWIZZLER",				"twizzler",				41),
	TWIZZLERMOD(			"TWIZZLERMOD",			"twizmod",				42),
	UFTC(					"UFTC",					"uftc",					43),
	UFTC15(					"UFTC15",				"uftc",					44),
	UNAPLIB(				"UNAPLIB",				"",						45),
	ZX0(					"ZX0",					"salvador",				46);

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
