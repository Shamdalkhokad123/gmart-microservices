-- Scheduled jobs run on one instance at a time (ShedLock)
create table shedlock (
    name varchar(64) not null,
    lock_until timestamp(3) not null,
    locked_at timestamp(3) not null default current_timestamp(3),
    locked_by varchar(255) not null,
    primary key (name)
) engine=InnoDB;

-- REFUND_PENDING: money must go back but Razorpay refused or was unreachable; retried by the reconciler.
-- EXPIRED: the customer never completed the payment.
alter table payment_transactions
    modify status enum ('CREATED','PAID','REFUNDED','REFUND_PENDING','EXPIRED') not null;

alter table payment_transactions add column refund_attempts integer not null default 0;
alter table payment_transactions add column last_checked_at datetime(6);
