import assert from "node:assert/strict";
import {
  existsSync,
  mkdtempSync,
  mkdirSync,
  readFileSync,
  writeFileSync,
} from "node:fs";
import { resolve, join } from "node:path";
import {
  root,
  run,
  releaseEnvironment,
  runtimeHashes,
  hash,
} from "./review-utils.mjs";

const version = JSON.parse(
  readFileSync(join(root, "package.json"), "utf8"),
).version;
const archive = join(root, `.output/know-firefox-${version}-sources.zip`);
if (!existsSync(archive)) throw new Error("Run npm run review:build first.");
const baseline = JSON.parse(
  readFileSync(join(root, ".output/review-build.json"), "utf8"),
);
const temporary = mkdtempSync(join(root, ".output/repro-"));
const extracted = join(temporary, "source");
mkdirSync(extracted);
if (process.platform === "win32") {
  run(
    "powershell.exe",
    [
      "-NoProfile",
      "-NonInteractive",
      "-Command",
      "Expand-Archive -LiteralPath $env:KNOW_REVIEW_ARCHIVE -DestinationPath $env:KNOW_REVIEW_EXTRACT",
    ],
    {
      env: {
        ...process.env,
        KNOW_REVIEW_ARCHIVE: archive,
        KNOW_REVIEW_EXTRACT: extracted,
      },
    },
  );
} else run("unzip", ["-q", archive, "-d", extracted]);
const schema = join(extracted, "server/openapi/openapi.yaml");
assert.ok(existsSync(schema), "Source ZIP must contain the OpenAPI schema.");
assert.equal(
  readFileSync(schema, "utf8"),
  readFileSync(resolve(root, "../../server/openapi/openapi.yaml"), "utf8"),
);
const cwd = join(extracted, "extension/firefox");
const env = releaseEnvironment();
const npmCli = process.env.npm_execpath;
if (!npmCli) throw new Error("Run through npm run review:reproduce.");
// This is a fresh extraction: no symlinks or copied node_modules/build outputs.
run(process.execPath, [npmCli, "ci", "--no-audit", "--no-fund"], { cwd, env });
run(process.execPath, [npmCli, "run", "review:build"], { cwd, env });
const actual = runtimeHashes(join(cwd, ".output/firefox-mv3"));
assert.deepEqual(
  actual,
  baseline.files,
  "Rebuilt runtime files must match the submitted build byte for byte.",
);
writeFileSync(
  join(root, ".output/reproduction.json"),
  JSON.stringify(
    {
      version,
      toolchain: baseline.toolchain,
      matchedFiles: Object.keys(actual).length,
      sourceSha256: hash(archive),
      platform: process.platform,
      architecture: process.arch,
      result: "PASS",
    },
    null,
    2,
  ) + "\n",
);
console.log(
  `PASS: ${Object.keys(actual).length} runtime files match after a fresh source-ZIP extraction and npm ci.`,
);
