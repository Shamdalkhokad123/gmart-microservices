-- store-service initial schema (MySQL 8)

create table stores (
    id integer not null auto_increment,
    name varchar(120) not null,
    description varchar(500),
    owner_username varchar(255) not null,
    legal_name varchar(120),
    phone varchar(20),
    email varchar(255),
    address varchar(500) not null,
    city varchar(80) not null,
    pincode varchar(10) not null,
    gstin varchar(20),
    fssai varchar(20),
    is_open bit not null,
    status enum ('PENDING','APPROVED','REJECTED','SUSPENDED') not null,
    review_note varchar(500),
    commission_percent decimal(5,2) not null,
    razorpay_account_id varchar(40),
    created_at datetime(6) not null,
    reviewed_at datetime(6),
    primary key (id)
) engine=InnoDB;
create index ix_stores_status on stores (status);
create index ix_stores_owner on stores (owner_username);
