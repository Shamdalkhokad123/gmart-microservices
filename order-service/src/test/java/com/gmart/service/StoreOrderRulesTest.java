package com.gmart.service;

import com.gmart.client.CatalogClient;
import com.gmart.client.CatalogClient.ProductInfo;
import com.gmart.client.PaymentClient;
import com.gmart.client.StoreClient;
import com.gmart.client.StoreClient.StoreInfo;
import com.gmart.dto.CheckoutRequest;
import com.gmart.entity.CartItem;
import com.gmart.entity.OrderDetail;
import com.gmart.entity.OrderStatus;
import com.gmart.repository.CartItemRepository;
import com.gmart.repository.OrderDetailRepository;
import com.gmart.security.CurrentUser;
import com.gmart.security.Roles;
import com.gmart.web.BusinessException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/** Marketplace rules for carts and orders: one store per cart, who may see / process / cancel. */
class StoreOrderRulesTest {

    private final OrderDetailRepository orders = mock(OrderDetailRepository.class);
    private final CartItemRepository cart = mock(CartItemRepository.class);
    private final CatalogClient catalog = mock(CatalogClient.class);
    private final StoreClient storeClient = mock(StoreClient.class);
    private final PaymentClient payments = mock(PaymentClient.class);
    private final StockReleaseService releases = mock(StockReleaseService.class);
    private final PlatformTransactionManager txm = mock(PlatformTransactionManager.class);
    private final PricingService pricing = new PricingService(orders);

    private OrderService orderService;
    private CartService cartService;
    private OrderStatusService statusService;
    private OrderDetail order;

    private static final CurrentUser CUSTOMER = new CurrentUser("asha", Roles.CUSTOMER, null);
    private static final CurrentUser OTHER_CUSTOMER = new CurrentUser("ravi", Roles.CUSTOMER, null);
    private static final CurrentUser OWNER_1 = new CurrentUser("owner1", Roles.STORE_OWNER, 1);
    private static final CurrentUser STAFF_1 = new CurrentUser("staff1", Roles.STAFF, 1);
    private static final CurrentUser OWNER_2 = new CurrentUser("owner2", Roles.STORE_OWNER, 2);
    private static final CurrentUser SUPER = new CurrentUser("root", Roles.SUPER_ADMIN, null);

    @BeforeEach
    void setUp() {
        when(txm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        orderService = new OrderService(orders, cart, pricing, mock(ApplicationEventPublisher.class), catalog, payments,
                txm, releases, new SimpleMeterRegistry(), storeClient, 30);
        cartService = new CartService(cart, catalog, pricing, storeClient);
        statusService = new OrderStatusService(orders, cart, mock(ApplicationEventPublisher.class), 20);

        order = new OrderDetail();
        order.setOrderId(9);
        order.setUsername("asha");
        order.setStoreId(1);
        order.setStatus(OrderStatus.PLACED);
        order.setPaymentStatus("PENDING");
        order.setTotalAmount(new BigDecimal("100.00"));
        when(orders.findById(9)).thenReturn(Optional.of(order));
        when(cart.findByOrderId(9)).thenReturn(List.of());
    }

    // ---- seeing orders ----

    @Test
    void customerStoreTeamAndSuperAdminCanSeeAnOrder() {
        assertThat(orderService.getOrderFor(CUSTOMER, 9)).isSameAs(order);
        assertThat(orderService.getOrderFor(STAFF_1, 9)).isSameAs(order);
        assertThat(orderService.getOrderFor(SUPER, 9)).isSameAs(order);
    }

    @Test
    void othersGetNotFound() {
        assertThatThrownBy(() -> orderService.getOrderFor(OTHER_CUSTOMER, 9)).hasMessage("Order not found");
        assertThatThrownBy(() -> orderService.getOrderFor(OWNER_2, 9)).hasMessage("Order not found");
    }

    @Test
    void storeOrderListIsAlwaysTheirOwnStore() {
        orderService.getOrdersForStore(STAFF_1, 2); // the requested id is ignored for store members
        verify(orders).findByStoreIdOrderByOrderDateDesc(1);
        assertThatThrownBy(() -> orderService.getOrdersForStore(CUSTOMER, 1)).isInstanceOf(BusinessException.class);
    }

    // ---- processing ----

    @Test
    void onlyTheStoresTeamProcessesItsOrders() {
        statusService.advance(STAFF_1, 9, OrderStatus.CONFIRMED, null, null);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThatThrownBy(() -> statusService.advance(OWNER_2, 9, OrderStatus.PACKED, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus()).isEqualTo(403);
    }

    // ---- cancelling ----

    @Test
    void staffCannotCancel() {
        assertThatThrownBy(() -> orderService.cancelOrder(9, STAFF_1))
                .hasMessage("Only the store owner can cancel orders");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PLACED);
    }

    @Test
    void anotherStoresOwnerCannotCancel() {
        assertThatThrownBy(() -> orderService.cancelOrder(9, OWNER_2)).hasMessage("Order not found");
    }

    @Test
    void storeOwnerCancelsAndPaidOrdersAreRefunded() {
        order.setPaymentStatus("PAID");
        order.setRazorpayPaymentId("pay_1");
        when(payments.refund("pay_1", 10000L)).thenReturn("rfnd_1");
        orderService.cancelOrder(9, OWNER_1);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getPaymentStatus()).isEqualTo("REFUNDED");
    }

