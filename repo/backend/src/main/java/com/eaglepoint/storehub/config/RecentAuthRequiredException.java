package com.eaglepoint.storehub.config;

/**
 * Thrown when a privileged action requires recent authentication
 * but the user's last authentication is outside the allowed window.
 * Produces a 403 with code "RECENT_AUTH_REQUIRED" so the frontend
 * can prompt for password re-entry and retry.
 */
public class RecentAuthRequiredException extends RuntimeException {
    public RecentAuthRequiredException() {
        super("Recent authentication required. Please re-enter your password.");
    }
}
