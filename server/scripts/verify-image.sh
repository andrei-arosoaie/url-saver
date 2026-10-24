#!/bin/sh
set -eu

image=${1:-know-server:local}
command -v docker >/dev/null
command -v curl >/dev/null
suffix="$(date +%s)-$$"
network="know-image-smoke-$suffix"
database="know-image-db-$suffix"
application="know-image-app-$suffix"
missing="know-image-missing-$suffix"
secret=$(mktemp)
missing_log=$(mktemp)
cleanup() {
  docker rm -f "$application" "$missing" "$database" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  rm -f "$secret" "$missing_log"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

[ "$(docker image inspect "$image" --format '{{.Architecture}}')" = amd64 ]
[ "$(docker run --rm --entrypoint id "$image" -u)" = 10001 ]
docker run --rm --entrypoint sh "$image" -c \
  'test ! -e /opt/java/openjdk/bin/javac && test ! -e /app/src && test ! -e /app/gradlew && test -r /app/know-server.jar'
if docker run --name "$missing" "$image" >"$missing_log" 2>&1; then
  echo 'Image unexpectedly started without its required secret file.' >&2
  exit 1
fi
grep -q 'config/secrets.yml' "$missing_log"

docker network create "$network" >/dev/null
docker run -d --name "$database" --network "$network" \
  -e POSTGRES_DB=know -e POSTGRES_USER=know -e POSTGRES_PASSWORD=smoke-only-password \
  postgres:17.11-alpine >/dev/null
attempt=0
until docker exec "$database" pg_isready -U know -d know >/dev/null 2>&1; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 60 ]; then docker logs "$database"; exit 1; fi
  sleep 1
done
cat >"$secret" <<EOF
spring:
  datasource:
    url: jdbc:postgresql://$database:5432/know
    username: know
    password: smoke-only-password
EOF
chmod 644 "$secret"
docker run -d --name "$application" --network "$network" \
  --memory=512m --cpus=1 -e PORT=10001 -p 127.0.0.1::10001 \
  --mount "type=bind,source=$secret,target=/etc/secrets/secrets.yml,readonly" \
  "$image" >/dev/null
address="http://$(docker port "$application" 10001/tcp)"
attempt=0
until curl -fsS --max-time 2 "$address/actuator/health/liveness" 2>/dev/null | grep -q '"status":"UP"'; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 120 ]; then docker logs "$application"; exit 1; fi
  if [ "$(docker inspect "$application" --format '{{.State.Running}}')" != true ]; then docker logs "$application"; exit 1; fi
  sleep 1
done
[ "$(curl -sS -o /dev/null -w '%{http_code}' "$address/api/v1/articles")" = 200 ]
[ "$(curl -sS -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' --data '{"url":"https://example.test/image-smoke","title":"Image smoke article","content":"<p>Saved through the real container API.</p>"}' "$address/api/v1/articles")" = 201 ]
curl -fsS "$address/api/v1/articles" | grep -q 'Image smoke article'
[ "$(docker exec "$database" psql -U know -d know -Atc "select count(*) from articles where account_id='00000000-0000-4000-8000-000000000001' and content='<p>Saved through the real container API.</p>'")" = 1 ]
[ "$(curl -sS -o /dev/null -w '%{http_code}' "$address/v3/api-docs")" = 404 ]
[ "$(curl -sS -o /dev/null -w '%{http_code}' "$address/swagger-ui/index.html")" = 404 ]
[ "$(docker exec "$database" psql -U know -d know -Atc 'select count(*) from flyway_schema_history where success')" = 1 ]
echo 'Image smoke check passed: amd64, non-root, JRE-only, required mounted YAML, custom PORT, real PostgreSQL migration, liveness, temporary-account saving and persisted article content, disabled docs.'
