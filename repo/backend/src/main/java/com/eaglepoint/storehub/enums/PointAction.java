package com.eaglepoint.storehub.enums;

public enum PointAction {
    POST_CREATED(5),
    UPVOTE_RECEIVED(1),
    DOWNVOTE_RECEIVED(-1),
    POST_REMOVED(-10),
    COMMENT_CREATED(2),
    QUARANTINED(-20),
    QUARANTINE_REVERSED(20);

    private final int points;

    PointAction(int points) {
        this.points = points;
    }

    public int getPoints() {
        return points;
    }
}
