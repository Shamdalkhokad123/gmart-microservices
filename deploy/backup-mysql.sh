#!/bin/bash
# Nightly backup of every GMart database from the production compose stack.
#   BACKUP_DIR=/var/backups/gmart ./deploy/backup-mysql.sh
# Run from cron, e.g.:  15 2 * * * cd /opt/gmart-microservices && ./deploy/backup-mysql.sh
# Copy the backup folder off the server too (another machine or cloud storage), and
# test a restore now and then:
#   gunzip -c gmart_order_db-<date>.sql.gz | docker exec -i <mysql container> mysql -uroot -p<pw> gmart_order_db
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/var/backups/gmart}"
KEEP_DAYS="${KEEP_DAYS:-14}"
CONTAINER="${MYSQL_CONTAINER:-$(docker compose -f docker-compose.prod.yml ps -q mysql)}"
STAMP="$(date +%Y%m%d-%H%M%S)"

mkdir -p "$BACKUP_DIR"
for db in gmart_user_db gmart_catalog_db gmart_order_db gmart_payment_db; do
  out="$BACKUP_DIR/$db-$STAMP.sql.gz"
  # --single-transaction: consistent snapshot without locking the shop
  docker exec "$CONTAINER" sh -c "exec mysqldump -uroot -p\"\$MYSQL_ROOT_PASSWORD\" --single-transaction --routines --triggers $db" | gzip > "$out"
  echo "backed up $db -> $out ($(du -h "$out" | cut -f1))"
done
find "$BACKUP_DIR" -name 'gmart_*.sql.gz' -mtime +"$KEEP_DAYS" -delete
