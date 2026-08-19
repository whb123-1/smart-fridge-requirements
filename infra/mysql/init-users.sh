#!/bin/sh
set -eu

app_password="$(cat /run/secrets/mysql_app_password)"
migration_password="$(cat /run/secrets/mysql_migration_password)"
root_password="${MYSQL_ROOT_PASSWORD:-$(cat /run/secrets/mysql_root_password)}"
app_password_sql="$(printf '%s' "$app_password" | sed "s/'/''/g")"
migration_password_sql="$(printf '%s' "$migration_password" | sed "s/'/''/g")"

mysql --protocol=socket -uroot -p"$root_password" <<SQL
CREATE USER IF NOT EXISTS 'xianzhi_app'@'%' IDENTIFIED BY '$app_password_sql';
ALTER USER 'xianzhi_app'@'%' IDENTIFIED BY '$app_password_sql';
GRANT SELECT, INSERT, UPDATE, DELETE ON xianzhi.* TO 'xianzhi_app'@'%';
CREATE USER IF NOT EXISTS 'xianzhi_migration'@'%' IDENTIFIED BY '$migration_password_sql';
ALTER USER 'xianzhi_migration'@'%' IDENTIFIED BY '$migration_password_sql';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON xianzhi.* TO 'xianzhi_migration'@'%';
FLUSH PRIVILEGES;
SQL
