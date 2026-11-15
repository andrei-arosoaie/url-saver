import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { applicationBuild } from "../../webapp/buildVersion.ts";

export default defineConfig({
  plugins: [react()],
  base: "./",
  define: { __APP_BUILD__: JSON.stringify(applicationBuild()) },
  resolve: { dedupe: ["react", "react-dom"] },
  build: { outDir: "dist", emptyOutDir: true },
});
