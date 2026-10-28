import { expect, it, vi } from "vitest";
import { ApiFailure, ServerNonResponse } from "./api";
import {
  SaveQueue,
  queueKey,
  type SaveJob,
  type QueueStorage,
} from "./saveQueue";

const capture = {
  url: "https://example.test/article",
  title: "Original",
  content: "<p>All article text.</p>",
};
function queueFixture(
  save = vi.fn().mockResolvedValue({ outcome: "CREATED" }),
) {
  const records: Record<string, SaveJob> = {};
  let now = 1000;
  const storage: QueueStorage = {
    async get(key) {
      return records[key] ? structuredClone(records[key]) : undefined;
    },
    async set(key, value) {
      records[key] = structuredClone(value);
    },
    async remove(key) {
      delete records[key];
    },
    async all() {
      return structuredClone(records);
    },
  };
  const alarms = {
    schedule: vi.fn().mockResolvedValue(undefined),
    clear: vi.fn().mockResolvedValue(undefined),
  };
  const build = () => new SaveQueue({ save }, storage, alarms, () => now);
  return {
    save,
    storage,
    alarms,
    records,
    build,
    advance(ms: number) {
      now += ms;
    },
    clock: () => now,
  };
}

it("persists the original capture before sending, deduplicates clicks, and clears captured text after confirmed success", async () => {
  const f = queueFixture();
  const queue = f.build();
  const [one, two] = await Promise.all([
    queue.start(capture),
    queue.start({ ...capture, title: "Later click" }),
  ]);
  expect(two.jobId).toBe(one.jobId);
  expect(f.save).not.toHaveBeenCalled();
  await queue.run(queueKey(capture.url));
  expect(f.save).toHaveBeenCalledExactlyOnceWith(capture);
  expect(await queue.status(capture.url)).toMatchObject({ ok: true });
  expect(f.records[queueKey(capture.url)]?.capture).toBeUndefined();
  await queue.acknowledge(capture.url);
  expect(Object.keys(f.records)).toHaveLength(0);
});

it("restores retries and exact payload from storage without resetting counts or deadlines", async () => {
  const f = queueFixture(
    vi
      .fn()
      .mockRejectedValueOnce(new ServerNonResponse())
      .mockResolvedValue("saved"),
  );
  let queue = f.build();
  await queue.start(capture);
  await queue.run(queueKey(capture.url));
  expect(await queue.status(capture.url)).toMatchObject({
    progress: { phase: "waiting", retry: 1, nextAttemptAt: 61_000 },
  });
  queue = f.build(); // New background process; alarms are reconstructed from durable storage.
  await queue.status(capture.url);
  expect(f.alarms.schedule).toHaveBeenLastCalledWith(
    queueKey(capture.url),
    61_000,
  );
  await queue.run(queueKey(capture.url));
  expect(f.save).toHaveBeenCalledTimes(1);
  f.advance(60_000);
  await queue.run(queueKey(capture.url));
  expect(f.save).toHaveBeenCalledTimes(2);
  expect(f.save.mock.calls[1]?.[0]).toEqual(capture);
  expect(await queue.status(capture.url)).toMatchObject({ ok: true });
});

it("allows only ten retries across repeated browser restarts, then erases the captured content", async () => {
  const f = queueFixture(vi.fn().mockRejectedValue(new ServerNonResponse()));
  let queue = f.build();
  await queue.start(capture);
  for (let attempt = 0; attempt < 11; attempt++) {
    queue = f.build();
    await queue.run(queueKey(capture.url));
    f.advance(60_000);
  }
  expect(f.save).toHaveBeenCalledTimes(11);
  expect(await queue.status(capture.url)).toMatchObject({
    ok: false,
    message: expect.stringContaining("after 10 retries"),
  });
  expect(f.records[queueKey(capture.url)]?.capture).toBeUndefined();
  await queue.run(queueKey(capture.url));
  expect(f.save).toHaveBeenCalledTimes(11);
});

