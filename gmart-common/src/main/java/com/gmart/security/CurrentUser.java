package com.gmart.security;

import com.gmart.web.BusinessException;
import org.springframework.security.core.Authentication;

import java.security.Principal;

/**
 * The signed-in user as carried in the JWT: username, role and (for store owners and staff)
 * the store they belong to. Use {@link #of(Authentication)} in controllers.
 */
public record CurrentUser(String username, String role, Integer storeId) implements Principal {

    @Override
    public String getName() {
        return username;
    }

    public static CurrentUser of(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof CurrentUser u) {
            return u;
        }
        throw new BusinessException("Please sign in", 401);
    }

    public boolean isSuperAdmin() {
        return Roles.SUPER_ADMIN.equals(role);
    }

    public boolean isOwner() {
        return Roles.STORE_OWNER.equals(role);
    }

    public boolean isStaff() {
        return Roles.STAFF.equals(role);
    }

    /** Owner or staff of exactly this store. */
    public boolean worksFor(Integer store) {
        return store != null && store.equals(storeId) && (isOwner() || isStaff());
    }

    /** Super admin, or owner/staff of this store (products, stock, processing orders). */
    public boolean canManage(Integer store) {
        return isSuperAdmin() || worksFor(store);
    }

    /** Super admin, or owner of this store (refunds, staff, settings, earnings). */
    public boolean canOwn(Integer store) {
        return isSuperAdmin() || (isOwner() && store != null && store.equals(storeId));
    }

    /** Throws 403 unless {@link #canManage}. */
    public void requireManage(Integer store) {
        if (!canManage(store)) {
            throw new BusinessException("You can only manage your own store", 403);
        }
    }

    /** Throws 403 unless {@link #canOwn}. */
    public void requireOwner(Integer store) {
        if (!canOwn(store)) {
            throw new BusinessException("Only the store owner can do this", 403);
        }
    }

    /** The store an owner/staff member works for; 403 for anyone else. */
    public Integer requireStore() {
        if ((isOwner() || isStaff()) && storeId != null) {
            return storeId;
        }
        throw new BusinessException("This is only for store owners and staff", 403);
    }
}
