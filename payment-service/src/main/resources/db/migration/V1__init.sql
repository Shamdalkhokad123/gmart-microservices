-- payment-service initial schema (MySQL 8). Amounts in paise, as sent to Razorpay.

create table payment_transactions (
    id bigint not null auto_increment,
    razorpay_order_id varchar(64) not null,
    razorpay_payment_id varchar(64),
    username varchar(255) not null,
    amount_paise bigint not null,
    currency varchar(8),
    coupon_code varchar(255),
    shipping_addr varchar(1000),
    status enum ('CREATED','PAID','REFUNDED') not null,
    order_id integer,
    failure_reason varchar(500),
    created_at datetime(6),
    updated_at datetime(6),
    primary key (id),
    constraint ux_pay_rzp_order unique (razorpay_order_id)
) engine=InnoDB;
create index ix_pay_status_created on payment_transactions (status, created_at);
