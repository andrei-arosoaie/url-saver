import { build } from "vite";
import react from "@vitejs/plugin-react";
import fs from "node:fs/promises";
import { fileURLToPath, URL } from "node:url";
import console from "node:console";
import path from "node:path";
const webRoot = fileURLToPath(new URL("../", import.meta.url));
const destination = fileURLToPath(
  new URL("../../mockup-assets/android-preview/design/", import.meta.url),
);
// A self-contained classic script works when the phase page is opened from disk.
// Module-script imports are blocked by browsers on file:// and must not be used here.
const result = await build({
  configFile: false,
  root: webRoot,
  plugins: [react()],
  define: {
    __APP_BUILD__: JSON.stringify({ version: "design", revision: "phase13" }),
    "process.env.NODE_ENV": JSON.stringify("production"),
  },
  build: {
    write: false,
    lib: {
      entry: path.join(webRoot, "design/android-preview.tsx"),
      name: "KnowAndroidMock",
      formats: ["iife"],
    },
    cssCodeSplit: false,
  },
});
const outputs = (Array.isArray(result) ? result : [result]).flatMap(
  (r) => r.output,
);
const javascript = outputs
  .filter((o) => o.type === "chunk")
  .map((o) => o.code)
  .join("\n")
  .replace(/<\/script/gi, "<\\/script");
const css = outputs
  .filter((o) => o.type === "asset" && o.fileName.endsWith(".css"))
  .map((o) => o.source)
  .join("\n");
const html =
  '<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Know - Android interactive mock</title><style>' +
  css +
  '</style></head><body><div id="root"></div><script>' +
  javascript +
  "</script></body></html>";
await fs.mkdir(destination, { recursive: true });
await fs.writeFile(path.join(destination, "android-preview.html"), html);
console.log(
  "Built self-contained Android mock: inline production CSS and classic JavaScript; no server or module imports required.",
);
