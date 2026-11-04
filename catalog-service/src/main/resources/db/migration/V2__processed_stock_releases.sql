-- Makes stock releases idempotent: order-service may retry the same release after a timeout,
-- and the stock must only be added back once.
create table processed_stock_releases (
    release_id varchar(64) not null,
    created_at datetime(6) not null,
    primary key (release_id)
) engine=InnoDB;
