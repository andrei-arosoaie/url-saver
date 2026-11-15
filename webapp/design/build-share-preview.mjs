import { build } from "vite";
import react from "@vitejs/plugin-react";
import fs from "node:fs/promises";
import { fileURLToPath, URL } from "node:url";
import console from "node:console";
import path from "node:path";
const webRoot = fileURLToPath(new URL("../", import.meta.url));
const destination = fileURLToPath(
  new URL("../../mockup-assets/share-preview/", import.meta.url),
);
const result = await build({
  configFile: false,
  root: webRoot,
  plugins: [react()],
  define: { "process.env.NODE_ENV": JSON.stringify("production") },
  build: {
    write: false,
    lib: {
      entry: path.join(webRoot, "design/share-preview.tsx"),
      name: "KnowShareMock",
      formats: ["iife"],
    },
    cssCodeSplit: false,
  },
});
const output = (Array.isArray(result) ? result : [result]).flatMap(
  (r) => r.output,
);
const script = output
  .filter((o) => o.type === "chunk")
  .map((o) => o.code)
  .join("\n")
  .replace(/<\/script/gi, "<\\/script");
const css = output
  .filter((o) => o.type === "asset" && o.fileName.endsWith(".css"))
  .map((o) => o.source)
  .join("\n");
await fs.mkdir(destination, { recursive: true });
await fs.writeFile(
  path.join(destination, "index.html"),
  '<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Know - Share link mock</title><style>' +
    css +
    '</style></head><body><div id="root"></div><script>' +
    script +
    "</script></body></html>",
);
console.log(
  "Built self-contained Phase 14 share mock using existing web styles.",
);
