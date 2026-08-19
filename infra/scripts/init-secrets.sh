#!/bin/sh
set -eu
umask 077

root_dir="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
secret_dir="$root_dir/secrets"
mkdir -p "$secret_dir"

random_secret() { openssl rand -base64 "$1" | tr -d '\n'; }
create_secret() {
  name="$1"; bytes="$2"; target="$secret_dir/$name"
  if [ -e "$target" ]; then printf '%s\n' "保留已有 secret: $name"; return; fi
  random_secret "$bytes" > "$target"
  chmod 644 "$target"
  printf '%s\n' "已生成 secret: $name"
}
create_secret_or_override() {
  name="$1"; bytes="$2"; override_value="$3"; target="$secret_dir/$name"
  if [ -e "$target" ]; then printf '%s\n' "保留已有 secret: $name"; return; fi
  if [ -n "$override_value" ]; then printf '%s' "$override_value" > "$target"; else random_secret "$bytes" > "$target"; fi
  chmod 644 "$target"
  printf '%s\n' "已生成 secret: $name"
}
create_empty() {
  target="$secret_dir/$1"
  if [ ! -e "$target" ]; then : > "$target"; chmod 644 "$target"; fi
}

create_secret_or_override mysql_root_password 32 "${MYSQL_ROOT_PASSWORD_OVERRIDE:-}"
create_secret mysql_app_password 32
create_secret mysql_migration_password 32
create_secret redis_password 32
create_secret jwt_signing_key 64
create_secret identity_tombstone_key 64
create_secret mqtt_service_password 32
create_secret mqtt_internal_token 48
create_secret emqx_node_cookie 48
create_secret emqx_dashboard_password 32
create_secret storage_access_key 16
create_secret storage_secret_key 40
create_secret minio_kms_key 32
create_secret qdrant_api_key 32
create_secret grafana_admin_password 32
create_secret restic_password 32
create_empty deepseek_api_key
create_empty openai_api_key
create_empty restic_s3_access_key
create_empty restic_s3_secret_key

# Compose file-backed secrets retain the source file mode. The parent directory
# prevents host users from traversing into it, while 0644 lets the explicitly
# non-root container users read the individually mounted, read-only files.
chmod 700 "$secret_dir"
chmod 644 "$secret_dir"/*

printf '%s\n' "Secret 已写入 $secret_dir；可选供应商 Secret 仍为空，启用对应功能前必须填写。"
