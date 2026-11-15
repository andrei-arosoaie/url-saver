import type { CapacitorConfig } from "@capacitor/cli";

const config: CapacitorConfig = {
  appId: "com.know.mobile.preview",
  appName: "Know Preview",
  webDir: "dist",
  android: {
    path: "native",
    allowMixedContent: false,
    webContentsDebuggingEnabled: false,
    minWebViewVersion: 120,
  },
  plugins: { SystemBars: { insetsHandling: "native", initialViewportFitValueHint: "contain" } },
};

export default config;
