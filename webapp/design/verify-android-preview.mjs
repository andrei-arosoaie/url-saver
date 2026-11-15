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
const output = path.join(
  root,
  alternate
    ? ".tools/capacitor-browser-proof"
    : "mockup-assets/android-preview/proof",
);
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
const routes = process.env.KNOW_VERIFY_EMBEDS_ONLY
  ? []
  : [
      ["timeline", "", "PostgreSQL indexes"],
      [
        "reader",
        "#article/00000000-0000-4000-8000-000000000001",
        "PostgreSQL indexes",
      ],
      ["entities", "#entities/BOOK/table", "Dune"],
      ["board", "#entities/BOOK/board", "The Dispossessed"],
      ["year", "#entities/BOOK/year", "2026"],
      [
        "entity-detail",
        "#entities/BOOK/table/00000000-0000-4000-8000-000000000021",
        "Dune",
      ],
      ["movies", "#entities/MOVIE/table", "Arrival"],
      [
        "tv",
        "#entities/TV_SHOW/table/00000000-0000-4000-8000-000000000025",
        "Severance",
      ],
      ["games", "#entities/GAME/table", "Hades"],
      ["categories", "#categories", "Technology"],
    ];
try {
  for (const [device, width, height] of [
    ["phone", 390, 844],
    ["tablet", 1280, 900],
  ]) {
    await command("Emulation.setDeviceMetricsOverride", {
      width,
      height,
      deviceScaleFactor: 1,
      mobile: false,
    });
    for (const [screen, hash, expected] of routes) {
      await command("Page.navigate", {
        url:
          (alternate
            ? "http://127.0.0.1:8765/" +
              path.relative(root, bundle).split(path.sep).join("/")
            : pathToFileURL(
                path.join(
                  root,
                  "mockup-assets/android-preview/design/android-preview.html",
                ),
              ).href) + hash,
      });
      await new Promise((r) => setTimeout(r, 350));
      await until(
        `document.body.innerText.includes(${JSON.stringify(expected)})`,
      );
      await new Promise((r) => setTimeout(r, 180));
      const metrics = await evaluate(
        `JSON.stringify({width:innerWidth,scroll:document.documentElement.scrollWidth,accent:getComputedStyle(document.documentElement).getPropertyValue('--accent').trim(),errors:[...document.querySelectorAll('[role="alert"]')].map(e=>e.textContent)})`,
      );
      const m = JSON.parse(metrics);
      assert.equal(m.accent, "#1660ec");
      assert.deepEqual(m.errors, [], `${device}/${screen} alert`);
      assert.equal(
        await evaluate(
          `document.body.innerText.includes('Unexpected show response')`,
        ),
        false,
      );
      assert.ok(
        m.scroll <= width,
        `${device}/${screen} page overflow ${m.scroll}`,
      );
      const shot = await command("Page.captureScreenshot", { format: "png" });
      await fs.writeFile(
        path.join(output, device + "-" + screen + ".png"),
        Buffer.from(shot.data, "base64"),
      );
      results.push({ device, screen, ...m });
      console.log("PASS " + device + " " + screen);
    }
  }
  if (!alternate) {
    await command("Emulation.setDeviceMetricsOverride", {
      width: 1440,
      height: 1000,
      deviceScaleFactor: 1,
      mobile: false,
    });
    await command("Page.navigate", {
      url: pathToFileURL(path.join(root, "phase-13.html")).href + "#preview",
    });
    await until(
      "document.querySelectorAll('[data-android-route]').length===10",
    );
    await until(
      "window.knowAndroidMockStatus.phone?.text.includes('PostgreSQL')",
    );
    assert.equal(
      await evaluate("window.knowAndroidMockStatus.phone?.width"),
      390,
    );
    await until("window.knowAndroidMockStatus.tablet?.width===1280");
    await evaluate(
      "document.querySelectorAll('[data-android-route]')[3].click()",
    );
    await until(
      "window.knowAndroidMockStatus.phone?.text.includes('The Dispossessed')",
    );
    assert.ok(
      await evaluate(
        "window.knowAndroidMockStatus.tablet?.route.includes('board')",
      ),
    );
    await evaluate(
      "document.querySelectorAll('[data-android-route]')[9].click()",
    );
    await until(
      "window.knowAndroidMockStatus.phone?.text.includes('New category')",
    );
    await until(
      "window.knowAndroidMockStatus.tablet?.text.includes('New category')",
    );
    await evaluate(
      "document.querySelectorAll('[data-android-route]')[0].click()",
    );
    await until(
      "window.knowAndroidMockStatus.phone?.text.includes('PostgreSQL')",
    );
    await command("Page.captureScreenshot", { format: "png" }).then((r) =>
      fs.writeFile(
        path.join(output, "phase13-file-mocks.png"),
        Buffer.from(r.data, "base64"),
      ),
    );
    console.log(
      "PASS file:// page: both live mocks rendered; correct widths; Board, Categories and Timeline screen switching",
    );
  }
  if (results.length)
    await fs.writeFile(
      path.join(output, "verification.json"),
      JSON.stringify(
        {
          source: "Unmodified production App and CSS with isolated fixtures",
          results,
        },
        null,
        2,
      ),
    );
} finally {
  await command("Browser.close", {}).catch(() => {});
  socket.close();
  browser.kill();
  server.close();
}
