package com.gmart.service;

import com.gmart.client.CatalogClient;
import com.gmart.client.CatalogClient.ProductInfo;
import com.gmart.client.CatalogClient.StockLine;
import com.gmart.client.PaymentClient;
import com.gmart.dto.BillDetails;
import com.gmart.dto.CheckoutRequest;
import com.gmart.entity.CartItem;
import com.gmart.entity.OrderDetail;
import com.gmart.entity.OrderStatus;
import com.gmart.messaging.OrderEvents;
import com.gmart.repository.CartItemRepository;
import com.gmart.repository.OrderDetailRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orders. Stock lives in catalog-service, so checkout is: reserve stock there,
 * then save the order here; if saving fails the stock is released again.
 */
@Service
public class OrderService {


    /** A verified, captured Razorpay payment that an order is being created for. */
    public record OnlinePayment(String razorpayOrderId, String razorpayPaymentId, long amountPaise) {
    }

    private final OrderDetailRepository orderDetailRepository;
    private final CartItemRepository cartItemRepository;
    private final PricingService pricingService;
    private final ApplicationEventPublisher eventPublisher;
    private final CatalogClient catalog;
    private final PaymentClient payments;
    private final TransactionTemplate tx;
    private final int promisedMinutes;

    public OrderService(OrderDetailRepository orderDetailRepository, CartItemRepository cartItemRepository,
                        PricingService pricingService, ApplicationEventPublisher eventPublisher,
                        CatalogClient catalog, PaymentClient payments, PlatformTransactionManager transactionManager,
                        @Value("${gmart.delivery.promised-minutes:30}") int promisedMinutes) {
        this.promisedMinutes = promisedMinutes;
        this.orderDetailRepository = orderDetailRepository;
        this.cartItemRepository = cartItemRepository;
        this.pricingService = pricingService;
        this.eventPublisher = eventPublisher;
        this.catalog = catalog;
        this.payments = payments;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** Delivery time promised to customers (gmart.delivery.promised-minutes). */
    public int getPromisedMinutes() {
        return promisedMinutes;
    }

    public static long toPaise(float rupees) {
        return Math.round(rupees * 100.0);
    }

    /** Price the cart exactly as checkout would, without reserving stock. Used to decide how much to charge online. */
    public BillDetails quote(String username, String couponCode) {
        List<CartItem> cart = loadCart(username);
        Map<Integer, ProductInfo> products = byId(catalog.products(cart.stream().map(CartItem::getProductId).toList()));
        for (CartItem item : cart) {
            ProductInfo p = products.get(item.getProductId());
            if (p == null) {
                throw new RuntimeException(item.getProductName() + " is no longer available");
            }
            int stock = p.stock() == null ? Integer.MAX_VALUE : p.stock();
            if (stock < item.getQuantity()) {
                throw new RuntimeException(stock <= 0
                        ? p.productName() + " just went out of stock"
                        : "Only " + stock + " of " + p.productName() + " left");
            }
            applyCurrentPrice(item, p);
        }
        return priceCart(cart, couponCode, username);
    }

    /** Cash on delivery checkout. */
    public OrderDetail checkout(String username, CheckoutRequest request) {
        return checkout(username, request, null);
    }

    /**
     * Creates the order. When {@code payment} is given, the server-side total must
     * match what was actually paid. Safe to repeat for the same payment.
     */
    public OrderDetail checkout(String username, CheckoutRequest request, OnlinePayment payment) {
        if (payment != null) {
            var existing = orderDetailRepository.findByRazorpayOrderId(payment.razorpayOrderId());
            if (existing.isPresent()) {
                return withItems(existing.get());
            }
        }
        List<CartItem> cart = loadCart(username);
        List<StockLine> lines = toLines(cart);

        // 1. Reserve stock in catalog-service (fails the whole checkout if anything ran out)
        Map<Integer, ProductInfo> reserved = byId(catalog.reserve(lines));

        // 2. Save the order here; on any failure, give the stock back
        try {
            return tx.execute(status -> placeOrder(username, request, payment, cart, reserved));
        } catch (RuntimeException e) {
            catalog.releaseQuietly(lines);
            throw e;
        }
    }

    private OrderDetail placeOrder(String username, CheckoutRequest request, OnlinePayment payment,
                                   List<CartItem> cartItems, Map<Integer, ProductInfo> reserved) {
        for (CartItem item : cartItems) {
            applyCurrentPrice(item, reserved.get(item.getProductId())); // charge the current price
        }

        // Price it on the server (never trust client totals)
        BillDetails bill = priceCart(cartItems, request.getCouponCode(), username);
        if (payment != null && toPaise(bill.getToPay()) != payment.amountPaise()) {
            throw new RuntimeException("Your cart changed after the payment was started");
        }

        String itemsSummary = cartItems.stream()
                .map(i -> i.getProductName() + " x" + i.getQuantity())
                .collect(Collectors.joining(", "));

        Date now = new Date();
        OrderDetail order = new OrderDetail();
        order.setUsername(username);
        order.setOrderDate(now);
        order.setShippingAddr(request.getShippingAddr());
        order.setTrans(request.getTrans());
        if (payment != null) {
            order.setPaymentStatus("PAID");
            order.setRazorpayOrderId(payment.razorpayOrderId());
            order.setRazorpayPaymentId(payment.razorpayPaymentId());
        } else {
            order.setPaymentStatus("PENDING");
        }
        order.setItemTotal(bill.getItemTotal());
        order.setMrpTotal(bill.getMrpTotal());
        order.setDeliveryFee(bill.getDeliveryFee());
        order.setHandlingFee(bill.getHandlingFee());
        order.setDiscount(bill.getCouponDiscount());
        order.setCouponCode(bill.getCouponCode());
        order.setTotalAmount(bill.getToPay());
        order.setCartItemId(cartItems.get(0).getCartItemId());
        order.setItemsSummary(itemsSummary.length() > 1990 ? itemsSummary.substring(0, 1990) + "…" : itemsSummary);
        order.setStatus(OrderStatus.PLACED);
        order.setEstimatedDeliveryAt(new Date(now.getTime() + promisedMinutes * 60_000L));
        OrderDetail saved = orderDetailRepository.save(order);

        // Move cart lines onto the order
        for (CartItem item : cartItems) {
            item.setPaymentStatus("P");
            item.setOrderId(saved.getOrderId());
        }
        saved.setItems(cartItemRepository.saveAll(cartItems));

        // Sent to Kafka after commit; the store confirms it from there
        eventPublisher.publishEvent(OrderEvents.placed(saved));
        return saved;
    }

    /** Fetch an order with its items; customers can only see their own. */
    public OrderDetail getOrderForUser(Integer orderId, String username, boolean isAdmin) {
        OrderDetail order = orderDetailRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        if (!isAdmin && !order.getUsername().equals(username)) {
            throw new RuntimeException("Order not found");
        }
        return withItems(order);
    }

    public OrderDetail cancelOrder(Integer orderId, String username, boolean isAdmin) {
        OrderDetail cancelled = tx.execute(status -> {
            OrderDetail order = getOrderForUser(orderId, username, isAdmin);
            if (order.getStatus() == null || !order.getStatus().isCancellable()) {
                throw new RuntimeException("This order can no longer be cancelled");
            }
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancelledAt(new Date());
            // If payment-service can't refund, the exception rolls the cancellation back
            if ("PAID".equals(order.getPaymentStatus()) && order.getRazorpayPaymentId() != null) {
                order.setRazorpayRefundId(payments.refund(order.getRazorpayPaymentId(), toPaise(order.getTotalAmount())));
                order.setPaymentStatus("REFUNDED");
            }
            orderDetailRepository.save(order);
            eventPublisher.publishEvent(OrderEvents.status(order));
            return order;
        });
        // Put the stock back
        catalog.releaseQuietly(toLines(cancelled.getItems()));
        return cancelled;
    }

    public List<OrderDetail> getOrdersForUser(String username) {
        return orderDetailRepository.findByUsernameOrderByOrderDateDesc(username);
    }

    public List<OrderDetail> getAllOrders() {
        return orderDetailRepository.findAllByOrderByOrderDateDesc();
    }

    // --- helpers ---

    private List<CartItem> loadCart(String username) {
        List<CartItem> cartItems = cartItemRepository.findByUserNameAndPaymentStatus(username, "NP");
        if (cartItems.isEmpty()) {
            throw new RuntimeException("Your cart is empty");
        }
        return cartItems;
    }

    private BillDetails priceCart(List<CartItem> cartItems, String couponCode, String username) {
        BillDetails bill = pricingService.calculate(cartItems, couponCode, username);
        if (couponCode != null && !couponCode.isBlank() && !bill.isCouponApplied()) {
            throw new RuntimeException(bill.getCouponMessage());
        }
        return bill;
    }

    private OrderDetail withItems(OrderDetail order) {
        order.setItems(cartItemRepository.findByOrderId(order.getOrderId()));
        return order;
    }

    private static void applyCurrentPrice(CartItem item, ProductInfo p) {
        if (p == null) return;
        item.setPrice(p.price());
        item.setMrp(p.mrp());
    }

    private static List<StockLine> toLines(List<CartItem> items) {
        return items.stream().map(i -> new StockLine(i.getProductId(), i.getProductName(), i.getQuantity())).toList();
    }

    private static Map<Integer, ProductInfo> byId(List<ProductInfo> products) {
        return products == null ? Map.of()
                : products.stream().collect(Collectors.toMap(ProductInfo::productId, Function.identity(), (a, b) -> a));
    }
}
