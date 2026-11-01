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
provider="know-image-provider-$suffix"
secret=$(mktemp)
auth_dir=$(mktemp -d)
missing_log=$(mktemp)
live_config=${KNOW_LIVE_IMAGEKIT_CONFIG:-}
cleanup() {
  if [ -n "$live_config" ] && [ "$(docker inspect "$application" --format '{{.State.Running}}' 2>/dev/null || true)" = true ]; then
    # This database exists only for this verification. Flush every journaled test
    # object before discarding it, including uploads whose response was lost.
    docker exec "$database" psql -U know -d know -Atc "update screenshot_cleanup set next_attempt_at=now(),retain_until=now()-interval '1 second'" >/dev/null 2>&1 || true
    count=0
    while [ "$(docker exec "$database" psql -U know -d know -Atc 'select count(*) from screenshot_cleanup' 2>/dev/null || echo unknown)" != 0 ]; do
      count=$((count + 1))
      if [ "$count" -ge 45 ]; then
        echo 'Live ImageKit cleanup is still pending. Test containers retained to preserve the cleanup journal.' >&2
        rm -f "$secret" "$missing_log"
        return
      fi
      sleep 1
    done
    echo 'Live ImageKit test files deleted through the official SDK.'
  fi
  docker rm -f "$application" "$missing" "$database" "$provider" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  rm -f "$secret" "$missing_log"
  rm -rf "$auth_dir"
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
grep -Eq 'config/(auth-)?secrets.yml' "$missing_log"

docker network create "$network" >/dev/null
if [ "${KNOW_RUN_EXTENSION_IT:-0}" = 1 ]; then
  docker run -d --name "$provider" --network "$network" -p 127.0.0.1::10002 \
    --mount "type=bind,source=$(pwd)/scripts/imagekit-fixture.mjs,target=/fixture.mjs,readonly" \
    node:24 node /fixture.mjs >/dev/null
  provider_address="http://$(docker port "$provider" 10002/tcp)"
fi
docker run -d --name "$database" --network "$network" \
  -e POSTGRES_DB=know -e POSTGRES_USER=know -e POSTGRES_PASSWORD=smoke-only-password \
  pgvector/pgvector:0.8.7-pg17 >/dev/null
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
if [ "${KNOW_RUN_EXTENSION_IT:-0}" = 1 ]; then
  cat >>"$secret" <<EOF
know:
  screenshots:
    imagekit:
      private-key: integration-fixture-only
      api-base-url: http://$provider:10002
      url-endpoint: $provider_address
EOF
fi
chmod 644 "$secret"
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$auth_dir/private.pem" 2>/dev/null
openssl pkey -in "$auth_dir/private.pem" -pubout -out "$auth_dir/public.pem" 2>/dev/null
cat >"$auth_dir/auth-secrets.yml" <<EOF
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: smoke-fixture-only
            client-secret: smoke-fixture-only
know:
  identity:
    issuer: http://localhost:8080
    web-origin: http://localhost:5173
    web-redirect-uri: http://localhost:5173/auth/callback
    extension-client-id: know-firefox
    extension-redirect-uri: https://fixture.extensions.allizom.org/
    signing:
      key-id: smoke-only
      private-key: file:/etc/secrets/auth-private-key.pem
      public-key: file:/etc/secrets/auth-public-key.pem
    access-token-lifetime: 24h
    refresh-window: 24h
    refresh-expiry-mode: from-sign-in
    cookie:
      secure: false
      same-site: Lax
    legacy-account:
      id: 00000000-0000-4000-8000-000000000001
      owner-email: owner@example.test
EOF
chmod 755 "$auth_dir"
chmod 644 "$auth_dir/"*
set -- docker run -d --name "$application" --network "$network" \
  --memory=512m --cpus=1 -e PORT=10001 -p 127.0.0.1::10001 \
  --mount "type=bind,source=$secret,target=/etc/secrets/secrets.yml,readonly" \
  --mount "type=bind,source=$auth_dir/auth-secrets.yml,target=/etc/secrets/auth-secrets.yml,readonly" \
  --mount "type=bind,source=$auth_dir/private.pem,target=/etc/secrets/auth-private-key.pem,readonly" \
  --mount "type=bind,source=$auth_dir/public.pem,target=/etc/secrets/auth-public-key.pem,readonly"
if [ -n "$live_config" ]; then
  [ "${KNOW_RUN_EXTENSION_IT:-0}" != 1 ]
  [ -r "$live_config" ]
  set -- "$@" --mount "type=bind,source=$live_config,target=/app/config/imagekit-secrets.yml,readonly" \
    -e KNOW_SCREENSHOTS_CLEANUP_DELAY=1000
fi
"$@" "$image" >/dev/null
address="http://$(docker port "$application" 10001/tcp)"
attempt=0
until curl -fsS --max-time 2 "$address/actuator/health/liveness" 2>/dev/null | grep -q '"status":"UP"'; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 120 ]; then docker logs "$application"; exit 1; fi
  if [ "$(docker inspect "$application" --format '{{.State.Running}}')" != true ]; then docker logs "$application"; exit 1; fi
  sleep 1
