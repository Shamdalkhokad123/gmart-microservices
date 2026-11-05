-- Marketplace: super admin / store owner / staff / customer.
-- Store owners and staff belong to one store (id from store-service).
alter table user_details add column store_id integer;
create index ix_user_store on user_details (store_id);

-- Existing admins become platform super admins
update user_details set role = 'ROLE_SUPER_ADMIN' where role = 'ROLE_ADMIN';

-- Store staff accounts replace the old employee records
drop table employees;
