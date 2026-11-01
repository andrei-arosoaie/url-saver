import { browser } from "wxt/browser";
import { ArticleApi, ApiFailure } from "../src/api";
import { articleHandler } from "../src/messages";
import { SaveQueue, queuePrefix, type SaveJob } from "../src/saveQueue";
import {
  ScreenshotQueue,
  screenshotPrefix,
  type ScreenshotJob,
} from "../src/screenshotQueue";
import { captureViewport } from "../src/viewportCapture";
import { CaptureFailure } from "../src/captureFailure";
import { requireWebsiteCaptureAccess } from "../src/captureAccess";
import { sessionQueueStorage } from "../src/sessionQueueStorage";
import {
  accessToken,
  apiOrigin,
  currentSession,
  signIn,
  signOut,
} from "../src/auth";
export default defineBackground(() => {
  function createServices() {
    const binding = currentSession();
    const api = new ArticleApi(apiOrigin, async () =>
      accessToken(await binding),
    );
    const alarms = {
      async schedule(key: string, when: number) {
        await browser.alarms.create(key, { when });
      },
      async clear(key: string) {
        await browser.alarms.clear(key);
      },
    };
    const screenshots = new ScreenshotQueue(
      api,
      sessionQueueStorage<ScreenshotJob>(screenshotPrefix, binding),
      alarms,
    );
    const queue = new SaveQueue(
      api,
      sessionQueueStorage<SaveJob>(queuePrefix, binding),
      alarms,
      Date.now,
      screenshots,
    );
    return { api, queue, screenshots, binding };
  }
  let services = createServices();
  let generation = 0;
  browser.tabs.onActivated.addListener(() => {
    generation++;
  });
  browser.tabs.onUpdated.addListener((_id, change) => {
    if (change.url || change.status === "loading") generation++;
  });
  let captureLock: Promise<unknown> = Promise.resolve();
  const viewport = (id: number, url: string): Promise<string | undefined> => {
    const work = captureLock
      .catch(() => {})
      .then(async () => {
        await requireWebsiteCaptureAccess((permissions) =>
          browser.permissions.contains(permissions),
        );
        if (typeof browser.tabs.captureVisibleTab !== "function")
          throw new CaptureFailure("UNAVAILABLE");
        const token = crypto.randomUUID();
        const image = await captureViewport(
          {
            async tab(tabId) {
              const tab = await browser.tabs.get(tabId);
              const [viewed] = await browser.tabs.query({
                active: true,
                currentWindow: true,
              });
              return { ...tab, active: tab.active && viewed?.id === tabId };
            },
            generation: () => generation,
            async capture(windowId, options) {
              try {
                return await browser.tabs.captureVisibleTab(windowId, options);
              } catch (error) {
                throw new CaptureFailure("CAPTURE_FAILED", error);
              }
            },
            async hideControl(tabId) {
              const result = await Promise.race([
                browser.tabs.sendMessage(
                  tabId,
                  { type: "know:viewport", hidden: true, token, url },
                  { frameId: 0 },
                ),
                new Promise((_, reject) =>
                  setTimeout(
                    () => reject(new CaptureFailure("PREPARATION")),
                    2000,
                  ),
                ),
              ]);
              if (result !== true) throw new CaptureFailure("PREPARATION");
            },
            async restoreControl(tabId) {
              await browser.tabs.sendMessage(
                tabId,
                { type: "know:viewport", hidden: false, token, url },
                { frameId: 0 },
              );
            },
          },
          id,
          url,
          { format: "png" },
        );
        // Transport ceiling only; output format/quality are exclusively backend policy.
        const encoded = image.slice("data:image/png;base64,".length);
        const bytes =
          Math.floor((encoded.length * 3) / 4) -
          (encoded.endsWith("==") ? 2 : encoded.endsWith("=") ? 1 : 0);
        if (bytes > 20 * 1024 * 1024) throw new CaptureFailure("TOO_LARGE");
        return image;
      });
    captureLock = work;
    return work;
  };
  browser.alarms.onAlarm.addListener((alarm) => {
    void (
      alarm.name.startsWith(screenshotPrefix)
        ? services.screenshots.run(alarm.name)
        : services.queue.run(alarm.name)
    ).catch(() => {
      // Never log captured content, credentials or raw transport errors.
      console.error(
        "Know: could not process the pending save. Reopen the page to review its status.",
      );
    });
  });
  // Register startup listeners synchronously so Firefox loads this background
  // after a browser restart even though alarms themselves do not survive it.
  browser.runtime.onStartup.addListener(() => {});
  browser.runtime.onInstalled.addListener(() => {});
  browser.runtime.onMessage.addListener(async (message: unknown, sender) => {
    if (
      message &&
      typeof message === "object" &&
      "type" in message &&
      message.type === "know:article"
    ) {
      const current = services;
      if ("action" in message && message.action === "save") {
        try {
          await accessToken(await current.binding);
        } catch (error) {
          return {
            ok: false,
            status: error instanceof ApiFailure ? error.status : 0,
            message:
              error instanceof Error
                ? error.message
                : "Could not connect to sign-in. Please try again.",
          };
        }
      }
      return articleHandler(
        current.api,
        (id) => browser.tabs.get(id),
        current.queue,
        viewport,
      )(message, sender);
    }
    if (
      !message ||
      typeof message !== "object" ||
      !("type" in message) ||
      message.type !== "know:identity"
    )
      return;
    if (sender.id !== browser.runtime.id || sender.tab?.incognito) return;
    const action = "action" in message ? message.action : undefined;
    // Content scripts may initiate sign-in only from the active top-level tab.
    if (sender.tab) {
      if (
        sender.frameId !== 0 ||
        !sender.tab.id ||
        !(await browser.tabs.get(sender.tab.id)).active ||
        action !== "signIn"
      )
        return;
    } else if (!sender.url?.startsWith(browser.runtime.getURL(""))) return;
    try {
      if (action === "status") return { signedIn: !!(await currentSession()) };
      if (action === "signIn") await signIn();
      else if (action === "signOut") await signOut();
      services = createServices();
      return { ok: true };
    } catch (error) {
      return {
        ok: false,
        message:
          error instanceof Error
            ? error.message
            : "Sign-in failed. Please try again.",
      };
    }
  });
});
