-- Marketplace: one store per cart and per order.
alter table cart_items add column store_id integer;

alter table order_details add column store_id integer;
alter table order_details add column store_name varchar(120);
-- Platform commission (percent of item sales) at the time of the order
alter table order_details add column commission_percent decimal(5,2);
create index ix_orders_store_date on order_details (store_id, order_date);
