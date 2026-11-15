// Reuse the actual web screens, styles and isolated fixture adapters.
// This first native checkpoint deliberately makes no production API requests.
import "../../../webapp/design/android-preview";
import { Capacitor } from "@capacitor/core";
import { App } from "@capacitor/app";
import { handleAndroidBack } from "./navigation.mjs";

if (Capacitor.isNativePlatform() && Capacitor.getPlatform() === "android") {
  void App.addListener("backButton", ({ canGoBack }) => {
    handleAndroidBack({
      canGoBack,
      dismissDialog: () => {
        const dialog = document.querySelector<HTMLDialogElement>("dialog[open]");
        if (!dialog) return false;
        dialog.dispatchEvent(new Event("cancel", { bubbles: false, cancelable: true }));
        return true;
      },
      goBack: () => window.history.back(),
      minimize: () => { void App.minimizeApp(); },
    });
  });
}
