import { defineConfig } from "vitest/config";
export default defineConfig({
  test: {
    environment: "./scripts/integration-environment.mjs",
    restoreMocks: true,
    include: ["src/**/*IT.{ts,tsx}"],
    testTimeout: 20_000,
  },
});
