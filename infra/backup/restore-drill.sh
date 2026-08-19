#!/bin/sh
set -eu

MYSQL_PWD="$(cat /run/secrets/mysql_root_password)"
export MYSQL_PWD

drill_schema=xianzhi_restore_drill
latest="$(find /backups -maxdepth 1 -type f -name 'xianzhi-*.sql.gz' | sort | tail -n 1)"
test -n "$latest"

mysql_args="--host=mysql --user=root --ssl-mode=REQUIRED"
gzip -t "$latest"
mysql $mysql_args -e "DROP DATABASE IF EXISTS ${drill_schema}; CREATE DATABASE ${drill_schema} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
gzip -dc "$latest" | mysql $mysql_args "$drill_schema"
table_count="$(mysql $mysql_args --batch --skip-column-names -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${drill_schema}'")"
test "$table_count" -gt 10
mysql $mysql_args -e "DROP DATABASE ${drill_schema};"

metric_tmp=/metrics/restore-drill.prom.tmp
{
  echo "# HELP xianzhi_restore_drill_last_success_timestamp_seconds Unix timestamp of the last successful isolated restore drill."
  echo "# TYPE xianzhi_restore_drill_last_success_timestamp_seconds gauge"
  echo "xianzhi_restore_drill_last_success_timestamp_seconds $(date -u +%s)"
} > "$metric_tmp"
mv "$metric_tmp" /metrics/restore-drill.prom
