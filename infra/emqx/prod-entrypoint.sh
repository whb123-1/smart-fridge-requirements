#!/bin/sh
set -eu
internal_token="$(cat /run/secrets/mqtt_internal_token)"
export EMQX_NODE__COOKIE="$(cat /run/secrets/emqx_node_cookie)"
export EMQX_DASHBOARD__DEFAULT_PASSWORD="$(cat /run/secrets/emqx_dashboard_password)"
umask 077
awk -v token="$internal_token" '{gsub(/configured-by-environment/, token); print}' \
  /opt/xianzhi/base.hocon.template > /opt/emqx/etc/base.hocon
exec /usr/bin/docker-entrypoint.sh emqx foreground
