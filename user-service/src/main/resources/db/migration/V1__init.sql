-- user-service initial schema (MySQL 8)

create table user_details (
    cust_id bigint not null auto_increment,
    username varchar(255) not null,
    full_name varchar(255),
    mobile_no bigint,
    email varchar(255) not null,
    pass_word varchar(255) not null,
    address varchar(255),
    role varchar(255) not null,
    primary key (cust_id),
    constraint uk_user_username unique (username),
    constraint uk_user_email unique (email)
) engine=InnoDB;

create table addresses (
    address_id integer not null auto_increment,
    username varchar(255) not null,
    label varchar(255),
    receiver_name varchar(255),
    phone varchar(255),
    line1 varchar(500) not null,
    landmark varchar(255),
    city varchar(255),
    pincode varchar(255),
    default_address bit not null,
    primary key (address_id)
) engine=InnoDB;
create index ix_addresses_username on addresses (username);

create table employees (
    employee_id integer not null auto_increment,
    first_name varchar(255) not null,
    last_name varchar(255),
    mobile_no bigint,
    email varchar(255) not null,
    pass_word varchar(255),
    address varchar(255),
    primary key (employee_id),
    constraint uk_employee_email unique (email)
) engine=InnoDB;

create table contact_details (
    cust_id integer not null auto_increment,
    cust_name varchar(255),
    cust_email varchar(255),
    cust_message varchar(2000),
    created_at datetime(6),
    primary key (cust_id)
) engine=InnoDB;
