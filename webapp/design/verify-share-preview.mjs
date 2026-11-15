import fs from "node:fs/promises";
import path from "node:path";
import { pathToFileURL, fileURLToPath, URL } from "node:url";
import process from "node:process";
import console from "node:console";
import { Buffer } from "node:buffer";
import { setTimeout } from "node:timers";
const { fetch, WebSocket } = globalThis;
import http from "node:http";
import { spawn } from "node:child_process";
import assert from "node:assert/strict";
const root = path.resolve(fileURLToPath(new URL("../../", import.meta.url)));
const alternate = process.env.KNOW_ANDROID_BUNDLE_PATH;
const bundle = alternate ? path.resolve(root, alternate) : null;
if (bundle && !bundle.startsWith(root + path.sep))
  throw Error("Bundle must stay inside the repository");
const output = path.join(root, ".tools/phase14-proof");
await fs.mkdir(output, { recursive: true });
const server = http.createServer(async (req, res) => {
  try {
    const file = path.resolve(
      root,
      "." + decodeURIComponent(new URL(req.url, "http://localhost").pathname),
    );
    if (!file.startsWith(root + path.sep)) throw Error("path");
    const data = await fs.readFile(file);
    res.setHeader(
      "Content-Type",
      {
        html: "text/html",
        js: "text/javascript",
        css: "text/css",
        png: "image/png",
      }[file.split(".").pop()] || "application/octet-stream",
    );
    res.end(data);
  } catch {
    res.writeHead(404);
    res.end();
  }
});
await new Promise((resolve) => server.listen(8765, "127.0.0.1", resolve));
const browser = spawn(
  "C:/Program Files/Google/Chrome/Application/chrome.exe",
  [
    "--headless=new",
    "--no-first-run",
    "--no-default-browser-check",
    "--user-data-dir=" + path.join(root, ".tools/phase13-chrome"),
    "--remote-debugging-port=9227",
    "about:blank",
  ],
  { windowsHide: true, stdio: "ignore" },
);

