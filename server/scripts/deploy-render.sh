#!/usr/bin/env bash
set -euo pipefail

: "${RENDER_API_KEY:?Set RENDER_API_KEY}"
: "${RENDER_SERVICE_ID:?Set RENDER_SERVICE_ID}"
: "${GITHUB_REPOSITORY:?Set GITHUB_REPOSITORY}"
: "${IMAGE_SHA:?Set IMAGE_SHA}"
[[ "$IMAGE_SHA" =~ ^[0-9a-f]{40}$ ]] || { echo 'Invalid image commit SHA.' >&2; exit 1; }
[[ "$RENDER_SERVICE_ID" =~ ^srv-[a-z0-9]+$ ]] || { echo 'Invalid Render service ID.' >&2; exit 1; }
[[ "$GITHUB_REPOSITORY" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]] || { echo 'Invalid repository.' >&2; exit 1; }

repository="ghcr.io/${GITHUB_REPOSITORY,,}-server"
image="$repository:$IMAGE_SHA"
api="https://api.render.com/v1/services/$RENDER_SERVICE_ID"
temporary=$(mktemp -d)
trap 'rm -rf "$temporary"' EXIT

# GETs may be retried; the deployment POST is deliberately sent only once.
read_render() {
  curl --fail --silent --show-error --retry 2 --max-time 30 \
    -H "Authorization: Bearer $RENDER_API_KEY" "$1"
}
read_render "$api" > "$temporary/service.json"
configured=$(jq -er '.imagePath' "$temporary/service.json")
[[ "$configured" == "$repository:"* || "$configured" == "$repository@"* ]] || {
  echo 'The selected Render service uses a different image repository. Deployment stopped.' >&2; exit 1;
}
url=$(jq -er '.serviceDetails.url' "$temporary/service.json")
[[ "$url" =~ ^https://[a-zA-Z0-9-]+\.onrender\.com$ ]] || {
  echo 'Expected an HTTPS onrender.com service URL.' >&2; exit 1;
}
jq -n --arg image "$image" '{imageUrl:$image}' > "$temporary/request.json"
curl --fail --silent --show-error --max-time 60 \
  -H "Authorization: Bearer $RENDER_API_KEY" -H 'Content-Type: application/json' \
  --data-binary "@$temporary/request.json" "$api/deploys" > "$temporary/deploy.json"
deploy=$(jq -er '.id | select(test("^dep-[a-z0-9]+$"))' "$temporary/deploy.json")
echo "Render deployment: $deploy"
echo "Image: $image"

for ((attempt=0; attempt<120; attempt++)); do
  read_render "$api/deploys/$deploy" > "$temporary/status.json"
  status=$(jq -er '.status' "$temporary/status.json")
  case "$status" in
    live)
      curl --fail --silent --show-error --retry 3 --max-time 30 \
        "$url/actuator/health/liveness" | jq -e '.status == "UP"' >/dev/null
      if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
        printf 'Deployed `%s`\n\n[Render deployment](https://dashboard.render.com/web/%s/deploys/%s)\n\n[Health](%s/actuator/health/liveness): UP\n' \
          "$image" "$RENDER_SERVICE_ID" "$deploy" "$url" >> "$GITHUB_STEP_SUMMARY"
      fi
      echo 'Deployment is live; health is UP.'
      exit 0
      ;;
    build_failed|update_failed|pre_deploy_failed|canceled|deactivated)
      echo "Render deployment failed: $status. Inspect deployment $deploy in Render." >&2; exit 1 ;;
    created|queued|build_in_progress|pre_deploy_in_progress|update_in_progress) ;;
    *) echo "Unexpected deployment status: $status" >&2; exit 1 ;;
  esac
  sleep 10
done
echo "Timed out waiting for deployment $deploy. It may still be running on Render." >&2
exit 1
