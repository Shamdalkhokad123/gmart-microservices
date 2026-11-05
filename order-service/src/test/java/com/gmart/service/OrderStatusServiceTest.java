package com.gmart.service;

import com.gmart.entity.OrderDetail;
import com.gmart.entity.OrderStatus;
import com.gmart.messaging.OrderStatusEvent;
import com.gmart.repository.CartItemRepository;
import com.gmart.repository.OrderDetailRepository;
import com.gmart.web.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderStatusServiceTest {

    private final OrderDetailRepository orders = mock(OrderDetailRepository.class);
    private final CartItemRepository items = mock(CartItemRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final OrderStatusService service = new OrderStatusService(orders, items, events, 20);
    private OrderDetail order;

    @BeforeEach
    void setUp() {
        order = new OrderDetail();
        order.setOrderId(7);
        order.setStatus(OrderStatus.PLACED);
        order.setTrans("cod");
        order.setPaymentStatus("PENDING");
        when(orders.findById(7)).thenReturn(Optional.of(order));
    }

    @Test
    void movesOneStepAtATimeAndPublishesEachChange() {
        service.advance(7, OrderStatus.CONFIRMED, null, null);
        service.advance(7, OrderStatus.PACKED, null, null);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PACKED);
        assertThat(order.getConfirmedAt()).isNotNull();
        assertThat(order.getPackedAt()).isNotNull();
        verify(events, times(2)).publishEvent(any(OrderStatusEvent.class));
    }

    @Test
    void cannotSkipSteps() {
        assertThatThrownBy(() -> service.advance(7, OrderStatus.DELIVERED, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot move to DELIVERED");
        verify(orders, never()).save(any());
    }

    @Test
    void sendingOutNeedsARider() {
        order.setStatus(OrderStatus.PACKED);
        assertThatThrownBy(() -> service.advance(7, OrderStatus.OUT_FOR_DELIVERY, "Ravi", " "))
                .hasMessageContaining("Delivery partner name and phone are required");

        service.advance(7, OrderStatus.OUT_FOR_DELIVERY, " Ravi ", "+91 90000 00000");
        assertThat(order.getDeliveryPartnerName()).isEqualTo("Ravi");
        assertThat(order.getEstimatedDeliveryAt()).isAfter(order.getOutForDeliveryAt());
    }

    @Test
    void deliveringCashOrderRecordsPayment() {
        order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
        service.advance(7, OrderStatus.DELIVERED, null, null);
        assertThat(order.getPaymentStatus()).isEqualTo("PAID");
        assertThat(order.getDeliveredAt()).isNotNull();
    }

    @Test
    void repeatingTheCurrentStepIsHarmless() {
        order.setStatus(OrderStatus.CONFIRMED);
        service.advance(7, OrderStatus.CONFIRMED, null, null);
        verify(orders, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void cancelledOrdersCannotMove() {
        order.setStatus(OrderStatus.CANCELLED);
        assertThatThrownBy(() -> service.advance(7, OrderStatus.CONFIRMED, null, null))
                .isInstanceOf(BusinessException.class);
    }
}
