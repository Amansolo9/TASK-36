package com.eaglepoint.storehub.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Methods annotated with this require the user to have authenticated
 * within the recent-auth window (default 10 minutes).
 * If the window has elapsed, a 403 with re-authentication required is returned.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresRecentAuth {
}
