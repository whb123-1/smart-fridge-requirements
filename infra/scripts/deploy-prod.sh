#!/usr/bin/env bash
set -euo pipefail

root_dir="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
cd "$root_dir"
env_file="${PROD_ENV_FILE:-.env.prod}"
core=(-f compose.prod.yaml)
full=(-f compose.prod.yaml -f compose.monitoring.yaml -f compose.backup.yaml)

set -a
. "$env_file"
set +a

previous_api="$(docker compose --env-file "$env_file" "${core[@]}" ps -q api | xargs -r docker inspect --format '{{.Config.Image}}' 2>/dev/null || true)"
previous_web="$(docker compose --env-file "$env_file" "${core[@]}" ps -q web | xargs -r docker inspect --format '{{.Config.Image}}' 2>/dev/null || true)"
previous_backup="$(docker compose --env-file "$env_file" "${full[@]}" ps -q backup | xargs -r docker inspect --format '{{.Config.Image}}' 2>/dev/null || true)"

rollback() {
  echo "部署验收失败，回滚到上一组不可变镜像" >&2
  [[ -n "$previous_api" ]] && export API_IMAGE="$previous_api"
  [[ -n "$previous_web" ]] && export WEB_IMAGE="$previous_web"
  [[ -n "$previous_backup" ]] && export BACKUP_IMAGE="$previous_backup"
  docker compose --env-file "$env_file" "${full[@]}" up -d api worker web backup
}
trap rollback ERR

docker compose --env-file "$env_file" "${core[@]}" up -d mysql redis emqx minio qdrant
docker compose --env-file "$env_file" "${core[@]}" exec -T mysql /docker-entrypoint-initdb.d/20-users.sh
if [[ "${STORAGE_PROVIDER:-disabled}" == "s3" ]]; then docker compose --env-file "$env_file" "${core[@]}" --profile tools run --rm minio-init; fi
docker compose --env-file "$env_file" "${full[@]}" run --rm --entrypoint /opt/xianzhi/backup.sh backup
docker compose --env-file "$env_file" "${core[@]}" --profile tools run --rm migrate
docker compose --env-file "$env_file" "${full[@]}" pull
docker compose --env-file "$env_file" "${full[@]}" up -d --remove-orphans
bash ./infra/scripts/smoke-prod.sh
sleep "${DEPLOY_OBSERVE_SECONDS:-60}"
if docker compose --env-file "$env_file" "${core[@]}" logs --since "${DEPLOY_OBSERVE_SECONDS:-60}s" api worker | grep -Eq '"level":"ERROR"| level=ERROR '; then
  echo "观察窗口内出现 ERROR 日志" >&2
  exit 1
fi

trap - ERR
echo "生产部署完成"
