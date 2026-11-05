package com.gmart.service;

import com.gmart.entity.OrderDetail;
import com.gmart.entity.OrderStatus;
import com.gmart.repository.OrderDetailRepository;
import com.gmart.security.CurrentUser;
import com.gmart.web.BusinessException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * What a store earned in a period, and who owes whom.
 * <p>
 * Per order: item sales after the coupon ("items net"); the platform's commission is a
 * percentage of that; the store's share is the rest. Delivery and handling fees belong to
 * the platform, which also funds coupons.
 * <ul>
 *   <li>Paid online: Razorpay Route already transferred the store's share.</li>
 *   <li>Cash on delivery: the store collected the whole bill, so it owes the platform
 *       the commission plus the fees.</li>
 * </ul>
 * Counted once the money is in: online orders that are paid, cash orders once delivered.
 */
@Service
public class EarningsService {

    public record Line(Integer orderId, Date orderDate, String payment, BigDecimal itemsNet, BigDecimal commission,
                       BigDecimal storeShare, BigDecimal platformFees, BigDecimal transferredOnline, BigDecimal owedToPlatform) {
    }

    public record Report(Integer storeId, LocalDate from, LocalDate to, int orders, BigDecimal itemsNet, BigDecimal commission,
                         BigDecimal storeShare, BigDecimal transferredOnline, BigDecimal cashCollected,
                         BigDecimal owedToPlatform, List<Line> lines) {
    }

    private final OrderDetailRepository orders;

    public EarningsService(OrderDetailRepository orders) {
        this.orders = orders;
    }

    public Report report(CurrentUser me, Integer storeId, LocalDate from, LocalDate to) {
        Integer store = me.isSuperAdmin() ? storeId : me.requireStore();
        if (store == null) {
            throw new BusinessException("Choose a store");
        }
        me.requireOwner(store);
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.withDayOfMonth(1) : from;
        if (start.isAfter(end)) {
            throw new BusinessException("'From' must be before 'to'");
        }
        ZoneId zone = ZoneId.systemDefault();
        Date fromDate = Date.from(start.atStartOfDay(zone).toInstant());
        Date toDate = Date.from(end.plusDays(1).atStartOfDay(zone).toInstant());

        BigDecimal itemsNet = BigDecimal.ZERO, commission = BigDecimal.ZERO, share = BigDecimal.ZERO,
                transferred = BigDecimal.ZERO, cash = BigDecimal.ZERO, owed = BigDecimal.ZERO;
        List<Line> lines = new ArrayList<>();
        for (OrderDetail o : orders.findByStoreIdOrderByOrderDateDesc(store)) {
            if (o.getOrderDate() == null || o.getOrderDate().before(fromDate) || !o.getOrderDate().before(toDate)) continue;
            if (o.getStatus() == OrderStatus.CANCELLED || !"PAID".equals(o.getPaymentStatus())) continue;
            boolean online = "razorpay".equals(o.getTrans());
            if (!online && o.getStatus() != OrderStatus.DELIVERED) continue;

            BigDecimal net = nz(o.getItemTotal()).subtract(nz(o.getDiscount())).max(BigDecimal.ZERO);
            BigDecimal pct = nz(o.getCommissionPercent());
            BigDecimal comm = net.multiply(pct).divide(BigDecimal.valueOf(100), 2, RoundingMode.UP);
            BigDecimal storeShare = net.subtract(comm);
            BigDecimal fees = nz(o.getDeliveryFee()).add(nz(o.getHandlingFee()));
            BigDecimal lineTransferred = online ? storeShare : BigDecimal.ZERO;
            BigDecimal lineOwed = online ? BigDecimal.ZERO : comm.add(fees);

            itemsNet = itemsNet.add(net);
            commission = commission.add(comm);
            share = share.add(storeShare);
            transferred = transferred.add(lineTransferred);
            if (!online) cash = cash.add(nz(o.getTotalAmount()));
            owed = owed.add(lineOwed);
            lines.add(new Line(o.getOrderId(), o.getOrderDate(), online ? "online" : "cash", net, comm, storeShare, fees, lineTransferred, lineOwed));
        }
        return new Report(store, start, end, lines.size(), itemsNet, commission, share, transferred, cash, owed, lines);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
