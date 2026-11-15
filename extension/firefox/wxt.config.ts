import { defineConfig } from "wxt";
export default defineConfig({
  modules: ["@wxt-dev/module-react"],
  vite: () => ({
    build: {
      sourcemap: process.env.KNOW_REVIEW_SOURCE_MAPS === "1" ? "hidden" : false,
    },
  }),
  zip: { zipSources: false, exclude: ["**/*.map"] },
  manifest: {
    name: "Know",
    description:
      "Save articles and visible-tab screenshots to your Know library.",
    permissions: ["storage", "unlimitedStorage", "tabs", "alarms", "identity"],
    host_permissions: ["<all_urls>"],
    incognito: "not_allowed",
    browser_specific_settings: {
      gecko: {
        id: "know@url-saver.local",
        strict_min_version: "140.0",
        data_collection_permissions: {
          required: [
            "browsingActivity",
            "websiteContent",
            "authenticationInfo",
            "personallyIdentifyingInfo",
          ],
        },
      },
      gecko_android: { strict_min_version: "142.0" },
    },
  },
});
