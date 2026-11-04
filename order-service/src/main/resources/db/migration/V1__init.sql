-- order-service initial schema (MySQL 8). Amounts are exact decimals in rupees.

create table cart_items (
    cart_item_id integer not null auto_increment,
    product_id integer,
    quantity integer,
    price decimal(10,2),
    mrp decimal(10,2),
    unit varchar(255),
    product_name varchar(255),
    user_name varchar(255),
    payment_status varchar(10),
    image_url varchar(255),
    order_id integer,
    primary key (cart_item_id)
) engine=InnoDB;
create index ix_cart_user_status on cart_items (user_name, payment_status);
create index ix_cart_order on cart_items (order_id);

create table order_details (
    order_id integer not null auto_increment,
    cart_item_id integer,
    total_amount decimal(10,2) not null,
    item_total decimal(10,2),
    mrp_total decimal(10,2),
    delivery_fee decimal(10,2),
    handling_fee decimal(10,2),
    discount decimal(10,2),
    coupon_code varchar(255),
    username varchar(255),
    order_date datetime(6),
    shipping_addr varchar(1000),
    trans varchar(255),
    payment_status varchar(20),
    razorpay_order_id varchar(64),
    razorpay_payment_id varchar(64),
    razorpay_refund_id varchar(64),
    items_summary varchar(2000),
    status enum ('PLACED','CONFIRMED','PACKED','OUT_FOR_DELIVERY','DELIVERED','CANCELLED'),
    estimated_delivery_at datetime(6),
    confirmed_at datetime(6),
    packed_at datetime(6),
    out_for_delivery_at datetime(6),
    delivered_at datetime(6),
    cancelled_at datetime(6),
    delivery_partner_name varchar(255),
    delivery_partner_phone varchar(255),
    primary key (order_id),
    -- one order per Razorpay payment (makes order creation from a payment idempotent)
    constraint uk_order_razorpay_order unique (razorpay_order_id)
) engine=InnoDB;
create index ix_orders_user_date on order_details (username, order_date);
create index ix_orders_status on order_details (status);
