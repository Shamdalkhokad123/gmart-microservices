package com.gmart.service;

import com.gmart.client.PlatformClients;
import com.gmart.entity.Store;
import com.gmart.entity.Store.Status;
import com.gmart.repository.StoreRepository;
import com.gmart.security.CurrentUser;
import com.gmart.security.Roles;
import com.gmart.web.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Store lifecycle: a customer applies -> PENDING; the super admin approves (the applicant becomes
 * the store owner and the store's products become visible), rejects, suspends or reactivates.
 */
@Service
public class StoreService {

    private static final Logger log = LoggerFactory.getLogger(StoreService.class);
    private static final Pattern PINCODE = Pattern.compile("\\d{6}");
    private static final Pattern GSTIN = Pattern.compile("\\d{2}[A-Z]{5}\\d{4}[A-Z][1-9A-Z]Z[0-9A-Z]");
    private static final Pattern FSSAI = Pattern.compile("\\d{14}");
    private static final Pattern RAZORPAY_ACCOUNT = Pattern.compile("acc_[A-Za-z0-9]{8,30}");

    /** What an applicant (or the owner, later) can fill in. */
    public record StoreForm(String name, String description, String legalName, String phone, String email,
                            String address, String city, String pincode, String gstin, String fssai) {
    }

    /** Super admin settings. */
    public record AdminSettings(BigDecimal commissionPercent, String razorpayAccountId) {
    }

    private final StoreRepository stores;
    private final PlatformClients platform;

    public StoreService(StoreRepository stores, PlatformClients platform) {
        this.stores = stores;
        this.platform = platform;
    }

    // ---------------- public ----------------

    public List<Store> visibleStores() {
        return stores.findByStatusOrderByNameAsc(Status.APPROVED);
    }

    public Store visibleStore(Integer id) {
        Store s = find(id);
        if (!s.isVisible()) {
            throw new BusinessException("Store not found", 404);
        }
        return s;
    }

    // ---------------- applicant ----------------

    @Transactional
    public Store apply(CurrentUser me, StoreForm form) {
        if (!Roles.CUSTOMER.equals(me.role())) {
            throw new BusinessException("This account already works for the platform or a store. Use a customer account to apply.", 409);
        }
        if (stores.existsByOwnerUsernameAndStatusIn(me.username(), EnumSet.of(Status.PENDING, Status.APPROVED, Status.SUSPENDED))) {
            throw new BusinessException("You already have a store or an application in review", 409);
        }
        Store s = new Store();
        s.setOwnerUsername(me.username());
        apply(s, form);
        s.setStatus(Status.PENDING);
        Store saved = stores.save(s);
        log.info("Store application {} '{}' from {}", saved.getId(), saved.getName(), me.username());
        return saved;
    }

    public List<Store> myApplications(String username) {
        return stores.findByOwnerUsernameOrderByCreatedAtDesc(username);
    }

    // ---------------- owner ----------------

    public Store mine(CurrentUser me) {
        return find(me.requireStore());
    }

    @Transactional
    public Store updateMine(CurrentUser me, StoreForm form, Boolean open) {
        Store s = find(me.requireStore());
        me.requireOwner(s.getId());
        apply(s, form);
        if (open != null) {
            s.setOpen(open);
        }
        Store saved = stores.save(s);
        if (saved.isVisible()) {
            platform.syncCatalog(saved.getId(), saved.getName(), true);
        }
        return saved;
    }

    // ---------------- super admin ----------------

    public List<Store> all(Status status) {
        return status == null ? stores.findAllByOrderByCreatedAtDesc() : stores.findByStatusOrderByCreatedAtDesc(status);
    }

    @Transactional
    public Store approve(String actor, Integer id, AdminSettings settings) {
        Store s = find(id);
        if (s.getStatus() != Status.PENDING) {
            throw new BusinessException("Only pending applications can be approved (this one is " + s.getStatus() + ")", 409);
        }
        applyAdmin(s, settings);
        s.setStatus(Status.APPROVED);
        s.setReviewNote(null);
        s.setReviewedAt(new Date());
        Store saved = stores.save(s);
        // If either call fails, the approval is rolled back
        platform.makeOwner(saved.getOwnerUsername(), saved.getId());
        platform.syncCatalog(saved.getId(), saved.getName(), true);
        log.warn("AUDIT: '{}' approved store {} '{}' (owner {})", actor, saved.getId(), saved.getName(), saved.getOwnerUsername());
        return saved;
    }

    @Transactional
    public Store reject(String actor, Integer id, String note) {
        Store s = find(id);
        if (s.getStatus() != Status.PENDING) {
            throw new BusinessException("Only pending applications can be rejected", 409);
        }
        s.setStatus(Status.REJECTED);
        s.setReviewNote(requireNote(note));
        s.setReviewedAt(new Date());
        log.warn("AUDIT: '{}' rejected store application {}", actor, id);
        return stores.save(s);
    }

    /** Hides the store and its products; no new orders. The owner keeps access to their history. */
    @Transactional
    public Store suspend(String actor, Integer id, String note) {
        Store s = find(id);
        if (s.getStatus() != Status.APPROVED) {
            throw new BusinessException("Only approved stores can be suspended", 409);
        }
        s.setStatus(Status.SUSPENDED);
        s.setReviewNote(requireNote(note));
        s.setReviewedAt(new Date());
        Store saved = stores.save(s);
        platform.syncCatalog(saved.getId(), saved.getName(), false);
        log.warn("AUDIT: '{}' suspended store {}: {}", actor, id, note);
        return saved;
    }

    @Transactional
    public Store reactivate(String actor, Integer id) {
        Store s = find(id);
        if (s.getStatus() != Status.SUSPENDED) {
            throw new BusinessException("Only suspended stores can be reactivated", 409);
        }
        s.setStatus(Status.APPROVED);
        s.setReviewNote(null);
        s.setReviewedAt(new Date());
        Store saved = stores.save(s);
        platform.syncCatalog(saved.getId(), saved.getName(), true);
        log.warn("AUDIT: '{}' reactivated store {}", actor, id);
        return saved;
    }

    @Transactional
    public Store updateAdmin(String actor, Integer id, AdminSettings settings) {
        Store s = find(id);
        applyAdmin(s, settings);
        log.warn("AUDIT: '{}' set store {} commission={} account={}", actor, id, s.getCommissionPercent(), s.getRazorpayAccountId());
        return stores.save(s);
    }

    public Store find(Integer id) {
        return stores.findById(id).orElseThrow(() -> new BusinessException("Store not found", 404));
    }

    // ---------------- helpers ----------------

    private static void apply(Store s, StoreForm f) {
        if (f == null || blank(f.name()) || blank(f.address()) || blank(f.city()) || blank(f.pincode()) || blank(f.phone())) {
            throw new BusinessException("Store name, phone, address, city and PIN code are required");
        }
        if (!PINCODE.matcher(f.pincode().trim()).matches()) {
            throw new BusinessException("PIN code must be 6 digits");
        }
        String gstin = upper(f.gstin());
        if (gstin != null && !GSTIN.matcher(gstin).matches()) {
            throw new BusinessException("GSTIN doesn't look right (15 characters, e.g. 29ABCDE1234F1Z5)");
        }
        String fssai = trim(f.fssai());
        if (fssai != null && !FSSAI.matcher(fssai).matches()) {
            throw new BusinessException("FSSAI licence number must be 14 digits");
        }
        s.setName(f.name().trim());
        s.setDescription(trim(f.description()));
        s.setLegalName(trim(f.legalName()));
        s.setPhone(f.phone().trim());
        s.setEmail(trim(f.email()));
        s.setAddress(f.address().trim());
        s.setCity(f.city().trim());
        s.setPincode(f.pincode().trim());
        s.setGstin(gstin);
        s.setFssai(fssai);
    }

    private static void applyAdmin(Store s, AdminSettings settings) {
        if (settings == null) return;
        if (settings.commissionPercent() != null) {
            BigDecimal c = settings.commissionPercent();
            if (c.signum() < 0 || c.compareTo(BigDecimal.valueOf(50)) > 0) {
                throw new BusinessException("Commission must be between 0 and 50 %");
            }
            s.setCommissionPercent(c);
        }
        if (settings.razorpayAccountId() != null) {
            String acc = settings.razorpayAccountId().trim();
            if (!acc.isEmpty() && !RAZORPAY_ACCOUNT.matcher(acc).matches()) {
                throw new BusinessException("Razorpay linked account id looks like acc_XXXXXXXXXXXXXX");
            }
            s.setRazorpayAccountId(acc.isEmpty() ? null : acc);
        }
    }

    private static String requireNote(String note) {
        if (blank(note)) {
            throw new BusinessException("Please give a reason (the owner will see it)");
        }
        return note.trim().length() > 500 ? note.trim().substring(0, 500) : note.trim();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String trim(String s) {
        return blank(s) ? null : s.trim();
    }

    private static String upper(String s) {
        return blank(s) ? null : s.trim().toUpperCase();
    }
}
