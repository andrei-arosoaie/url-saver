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
  await until(`Boolean(${found}) && !(${found}).disabled`);
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

async function input(label, value, selector = "input") {
  assert.equal(
    await evaluate(
      `(()=>{const field=Array.from(document.querySelectorAll('label')).filter(e=>e.textContent.trim().startsWith(${JSON.stringify(label)})).map(e=>e.querySelector(${JSON.stringify(selector)})||document.getElementById(e.htmlFor)).find(e=>e && !e.disabled && e.getClientRects().length);if(!field)return false;Object.getOwnPropertyDescriptor(${selector === "select" ? "HTMLSelectElement" : "HTMLInputElement"}.prototype,'value').set.call(field,${JSON.stringify(value)});field.dispatchEvent(new Event('${selector === "select" ? "change" : "input"}',{bubbles:true}));return true;})()`,
    ),
    true,
  );
}
async function navigate(hash) {
  await command("browsingContext.navigate", {
    context,
    url: origin + "/" + hash,
    wait: "complete",
  });
  await until(`document.querySelector('[aria-label="Entity type"]')!==null`);
}
async function details(id) {
  return api(`/entities/${id}/tv-show`);
}
async function choose(index) {
  for (let i = 0; i < index; i++)
    await evaluate(
      `document.querySelector('[role="combobox"]').dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowDown',bubbles:true}))`,
    );
  await evaluate(
    `document.querySelector('[role="combobox"]').dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true}))`,
  );
  await until(
    `document.querySelector('[role="combobox"]')?.getAttribute('aria-expanded')==='false' && document.body.textContent.includes('Selected:')`,
  );
}
let session;
try {
  for (let n = 0; n < 120; n++) {
    const ready = await fetch(origin + "/api/v1/entities?type=TV_SHOW")
      .then(
        async (response) =>
          response.status === 200 &&
          (await response.json()).items.some(
            (entity) => entity.tvShow?.alias === "The remake",
          ),
      )
      .catch(() => false);
    if (ready) break;
    assert.ok(n < 119, "Disposable TV preview did not become ready");
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  session = await command("session.new", { capabilities: {} });
  ({ context } = await command("browsingContext.create", { type: "tab" }));
  await command("browsingContext.setViewport", {
    context,
    viewport: { width: 1360, height: 1000 },
  });
  const listed = (await api("/entities?type=TV_SHOW")).items;
  const flagged = listed.find((e) => e.tvShow?.newEpisodesAvailable);
  assert.ok(flagged, "Use the seeded disposable TV preview.");
  const second = listed.find((e) => e.id !== flagged.id);
  await navigate("#entities/TV_SHOW/board");
  await until(
    `document.querySelector('[aria-label="New episodes available"]')!==null`,
  );
  assert.equal(
    await evaluate(
      `Array.from(document.querySelectorAll('h3')).filter(e=>/To be seen|Seeing|Seen/.test(e.textContent)).length`,
    ),
    3,
  );
  await screenshot("tv-board-desktop.png");
  await navigate(`#entities/TV_SHOW/table/${flagged.id}`);
  await until(
    `document.querySelector('[aria-label="Viewing progress"]')?.textContent.includes('New episodes available')`,
  );
  await screenshot("tv-details-desktop.png");
  await until(`document.querySelector('fieldset select')?.options.length>1`);
  await input("Season", "100", "select");
  await until(
    `document.querySelector('fieldset select:nth-of-type(1)')!==null && Array.from(document.querySelectorAll('fieldset select'))[1]?.options.length>1`,
  );
  await input("Episode", "1004", "select");
  await click("Save position only");
  await until(
    `document.querySelector('[aria-label="Viewing progress"]')?.textContent.includes('S01E04')`,
  );
  assert.equal((await details(flagged.id)).newEpisodesAvailable, true);
  assert.equal((await details(flagged.id)).status, "SEEING");
  await evaluate(
    `Array.from(document.querySelectorAll('summary')).find(e=>e.textContent.startsWith('Episodes ·')).parentElement.open=true`,
  );
  await until(
    `Array.from(document.querySelectorAll('input[type="checkbox"]')).length===4`,
  );
  await evaluate(
    `Array.from(document.querySelectorAll('label')).find(e=>e.textContent.includes('E4 ·')).querySelector('input').click()`,
  );
  await until(
    `document.querySelector('[aria-label="Viewing progress"]')?.textContent.includes('3 watched / 3 aired')`,
  );
  assert.equal((await details(flagged.id)).newEpisodesAvailable, false);
  await click("Mark show seen");
  await until(
    `document.querySelector('[aria-label="Completion history"] h4')?.textContent.includes('(2)')`,
  );
  assert.equal((await details(flagged.id)).status, "SEEN");
  await evaluate(
    `Array.from(document.querySelectorAll('summary')).find(e=>e.textContent==='Edit show').parentElement.open=true`,
  );
  await input("Alias", "Evening watch");
  await click("Save alias");
  await until(
    `document.querySelector('[aria-label="Viewing progress"] h4')?.textContent.includes('Evening watch')`,
  );
  await click("Change show");
  await input("Title", "Same title");
  await until(`document.querySelectorAll('[role="option"]').length===8`);
  await screenshot("tv-change-autocomplete.png");
  await choose(3);
  await click("Review replacement…");
  await click("Cancel");
  assert.equal((await details(flagged.id)).tvmazeId, 1);
  await click("Review replacement…");
  await screenshot("tv-change-confirmation.png");
  await click("Confirm");
  await until(
    `document.querySelector('[aria-label="Viewing progress"]')?.textContent.includes('Not set')`,
  );
  const replaced = await details(flagged.id);
  assert.equal(replaced.tvmazeId, 3);
  assert.equal(replaced.status, "TO_BE_SEEN");
  assert.equal(replaced.alias, "Evening watch");
  assert.equal(replaced.watchedCount, 0);
  assert.equal(
    (await api(`/entities/${flagged.id}/completions`)).items.length,
    2,
  );
  await command("browsingContext.setViewport", {
    context,
    viewport: { width: 390, height: 844 },
  });
  await navigate(`#entities/TV_SHOW/table/${second.id}`);
  await until(
    `document.querySelector('[aria-label="Viewing progress"]')?.textContent.includes('The remake')`,
  );
  await screenshot("tv-details-mobile.png");
  await click("← TV shows");
  await click("＋ Add TV show");
  await until(`document.querySelector('dialog[open] input')!==null`);
  await input("Title", "Same title");
  await until(`document.querySelectorAll('dialog [role="option"]').length===8`);
  await screenshot("tv-autocomplete-mobile.png");
  await click("More matches");
  assert.equal(
    await evaluate(
      `document.querySelectorAll('dialog [role="option"]').length`,
    ),
    12,
  );
  await choose(4);
  await click("Add");
  await until(`document.querySelector('dialog[open]')===null`);
  const created = (await api("/entities?type=TV_SHOW")).items.find(
    (e) => e.tvShow?.tvmazeId === 4,
  );
  assert.ok(
    created,
    "Selected stable ID must be saved atomically, even for duplicate titles.",
  );
  console.log(
    "TV browser flows passed: board/table, progress, NEW clearing, completion, alias, confirmed replacement, autocomplete, mobile.",
  );
} finally {
  if (session) await command("session.end", {}).catch(() => {});
  socket.close();
}
