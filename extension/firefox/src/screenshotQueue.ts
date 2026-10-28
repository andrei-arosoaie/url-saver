import { ApiFailure, ServerNonResponse } from "./api";
import type { QueueAlarms, SaveReply } from "./saveQueue";
import { maxRetries, retryDelay } from "./retrySave";

export const screenshotPrefix = "know:pending-screenshot:";
export const screenshotKey = (url: string) =>
  screenshotPrefix + encodeURIComponent(url);
export type ScreenshotJob = {
  articleId: string;
  captureId: string;
  dataUrl?: string;
  expectedVersion?: number;
  attempts: number;
  nextAt: number;
  phase: "waiting" | "sending" | "finished";
  available?: boolean;
};
export interface ScreenshotJobs {
  get(key: string): Promise<ScreenshotJob | undefined>;
  set(key: string, value: ScreenshotJob): Promise<void>;
  remove(key: string): Promise<void>;
  all(): Promise<Record<string, ScreenshotJob>>;
}
export interface ScreenshotApi {
  screenshotVersion(id: string): Promise<number>;
  uploadScreenshot(
    id: string,
    captureId: string,
    version: number,
    dataUrl?: string,
  ): Promise<boolean>;
}

// An image job never calls article save/update. Persist the original PNG and
// capture identifier so a restart or lost response cannot recapture another page.
export class ScreenshotQueue {
  private ready: Promise<void>;
  private locks = new Map<string, Promise<void>>();
  constructor(
    private api: ScreenshotApi,
    private storage: ScreenshotJobs,
    private alarms: QueueAlarms,
    private now = Date.now,
  ) {
    this.ready = this.restore();
  }
  private async restore() {
    for (const [key, job] of Object.entries(await this.storage.all())) {
      if (!key.startsWith(screenshotPrefix) || job.phase === "finished")
        continue;
      await this.alarms.schedule(key, Math.max(this.now() + 1, job.nextAt));
    }
  }
  async start(
    url: string,
    articleId: string,
    captureId: string,
    dataUrl?: string,
  ) {
    await this.ready;
    const key = screenshotKey(url),
      previous = await this.storage.get(key);
    if (previous?.captureId === captureId) return;
    const job: ScreenshotJob = {
      articleId,
      captureId,
      dataUrl,
      attempts: 0,
      nextAt: this.now(),
      phase: "waiting",
    };
    try {
      await this.storage.set(key, job);
    } catch (error) {
      if (!job.dataUrl) throw error;
      delete job.dataUrl;
      await this.storage.set(key, job);
    }
    await this.alarms.schedule(key, this.now() + 1);
  }
  async status(url: string): Promise<SaveReply | undefined> {
    await this.ready;
    const job = await this.storage.get(screenshotKey(url));
    if (!job) return;
    if (job.phase === "finished")
      return {
        ok: true,
        message: job.available
          ? "Article and screenshot saved."
          : "Article saved. Screenshot unavailable.",
      };
    return {
      ok: true,
      progress: {
        target: "screenshot",
        phase: job.phase,
        retry:
          job.phase === "sending"
            ? Math.max(0, job.attempts - 1)
            : job.attempts,
        nextAttemptAt: job.nextAt,
      },
      message: "Article saved. Uploading screenshot.",
    };
  }
  async acknowledge(url: string) {
    const key = screenshotKey(url);
    if ((await this.storage.get(key))?.phase === "finished") {
      await this.storage.remove(key);
      await this.alarms.clear(key);
    }
  }
  async run(key: string): Promise<void> {
    if (!key.startsWith(screenshotPrefix)) return;
    await this.ready;
    if (this.locks.has(key)) return this.locks.get(key);
    const work = this.attempt(key);
    this.locks.set(key, work);
    try {
      await work;
    } finally {
      this.locks.delete(key);
      const current = await this.storage.get(key);
      if (current && current.phase === "waiting")
        await this.alarms.schedule(
          key,
          Math.max(this.now() + 1, current.nextAt),
        );
    }
  }
  private async attempt(key: string) {
    const job = await this.storage.get(key);
    if (!job || job.phase === "finished") return;
    if (job.nextAt > this.now()) {
      await this.alarms.schedule(key, job.nextAt);
      return;
    }
    if (job.attempts > maxRetries) {
      await this.finish(key, job, false);
      return;
    }
    job.phase = "sending";
    job.attempts++;
    job.nextAt = this.now() + 60_000 + retryDelay;
    await this.storage.set(key, job);
    await this.alarms.schedule(key, job.nextAt);
    try {
      if (job.expectedVersion === undefined) {
        job.expectedVersion = await this.api.screenshotVersion(job.articleId);
        if ((await this.storage.get(key))?.captureId !== job.captureId) return;
        await this.storage.set(key, job);
      }
      const available = await this.api.uploadScreenshot(
        job.articleId,
        job.captureId,
        job.expectedVersion,
        job.dataUrl,
      );
      await this.finish(key, job, available);
    } catch (error) {
      if ((await this.storage.get(key))?.captureId !== job.captureId) return;
      if (
        (error instanceof ServerNonResponse ||
          (error instanceof ApiFailure && error.status === 503)) &&
        job.attempts <= maxRetries
      ) {
        job.phase = "waiting";
        job.nextAt = this.now() + retryDelay;
        await this.storage.set(key, job);
        await this.alarms.schedule(key, job.nextAt);
      } else await this.finish(key, job, false);
    }
  }
  private async finish(key: string, job: ScreenshotJob, available: boolean) {
    // A late response from an older generation must not replace a newer job.
    if ((await this.storage.get(key))?.captureId !== job.captureId) return;
    job.phase = "finished";
    job.available = available;
    delete job.dataUrl;
    await this.storage.set(key, job);
    await this.alarms.clear(key);
  }
}
