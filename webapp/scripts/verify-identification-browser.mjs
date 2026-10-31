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
const { WebSocket, fetch } = globalThis;

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
const controlOrigin = process.env.KNOW_IDENTIFICATION_CONTROL_ORIGIN;
assert.ok(controlOrigin, "Provide the disposable AI fixture control origin.");
assert.ok(["127.0.0.1", "localhost"].includes(new URL(controlOrigin).hostname));
async function control(mode) {
  const response = await fetch(`${controlOrigin}/${mode}`, { method: "POST" });
  assert.equal(response.status, 200);
}
const panel = `document.querySelector('[aria-label="AI identification"]')`;
async function open(article) {
  await command("browsingContext.navigate", {
    context,
    url: `${origin}/#article/${article.id}`,
    wait: "complete",
  });
  await until(`Boolean(${panel})`);
}
async function saved(
  title,
  content = "<h2>1984 and Animal Farm</h2><p>A review of these two books and their central themes.</p>",
) {
  return (
    await api(
      "/articles",
      "POST",
      {
        url: `https://phase6.test/browser-${randomUUID()}`,
        title,
        ...(content === null ? {} : { content }),
      },
      201,
    )
  ).article;
}
async function field(label, value) {
  await evaluate(
    `(()=>{const l=Array.from(document.querySelectorAll('label')).find(l=>l.firstChild.textContent.trim()===${JSON.stringify(label)});const i=l.control;Object.getOwnPropertyDescriptor(i.tagName==='SELECT'?HTMLSelectElement.prototype:HTMLInputElement.prototype,'value').set.call(i,${JSON.stringify(value)});i.dispatchEvent(new Event(i.tagName==='SELECT'?'change':'input',{bubbles:true}));})()`,
  );
}
let session;
try {
  session = await command("session.new", { capabilities: {} });
  ({ context } = await command("browsingContext.create", { type: "tab" }));
  await command("browsingContext.setViewport", {
    context,
    viewport: { width: 1360, height: 1000 },
  });
  for (const previous of (await api("/articles")).items) {
    if (previous.url.startsWith("https://phase6.test/browser-"))
      await api(
        `/articles/${previous.id}?version=${previous.version}`,
        "DELETE",
        undefined,
        204,
      );
  }
  await control("hold");
  const article = await saved("A reading review");
  await api(
    `/articles/${article.id}/entities`,
    "POST",
    { type: "BOOK", title: `Preserved entity ${article.id}` },
    201,
  );
  await open(article);
  await evaluate(
    `document.querySelector('button[aria-label="light theme"]').click()`,
  );
  await until(`${panel}.textContent.includes('Identifying missing details')`);
  await screenshot("identification-pending-desktop.png");
  await control("release");
  await until(
    `${panel}.textContent.includes('Identification complete') && ${panel}.textContent.includes('Animal Farm')`,
  );
  assert.equal(await evaluate(`${panel}.querySelectorAll('strong').length`), 1);
  assert.equal((await api(`/articles/${article.id}`)).entityName, "1984");
  assert.equal((await api(`/articles/${article.id}/entities`)).items.length, 1);
  await screenshot("identification-success-desktop.png");
  await click("Edit details");
  await field("Category", "AI");
  await click("Save details");
  await until(`${panel}.textContent.includes('Your choice')`);
  let state = await api(`/articles/${article.id}/identification`);
  assert.equal(state.categorySource, "USER");
  assert.equal(state.nameSource, "AI");
  await command("browsingContext.setViewport", {
    context,
    viewport: { width: 390, height: 844 },
  });
  await evaluate(`${panel}.scrollIntoView({block:'center'})`);
  await screenshot("identification-mobile.png");

  await control("hold");
  const manual = await saved("Manual name with automatic category");
  await open(manual);
  await until(`${panel}.textContent.includes('Identifying missing details')`);
  await click("Edit details");
  await field("Identified entity name", "My chosen subject");
  await click("Save details");
  await until(`${panel}.textContent.includes('Your choice')`);
  await control("release");
  await until(`${panel}.textContent.includes('Identification complete')`);
  state = await api(`/articles/${manual.id}/identification`);
  assert.equal(state.categorySource, "AI");
  assert.equal(state.nameSource, "USER");
  const details = await api(`/articles/${manual.id}`);
  assert.equal(details.category, "BOOKS");
  assert.equal(details.entityName, "My chosen subject");
  assert.deepEqual(state.subjects, []);
  await evaluate(`${panel}.scrollIntoView({block:'center'})`);
  await screenshot("identification-manual-mobile.png");

  await control("fail");
  const failed = await saved("Provider failure and user retry");
  await open(failed);
  await until(`${panel}.textContent.includes('Identification failed')`);
  await evaluate(`${panel}.scrollIntoView({block:'center'})`);
  await screenshot("identification-failure-mobile.png");
  assert.equal(
    (await api(`/articles/${failed.id}/identification`)).status,
    "FAILED",
  );
  await control("fallback");
  await click("Retry identification");
  await until(
    `${panel}.textContent.includes('Identification complete') && ${panel}.textContent.includes('Groq')`,
  );
  assert.equal(
    (await api(`/articles/${failed.id}/identification`)).provider,
    "groq",
  );
  await screenshot("identification-retry-mobile.png");

  const link = await saved("Link-only entry", null);
  await open(link);
  await until(`${panel}.textContent.includes('No article text available')`);
  assert.equal(await evaluate(`${panel}.querySelectorAll('button').length`), 0);
  await control("success");
  const protectedArticle = await saved("All details supplied manually");
  await api(`/articles/${protectedArticle.id}`, "PATCH", {
    version: 0,
    category: "OTHER",
    entityName: "My manual subject",
  });
  await open(protectedArticle);
  await until(`${panel}.textContent.includes('Your choices are preserved')`);
  assert.equal(await evaluate(`${panel}.querySelectorAll('button').length`), 0);
  await command("browsingContext.setViewport", {
    context,
    viewport: { width: 1360, height: 1000 },
  });
  await screenshot("identification-manual-desktop.png");
  await evaluate(
    `document.querySelector('button[aria-label="dark theme"]').click()`,
  );
  await screenshot("identification-manual-desktop-dark.png");
  console.log(
    JSON.stringify({
      firefox: session.capabilities.browserVersion,
      identification: "passed",
      manualFields: "preserved",
      explicitRetry: "passed",
      entityLinks: "preserved",
      desktopAndMobileOverflow: false,
      proofDirectory: proof,
    }),
  );
} finally {
  await control("release").catch(() => {});
  if (session) await command("session.end", {}).catch(() => {});
  socket.close();
}
