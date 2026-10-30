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
    let captureToken: string | undefined;
    let restoreTimer: ReturnType<typeof setTimeout> | undefined;
    const viewportControl = async (input: unknown) => {
      if (
        !input ||
        typeof input !== "object" ||
        !("type" in input) ||
        input.type !== "know:viewport"
      )
        return;
      const message = input as {
        hidden?: boolean;
        token?: string;
        url?: string;
      };
      if (typeof message.token !== "string") return false;
      if (message.hidden) {
        if (
          message.url !== location.href ||
          document.visibilityState !== "visible"
        )
          return false;
        captureToken = message.token;
        visibility();
        clearTimeout(restoreTimer);
        restoreTimer = setTimeout(() => {
          captureToken = undefined;
          visibility();
        }, 5000);
        await new Promise<void>((resolve) =>
          requestAnimationFrame(() => requestAnimationFrame(() => resolve())),
        );
        return (
          captureToken === message.token &&
          message.url === location.href &&
          document.visibilityState === "visible"
        );
      }
      if (captureToken === message.token) {
        clearTimeout(restoreTimer);
        captureToken = undefined;
        visibility();
      }
      return true;
    };
    browser.runtime.onMessage.addListener(viewportControl);
    const root = createRoot(mount);
    let currentUrl = location.href;
    const reported = new Set<string>();
    const send: import("../src/SaveControl").Send = async (
      action,
      value,
      id,
      version,
    ) => {
      const reply = await browser.runtime.sendMessage({
        type: "know:article",
        action,
        capture: value,
        id,
        version,
      });
      if (reply?.reason?.startsWith("SCREENSHOT_")) {
        const key = `${reply.jobId ?? currentUrl}:${reply.reason}`;
        if (!reported.has(key)) {
          reported.add(key);
          console.error("Know: article saved, screenshot failed.", {
            reason: reply.reason,
            message: reply.message,
          });
        }
      }
      return reply;
    };
    const render = () =>
      root.render(
        <SaveControl
          key={currentUrl}
          pageUrl={currentUrl}
          extract={() => capture(document, location.href)}
          send={send}
        />,
      );
    render();
    const visibility = () => {
      host.style.setProperty(
        "display",
        document.visibilityState === "visible" && !captureToken
          ? "block"
          : "none",
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
      browser.runtime.onMessage.removeListener(viewportControl);
      clearTimeout(restoreTimer);
      root.unmount();
      host.remove();
    });
  },
});
