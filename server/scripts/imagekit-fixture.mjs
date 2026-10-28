// Disposable integration-only ImageKit HTTP fixture; never shipped in the JAR.
import http from "node:http";
import crypto from "node:crypto";
const files = new Map();
let failNext = false;
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, "http://fixture");
  const json = (status, value) => { res.writeHead(status, { "Content-Type": "application/json" }); res.end(JSON.stringify(value)); };
  if (url.pathname === "/article") {
    res.writeHead(200, { "Content-Type": "text/html" });
    return res.end(`<!doctype html><html><head><title>Firefox native screenshot verification</title><style>body{margin:0;background:#fff;color:#123;font:22px/1.7 sans-serif}article{max-width:800px;margin:100px auto}h1{font-size:42px}</style></head><body><article><h1>Native viewport capture</h1>${Array.from({length:12},(_,i)=>`<p>Verification paragraph ${i+1}. This article checks that Firefox captures the visible page at native resolution, without the Know button. Every word is retained in article storage while the screenshot is uploaded separately and the reader remains usable.</p>`).join("")}</article></body></html>`);
  }
  if (url.pathname === "/__test/fail-next") { failNext = true; return json(200, {}); }
  if (url.pathname === "/__test/files") return json(200, [...files.values()].map(({bytes, ...file}) => ({ ...file, size: bytes.length })));
  if (req.method === "POST" && url.pathname === "/api/v1/files/upload") {
    const chunks = []; for await (const chunk of req) chunks.push(chunk);
    const body = Buffer.concat(chunks);
    const boundary = /boundary=(?:"([^"]+)"|([^;]+))/.exec(req.headers["content-type"] ?? "");
    if (!boundary) return json(400, { message: "Missing multipart boundary" });
    const parts = body.toString("latin1").split("--" + (boundary[1] || boundary[2]));
    const fields = {};
    for (const part of parts) {
      const split = part.indexOf("\r\n\r\n"), name = /name="([^"]+)"/.exec(part.slice(0, split));
      if (split >= 0 && name) fields[name[1]] = Buffer.from(part.slice(split + 4, -2), "latin1");
    }
    if (fields.isPrivateFile?.toString() !== "true" || !fields.file) return json(400, { message: "Expected a private file" });
    const fileId = crypto.randomUUID(), filePath = fields.folder.toString() + "/" + fields.fileName.toString();
    files.set(fileId, { fileId, filePath, privateFile: true, bytes: fields.file });
    // Simulate an uncertain provider outcome after storing the remote file.
    if (failNext) { failNext = false; return json(503, { message: "Simulated provider failure" }); }
    return json(200, { fileId, filePath, url: "http://fixture" + filePath });
  }
  if (req.method === "DELETE" && url.pathname.startsWith("/v1/files/")) {
    files.delete(decodeURIComponent(url.pathname.slice("/v1/files/".length)));
    res.writeHead(204); return res.end();
  }
  if (url.pathname === "/v1/files") return json(200, [...files.values()].map(f => ({ type: "file", fileId: f.fileId, filePath: f.filePath, name: f.filePath.split("/").at(-1) })));
  const file = [...files.values()].find(f => f.filePath === url.pathname);
  if (file && url.searchParams.has("ik-s")) { res.writeHead(200, { "Content-Type": "image/jpeg" }); return res.end(file.bytes); }
  json(404, { message: "Not found" });
});
server.listen(10002, "0.0.0.0");
