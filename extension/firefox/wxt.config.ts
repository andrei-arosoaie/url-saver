import { defineConfig } from "wxt";
export default defineConfig({
  modules: ["@wxt-dev/module-react"],
  manifest: {
    name: "Know",
    description: "Save the current article to Know.",
    permissions: ["storage", "tabs"],
    host_permissions: ["http://*/*", "https://*/*"],
    incognito: "not_allowed",
    browser_specific_settings: {
      gecko: {
        id: "know@url-saver.local",
        strict_min_version: "140.0",
        data_collection_permissions: {
          required: ["browsingActivity", "websiteContent"],
        },
      },
      gecko_android: { strict_min_version: "142.0" },
    },
  },
});
