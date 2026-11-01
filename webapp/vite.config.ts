import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { applicationBuild } from "./buildVersion";
const build = applicationBuild();
export default defineConfig({
  define: { __APP_BUILD__: JSON.stringify(build) },
  plugins: [
    react(),
    {
      name: "application-version",
      transformIndexHtml() {
        return [
          {
            tag: "meta",
            attrs: { name: "application-version", content: build.version },
            injectTo: "head",
          },
          {
            tag: "meta",
            attrs: { name: "application-revision", content: build.revision },
            injectTo: "head",
          },
        ];
      },
    },
  ],
  server: {
    proxy: {
      "/oauth2": process.env.KNOW_DEV_API_ORIGIN || "http://localhost:8080",
      "/login": process.env.KNOW_DEV_API_ORIGIN || "http://localhost:8080",
      "/api": process.env.KNOW_DEV_API_ORIGIN || "http://localhost:8080",
    },
  },
});
