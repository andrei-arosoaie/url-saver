import assert from "node:assert/strict";
const origin = process.env.KNOW_VERIFY_ORIGIN;
assert.match(origin ?? "", /^http:\/\/127\.0\.0\.1:\d+$/);
const call = (path, init) => fetch(origin + path, { ...init, signal: AbortSignal.timeout(60000) });
const created = await call("/api/v1/articles", {
  method: "POST", headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ url: "https://example.test/imagekit-live-smoke", title: "Disposable ImageKit verification", content: "<p>Private screenshot verification.</p>" }),
});
assert.equal(created.status, 201);
const { article } = await created.json();
const path = `/api/v1/articles/${article.id}/screenshot`;
const upload = async (version, file) => {
  const body = new FormData();
  body.append("metadata", new Blob([JSON.stringify({ captureId: crypto.randomUUID(), expectedVersion: version })], { type: "application/json" }));
  if (file) body.append("file", file, "native-viewport.png");
  return call(path, { method: "PUT", body });
};
try {
  const png = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAACAAAAAQCAIAAAD4YuoOAAAAG0lEQVR4nGNooDFgGLVg1IJRC0YtGLVgKFgAAI54AC7f1vaqAAAAAElFTkSuQmCC", "base64");
  const response = await upload(0, new Blob([png], { type: "image/png" }));
  assert.equal(response.status, 200, `Screenshot upload returned HTTP ${response.status}`);
  const image = await response.json();
  assert.equal(image.status, "AVAILABLE"); assert.equal(image.width, 32); assert.equal(image.height, 16);
  assert.equal(image.contentType, "image/jpeg"); assert.ok(image.byteSize <= 5 * 1024 * 1024);
  assert.ok(Date.parse(image.expiresAt) > Date.now() + 29 * 60000);
  const signed = new URL(image.url); assert.equal(signed.protocol, "https:");
  assert.ok(signed.searchParams.has("ik-s"));
  const downloaded = await fetch(signed, { signal: AbortSignal.timeout(60000) });
  assert.equal(downloaded.status, 200, `Signed download returned HTTP ${downloaded.status}`);
  assert.deepEqual([...new Uint8Array(await downloaded.arrayBuffer()).slice(0, 2)], [255, 216]);
  const unsigned = new URL(signed); unsigned.search = "";
  const denied = await fetch(unsigned, { signal: AbortSignal.timeout(60000) });
  assert.ok([401, 403].includes(denied.status), `Unsigned private download returned HTTP ${denied.status}`);
  console.log("Live ImageKit passed: official SDK private upload, backend JPEG transformation, native dimensions, signed download, unsigned access denied.");
} finally {
  const current = await call(path);
  if (current.status === 200) {
    const image = await current.json();
    const removed = await upload(image.version);
    assert.equal(removed.status, 200, "Could not journal live test file cleanup");
    assert.equal((await removed.json()).status, "MISSING");
  }
}
