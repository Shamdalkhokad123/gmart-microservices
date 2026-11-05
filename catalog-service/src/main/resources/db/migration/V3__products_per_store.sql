-- Marketplace: every product belongs to a store.
alter table products add column store_id integer;
create index ix_products_store on products (store_id);

-- Which stores are visible to customers (kept in sync by store-service on approve / suspend)
create table stores (
    id integer not null,
    name varchar(120) not null,
    active bit not null,
    primary key (id)
) engine=InnoDB;
