package com.gmart.security;

import java.util.Set;

/**
 * Marketplace roles.
 * <ul>
 *   <li>SUPER_ADMIN: runs the platform (approves stores, categories, all orders and payments).</li>
 *   <li>STORE_OWNER: runs one store (products, orders incl. refunds, staff, settings, earnings).</li>
 *   <li>STAFF: works for one store (products and stock, processing orders).</li>
 *   <li>CUSTOMER: shops; can apply to open a store.</li>
 * </ul>
 */
public final class Roles {

    public static final String SUPER_ADMIN = "ROLE_SUPER_ADMIN";
    public static final String STORE_OWNER = "ROLE_STORE_OWNER";
    public static final String STAFF = "ROLE_STAFF";
    public static final String CUSTOMER = "ROLE_CUSTOMER";

    /** Roles with back-office access; they get short-lived sessions. */
    public static final Set<String> ELEVATED = Set.of(SUPER_ADMIN, STORE_OWNER, STAFF);

    /** SpEL for @PreAuthorize: anyone who works for a store (or the platform). */
    public static final String CAN_MANAGE_STORE = "hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_STORE_OWNER','ROLE_STAFF')";

    private Roles() {
    }
}
