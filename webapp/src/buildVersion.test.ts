// @vitest-environment node
import { execFileSync } from "node:child_process";
import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { expect, test } from "vitest";
import { applicationBuild } from "../buildVersion";

test("commits advance the minor version and tracked edits preview the next version", () => {
  const repository = mkdtempSync(join(tmpdir(), "know-version-"));
  const git = (...args: string[]) =>
    execFileSync("git", ["-C", repository, ...args], { stdio: "pipe" });
  git("init");
  git("config", "user.name", "Version test");
  git("config", "user.email", "version@example.test");
  const file = join(repository, "change.txt");
  writeFileSync(file, "first");
  git("add", "change.txt");
  git("-c", "commit.gpgsign=false", "commit", "-m", "First");
  const first = applicationBuild(repository);
  expect(first.version).toBe("0.1.0");
  writeFileSync(file, "second");
  expect(applicationBuild(repository)).toEqual({
    version: "0.2.0-dev",
    revision: first.revision,
  });
  git("add", "change.txt");
  git("-c", "commit.gpgsign=false", "commit", "-m", "Second");
  const second = applicationBuild(repository);
  expect(second.version).toBe("0.2.0");
  expect(second.revision).not.toBe(first.revision);
  writeFileSync(join(repository, "untracked.txt"), "local settings");
  expect(applicationBuild(repository)).toEqual(second);
});
