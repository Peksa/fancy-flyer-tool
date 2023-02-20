package dev.peksa.speedrun.journey.savefile;

public enum Level {
    CS(0),
    BB(1),
    PD(2),
    SC(3),
    UG(4),
    TW(5),
    SN(6),
    PR(7);
    private final int val;

    Level(int val) {
        this.val = val;
    }

    public int val() {
        return val;
    }
    
    public static Level fromInt(int val) {
        return switch (val) {
            case 0 -> CS;
            case 1 -> BB;
            case 2 -> PD;
            case 3 -> SC;
            case 4 -> UG;
            case 5 -> TW;
            case 6 -> SN;
            case 7 -> PR;
            default -> throw new IllegalStateException("Unexpected value: " + val);
        };
    }
}
