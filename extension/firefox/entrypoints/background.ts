import { browser } from "wxt/browser";
import { ArticleApi } from "../src/api";
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
export default defineBackground(() => {
  const api = new ArticleApi(
    import.meta.env.VITE_API_ORIGIN || "https://url-saver-server.onrender.com",
    async () => {
      const { accessToken } = await browser.storage.session.get("accessToken");
      return typeof accessToken === "string" ? accessToken : undefined;
    },
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
    {
      async get(key) {
        return (await browser.storage.local.get(key))[key] as
          ScreenshotJob | undefined;
      },
      async set(key, job) {
        await browser.storage.local.set({ [key]: job });
      },
      async remove(key) {
        await browser.storage.local.remove(key);
      },
      async all() {
        return Object.fromEntries(
          Object.entries(await browser.storage.local.get(null)).filter(
            ([key]) => key.startsWith(screenshotPrefix),
          ),
        ) as Record<string, ScreenshotJob>;
      },
    },
    alarms,
  );
  const queue = new SaveQueue(
    api,
    {
      async get(key) {
        return (await browser.storage.local.get(key))[key] as
          SaveJob | undefined;
      },
      async set(key, job) {
        await browser.storage.local.set({ [key]: job });
      },
      async remove(key) {
        await browser.storage.local.remove(key);
      },
      async all() {
        return Object.fromEntries(
          Object.entries(await browser.storage.local.get(null)).filter(
            ([key]) => key.startsWith(queuePrefix),
          ),
        ) as Record<string, SaveJob>;
      },
    },
    alarms,
    Date.now,
    screenshots,
  );
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
        ? screenshots.run(alarm.name)
        : queue.run(alarm.name)
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
  browser.runtime.onMessage.addListener(
    articleHandler(api, (id) => browser.tabs.get(id), queue, viewport),
  );
});
