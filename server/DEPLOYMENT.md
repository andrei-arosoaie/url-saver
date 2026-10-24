# Prepare deployment to Render

GitHub builds the application and Docker image. Render pulls the image from private GHCR; use Existing Image, not a connected source repository.

## Build and publish

Commit these files and push them to GitHub. Under Actions, select Publish server image and Run workflow. The manual workflow runs all backend checks, builds a linux/amd64 image, tests it with real PostgreSQL and publishes:

`ghcr.io/andrei-arosoaie/url-saver-server:<full-git-commit-sha>`

Confirm the GHCR package visibility is Private. Keep the commit-tagged image available for Render restarts and rollbacks. The workflow does not deploy to Render or delete registry images; retention automation still needs protection for deployed digests.

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

The smoke check uses disposable containers and mounted test-only YAML. It checks the non-root JRE runtime, required secrets, a custom port, real migrations, liveness, API 401 responses and disabled documentation endpoints. Its 512 MiB / 1 CPU limit is a local smoke-test environment, not a Render startup benchmark.

Real sign-in, the Firefox extension and the webpage are still upcoming checkpoints. The current image starts the backend, but article operations require authentication that has not yet been implemented. Local liveness checks do not establish the under-five-second save-ready target.

References: [Render prebuilt images](https://render.com/docs/deploying-an-image), [secret files](https://render.com/docs/configure-environment-variables), [port binding](https://render.com/docs/web-services#port-binding).
