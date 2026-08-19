#!/bin/sh
set -eu

export MYSQL_PWD="$(cat /run/secrets/mysql_root_password)"
export RESTIC_PASSWORD_FILE=/run/secrets/restic_password
export AWS_ACCESS_KEY_ID="$(cat /run/secrets/restic_s3_access_key)"
export AWS_SECRET_ACCESS_KEY="$(cat /run/secrets/restic_s3_secret_key)"

sleep "${BACKUP_INITIAL_DELAY_SECONDS:-60}"
while true; do
  /opt/xianzhi/backup.sh
  if [ "$(date -u +%u)" = "7" ]; then /opt/xianzhi/restore-drill.sh; fi
  sleep 86400
done
