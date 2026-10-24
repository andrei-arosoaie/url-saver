#!/usr/bin/env bash
set -euo pipefail
script="$(cd "$(dirname "$0")" && pwd)/deploy-render.sh"
temporary=$(mktemp -d)
trap 'rm -rf "$temporary"' EXIT
mkdir "$temporary/bin"
cat > "$temporary/bin/curl" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
url="${!#}"
echo "$url" >> "$MOCK_LOG"
case "$url" in
  */deploys/dep-test)
    if [[ "$MOCK_CASE" == failed ]]; then echo '{"status":"update_failed"}'; else echo '{"status":"live"}'; fi ;;
  */deploys)
    if [[ "$MOCK_CASE" == post-failure ]]; then exit 22; fi
    previous=''
    for argument in "$@"; do
      if [[ "$previous" == --data-binary ]]; then cp "${argument#@}" "$MOCK_REQUEST"; fi
      previous="$argument"
    done
    echo '{"id":"dep-test"}' ;;
  */actuator/health/liveness)
    if [[ "$MOCK_CASE" == unhealthy ]]; then echo '{"status":"DOWN"}'; else echo '{"status":"UP"}'; fi ;;
  */services/srv-test)
    if [[ "$MOCK_CASE" == wrong-service ]]; then
      echo '{"imagePath":"ghcr.io/other/app:old","serviceDetails":{"url":"https://know-test.onrender.com"}}'
    else
      echo '{"imagePath":"ghcr.io/owner/repo-server:old","serviceDetails":{"url":"https://know-test.onrender.com"}}'
    fi ;;
  *) echo 'Unexpected request in deployment test.' >&2; exit 1 ;;
esac
MOCK
chmod +x "$temporary/bin/curl"
export PATH="$temporary/bin:$PATH"
export RENDER_API_KEY='test-only-not-a-real-key' RENDER_SERVICE_ID=srv-test GITHUB_REPOSITORY=Owner/Repo
export IMAGE_SHA=0123456789012345678901234567890123456789
export MOCK_LOG="$temporary/requests" MOCK_REQUEST="$temporary/body" GITHUB_STEP_SUMMARY="$temporary/summary"
for scenario in success failed unhealthy wrong-service post-failure invalid-sha; do
  export MOCK_CASE="$scenario"
  : > "$MOCK_LOG"
  selected="$IMAGE_SHA"
  if [[ "$scenario" == invalid-sha ]]; then selected='bad; echo unsafe'; fi
  if IMAGE_SHA="$selected" bash "$script" > "$temporary/output" 2>&1; then
    [[ "$scenario" == success ]] || { echo "Unexpected success: $scenario" >&2; exit 1; }
    jq -e '.imageUrl == "ghcr.io/owner/repo-server:0123456789012345678901234567890123456789"' "$MOCK_REQUEST" >/dev/null
    grep -q 'health/liveness' "$MOCK_LOG"
    grep -q 'Health' "$GITHUB_STEP_SUMMARY"
  else
    [[ "$scenario" != success ]] || { cat "$temporary/output"; exit 1; }
  fi
  if [[ "$scenario" == wrong-service || "$scenario" == invalid-sha ]]; then
    ! grep -q '/deploys$' "$MOCK_LOG"
  fi
  if [[ "$scenario" == post-failure ]]; then
    [[ "$(grep -c '/deploys$' "$MOCK_LOG")" == 1 ]]
  fi
  echo "Deployment script test passed: $scenario"
done
