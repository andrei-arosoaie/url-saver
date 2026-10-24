# Prepare deployment to Render

GitHub builds the application and Docker image. Render pulls the image from private GHCR; use Existing Image, not a connected source repository.

## Build and publish

Commit these files and push them to GitHub. Under Actions, select Publish server image and Run workflow. The manual workflow runs all backend checks, builds a linux/amd64 image, tests it with real PostgreSQL and publishes:

`ghcr.io/andrei-arosoaie/url-saver-server:<full-git-commit-sha>`

Confirm the GHCR package visibility is Private. Keep the commit-tagged image available for Render restarts and rollbacks. The workflow does not deploy to Render or delete registry images; retention automation still needs protection for deployed digests.

## Manually deploy a published image

In GitHub, configure the **production** environment with:

| Type | Name | Value |
| --- | --- | --- |
| Secret | RENDER_API_KEY | Render API key with access to the service |
| Variable | RENDER_SERVICE_ID | srv-dbehfmhsrm7s73c5nk00 |

After **Publish server image** succeeds, run **Deploy server to Render** and supply the image's full 40-character commit SHA as `image_sha`. This is a separate manual action: publishing does not deploy. The workflow must be present on the default branch to appear in GitHub's manual workflow list.

The deploy workflow verifies the private GHCR image exists, checks that the selected Render service uses this repository's image, triggers exactly one deploy, waits for that deployment to become live and verifies `/actuator/health/liveness` returns UP. It fails on deployment failure, unhealthy response or timeout. Deployments are serialized; a new run does not cancel a running deployment. Render continues using its existing GHCR pull credentials and mounted secrets.

The API selects the requested image for this deployment; it does not change the service's default image reference. Use this workflow with an explicit SHA for subsequent deploys and rollbacks. Dashboard deployments without an explicit image can use the older configured reference.

Local deployment-script tests use fake HTTP responses only: `bash server/scripts/test-deploy-render.sh` (requires bash, curl and jq). They never call Render. No deployment is triggered by creating or testing these files.

## Configure Render

| Setting | Value |
| --- | --- |
| Service type/source | Web Service → Existing Image |
| Image URL | The exact commit-tagged URL from the workflow summary |
| Registry username | andrei-arosoaie |
| Registry token | GitHub personal access token with read:packages, stored in Render |
| Health check path | /actuator/health/liveness |
| Start command | Keep the image default |
| Secret file | secrets.yml at /etc/secrets/secrets.yml |
| Database certificate | supabase-ca.crt at /etc/secrets/supabase-ca.crt |

Use config/render-secrets.example.yml as the secret-file template. Copy the session-pooler host, database and username from your actual Supabase project; use its CA certificate. Database TLS verifies the certificate and hostname. Flyway runs at application startup using the configured role. The image maps its mandatory config/secrets.yml to the Render-mounted file. Never commit completed secrets or copy them into the image.

Select the Render/Supabase region and project values when configuring the actual service; those choices remain unconfigured. Render supplies PORT; the app binds 0.0.0.0 and that port. No domain purchase is required for its provided onrender.com address.

## Verify locally with Debian WSL Docker

From Windows, build/check the application with .\server\verify-wsl.ps1. In Debian_Recovered, run:

```sh
cd /mnt/c/Projects/url-saver/server
docker build --platform linux/amd64 -t know-server:local .
sh scripts/verify-image.sh know-server:local
```

The smoke check uses disposable containers and mounted test-only YAML. It checks the non-root JRE runtime, required secrets, a custom port, real migrations, liveness, temporary-account saves and persisted content and disabled documentation endpoints. Its 512 MiB / 1 CPU limit is a local smoke-test environment, not a Render startup benchmark.

The Firefox capture client is implemented. The current iteration enables know.temporary-account.enabled: true: unauthenticated requests share account 00000000-0000-4000-8000-000000000001, created automatically. Disable that property when authentication is implemented. Deploy the rebuilt image before using the rebuilt extension against Render. Real sign-in and the webpage remain upcoming checkpoints. Local liveness checks do not establish the under-five-second save-ready target.

References: [Render prebuilt images](https://render.com/docs/deploying-an-image), [secret files](https://render.com/docs/configure-environment-variables), [port binding](https://render.com/docs/web-services#port-binding).
