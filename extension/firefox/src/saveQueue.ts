import { ApiFailure, ServerNonResponse, type ArticleApi } from "./api";
import type { Capture } from "./capture";
import { maxRetries, retryDelay, RetriesExhausted } from "./retrySave";
import type { ScreenshotQueue } from "./screenshotQueue";

export type SaveReply = {
  ok: boolean;
  status?: number;
  reason?: string;
  id?: string;
  message?: string;
  data?: { article?: unknown; version?: number };
  progress?: {
    target?: "screenshot";
    phase: "sending" | "waiting";
    retry: number;
    nextAttemptAt?: number;
  };
  capture?: Capture;
  jobId?: string;
};
export type SaveJob = {
  id: string;
  url: string;
  capture?: Capture;
  attempts: number;
  phase: "waiting" | "sending" | "finished";
  nextAt: number;
  reply?: SaveReply;
  screenshot?: { captureId: string; dataUrl?: string };
};
export const queuePrefix = "know:pending-save:";
export const queueKey = (url: string) => queuePrefix + encodeURIComponent(url);
export interface QueueStorage {
  get(key: string): Promise<SaveJob | undefined>;
  set(key: string, job: SaveJob): Promise<void>;
  remove(key: string): Promise<void>;
  all(): Promise<Record<string, SaveJob>>;
}
export interface QueueAlarms {
  schedule(key: string, when: number): Promise<void>;
  clear(key: string): Promise<void>;
}

export function failureReply(error: unknown): SaveReply {
  return {
    ok: false,
    status: error instanceof ApiFailure ? error.status : 0,
    reason: error instanceof ApiFailure ? error.reason : undefined,
    id: error instanceof ApiFailure ? error.existingArticleId : undefined,
    message:
      error instanceof Error
        ? error.message
        : "Save not confirmed. Please try again.",
  };
}

