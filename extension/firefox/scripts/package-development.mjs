import { copyFileSync, readFileSync, readdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { join } from "node:path";
import { spawnSync } from "node:child_process";

const root = fileURLToPath(new URL("../", import.meta.url));
function run(script, args) {
  const result = spawnSync(process.execPath, [join(root, script), ...args], {
    cwd: root,
    stdio: "inherit",
  });
  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status ?? 1);
}

run("node_modules/openapi-typescript/bin/cli.js", [
  "../../server/openapi/openapi.yaml",
  "-o",
  "src/generated/api.d.ts",
]);
run("node_modules/wxt/bin/wxt.mjs", ["zip", "-b", "firefox", "--mv3"]);

const { version } = JSON.parse(
  readFileSync(join(root, "package.json"), "utf8"),
);
const output = join(root, ".output");
const packages = readdirSync(output).filter((name) =>
  name.endsWith(`-${version}-firefox.zip`),
);
if (packages.length !== 1) {
  throw new Error(
    "Expected exactly one Firefox extension ZIP for this version.",
  );
}
const target = join(output, "know-development.xpi");
copyFileSync(join(output, packages[0]), target);
console.log(`\nReady to copy to your phone: ${target}`);
console.log("Unsigned development package: install using Firefox Nightly.");
