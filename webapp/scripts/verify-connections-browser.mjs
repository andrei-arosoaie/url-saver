// Run against an isolated local server and a separately launched Firefox BiDi profile.
// KNOW_IT_API_ORIGIN, KNOW_FIREFOX_BIDI_URL, KNOW_BROWSER_PROOF_DIR are required.
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import console from "node:console";
import { Buffer } from "node:buffer";
import { URL } from "node:url";
import { setTimeout, clearTimeout } from "node:timers";
import { randomUUID } from "node:crypto";
const { WebSocket, fetch, AbortSignal } = globalThis;

const origin = process.env.KNOW_IT_API_ORIGIN;
assert.ok(origin, "Provide the disposable backend origin.");
const parsed = new URL(origin);
assert.ok(
  parsed.protocol === "http:" &&
    ["localhost", "127.0.0.1"].includes(parsed.hostname),
  "This verification writes fixtures and must never target production.",
);
const proof = process.env.KNOW_BROWSER_PROOF_DIR;
assert.ok(proof, "Provide an output directory for visual verification.");
await fs.mkdir(proof, { recursive: true });
const socket = new WebSocket(
  process.env.KNOW_FIREFOX_BIDI_URL || "ws://127.0.0.1:9224/session",
);
await new Promise((resolve, reject) => {
  socket.onopen = resolve;
  socket.onerror = reject;
});
let sequence = 0;
const pending = new Map();
socket.onmessage = (event) => {
  const message = JSON.parse(event.data);
  const request = pending.get(message.id);
  if (!request) return;
  pending.delete(message.id);
  if (message.type === "error")
    request.reject(new Error(`${message.error}: ${message.message}`));
  else request.resolve(message.result);
};
function command(method, params) {
  return new Promise((resolve, reject) => {
    const id = ++sequence;
    const timeout = setTimeout(() => {
      pending.delete(id);
      reject(new Error(`${method} timed out`));
    }, 30_000);
    pending.set(id, {
      resolve: (result) => {
        clearTimeout(timeout);
        resolve(result);
      },
      reject: (error) => {
        clearTimeout(timeout);
        reject(error);
      },
    });
    socket.send(JSON.stringify({ id, method, params }));
  });
}
async function api(route, method = "GET", body, expected = 200) {
  const response = await fetch(`${origin}/api/v1${route}`, {
    method,
    headers: body ? { "Content-Type": "application/json" } : {},
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  assert.equal(
    response.status,
    expected,
    `${method} ${route}: ${await response.clone().text()}`,
  );
  return expected === 204 ? undefined : response.json();
}
let context;
async function evaluate(expression) {
  const result = await command("script.evaluate", {
    expression,
    target: { context },
    awaitPromise: true,
  });
  if (result.type === "exception")
    throw new Error(JSON.stringify(result.exceptionDetails));
  return result.result.value;
}
async function until(expression) {
  for (let n = 0; n < 80; n++) {
    if (await evaluate(expression)) return;
    await new Promise((resolve) => setTimeout(resolve, 125));
  }
  throw new Error(`Browser condition did not become true: ${expression}`);
}
async function click(text) {
  const found = `Array.from(document.querySelectorAll('button')).find(b=>{const copy=b.cloneNode(true);copy.querySelectorAll('[aria-hidden="true"]').forEach(e=>e.remove());return copy.textContent.trim()===${JSON.stringify(text)}})`;
  await until(`Boolean(${found})`);
  assert.equal(
    await evaluate(
      `(()=>{const b=${found};if(b.disabled)return false;b.click();return true;})()`,
    ),
    true,
    `Button disabled: ${text}`,
  );
}
async function screenshot(name) {
  const result = await command("browsingContext.captureScreenshot", {
    context,
  });
  await fs.writeFile(
    path.join(proof, name),
    Buffer.from(result.data, "base64"),
  );
  assert.equal(
    await evaluate("document.documentElement.scrollWidth <= innerWidth"),
    true,
    `${name}: horizontal overflow`,
  );
}
const controlOrigin = process.env.KNOW_CONNECTIONS_CONTROL_ORIGIN;
assert.ok(
  controlOrigin &&
    ["127.0.0.1", "localhost"].includes(new URL(controlOrigin).hostname),
);
async function control(mode) {
  assert.equal(
    (await fetch(`${controlOrigin}/${mode}`, { method: "POST" })).status,
    200,
  );
}
const panel = `document.querySelector('[aria-label="Related articles"]')`;
async function open(article) {
  await command("browsingContext.navigate", {
    context,
    url: `${origin}/#article/${article.id}`,
    wait: "complete",
  });
  await until(`Boolean(${panel})`);
}
async function save(
  title,
  content = "<p>A thoughtful discussion of Dune and its themes.</p>",
) {
  return (
    await api(
      "/articles",
      "POST",
      {
        url: `https://phase7.test/${randomUUID()}`,
        title,
        ...(content === null ? {} : { content }),
      },
      201,
    )
  ).article;
}
async function waitComplete(article) {
  for (let n = 0; n < 150; n++) {
    if (
      (await api(`/articles/${article.id}/connections`)).processing.state ===
      "COMPLETE"
    )
      return;
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error("Backend did not complete connection work");
}
let session;
try {
  for (let n = 0; n < 60; n++) {
    const ready = await fetch(`${origin}/api/v1/articles?limit=1`, {
      signal: AbortSignal.timeout(1000),
    })
      .then((r) => r.status === 200)
      .catch(() => false);
    if (ready) break;
    assert.ok(n < 59, "Disposable backend did not become ready");
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  session = await command("session.new", { capabilities: {} });
  ({ context } = await command("browsingContext.create", { type: "tab" }));
  await command("browsingContext.setViewport", {
    context,
    viewport: { width: 1360, height: 1000 },
  });
  await control("success");
  for (const previous of (await api("/articles")).items) {
    if (previous.url.startsWith("https://phase7.test/"))
      await api(
        `/articles/${previous.id}?version=${previous.version}`,
        "DELETE",
        undefined,
        204,
      );
  }
  const a = await save("A captured title for Dune");
  await api(`/articles/${a.id}`, "PATCH", {
    version: 0,
    category: "BOOKS",
    entityName: "Dune",
  });
  const existingDune = (await api("/entities?type=BOOK&query=Dune")).items.some(
    (entity) => entity.title === "Dune",
  );
  await api(
    `/articles/${a.id}/entities`,
    "POST",
    { type: "BOOK", title: "Dune" },
    existingDune ? 200 : 201,
  );
  await waitComplete(a);
  await control("hold");
  const b = await save("Thoughts on Dune");
  await api(`/articles/${b.id}/entities`, "POST", {
    type: "BOOK",
    title: "Dune",
  });
  await open(b);
  await until(
    `${panel}.textContent.includes('Finding related articles') && ${panel}.textContent.includes('Both mention Dune')`,
  );
  await evaluate(
    `document.querySelector('button[aria-label="light theme"]').click();${panel}.scrollIntoView({block:'center'})`,
  );
  await screenshot("connections-processing-desktop.png");
  await control("release");
  await until(
    `${panel}.textContent.includes('Matching complete') && ${panel}.textContent.includes('Similar content')`,
  );
  assert.equal(await evaluate(`${panel}.querySelectorAll('a').length`), 1);
  assert.equal(
    await evaluate(`${panel}.querySelector('a').textContent`),
    "Dune",
  );
  await screenshot("connections-complete-desktop.png");
  await command("browsingContext.setViewport", {
    context,
    viewport: { width: 1360, height: 1500 },
  });
  await evaluate("window.scrollTo(0,0)");
  await screenshot("connections-context-desktop.png");
  await evaluate(`${panel}.querySelector('a').click()`);
  await until(
    `location.hash === '#article/${a.id}' && ${panel}?.textContent.includes('Thoughts on Dune')`,
  );
  await command("browsingContext.traverseHistory", { context, delta: -1 });
  await until(
    `location.hash === '#article/${b.id}' && ${panel}?.textContent.includes('Both mention Dune')`,
  );
  await command("browsingContext.setViewport", {
    context,
    viewport: { width: 390, height: 844 },
  });
  await evaluate(
    `document.querySelector('button[aria-label="dark theme"]').click();${panel}.scrollIntoView({block:'center'})`,
  );
  await screenshot("connections-complete-mobile-dark.png");
  await click("Remove connection");
  await screenshot("connections-confirm-mobile-dark.png");
  await click("Cancel");
  assert.equal((await api(`/articles/${b.id}/connections`)).items.length, 1);
  await click("Remove connection");
  await click("Confirm removal");
  await until(
    `${panel}.textContent.includes('Connection removed from both articles')`,
  );
  assert.equal((await api(`/articles/${a.id}/connections`)).items.length, 0);
  assert.equal((await api(`/articles/${b.id}/connections`)).items.length, 0);
  await command("browsingContext.reload", { context, wait: "complete" });
  await until(`${panel}?.textContent.includes('No related articles yet')`);
  await evaluate(`${panel}.scrollIntoView({block:'center'})`);
  await screenshot("connections-removed-mobile-dark.png");
  await control("fail");
  const failure = await save("An article awaiting retry");
  await open(failure);
  await until(`${panel}.textContent.includes('Matching failed')`);
  await evaluate(`${panel}.scrollIntoView({block:'center'})`);
  await screenshot("connections-failed-mobile-dark.png");
  await control("success");
  await click("Retry matching");
  await until(`${panel}.textContent.includes('Matching complete')`);
  await control("quota");
  const quota = await save("An article awaiting quota");
  await open(quota);
  await until(`${panel}.textContent.includes('Provider quota reached')`);
  await evaluate(`${panel}.scrollIntoView({block:'center'})`);
  await screenshot("connections-paused-mobile-dark.png");
  await control("success");
  await until(`${panel}.textContent.includes('Matching complete')`);
  const link = await save("A saved link", null);
  await open(link);
  await until(
    `${panel}.textContent.includes('Connections need saved article text')`,
  );
  await evaluate(`${panel}.scrollIntoView({block:'center'})`);
  await screenshot("connections-link-only-mobile-dark.png");
  console.log(
    JSON.stringify({
      firefox: session.capabilities.browserVersion,
      connections: "passed",
      provider: "local HTTP fixture",
      postgres: "real pgvector",
      navigationAndBack: "passed",
      removalBothSidesAndReload: "passed",
      retryAndQuotaResume: "passed",
      desktopAndMobileOverflow: false,
      proofDirectory: proof,
    }),
  );
} finally {
  await control("release").catch(() => {});
  if (session) await command("session.end", {}).catch(() => {});
  socket.close();
}
