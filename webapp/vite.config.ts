import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      "/api": process.env.KNOW_DEV_API_ORIGIN || "http://localhost:8080",
    },
  },
});
