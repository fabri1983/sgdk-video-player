package sgdk.rescomp.type;

public enum PalettesPositionEnum {

	PAL0("PAL0"),
	PAL1("PAL1"),
	PAL2("PAL2"),
	PAL3("PAL3"),
    PAL0PAL1("PAL0PAL1"),
    PAL2PAL3("PAL2PAL3");

    private final String value;

    PalettesPositionEnum (String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static PalettesPositionEnum from (String s) {
        for (PalettesPositionEnum myEnum : PalettesPositionEnum.values()) {
            if (myEnum.value.equalsIgnoreCase(s)) {
                return myEnum;
            }
        }
        throw new IllegalArgumentException("No enum constant with text: " + s);
    }

    public PalettesPositionEnum next () {
    	if (this == PAL0)
    		return PAL1;
    	else if (this == PAL1)
    		return PAL0;
    	else if (this == PAL2)
    		return PAL3;
    	else if (this == PAL3)
    		return PAL2;
    	else if (this == PAL0PAL1)
    		return PAL2PAL3;
    	else if (this == PAL2PAL3)
    		return PAL0PAL1;
    	return null;
    }
}