import { createZip } from "@aklinker1/zero-zip";
import { readFileSync, writeFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { root, walk } from "./review-utils.mjs";

export async function packageReviewSources(version) {
  const repository = resolve(root, "../..");
  const files = [
    ...["entrypoints", "src", "scripts", "publishing", "public"].flatMap(
      (directory) =>
        walk(join(root, directory)).map(
          (path) => `extension/firefox/${directory}/${path}`,
        ),
    ),
    ...[
      "package.json",
      "package-lock.json",
      "wxt.config.ts",
      "tsconfig.json",
      "eslint.config.js",
      "vitest.config.ts",
      "vitest.integration.config.ts",
      "README.md",
      "build-development.cmd",
      ".prettierignore",
      ".node-version",
    ].map((path) => `extension/firefox/${path}`),
    "server/openapi/openapi.yaml",
  ]
    .filter(
      (path) =>
        !/(?:^|\/)(?:node_modules|\.git|\.codex|\.wxt|\.output|generated)(?:\/|$)|(?:^|\/)\.env|\.local\.|\.(?:pem|p12|pfx|key)$|(?:^|\/)[^/]*(?:secret|credential|password)[^/]*\.(?:json|ya?ml|env|txt)$/i.test(
          path,
        ),
    )
    .sort();
  const zip = createZip({ level: 9 });
  for (const path of files) {
    const content = readFileSync(join(repository, path));
    if (
      /^-----BEGIN (?:[A-Z ]+ )?PRIVATE KEY-----/m.test(
        content.toString("utf8"),
      )
    )
      throw new Error(`Private key found in source input: ${path}`);
    zip.addFile(path, content);
  }
  writeFileSync(
    join(root, `.output/know-firefox-${version}-sources.zip`),
    await zip.toBuffer(),
  );
  console.log(
    `Packaged ${files.length} reviewer source files, including the schema and tests; no installed dependencies or credentials.`,
  );
}
