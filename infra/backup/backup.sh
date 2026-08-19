#!/bin/sh
set -eu

MYSQL_PWD="$(cat /run/secrets/mysql_root_password)"
export MYSQL_PWD

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
dump_partial="/backups/xianzhi-${stamp}.sql.partial"
partial="/backups/xianzhi-${stamp}.sql.gz.partial"
target="/backups/xianzhi-${stamp}.sql.gz"
trap 'rm -f "$dump_partial" "$partial"' EXIT

mysqldump --host=mysql --user=root --ssl-mode=REQUIRED \
  --single-transaction --quick --routines --events --triggers xianzhi > "$dump_partial"
test -s "$dump_partial"
gzip -9 < "$dump_partial" > "$partial"
test -s "$partial"
gzip -t "$partial"
rm "$dump_partial"
mv "$partial" "$target"
trap - EXIT
find /backups -maxdepth 1 -type f -name 'xianzhi-*.sql.gz' -mtime +7 -delete

if [ -n "${RESTIC_REPOSITORY:-}" ]; then
  restic snapshots >/dev/null 2>&1 || restic init
  restic backup "$target" --tag mysql --host "${BACKUP_HOST:-xianzhi-production}"
  restic forget --tag mysql --keep-daily 30 --keep-monthly 12 --prune
fi

metric_tmp=/metrics/backup.prom.tmp
{
  echo "# HELP xianzhi_backup_last_success_timestamp_seconds Unix timestamp of the last successful database backup."
  echo "# TYPE xianzhi_backup_last_success_timestamp_seconds gauge"
  echo "xianzhi_backup_last_success_timestamp_seconds $(date -u +%s)"
  echo "# HELP xianzhi_backup_size_bytes Size of the last local compressed backup."
  echo "# TYPE xianzhi_backup_size_bytes gauge"
  echo "xianzhi_backup_size_bytes $(wc -c < "$target")"
} > "$metric_tmp"
mv "$metric_tmp" /metrics/backup.prom
