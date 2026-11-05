#!/bin/bash
# Runs once, when the MySQL volume is first created (docker-entrypoint-initdb.d).
# One database and one user per service; each user can only touch its own database.
set -euo pipefail

create() {
  local db="$1" user="$2" password="$3"
  mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE DATABASE IF NOT EXISTS \`${db}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS '${user}'@'%' IDENTIFIED BY '${password}';
GRANT ALL PRIVILEGES ON \`${db}\`.* TO '${user}'@'%';
SQL
}

create gmart_user_db    gmart_user    "${USER_DB_PASSWORD}"
create gmart_catalog_db gmart_catalog "${CATALOG_DB_PASSWORD}"
create gmart_order_db   gmart_order   "${ORDER_DB_PASSWORD}"
create gmart_payment_db gmart_payment "${PAYMENT_DB_PASSWORD}"
create gmart_store_db   gmart_store   "${STORE_DB_PASSWORD}"
mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "FLUSH PRIVILEGES;"
