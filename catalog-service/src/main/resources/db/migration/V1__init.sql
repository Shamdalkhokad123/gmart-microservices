-- catalog-service initial schema (MySQL 8). Prices are exact decimals in rupees.

create table categories (
    category_id integer not null auto_increment,
    category_name varchar(255) not null,
    category_desc varchar(500),
    image_url varchar(255),
    primary key (category_id),
    constraint uk_category_name unique (category_name)
) engine=InnoDB;

create table products (
    product_id integer not null auto_increment,
    product_name varchar(255) not null,
    product_desc varchar(1000),
    product_category varchar(255) not null,
    price decimal(10,2) not null,
    mrp decimal(10,2),
    unit varchar(255),
    stock integer,
    image_url varchar(255),
    primary key (product_id)
) engine=InnoDB;
create index ix_products_category on products (product_category);
