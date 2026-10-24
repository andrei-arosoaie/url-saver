import { createRoot } from "react-dom/client";
import { browser } from "wxt/browser";
import { capture } from "../src/capture";
import { SaveControl } from "../src/SaveControl";
import css from "../src/SaveControl.module.css?inline";
export default defineContentScript({
  cssInjectionMode: "ui",
  matches: ["http://*/*", "https://*/*"],
  allFrames: false,
  runAt: "document_idle",
  main(ctx) {
    if (browser.extension.inIncognitoContext) return;
    const host = document.createElement("div");
    host.dataset.knowExtension = "true";
    host.style.cssText =
      "all:initial!important;position:fixed!important;top:0!important;right:0!important;z-index:2147483647!important;";
    const shadow = host.attachShadow({ mode: "closed" });
    const style = document.createElement("style");
    style.textContent = css;
    shadow.append(style);
    const mount = document.createElement("div");
    shadow.append(mount);
    document.documentElement.append(host);
    const root = createRoot(mount);
    let currentUrl = location.href;
    const render = () =>
      root.render(
        <SaveControl
          key={currentUrl}
          extract={() => capture(document, location.href)}
          send={(action, value, id, version) =>
            browser.runtime.sendMessage({
              type: "know:article",
              action,
              capture: value,
              id,
              version,
            })
          }
        />,
      );
    render();
    const visibility = () => {
      host.style.setProperty(
        "display",
        document.visibilityState === "visible" ? "block" : "none",
        "important",
      );
    };
    ctx.setInterval(() => {
      if (
        document.visibilityState === "visible" &&
        currentUrl !== location.href
      ) {
        currentUrl = location.href;
        render();
      }
    }, 500);
    ctx.addEventListener(document, "visibilitychange", visibility);
    visibility();
    ctx.onInvalidated(() => {
      root.unmount();
      host.remove();
    });
  },
});
