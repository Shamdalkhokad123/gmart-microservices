-- Marketplace payouts with Razorpay Route: the store's share of each online payment is
-- transferred to its linked account. Refunds reverse that transfer.
alter table payment_transactions add column store_id integer;
alter table payment_transactions add column linked_account_id varchar(40);
alter table payment_transactions add column transfer_amount_paise bigint;
create index ix_pay_store on payment_transactions (store_id, created_at);