    @Test
    void customerCancelsTheirOwnOrder() {
        orderService.cancelOrder(9, CUSTOMER);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    // ---- one store per cart ----

    private static ProductInfo product(int id, int store, String storeName) {
        return new ProductInfo(id, "P" + id, new BigDecimal("10.00"), null, "1 pc", 10, null, store, storeName);
    }

    private static CartItem line(int store) {
        CartItem i = new CartItem();
        i.setStoreId(store);
        i.setQuantity(1);
        i.setPrice(new BigDecimal("10.00"));
        i.setProductId(1);
        return i;
    }

    @Test
    void addingFromAnotherStoreAsksFirst() {
        when(catalog.product(5)).thenReturn(product(5, 2, "Fresh Basket"));
        when(cart.findByUserNameAndPaymentStatus("asha", "NP")).thenReturn(List.of(line(1)));
        when(storeClient.get(1)).thenReturn(new StoreInfo(1, "GMart Indiranagar", "APPROVED", true, true, BigDecimal.TEN, null));

        assertThatThrownBy(() -> cartService.addToCart("asha", 5, 1, false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Your cart has items from GMart Indiranagar. Start a new cart to shop from Fresh Basket?")
                .extracting(e -> ((BusinessException) e).getStatus()).isEqualTo(409);
        verify(cart, never()).deleteAll(anyList());
    }

    @Test
    void startingANewCartReplacesTheOldOne() {
        when(catalog.product(5)).thenReturn(product(5, 2, "Fresh Basket"));
        List<CartItem> old = List.of(line(1));
        when(cart.findByUserNameAndPaymentStatus("asha", "NP")).thenReturn(old);
        when(cart.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CartItem added = cartService.addToCart("asha", 5, 1, true);
        verify(cart).deleteAll(old);
        assertThat(added.getStoreId()).isEqualTo(2);
    }

    // ---- store must be taking orders ----

    @Test
    void checkoutRefusedWhenTheStoreIsClosed() {
        when(cart.findByUserNameAndPaymentStatus("asha", "NP")).thenReturn(List.of(line(1)));
        when(storeClient.get(1)).thenReturn(new StoreInfo(1, "GMart Indiranagar", "APPROVED", false, false, BigDecimal.TEN, null));
        when(storeClient.requireAcceptingOrders(1)).thenCallRealMethod();

        assertThatThrownBy(() -> orderService.checkout("asha", new CheckoutRequest("Home", "cod")))
                .hasMessage("GMart Indiranagar is closed right now");
        verify(catalog, never()).reserve(anyList());
    }
}
