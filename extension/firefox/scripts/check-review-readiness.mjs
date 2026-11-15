import { readFileSync } from "node:fs";
import { join } from "node:path";
import { root, hash } from "./review-utils.mjs";

const facts = JSON.parse(
  readFileSync(join(root, "publishing/publisher-facts.json"), "utf8"),
);
const missing = [];
for (const key of [
  "publicContact",
  "databaseProvider",
  "logAndBackupRetention",
  "originalCodeLicense",
]) {
  if (typeof facts[key] !== "string" || !facts[key].trim()) missing.push(key);
}
if (!Array.isArray(facts.enabledTextProcessors))
  missing.push("enabledTextProcessors (explicit [] if none)");
if (facts.reviewerGoogleAccessVerified !== true)
  missing.push("reviewerGoogleAccessVerified");
if (facts.privacyNoticeFinalized !== true)
  missing.push("privacyNoticeFinalized");
if (
  /CONTACT_REQUIRED_BEFORE_PUBLICATION/.test(
    readFileSync(join(root, "publishing/PRIVACY.md"), "utf8"),
  )
)
  missing.push("privacy notice still contains its publisher placeholder");
try {
  const proof = JSON.parse(
    readFileSync(join(root, ".output/reproduction.json"), "utf8"),
  );
  const audit = JSON.parse(
    readFileSync(join(root, ".output/warning-review.json"), "utf8"),
  );
  const version = JSON.parse(
    readFileSync(join(root, "package.json"), "utf8"),
  ).version;
  if (
    proof.result !== "PASS" ||
    proof.version !== version ||
    audit.version !== version ||
    audit.validation.errors
  )
    missing.push("current-version verification evidence");
  if (
    hash(join(root, `.output/know-firefox-${version}-sources.zip`)) !==
      proof.sourceSha256 ||
    proof.sourceSha256 !== audit.sourceSha256
  )
    missing.push("source archive changed since verification");
  if (
    hash(join(root, `.output/know-firefox-${version}-firefox.zip`)) !==
    audit.extensionSha256
  )
    missing.push("extension archive changed since audit");
} catch {
  missing.push("build/audit/reproduction evidence");
}
if (missing.length) {
  console.error(
    "NOT READY FOR SUBMISSION. Complete these publisher facts/checks without putting credentials in the repository:",
  );
  for (const item of missing) console.error(`- ${item}`);
  process.exitCode = 1;
} else
  console.log(
    "Local submission checks and publisher declarations are complete. This does not sign, upload, or guarantee Mozilla approval.",
  );
