import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

export const root = fileURLToPath(new URL("../", import.meta.url));
export const apiOrigin = "https://url-saver-server.onrender.com";
export const toolchain = { node: "24.22.1", npm: "12.2.1" };
export function run(executable, args, options = {}) {
  const result = spawnSync(executable, args, {
    cwd: root,
    encoding: "utf8",
    stdio: "inherit",
    windowsHide: true,
    ...options,
  });
  if (result.error || result.status !== 0)
    throw new Error(
      result.error?.message || `${executable} failed (${result.status})`,
    );
  return result.stdout;
}
export function releaseEnvironment() {
  const env = { ...process.env };
  for (const key of Object.keys(env)) if (/^VITE_/i.test(key)) delete env[key];
  delete env.NODE_ENV;
  delete env.npm_config_omit;
  delete env.npm_config_production;
  return { ...env, VITE_API_ORIGIN: apiOrigin, KNOW_REVIEW_SOURCE_MAPS: "1" };
}
export function walk(directory, base = "") {
  return readdirSync(directory, { withFileTypes: true })
    .flatMap((entry) => {
      const path = base ? `${base}/${entry.name}` : entry.name;
      return entry.isDirectory()
        ? walk(join(directory, entry.name), path)
        : [path];
    })
    .sort();
}
export const hash = (path) =>
  createHash("sha256").update(readFileSync(path)).digest("hex");
export function runtimeHashes(directory) {
  return Object.fromEntries(
    walk(directory)
      .filter((path) => !path.endsWith(".map"))
      .map((path) => [path, hash(join(directory, path))]),
  );
}
