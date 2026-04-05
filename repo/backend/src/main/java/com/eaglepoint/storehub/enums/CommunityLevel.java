package com.eaglepoint.storehub.enums;

public enum CommunityLevel {
    NEWCOMER(0),
    CONTRIBUTOR(100),
    TRUSTED(300),
    CHAMPION(700);

    private final int threshold;

    CommunityLevel(int threshold) {
        this.threshold = threshold;
    }

    public int getThreshold() {
        return threshold;
    }

    public static CommunityLevel fromPoints(int points) {
        if (points >= CHAMPION.threshold) return CHAMPION;
        if (points >= TRUSTED.threshold) return TRUSTED;
        if (points >= CONTRIBUTOR.threshold) return CONTRIBUTOR;
        return NEWCOMER;
    }
}
