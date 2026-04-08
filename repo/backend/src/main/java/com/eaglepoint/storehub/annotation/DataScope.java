package com.eaglepoint.storehub.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method for automatic multi-dimensional data-scope filtering
 * based on the current user's assigned site, device, and work-order context.
 *
 * Dimensions:
 *   site        — always enforced (based on role/org hierarchy)
 *   device      — enforced when requireDevice = true
 *   workOrder   — enforced when requireWorkOrder = true
 *
 * When a required dimension is absent from the request context, the aspect
 * denies the operation with an AccessDeniedException (deny-by-default).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {
    String siteField() default "site_id";

    /**
     * When true, the aspect requires a non-null device fingerprint in the
     * request context (X-Device-Fingerprint header). Operations without
     * device context are denied.
     */
    boolean requireDevice() default false;

    /**
     * When true, the aspect requires a non-null work-order ID in the
     * request context (X-Work-Order-Id header or workOrderId param).
     * Operations without work-order context are denied.
     */
    boolean requireWorkOrder() default false;
}