// Only the trusted background uses this store. Every attempt and deadline is
// committed before transmission; worker/browser restarts cannot reset the budget.
export class SaveQueue {
  private locks = new Map<string, Promise<unknown>>();
  private ready: Promise<void>;
  constructor(
    private api: Pick<ArticleApi, "save">,
    private storage: QueueStorage,
    private alarms: QueueAlarms,
    private now: () => number = Date.now,
    private screenshots?: ScreenshotQueue,
  ) {
    this.ready = this.restore();
  }
  private async restore() {
    for (const [key, job] of Object.entries(await this.storage.all())) {
      if (!key.startsWith(queuePrefix) || job.phase === "finished") continue;
      // An interrupted in-flight POST has an uncertain outcome. Count it, wait
      // out its timeout plus retry interval, and use normal duplicate handling.
      if (job.phase === "sending") {
        if (job.attempts > maxRetries) {
          await this.finish(key, job, failureReply(new RetriesExhausted()));
          continue;
        }
        job.phase = "waiting";
        await this.storage.set(key, job);
      }
      await this.alarms.schedule(key, Math.max(this.now() + 1, job.nextAt));
    }
  }
  private exclusive<T>(key: string, work: () => Promise<T>): Promise<T> {
    const previous = this.locks.get(key) ?? Promise.resolve();
    const next = previous.catch(() => {}).then(work);
    this.locks.set(key, next);
    void next
      .finally(() => {
        if (this.locks.get(key) === next) this.locks.delete(key);
      })
      .catch(() => {});
    return next;
  }
  private view(job: SaveJob): SaveReply {
    if (job.phase === "finished")
      return {
        ...job.reply!,
        jobId: job.id,
        ...(job.capture ? { capture: job.capture } : {}),
      };
    return {
      ok: false,
      jobId: job.id,
      progress: {
        phase:
          job.phase === "waiting" && job.attempts > 0 ? "waiting" : "sending",
        retry:
          job.phase === "sending"
            ? Math.max(0, job.attempts - 1)
            : job.attempts,
        ...(job.phase === "waiting" && job.attempts > 0
          ? { nextAttemptAt: job.nextAt }
          : {}),
      },
    };
  }
  async start(
    capture: Capture,
    viewport?: () => Promise<string | undefined>,
  ): Promise<SaveReply> {
    await this.ready;
    const key = queueKey(capture.url);
    return this.exclusive(key, async () => {
      const current = await this.storage.get(key);
      if (current && current.phase !== "finished") return this.view(current);
      const job: SaveJob = {
        id: crypto.randomUUID(),
        url: capture.url,
        capture: {
          url: capture.url,
          title: capture.title,
          ...(capture.content !== undefined
            ? { content: capture.content }
            : {}),
        },
        attempts: 0,
        phase: "waiting",
        nextAt: this.now(),
      };
      if (viewport)
        job.screenshot = {
          captureId: crypto.randomUUID(),
          dataUrl: await viewport().catch(() => undefined),
        };
      try {
        await this.storage.set(key, job);
      } catch (error) {
        // Image persistence failure must not prevent the smaller article job.
        if (!job.screenshot?.dataUrl) throw error;
        delete job.screenshot.dataUrl;
        await this.storage.set(key, job);
      }
      await this.alarms.schedule(key, this.now() + 1);
      return this.view(job);
    });
  }
  async status(url: string): Promise<SaveReply | undefined> {
    await this.ready;
    const job = await this.storage.get(queueKey(url));
    if (!job) return undefined;
    const image = job.reply?.ok
      ? await this.screenshots?.status(url)
      : undefined;
    return { ...this.view(job), ...image };
  }
  async screenshotAfterUpdate(
    url: string,
    articleId: string,
  ): Promise<SaveReply | undefined> {
    const job = await this.storage.get(queueKey(url));
    if (job?.screenshot && this.screenshots) {
      await this.screenshots.start(
        url,
        articleId,
        job.screenshot.captureId,
        job.screenshot.dataUrl,
      );
      delete job.screenshot;
      delete job.capture;
      job.reply = { ok: true, message: "Article updated." };
      await this.storage.set(queueKey(url), job);
      return this.status(url);
    }
  }
  async acknowledge(url: string): Promise<void> {
    await this.screenshots?.acknowledge(url);
    await this.ready;
    const key = queueKey(url);
    await this.exclusive(key, async () => {
      const job = await this.storage.get(key);
      if (job?.phase === "finished") {
        await this.storage.remove(key);
        await this.alarms.clear(key);
      }
    });
  }
  private async finish(key: string, job: SaveJob, reply: SaveReply) {
    job.phase = "finished";
    job.reply = reply;
    // Retain article text only while retrying or awaiting explicit duplicate review.
    if (reply.reason !== "DUPLICATE_URL") {
      delete job.capture;
      delete job.screenshot;
    }
    await this.storage.set(key, job);
    await this.alarms.clear(key);
  }
  async run(key: string): Promise<void> {
    if (!key.startsWith(queuePrefix)) return;
    await this.ready;
    await this.exclusive(key, async () => {
      const job = await this.storage.get(key);
      if (!job || job.phase === "finished") return;
      if (job.nextAt > this.now()) {
        await this.alarms.schedule(key, job.nextAt);
        return;
      }
      if (job.attempts > maxRetries) {
        await this.finish(key, job, failureReply(new RetriesExhausted()));
        return;
      }
      job.phase = "sending";
      job.attempts++;
      // A watchdog restores uncertain in-flight work after background termination.
      job.nextAt = this.now() + 60_000 + retryDelay;
      await this.storage.set(key, job);
      await this.alarms.schedule(key, job.nextAt);
      let reply: SaveReply;
      try {
        const saved = await this.api.save({ ...job.capture! });
        reply = {
          ok: true,
          message: job.capture?.content ? "Article saved." : "Link saved.",
        };
        if (job.screenshot && this.screenshots && saved.article?.id) {
          try {
            await this.screenshots.start(
              job.url,
              saved.article.id,
              job.screenshot.captureId,
              job.screenshot.dataUrl,
            );
          } catch {
            reply.message = "Article saved. Screenshot unavailable.";
          }
        }
      } catch (error) {
        if (error instanceof ServerNonResponse && job.attempts <= maxRetries) {
          job.phase = "waiting";
          job.nextAt = this.now() + retryDelay;
          await this.storage.set(key, job);
          await this.alarms.schedule(key, job.nextAt);
          return;
        }
        reply = failureReply(
          error instanceof ServerNonResponse ? new RetriesExhausted() : error,
        );
      }
      await this.finish(key, job, reply);
    });
  }
}
