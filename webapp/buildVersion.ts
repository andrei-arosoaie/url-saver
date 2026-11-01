import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { resolve } from "node:path";

export function applicationBuild(
  repository = fileURLToPath(new URL("../", import.meta.url)),
) {
  const root = resolve(repository);
  const git = (...args: string[]) =>
    execFileSync(
      "git",
      [
        "-c",
        `safe.directory=${root.replaceAll("\\", "/")}`,
        "-C",
        root,
        ...args,
      ],
      { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] },
    ).trim();
  if (git("rev-parse", "--is-shallow-repository") === "true") {
    throw new Error(
      "Application versioning requires full Git history. Use actions/checkout with fetch-depth: 0.",
    );
  }
  const count = Number(git("rev-list", "--count", "HEAD"));
  if (!Number.isSafeInteger(count) || count < 1)
    throw new Error("Cannot determine the application's revision count.");
  const modified = git("status", "--porcelain", "--untracked-files=no") !== "";
  return {
    version: `0.${count + (modified ? 1 : 0)}.0${modified ? "-dev" : ""}`,
    revision: git("rev-parse", "--short=7", "HEAD"),
  };
}