done
curl -fsS "$address/" | grep -q 'id="root"'
[ "$(curl -sS -o /dev/null -w '%{http_code}' "$address/api/v1/articles")" = 401 ]
# Seed one disposable authorization grant; exercise the real PKCE token endpoint.
verifier=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
code=smoke-authorization-code
docker exec "$database" psql -U know -d know -v ON_ERROR_STOP=1 -c "
insert into accounts(id,created_at) values ('00000000-0000-4000-8000-000000000001',now());
insert into identity_codes values (rtrim(translate(encode(sha256(convert_to('$code','UTF8')),'base64'),'+/','-_'),'='),'00000000-0000-4000-8000-000000000001','know-firefox','https://fixture.extensions.allizom.org/',rtrim(translate(encode(sha256(convert_to('$verifier','UTF8')),'base64'),'+/','-_'),'='),now()+interval '2 minutes');" >/dev/null
token=$(curl -fsS -H 'Content-Type: application/x-www-form-urlencoded' --data "grant_type=authorization_code&client_id=know-firefox&redirect_uri=https%3A%2F%2Ffixture.extensions.allizom.org%2F&code=$code&code_verifier=$verifier" "$address/oauth2/token" | python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])')
[ "$(curl -sS -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $token" "$address/api/v1/articles")" = 200 ]
[ "$(curl -sS -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $token" -H 'Content-Type: application/json' --data '{"url":"https://example.test/image-smoke","title":"Image smoke article","content":"<p>Saved through the real container API.</p>"}' "$address/api/v1/articles")" = 201 ]
curl -fsS -H "Authorization: Bearer $token" "$address/api/v1/articles" | grep -q 'Image smoke article'
[ "$(docker exec "$database" psql -U know -d know -Atc "select count(*) from articles where account_id='00000000-0000-4000-8000-000000000001' and content='<p>Saved through the real container API.</p>'")" = 1 ]
[ "$(curl -sS -o /dev/null -w '%{http_code}' "$address/v3/api-docs")" = 404 ]
[ "$(curl -sS -o /dev/null -w '%{http_code}' "$address/swagger-ui/index.html")" = 404 ]
expected_migrations=0
for migration in src/main/resources/db/migration/V*__*.sql; do
  [ -f "$migration" ] || continue
  expected_migrations=$((expected_migrations + 1))
done
[ "$expected_migrations" -gt 0 ]
[ "$(docker exec "$database" psql -U know -d know -Atc 'select count(*) from flyway_schema_history where success')" = "$expected_migrations" ]

if [ -n "${KNOW_BROWSER_READY_FILE:-}" ]; then
  printf '%s\n%s\n' "$address" "$provider_address" >"$KNOW_BROWSER_READY_FILE"
  while [ -f "$KNOW_BROWSER_READY_FILE" ]; do sleep 1; done
elif [ "${KNOW_RUN_EXTENSION_IT:-0}" = 1 ]; then
  (cd "${KNOW_EXTENSION_IT_DIR:-../extension/firefox}" && KNOW_IT_ACCESS_TOKEN="$token" KNOW_IT_API_ORIGIN="$address" KNOW_IT_IMAGE_ORIGIN="$provider_address" npm run test:integration)
  [ "$(docker exec "$database" psql -U know -d know -Atc "select count(*) from articles where url like 'https://example.test/phase3/%' and account_id='00000000-0000-4000-8000-000000000001' and version=0 and content='<h2>Article</h2><p>Every word survives retries.</p>'")" = 2 ]
  [ "$(docker exec "$database" psql -U know -d know -Atc "select count(*) from articles a join article_screenshots s on s.article_id=a.id and s.account_id=a.account_id where a.url like 'https://example.test/phase4/%' and a.content='<p>Article survives image failure.</p>' and s.version=2 and s.content_type='image/jpeg' and s.width=32 and s.height=16 and s.file_id is not null")" = 1 ]
fi

if [ -n "$live_config" ]; then
  docker run --rm --network host -e "KNOW_VERIFY_ORIGIN=$address" -e "KNOW_IT_ACCESS_TOKEN=$token" \
    --mount "type=bind,source=$(pwd)/scripts/verify-live-imagekit.mjs,target=/verify.mjs,readonly" \
    node:24 node /verify.mjs
fi
echo 'Image smoke check passed: amd64, non-root, JRE-only, required mounted secrets, custom PORT, real PostgreSQL migration, liveness, anonymous API denied, PKCE token exchange, authenticated saving, disabled docs.'
