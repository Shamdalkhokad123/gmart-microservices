-- Scheduled jobs run on one instance at a time (ShedLock)
create table shedlock (
    name varchar(64) not null,
    lock_until timestamp(3) not null,
    locked_at timestamp(3) not null default current_timestamp(3),
    locked_by varchar(255) not null,
    primary key (name)
) engine=InnoDB;

-- Stock that must be given back to catalog-service (cancelled orders, failed checkouts).
-- Written in the same transaction as the cancellation, then retried until catalog confirms.
create table pending_stock_releases (
    id bigint not null auto_increment,
    release_id varchar(64) not null,
    order_id integer,
    lines_json varchar(4000) not null,
    attempts integer not null,
    last_error varchar(500),
    next_attempt_at datetime(6) not null,
    created_at datetime(6) not null,
    primary key (id),
    constraint uk_stock_release_id unique (release_id)
) engine=InnoDB;
create index ix_stock_release_due on pending_stock_releases (next_attempt_at);
