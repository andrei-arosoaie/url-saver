# Rebuild the submitted extension

The source ZIP is a small repository-shaped tree, not only the Firefox directory.
It includes `server/openapi/openapi.yaml`, a required build input. It excludes
credentials, private keys, local configuration, generated types, installed
dependencies and build outputs. No Java, database, Google credentials or private
backend configuration is needed to build this extension.

Use Node **24.22.1** and npm **12.2.1**. Dependencies are public npm releases pinned
by `extension/firefox/package-lock.json`. Install the specified npm version with
`npm install --global npm@12.2.1` if necessary. Linux and Windows are supported;
the exact platform used for submitted verification is recorded with the artifacts.

From the source ZIP's root:

```sh
cd extension/firefox
npm ci
npm run review:build
```

The unpacked runtime is `.output/firefox-mv3/`. The installable submission archive
is `.output/know-firefox-<version>-firefox.zip`. The matching source archive is
`.output/know-firefox-<version>-sources.zip`. No API token or environment variable needs
to be supplied: the review build explicitly targets the production Know origin
`https://url-saver-server.onrender.com` and discards inherited `VITE_*` overrides.

The review build creates hidden source maps for auditing, but excludes them from
the extension ZIP. Compare runtime file contents, not ZIP timestamps or source-map
paths. `review-build.json` records each runtime file's SHA-256 digest.

To run the same fresh-extraction check used before submission:

```sh
npm run review:reproduce
```

It extracts the actual source ZIP into a new directory, runs a fresh `npm ci`,
rebuilds it, and compares every runtime file byte for byte. Windows uses
PowerShell `Expand-Archive`; Linux requires `unzip`. The temporary reproduction
directory is retained for inspection under `.output/repro-*` and is never included
in a submission artifact.

```sh
npm run review:audit
```

This validates the actual extension ZIP in AMO/listed mode, maps the six existing
warnings to their upstream source locations, rejects unexpected warnings/sinks,
and writes `amo-validation.json` and `warning-review.json`. Warnings are not hidden
or automatically approved; see `SECURITY-REVIEW.md` for their disposition.

Before submitting, run `npm run review:ready`. It checks evidence hashes and the
non-secret publisher declarations in `publishing/publisher-facts.json`. A successful
build is not sufficient while the privacy notice or reviewer-access facts remain
unfinished. Do not store reviewer passwords in that JSON file or in the source ZIP.
