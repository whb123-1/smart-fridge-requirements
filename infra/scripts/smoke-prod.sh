#!/usr/bin/env bash
set -euo pipefail

root_dir="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
cd "$root_dir"
set -a
. ./.env.prod
set +a

curl_args=(--fail --silent --show-error --connect-timeout 10 --max-time 30)
if [[ "${SMOKE_INSECURE_TLS:-false}" == "true" ]]; then curl_args+=(-k); fi
base_url="https://${APP_DOMAIN}"

curl "${curl_args[@]}" "$base_url/healthz" | grep -q '"status":"UP"'

ws_headers="$(curl "${curl_args[@]}" --max-time 3 --http1.1 -i -o - \
  -H 'Connection: Upgrade' -H 'Upgrade: websocket' \
  -H 'Sec-WebSocket-Version: 13' -H 'Sec-WebSocket-Key: MTIzNDU2Nzg5MGFiY2RlZg==' \
  -H 'Sec-WebSocket-Protocol: mqtt' \
  "$base_url/mqtt" || true)"
printf '%s' "$ws_headers" | grep -Eq 'HTTP/[0-9.]+ 101'

admin_identifier="${SMOKE_ADMIN_IDENTIFIER:-admin}"
admin_password_file="${SMOKE_ADMIN_PASSWORD_FILE:-$root_dir/secrets/smoke_admin_password}"
test -s "$admin_password_file" || { echo "缺少 $admin_password_file，无法验收管理员实链路" >&2; exit 1; }
admin_password="$(cat "$admin_password_file")"
login_body="$(jq -cn --arg identifier "$admin_identifier" --arg password "$admin_password" '{identifier:$identifier,password:$password}')"
login_response="$(curl "${curl_args[@]}" -H 'Content-Type: application/json' -d "$login_body" "$base_url/api/v1/auth/login")"
access_token="$(printf '%s' "$login_response" | jq -er '.data.accessToken')"
printf '%s' "$login_response" | jq -e '.data.user.role == "ADMIN" and .data.user.passwordChangeRequired == false' >/dev/null
curl "${curl_args[@]}" -H "Authorization: Bearer $access_token" "$base_url/api/v1/admin/users?page=0&size=1" | jq -e '.code == "OK"' >/dev/null

metrics="$(docker compose --env-file .env.prod -f compose.prod.yaml exec -T api curl --fail --silent http://localhost:8080/actuator/prometheus)"
printf '%s' "$metrics" | grep -q 'xianzhi_worker_heartbeat_age_seconds'
heartbeat_age="$(printf '%s' "$metrics" | awk '/^xianzhi_worker_heartbeat_age_seconds / {print $2; exit}')"
awk -v age="$heartbeat_age" 'BEGIN { exit !(age >= 0 && age < 120) }'

if [[ "${STORAGE_PROVIDER:-disabled}" == "s3" ]]; then
  docker compose --env-file .env.prod -f compose.prod.yaml exec -T minio curl --fail --silent http://localhost:9000/minio/health/ready >/dev/null
fi
if [[ "${AI_VECTOR_ENABLED:-false}" == "true" ]]; then
  qdrant_key="$(cat secrets/qdrant_api_key)"
  docker compose --env-file .env.prod -f compose.prod.yaml exec -T api curl --fail --silent -H "api-key: $qdrant_key" http://qdrant:6333/collections >/dev/null
fi

echo "生产冒烟通过：HTTPS、readiness、管理员 API、MQTT-over-WSS、Worker 与已启用内部适配器正常。"
