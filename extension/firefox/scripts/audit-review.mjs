import { SourceMap } from "node:module";
import { join } from "node:path";
import { readFileSync, writeFileSync } from "node:fs";
import { root, run, hash } from "./review-utils.mjs";

const version = JSON.parse(
  readFileSync(join(root, "package.json"), "utf8"),
).version;
const archive = join(root, `.output/know-firefox-${version}-firefox.zip`);
const output = run(
  process.execPath,
  [
    join(root, "node_modules/addons-linter/bin/addons-linter"),
    archive,
    "--output",
    "json",
  ],
  { stdio: "pipe", maxBuffer: 10 * 1024 * 1024 },
);
const validation = JSON.parse(output);
writeFileSync(
  join(root, ".output/amo-validation.json"),
  JSON.stringify(validation, null, 2) + "\n",
);
if (validation.summary.errors)
  throw new Error("Mozilla validator reported errors.");
const mapped = [];
for (const warning of validation.warnings) {
  if (warning.code !== "UNSAFE_VAR_ASSIGNMENT")
    throw new Error(`Unreviewed validator warning: ${warning.code}`);
  const sourceMapPath = join(
    root,
    ".output/firefox-mv3",
    warning.file + ".map",
  );
  const map = JSON.parse(readFileSync(sourceMapPath, "utf8"));
  const entry = new SourceMap(map).findEntry(
    warning.line - 1,
    warning.column - 1,
  );
  const original = entry.originalSource;
  if (
    !original ||
    !/node_modules\/(?:react-dom|@mozilla\/readability)\//.test(
      original.replaceAll("\\", "/"),
    )
  ) {
    throw new Error(
      `Warning requires a new source review: ${warning.file}:${warning.line}:${warning.column} (${original})`,
    );
  }
  const source = map.sources.findIndex(
    (path) => original.endsWith(path) || path.endsWith(original),
  );
  const lines = source >= 0 ? map.sourcesContent[source].split("\n") : [];
  if (!lines.length) throw new Error(`Source context missing for ${original}.`);
  mapped.push({
    code: warning.code,
    file: warning.file,
    line: warning.line,
    column: warning.column,
    originalSource: original.replace(/^.*node_modules\//, "node_modules/"),
    originalLine: entry.originalLine + 1,
    originalColumn: entry.originalColumn + 1,
    context: lines
      .slice(Math.max(0, entry.originalLine - 2), entry.originalLine + 3)
      .join("\n"),
  });
}
if (mapped.length !== 6)
  throw new Error(
    `Review the changed warning baseline: expected 6, got ${mapped.length}.`,
  );
const lock = JSON.parse(readFileSync(join(root, "package-lock.json"), "utf8"));
const dependencies = [
  "react",
  "react-dom",
  "scheduler",
  "@mozilla/readability",
  "wxt",
  "@wxt-dev/browser",
  "@wxt-dev/storage",
  "superlock",
].map((name) => ({
  name,
  version: lock.packages[`node_modules/${name}`].version,
}));
if (
  lock.packages["node_modules/react-dom"].version !== "19.3.1" ||
  lock.packages["node_modules/@mozilla/readability"].version !== "0.6.0"
)
  throw new Error(
    "Upstream sink versions changed; refresh the security review before accepting warnings.",
  );
// Reject first-party HTML/eval sinks rather than treating vendor warnings as a blanket waiver.
const { walk } = await import("./review-utils.mjs");
for (const directory of ["src", "entrypoints"])
  for (const path of walk(join(root, directory))) {
    if (
      !/\.tsx?$/.test(path) ||
      /(?:\.test|Test|IT)\.tsx?$/.test(path) ||
      path.startsWith("generated/")
    )
      continue;
    const text = readFileSync(join(root, directory, path), "utf8");
    if (
      /dangerouslySetInnerHTML|\.innerHTML\s*=|\beval\s*\(|new\s+Function\s*\(/.test(
        text,
      )
    )
      throw new Error(`Review first-party code sink: ${directory}/${path}`);
  }
const report = {
  version,
  extensionSha256: hash(archive),
  sourceSha256: hash(join(root, `.output/know-firefox-${version}-sources.zip`)),
  validation: validation.summary,
  dependencies,
  mapped,
  disposition:
    "Six upstream library sinks reviewed; not suppressed. See publishing/SECURITY-REVIEW.md. Mozilla retains the review decision.",
};
writeFileSync(
  join(root, ".output/warning-review.json"),
  JSON.stringify(report, null, 2) + "\n",
);
console.log(
  `PASS: ${mapped.length} validator warnings mapped to upstream React DOM/Readability; no first-party HTML/eval sink found.`,
);
