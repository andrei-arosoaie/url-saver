import { expect, it, vi } from "vitest";
import { ApiFailure, ServerNonResponse } from "./api";
import {
  ScreenshotQueue,
  screenshotKey,
  type ScreenshotJob,
} from "./screenshotQueue";
import { SaveQueue, queueKey, type SaveJob } from "./saveQueue";
import { CaptureFailure, captureIssueMessage } from "./captureFailure";

const url = "https://example.test/native";
const png = "data:image/png;base64,b3JpZ2luYWw=";
function fixture() {
  const records: Record<string, ScreenshotJob> = {};
  let now = 1000;
  const storage = {
    async get(key: string) {
      return records[key] && structuredClone(records[key]);
    },
    async set(key: string, value: ScreenshotJob) {
      records[key] = structuredClone(value);
    },
    async remove(key: string) {
      delete records[key];
    },
    async all() {
      return structuredClone(records);
    },
  };
  const api = {
    screenshotVersion: vi.fn().mockResolvedValue(4),
    uploadScreenshot: vi.fn().mockResolvedValue(true),
  };
  const alarms = {
    schedule: vi.fn().mockResolvedValue(undefined),
    clear: vi.fn().mockResolvedValue(undefined),
  };
  const build = () => new ScreenshotQueue(api, storage, alarms, () => now);
  return {
    records,
    api,
    alarms,
    build,
    clock: () => now,
    advance: () => {
      now += 60000;
    },
  };
}

it("manual retry runs the original image immediately without resetting its version or retry budget", async () => {
  const f = fixture(),
    queue = f.build();
  f.api.uploadScreenshot.mockRejectedValueOnce(new ServerNonResponse());
  await queue.start(url, "article", "capture", png);
  await queue.run(screenshotKey(url));
  expect(f.records[screenshotKey(url)]?.nextAt).toBeGreaterThan(f.clock());
  await queue.retryNow(url);
  await queue.run(screenshotKey(url));
  expect(f.api.uploadScreenshot.mock.calls).toEqual([
    ["article", "capture", 4, png],
    ["article", "capture", 4, png],
  ]);
  expect(f.records[screenshotKey(url)]?.attempts).toBe(2);
  expect((await queue.status(url))?.message).toBe(
    "Article and screenshot saved.",
  );
});

it("retries the original PNG after a browser restart without changing capture or version", async () => {
  const f = fixture();
  let queue = f.build();
  f.api.uploadScreenshot.mockRejectedValueOnce(new ServerNonResponse());
  await queue.start(url, "article", "capture", png);
  await queue.run(screenshotKey(url));
  expect((await queue.status(url))?.progress?.target).toBe("screenshot");
  expect(f.records[screenshotKey(url)]?.dataUrl).toBe(png);
  queue = f.build();
  f.advance();
  await queue.run(screenshotKey(url));
  expect(f.api.screenshotVersion).toHaveBeenCalledTimes(1);
  expect(f.api.uploadScreenshot.mock.calls).toEqual([
    ["article", "capture", 4, png],
    ["article", "capture", 4, png],
  ]);
  expect(f.records[screenshotKey(url)]?.dataUrl).toBeUndefined();
  expect((await queue.status(url))?.message).toBe(
    "Article and screenshot saved.",
  );
});

it("allows exactly ten retries on provider 503, then releases the PNG and reports the article saved", async () => {
  const f = fixture(),
    queue = f.build();
  f.api.uploadScreenshot.mockRejectedValue(new ApiFailure(503));
  await queue.start(url, "article", "capture", png);
  for (let i = 0; i < 12; i++) {
    await queue.run(screenshotKey(url));
    f.advance();
  }
  expect(f.api.uploadScreenshot).toHaveBeenCalledTimes(11);
  expect((await queue.status(url))?.message).toBe(
    "Article saved. Screenshot unavailable.",
  );
  expect(f.records[screenshotKey(url)]?.dataUrl).toBeUndefined();
});

