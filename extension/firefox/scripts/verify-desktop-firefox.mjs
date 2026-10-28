import fs from "node:fs";
import assert from "node:assert/strict";
import { Buffer } from "node:buffer";
import { setTimeout, clearTimeout } from "node:timers";
const { WebSocket, fetch, crypto } = globalThis;
const api = process.env.KNOW_IT_API_ORIGIN;
const provider = process.env.KNOW_IT_IMAGE_ORIGIN;
const extensionDir = process.env.KNOW_FIREFOX_EXTENSION_DIR;
const proofDir = process.env.KNOW_FIREFOX_PROOF_DIR;
for (const value of [api, provider])
  assert.match(value ?? "", /^http:\/\/127\.0\.0\.1:\d+$/);
assert.ok(
  extensionDir && proofDir,
  "Set KNOW_FIREFOX_EXTENSION_DIR and KNOW_FIREFOX_PROOF_DIR.",
);
assert.ok(
  fs.readFileSync(extensionDir + "/background.js", "utf8").includes(api),
  "Build this verification extension for the disposable local API first.",
);
fs.mkdirSync(proofDir, { recursive: true });
const socket = new WebSocket(
  process.env.KNOW_FIREFOX_BIDI_URL || "ws://127.0.0.1:9223/session",
);
await new Promise((resolve, reject) => {
  socket.onopen = resolve;
  socket.onerror = reject;
});
let id = 0;
const pending = new Map();
socket.onclose = () => {
  for (const request of pending.values())
    request.reject(new Error("Firefox connection closed"));
  pending.clear();
};
socket.onmessage = (event) => {
  const value = JSON.parse(event.data),
    request = pending.get(value.id);
  if (!request) return;
  pending.delete(value.id);
  if (value.type === "error")
    request.reject(new Error(value.error + ": " + value.message));
  else request.resolve(value.result);
};
const command = (method, params) =>
  new Promise((resolve, reject) => {
    const current = ++id;
    const timer = setTimeout(() => {
      pending.delete(current);
      reject(new Error(method + " timed out"));
    }, 60000);
    pending.set(current, {
      resolve: (v) => {
        clearTimeout(timer);
        resolve(v);
      },
      reject: (e) => {
        clearTimeout(timer);
        reject(e);
      },
    });
    socket.send(JSON.stringify({ id: current, method, params }));
  });
try {
  const session = await command("session.new", { capabilities: {} });
  console.log("Firefox", session.capabilities.browserVersion);
  await command("webExtension.install", {
    extensionData: { type: "path", path: extensionDir },
  });
  const { context } = await command("browsingContext.create", { type: "tab" });
  await command("browsingContext.activate", { context });
  await command("browsingContext.setViewport", {
    context,
    viewport: { width: 1280, height: 720 },
  });
  const source = provider + "/article?case=" + crypto.randomUUID();
  await command("browsingContext.navigate", {
    context,
    url: source,
    wait: "complete",
  });
  const evaluate = async (expression) => {
    const value = await command("script.evaluate", {
      expression,
      target: { context },
      awaitPromise: true,
    });
    if (value.type === "exception")
      throw new Error(JSON.stringify(value.exceptionDetails));
    return value.result.value;
  };
  for (let n = 0; n < 40; n++) {
    if (
      await evaluate('Boolean(document.querySelector("[data-know-extension]"))')
    )
      break;
    await new Promise((r) => setTimeout(r, 250));
  }
  assert.equal(
    await evaluate('Boolean(document.querySelector("[data-know-extension]"))'),
    true,
    "Know control not injected",
  );
  const dimensions = JSON.parse(
    await evaluate(
      "JSON.stringify({width:innerWidth,height:innerHeight,dpr:devicePixelRatio})",
    ),
  );
  const before = await command("browsingContext.captureScreenshot", {
    context,
  });
  fs.writeFileSync(
    proofDir + "/before.png",
    Buffer.from(before.data, "base64"),
  );
  await command("input.performActions", {
    context,
    actions: [
      {
        type: "pointer",
        id: "mouse",
        parameters: { pointerType: "mouse" },
        actions: [
          {
            type: "pointerMove",
            x: dimensions.width - 40,
            y: 40,
            origin: "viewport",
          },
          { type: "pointerDown", button: 0 },
          { type: "pointerUp", button: 0 },
        ],
      },
    ],
  });
  let article, screenshot;
  for (let n = 0; n < 90; n++) {
    const listing = await (
      await fetch(api + "/api/v1/articles?query=" + encodeURIComponent(source))
    ).json();
    article = listing.items?.find((item) => item.url === source);
    if (article) {
      screenshot = await (
        await fetch(api + "/api/v1/articles/" + article.id + "/screenshot")
      ).json();
      if (screenshot.status === "AVAILABLE") break;
    }
    await new Promise((r) => setTimeout(r, 1000));
  }
  assert.ok(article, "Actual Firefox click did not save the article");
  assert.equal(
    screenshot?.status,
    "AVAILABLE",
    "Actual Firefox capture did not reach storage",
  );
  assert.equal(screenshot.width, dimensions.width * dimensions.dpr);
  assert.equal(screenshot.height, dimensions.height * dimensions.dpr);
  const bytes = Buffer.from(await (await fetch(screenshot.url)).arrayBuffer());
  fs.writeFileSync(proofDir + "/stored.jpg", bytes);
  const whiteness = await evaluate(
    `(async()=>{const i=new Image();i.src=${JSON.stringify(screenshot.url)};await i.decode();const c=document.createElement('canvas');c.width=i.width;c.height=i.height;const x=c.getContext('2d');x.drawImage(i,0,0);const d=x.getImageData((innerWidth-55)*devicePixelRatio,25*devicePixelRatio,30*devicePixelRatio,30*devicePixelRatio).data;let min=255;for(let n=0;n<d.length;n++)if(n%4!==3)min=Math.min(min,d[n]);return min})()`,
  );
  assert.ok(whiteness > 245, "Know controls appeared in the stored screenshot");
  assert.equal(
    await evaluate(
      'getComputedStyle(document.querySelector("[data-know-extension]")).visibility',
    ),
    "visible",
  );
  console.log(
    JSON.stringify({
      actualFirefoxCapture: "passed",
      width: screenshot.width,
      height: screenshot.height,
      nativeDpr: dimensions.dpr,
      controlsExcluded: true,
      articleSaved: true,
    }),
  );
  await command("webExtension.uninstall", {
    extension: "know@url-saver.local",
  });
} finally {
  await command("session.end", {}).catch(() => {});
  socket.close();
}