it("counts an interrupted in-flight attempt and resumes only after its timeout and retry interval", async () => {
  const f = queueFixture();
  f.records[queueKey(capture.url)] = {
    id: "interrupted",
    url: capture.url,
    capture,
    attempts: 2,
    phase: "sending",
    nextAt: 121_000,
  };
  const queue = f.build();
  expect(await queue.status(capture.url)).toMatchObject({
    progress: { phase: "waiting", retry: 2 },
  });
  await queue.run(queueKey(capture.url));
  expect(f.save).not.toHaveBeenCalled();
  f.advance(120_000);
  await queue.run(queueKey(capture.url));
  expect(f.save).toHaveBeenCalledTimes(1);
  expect(f.records[queueKey(capture.url)]?.attempts).toBe(3);
});

it("never restarts an interrupted eleventh attempt", async () => {
  const f = queueFixture();
  f.records[queueKey(capture.url)] = {
    id: "last",
    url: capture.url,
    capture,
    attempts: 11,
    phase: "sending",
    nextAt: 1000,
  };
  const queue = f.build();
  await queue.run(queueKey(capture.url));
  expect(f.save).not.toHaveBeenCalled();
  expect(await queue.status(capture.url)).toMatchObject({
    ok: false,
    message: expect.stringContaining("after 10 retries"),
  });
});

it.each([400, 401, 409, 413, 500, 503])(
  "stops on HTTP %i without scheduling another POST",
  async (status) => {
    const f = queueFixture(
      vi
        .fn()
        .mockRejectedValue(
          new ApiFailure(
            status,
            status === 409 ? "DUPLICATE_URL" : undefined,
            "existing-id",
          ),
        ),
    );
    const queue = f.build();
    await queue.start(capture);
    await queue.run(queueKey(capture.url));
    f.advance(600_000);
    await queue.run(queueKey(capture.url));
    expect(f.save).toHaveBeenCalledTimes(1);
    expect(await queue.status(capture.url)).toMatchObject({
      ok: false,
      status,
    });
    expect(f.records[queueKey(capture.url)]?.capture).toEqual(
      status === 409 ? capture : undefined,
    );
  },
);

it("serializes overlapping alarm events and ignores dismissal while a save remains pending", async () => {
  let resolve!: (value: unknown) => void;
  const f = queueFixture(
    vi.fn().mockImplementation(
      () =>
        new Promise((yes) => {
          resolve = yes;
        }),
    ),
  );
  const queue = f.build();
  await queue.start(capture);
  const one = queue.run(queueKey(capture.url));
  const two = queue.run(queueKey(capture.url));
  await vi.waitFor(() => expect(f.save).toHaveBeenCalledTimes(1));
  resolve("saved");
  await Promise.all([one, two]);
  expect(f.save).toHaveBeenCalledTimes(1);
  const next = { ...capture, url: capture.url + "?next" };
  await queue.start(next);
  await queue.acknowledge(next.url);
  expect(await queue.status(next.url)).toHaveProperty("progress");
});

it("never transmits a capture that could not be durably stored", async () => {
  const f = queueFixture();
  const queue = f.build();
  f.storage.set = vi.fn().mockRejectedValue(new Error("Storage full"));
  await expect(queue.start(capture)).rejects.toThrow("Storage full");
  expect(f.save).not.toHaveBeenCalled();
});
it("waits until a slow request fails before scheduling the next minute, and rejects early alarms", async () => {
  let reject!: (error: unknown) => void;
  const f = queueFixture(
    vi
      .fn()
      .mockImplementationOnce(
        () =>
          new Promise((_, no) => {
            reject = no;
          }),
      )
      .mockResolvedValue("saved"),
  );
  const queue = f.build();
  await queue.start(capture);
  const pending = queue.run(queueKey(capture.url));
  await vi.waitFor(() => expect(f.save).toHaveBeenCalledTimes(1));
  f.advance(30_000);
  reject(new ServerNonResponse());
  await pending;
  expect(await queue.status(capture.url)).toMatchObject({
    progress: { retry: 1, nextAttemptAt: 91_000 },
  });
  f.advance(59_999);
  await queue.run(queueKey(capture.url));
  expect(f.save).toHaveBeenCalledTimes(1);
  f.advance(1);
  await queue.run(queueKey(capture.url));
  expect(f.save).toHaveBeenCalledTimes(2);
});
