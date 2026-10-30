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
async function labeledButton(label) {
  const selector = `button[aria-label=${JSON.stringify(label)}]`;
  await until(`Boolean(document.querySelector(${JSON.stringify(selector)}))`);
  await evaluate(`document.querySelector(${JSON.stringify(selector)}).click()`);
}
async function fill(label, value) {
  await evaluate(
    `(()=>{const l=Array.from(document.querySelectorAll('dialog label')).find(l=>l.firstChild.textContent.trim()===${JSON.stringify(label)});const i=l.querySelector('input');Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(i,${JSON.stringify(value)});i.dispatchEvent(new Event('input',{bubbles:true}));})()`,
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
let session;
try {
  session = await command("session.new", { capabilities: {} });
  ({ context } = await command("browsingContext.create", { type: "tab" }));
  await command("browsingContext.setViewport", {
    context,
    viewport: { width: 1360, height: 1000 },
  });
  // Permit rerunning after an interrupted test, only inside this disposable local fixture.
  for (const previous of (await api("/articles")).items) {
    if (previous.url.startsWith("https://phase5.test/browser-"))
      await api(
        `/articles/${previous.id}?version=${previous.version}`,
        "DELETE",
        undefined,
        204,
      );
  }
  for (const previous of (await api("/categories")).items) {
    if (
      !previous.isDefault &&
      previous.articleCount === 0 &&
      ["Browser research", "Reading research"].includes(previous.displayName)
    )
      await api(
        `/categories/${encodeURIComponent(previous.key)}?version=${previous.version}`,
        "DELETE",
        undefined,
        204,
      );
  }
  const article = (
    await api(
      "/articles",
      "POST",
      {
        url: `https://phase5.test/browser-${randomUUID()}`,
        title: "Phase 5 browser article",
        content:
          "<h2>Preserved content</h2><p>Saved from the verification fixture.</p>",
      },
      201,
    )
  ).article;
  const linked = await api(
    `/articles/${article.id}/entities`,
    "POST",
    { type: "BOOK", title: `Preserved browser entity ${article.id}` },
    201,
  );
  await command("browsingContext.navigate", {
    context,
    url: `${origin}/#categories`,
    wait: "complete",
  });
  await until("document.querySelectorAll('main li').length===8");
  await click("New category");
  await fill("Name", "Browser research");
  await fill("Hex color", "#2876dc");
  await click("Save category");
  await until(
    "!document.querySelector('dialog') && document.body.textContent.includes('Browser research')",
  );
  let category = (await api("/categories")).items.find(
    (c) => c.displayName === "Browser research",
  );
  assert.ok(category);
  await click("Timeline");
  await until(
    "Array.from(document.querySelectorAll('aside button')).some(b=>b.textContent.includes('Phase 5 browser article'))",
  );
  await evaluate(
    "Array.from(document.querySelectorAll('aside button')).find(b=>b.textContent.includes('Phase 5 browser article')).click()",
  );
  await click("Edit details");
  await evaluate(
    `(()=>{const s=document.querySelector('select');s.value=${JSON.stringify(category.key)};s.dispatchEvent(new Event('change',{bubbles:true}));})()`,
  );
  await click("Save details");
  await until(
    "document.querySelector('dl')?.textContent.includes('Browser research')",
  );
  await click("Categories");
  await until(
    "Boolean(document.querySelector('button[aria-label=\"View 1 articles in Browser research\"]'))",
  );
  assert.equal(
    await evaluate(
      "document.querySelector('button[aria-label=\"Disable Browser research\"]').disabled",
    ),
    true,
  );
  assert.equal(
    await evaluate(
      "document.querySelector('button[aria-label=\"Remove Browser research\"]').disabled",
    ),
    true,
  );
  await labeledButton("Edit Browser research");
  await fill("Name", "Reading research");
  await fill("Hex color", "#168e91");
  await click("Save category");
  await until(
    "Boolean(document.querySelector('button[aria-label=\"Edit Reading research\"]'))",
  );
  await labeledButton("dark theme");
  await screenshot("categories-desktop.png");
  await command("browsingContext.setViewport", {
    context,
    viewport: { width: 390, height: 844 },
  });
  await screenshot("categories-mobile.png");
  await labeledButton("Edit Reading research");
  await screenshot("category-edit-mobile.png");
  await click("Cancel");
  await labeledButton("View 1 articles in Reading research");
  await until(
    "Array.from(document.querySelectorAll('aside button')).some(b=>b.textContent.includes('Phase 5 browser article'))",
  );
  await evaluate(
    "Array.from(document.querySelectorAll('aside button')).find(b=>b.textContent.includes('Phase 5 browser article')).click()",
  );
  await click("Delete article");
  await screenshot("article-delete-mobile.png");
  await click("Cancel");
  await api(`/articles/${article.id}`);
  await click("Delete article");
  await click("Delete permanently");
  await until("location.hash==='' && !document.querySelector('dialog')");
  assert.equal(
    (await fetch(`${origin}/api/v1/articles/${article.id}`)).status,
    404,
  );
  await api(`/entities/${linked.entity.id}`);
  await click("Categories");
  await until(
    "Boolean(document.querySelector('button[aria-label=\"Disable Reading research\"]')) && !document.querySelector('button[aria-label=\"Disable Reading research\"]').disabled",
  );
  await labeledButton("Disable Reading research");
  await until(
    "Boolean(document.querySelector('button[aria-label=\"Enable Reading research\"]'))",
  );
  await labeledButton("Enable Reading research");
  await until(
    "Boolean(document.querySelector('button[aria-label=\"Disable Reading research\"]'))",
  );
  await labeledButton("Remove Reading research");
  await click("Remove category");
  await until(
    "!document.querySelector('dialog') && !document.querySelector('button[aria-label=\"Edit Reading research\"]')",
  );
  assert.equal(
    (await api("/categories")).items.some((c) => c.key === category.key),
    false,
  );
  console.log(
    JSON.stringify({
      firefox: session.capabilities.browserVersion,
      categoryLifecycle: "passed",
      articleDeletion: "passed",
      entitiesPreserved: true,
      desktopAndMobileOverflow: false,
      proofDirectory: proof,
    }),
  );
} finally {
  if (session) await command("session.end", {}).catch(() => {});
  socket.close();
}
