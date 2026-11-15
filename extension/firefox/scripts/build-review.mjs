import { join } from "node:path";
import { readFileSync, writeFileSync } from "node:fs";
import { packageReviewSources } from "./package-review-sources.mjs";
import {
  apiOrigin,
  root,
  run,
  releaseEnvironment,
  runtimeHashes,
  toolchain,
} from "./review-utils.mjs";

if (process.versions.node !== toolchain.node)
  throw new Error(
    `Use Node ${toolchain.node}; found ${process.versions.node}.`,
  );
const npmCli = process.env.npm_execpath;
if (!npmCli)
  throw new Error(
    "Run npm run review:build so the npm toolchain can be verified.",
  );
const npmVersion = run(process.execPath, [npmCli, "--version"], {
  stdio: "pipe",
}).trim();
if (npmVersion !== toolchain.npm)
  throw new Error(`Use npm ${toolchain.npm}; found ${npmVersion}.`);
const env = releaseEnvironment();
run(
  process.execPath,
  [
    join(root, "node_modules/openapi-typescript/bin/cli.js"),
    "../../server/openapi/openapi.yaml",
    "-o",
    "src/generated/api.d.ts",
  ],
  { env },
);
run(
  process.execPath,
  [join(root, "node_modules/wxt/bin/wxt.mjs"), "zip", "-b", "firefox", "--mv3"],
  { env },
);
const directory = join(root, ".output/firefox-mv3");
const manifest = JSON.parse(
  readFileSync(join(directory, "manifest.json"), "utf8"),
);
await packageReviewSources(manifest.version);
if (manifest.browser_specific_settings.gecko.id !== "know@url-saver.local")
  throw new Error("Unexpected extension ID.");
if (!readFileSync(join(directory, "background.js"), "utf8").includes(apiOrigin))
  throw new Error("Production API origin missing from build.");
writeFileSync(
  join(root, ".output/review-build.json"),
  JSON.stringify(
    {
      version: manifest.version,
      apiOrigin,
      toolchain,
      files: runtimeHashes(directory),
    },
    null,
    2,
  ) + "\n",
);
console.log(
  "Review build ready: production origin, complete source ZIP and file hashes verified.",
);
