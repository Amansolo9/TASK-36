package com.eaglepoint.storehub.aspect;

import java.util.List;

/**
 * Thread-local scope context for multi-dimensional data isolation.
 * Supports: site scope, team scope, device scope, and work-order scope.
 */
public final class DataScopeContext {

    private static final ThreadLocal<List<Long>> VISIBLE_SITE_IDS = new ThreadLocal<>();
    private static final ThreadLocal<Long> TEAM_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> DEVICE_HASH = new ThreadLocal<>();
    private static final ThreadLocal<Long> WORK_ORDER_ID = new ThreadLocal<>();

    private DataScopeContext() {}

    // --- Site scope ---
    public static void set(List<Long> siteIds) { VISIBLE_SITE_IDS.set(siteIds); }
    /** null = unrestricted (admin), empty = no access */
    public static List<Long> get() { return VISIBLE_SITE_IDS.get(); }

    // --- Team scope ---
    public static void setTeamId(Long teamId) { TEAM_ID.set(teamId); }
    public static Long getTeamId() { return TEAM_ID.get(); }

    // --- Device scope ---
    public static void setDeviceHash(String hash) { DEVICE_HASH.set(hash); }
    public static String getDeviceHash() { return DEVICE_HASH.get(); }

    // --- Work-order scope ---
    public static void setWorkOrderId(Long id) { WORK_ORDER_ID.set(id); }
    public static Long getWorkOrderId() { return WORK_ORDER_ID.get(); }

    public static void clear() {
        VISIBLE_SITE_IDS.remove();
        TEAM_ID.remove();
        DEVICE_HASH.remove();
        WORK_ORDER_ID.remove();
    }
}