let socket;
for (let n = 0; n < 80; n++) {
  try {
    const targets = await (await fetch("http://127.0.0.1:9227/json")).json();
    socket = new WebSocket(
      targets.find((t) => t.type === "page").webSocketDebuggerUrl,
    );
    await new Promise((resolve, reject) => {
      socket.onopen = resolve;
      socket.onerror = reject;
    });
    break;
  } catch {
    await new Promise((r) => setTimeout(r, 150));
  }
}
assert.equal(socket.readyState, 1, "Chrome connection unavailable");
let sequence = 0;
const pending = new Map();
socket.onmessage = (event) => {
  const m = JSON.parse(event.data);
  const request = pending.get(m.id);
  if (request) {
    pending.delete(m.id);
    if (m.error) request.reject(Error(JSON.stringify(m.error)));
    else request.resolve(m.result);
  }
};
function command(method, params) {
  return new Promise((resolve, reject) => {
    const id = ++sequence;
    pending.set(id, { resolve, reject });
    socket.send(JSON.stringify({ id, method, params }));
  });
}
await command("Page.enable", {});
async function evaluate(expression) {
  const r = await command("Runtime.evaluate", {
    expression,
    awaitPromise: true,
    returnByValue: true,
  });
  if (r.exceptionDetails) throw Error(JSON.stringify(r.exceptionDetails));
  return r.result.value;
}
async function until(expression) {
  for (let n = 0; n < 60; n++) {
    if (await evaluate(expression)) return;
    await new Promise((r) => setTimeout(r, 100));
  }
  throw Error("Condition failed: " + expression);
}
const results = [];
const mock = pathToFileURL(
  path.join(root, "mockup-assets/share-preview/index.html"),
).href;
let revision = 0;
async function open(scenario = "new") {
  await command("Page.navigate", {
    url: mock + "#scenario=" + scenario + "&test=" + ++revision,
  });
  await new Promise((r) => setTimeout(r, 200));
  await until(
    scenario === "signed-out"
      ? "document.body.innerText.includes('Sign in with Google')"
      : "Boolean(document.querySelector('select'))",
  );
}
async function click(label) {
  await evaluate(
    '(()=>{const b=[...document.querySelectorAll("button")].find(b=>b.textContent.trim()===' +
      JSON.stringify(label) +
      ');if(!b)throw Error("Missing button");b.click()})()',
  );
}
async function category(key) {
  await evaluate(
    '(()=>{const s=document.querySelector("select");s.value=' +
      JSON.stringify(key) +
      ';s.dispatchEvent(new Event("change",{bubbles:true}))})()',
  );
}
async function screenshot(name) {
  const s = await command("Page.captureScreenshot", { format: "png" });
  await fs.writeFile(
    path.join(output, name + ".png"),
    Buffer.from(s.data, "base64"),
  );
}
try {
  for (const [device, width, height] of [
    ["phone", 390, 900],
    ["tablet", 1280, 900],
  ]) {
    await command("Emulation.setDeviceMetricsOverride", {
      width,
      height,
      deviceScaleFactor: 1,
      mobile: false,
    });
    await open();
    await category("TECHNOLOGY");
    await screenshot(device + "-share");
    assert.ok(
      await evaluate("document.documentElement.scrollWidth<=innerWidth"),
    );
    await click("Save to Timeline");
    await until("document.body.innerText.includes('Saved to Timeline')");
    await screenshot(device + "-saved");
    assert.ok(
      await evaluate("document.body.innerText.includes('Technology')"),
      await evaluate("document.body.innerText"),
    );
    await click("View in Timeline");
    await until(
      `Boolean(document.querySelector('ol[aria-label="Captured pages"]'))`,
    );
    assert.ok(
      await evaluate(
        "document.querySelector('ol').innerText.includes('Technology')",
      ),
    );
    await screenshot(device + "-timeline");
    await evaluate("document.querySelector('ol button').click()");
    await until(
      "document.body.innerText.includes('Article text and a screenshot were not shared')",
    );
    await open();
    await category("CUSTOM_RESEARCH");
    await click("Save to Timeline");
    await until("document.body.innerText.includes('Saved to Timeline')");
    await click("View in Timeline");
    await until(
      "document.querySelector('ol')?.innerText.includes('My research')",
    );
    await open("duplicate");
    await category("BOOKS");
    await click("Save to Timeline");
    await until("document.body.innerText.includes('Already in your timeline')");
    assert.ok(
      await evaluate(
        "document.body.innerText.includes('Current category: Other')",
      ),
    );
    await click("Change category");
    await until("Boolean(document.querySelector('dialog[open]'))");
    await click("Cancel");
    await until("!document.querySelector('dialog[open]')");
    assert.ok(
      await evaluate(
        "document.body.innerText.includes('Current category: Other')",
      ),
    );
    await click("Change category");
    await until("Boolean(document.querySelector('dialog[open]'))");
    await click("Confirm category change");
    await until("document.body.innerText.includes('Category updated')");
    await click("View in Timeline");
    await until("document.querySelector('ol')?.innerText.includes('Books')");
    await open("offline");
    await category("TECHNOLOGY");
    await click("Save to Timeline");
    await until("document.body.innerText.includes('You are offline')");
    assert.equal(
      await evaluate('document.querySelector("select").value'),
      "TECHNOLOGY",
    );
    await click("Try again when connected");
    await click("Save to Timeline");
    await until("document.body.innerText.includes('Saved to Timeline')");
    await open("signed-out");
    assert.equal(
      await evaluate('Boolean(document.querySelector("select"))'),
      false,
    );
    await click("Sign in with Google");
    await until("Boolean(document.querySelector('select'))");
    assert.equal(
      await evaluate('document.querySelector("input").value'),
      "https://example.com/postgres-indexes",
    );
    await click("Cancel");
    await until(
      "document.body.innerText.includes('Returned to the source app')",
    );
    await open();
    await evaluate(
      `document.querySelector('button[aria-label="dark theme"]').click()`,
    );
    await until(
      "getComputedStyle(document.documentElement).getPropertyValue('--accent').trim()==='#8bb6ff'",
    );
    await evaluate(
      `document.querySelector('button[aria-label="light theme"]').click()`,
    );
    await until(
      "getComputedStyle(document.documentElement).getPropertyValue('--accent').trim()==='#1660ec'",
    );
    results.push({
      device,
      width,
      height,
      categorySave: true,
      customCategory: true,
      duplicateConfirmation: true,
      offlineDraft: true,
      signInDraft: true,
      cancel: true,
      theme: true,
      overflow: false,
    });
    console.log(
      "PASS " +
        device +
        ": category -> save -> timeline; custom, duplicate, offline, signed-out, cancel and themes",
    );
  }
  await command("Emulation.setDeviceMetricsOverride", {
    width: 1440,
    height: 1100,
    deviceScaleFactor: 1,
    mobile: false,
  });
  await command("Page.navigate", {
    url: pathToFileURL(path.join(root, "phase-14.html")).href + "#preview",
  });
  await until(
    "window.knowShareMockStatus?.phone?.text.includes('Save to Know') && window.knowShareMockStatus?.tablet?.text.includes('Save to Know')",
  );
  assert.equal(await evaluate("window.knowShareMockStatus.phone.width"), 390);
  assert.equal(await evaluate("window.knowShareMockStatus.tablet.width"), 1280);
  await evaluate(
    `document.querySelector('[data-share-scenario="signed-out"]').click()`,
  );
  await until(
    "window.knowShareMockStatus.phone.text.includes('Sign in with Google') && window.knowShareMockStatus.tablet.text.includes('Sign in with Google')",
  );
  await evaluate(
    `document.querySelector('[data-share-scenario="new"]').click()`,
  );
  await until(
    "window.knowShareMockStatus.phone.text.includes('Choose a category') && window.knowShareMockStatus.tablet.text.includes('Choose a category')",
  );
  console.log(
    "PASS phase-14.html from file://: both live frames and scenario controls",
  );
  await fs.writeFile(
    path.join(output, "verification.json"),
    JSON.stringify({ mode: "file://", results, embeddedFrames: true }, null, 2),
  );
} finally {
  await command("Browser.close", {}).catch(() => {});
  socket.close();
  browser.kill();
  server.close();
}
