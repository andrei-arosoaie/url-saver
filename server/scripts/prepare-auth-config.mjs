import { createHash, generateKeyPairSync } from "node:crypto";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const config = new URL("../config/", import.meta.url);
const manifest = readFileSync(
  new URL("../../extension/firefox/wxt.config.ts", import.meta.url),
  "utf8",
);
const extensionId = manifest.match(/id:\s*"([^"]+)"/)?.[1];
if (!extensionId)
  throw new Error("Cannot find the stable Firefox extension ID.");
// Firefox uses SHA-1 here as an identifier, not as a signing algorithm.
// https://github.com/mozilla/gecko-dev/blob/master/toolkit/components/extensions/child/ext-identity.js
const redirect = `https://${createHash("sha1").update(extensionId).digest("hex")}.extensions.allizom.org/`;

for (const prefix of ["auth", "render-auth"]) {
  const target = new URL(`${prefix}-secrets.yml`, config);
  if (!existsSync(target)) {
    const template = readFileSync(
      new URL(`${prefix}-secrets.example.yml`, config),
      "utf8",
    );
    writeFileSync(
      target,
      template.replace("GENERATED_FROM_EXTENSION_ID", redirect),
      { flag: "wx", mode: 0o600 },
    );
  }
}

// Separate local and production keys. Never replace an existing key automatically.
for (const prefix of ["auth", "render-auth"]) {
  const privatePath = new URL(`${prefix}-private-key.pem`, config);
  const publicPath = new URL(`${prefix}-public-key.pem`, config);
  if (existsSync(privatePath) || existsSync(publicPath)) {
    if (!existsSync(privatePath) || !existsSync(publicPath)) {
      throw new Error(
        `Incomplete ${prefix} key pair; restore the missing file before continuing.`,
      );
    }
    continue;
  }
  const keys = generateKeyPairSync("rsa", {
    modulusLength: 3072,
    privateKeyEncoding: { type: "pkcs8", format: "pem" },
    publicKeyEncoding: { type: "spki", format: "pem" },
  });
  writeFileSync(privatePath, keys.privateKey, { flag: "wx", mode: 0o600 });
  writeFileSync(publicPath, keys.publicKey, { flag: "wx", mode: 0o600 });
}
console.log(
  `Prepared configuration in ${fileURLToPath(config)}; existing files preserved.`,
);
console.log(`Firefox ID: ${extensionId}`);
console.log(
  `Know extension callback (verify with browser.identity.getRedirectURL()): ${redirect}`,
);
console.log(
  "Google credentials and legacy-account.owner-email still need to be filled in.",
);