it.each([400, 409, 413])(
  "does not retry a permanent %s response",
  async (status) => {
    const f = fixture(),
      queue = f.build();
    f.api.uploadScreenshot.mockRejectedValue(new ApiFailure(status));
    await queue.start(url, "article", "capture", png);
    await queue.run(screenshotKey(url));
    f.advance();
    await queue.run(screenshotKey(url));
    expect(f.api.uploadScreenshot).toHaveBeenCalledTimes(1);
    expect((await queue.status(url))?.ok).toBe(true);
  },
);

it("records a missing screenshot when capture fails while still saving the full article", async () => {
  const f = fixture(),
    images = f.build();
  const records: Record<string, SaveJob> = {};
  const storage = {
    async get(key: string) {
      return records[key] && structuredClone(records[key]);
    },
    async set(key: string, value: SaveJob) {
      records[key] = structuredClone(value);
    },
    async remove(key: string) {
      delete records[key];
    },
    async all() {
      return structuredClone(records);
    },
  };
  const save = vi.fn().mockResolvedValue({ article: { id: "article" } });
  const queue = new SaveQueue(
    { save } as never,
    storage,
    f.alarms,
    f.clock,
    images,
  );
  f.api.uploadScreenshot.mockResolvedValue(false);
  await queue.start(
    { url, title: "Original", content: "<p>All article text.</p>" },
    async () => {
      throw new CaptureFailure("PERMISSION");
    },
  );
  await queue.run(queueKey(url));
  await images.run(screenshotKey(url));
  expect(save).toHaveBeenCalledWith({
    url,
    title: "Original",
    content: "<p>All article text.</p>",
  });
  expect(f.api.uploadScreenshot).toHaveBeenCalledWith(
    "article",
    expect.any(String),
    4,
    undefined,
  );
  expect((await queue.status(url))?.message).toBe(
    "Article saved. Screenshot unavailable. " +
      captureIssueMessage("PERMISSION"),
  );
});

it("saves the article once, retries only its screenshot, and keeps duplicate captures until explicit update", async () => {
  const f = fixture(),
    images = f.build();
  const records: Record<string, SaveJob> = {};
  const storage = {
    async get(key: string) {
      return records[key] && structuredClone(records[key]);
    },
    async set(key: string, value: SaveJob) {
      records[key] = structuredClone(value);
    },
    async remove(key: string) {
      delete records[key];
    },
    async all() {
      return structuredClone(records);
    },
  };
  const save = vi.fn().mockResolvedValue({ article: { id: "article" } });
  const queue = new SaveQueue(
    { save } as never,
    storage,
    f.alarms,
    f.clock,
    images,
  );
  const capture = { url, title: "Original", content: "<p>Every word.</p>" };
  const viewport = vi.fn().mockResolvedValue(png);
  await queue.start(capture, viewport);
  await queue.run(queueKey(url));
  f.api.uploadScreenshot.mockRejectedValueOnce(new ServerNonResponse());
  await images.run(screenshotKey(url));
  f.advance();
  await images.run(screenshotKey(url));
  expect(save).toHaveBeenCalledTimes(1);
  expect(viewport).toHaveBeenCalledTimes(1);
  expect((await queue.status(url))?.message).toBe(
    "Article and screenshot saved.",
  );
  await queue.acknowledge(url);
  save.mockRejectedValueOnce(new ApiFailure(409, "DUPLICATE_URL", "article"));
  await queue.start(capture, viewport);
  await queue.run(queueKey(url));
  expect(f.records[screenshotKey(url)]).toBeUndefined();
  expect(records[queueKey(url)]?.screenshot?.dataUrl).toBe(png);
  await queue.screenshotAfterUpdate(url, "article");
  await images.run(screenshotKey(url));
  expect((await queue.status(url))?.message).toBe(
    "Article and screenshot saved.",
  );
  expect(save).toHaveBeenCalledTimes(2);
});
