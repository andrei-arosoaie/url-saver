import { browser } from "wxt/browser";

// Physical keys include the immutable sign-in session. Old workers cannot replace
// or delete a new session's job, even if both save the same URL concurrently.
export function sessionQueueStorage<T extends { authSession?: string }>(
  prefix: string,
  binding: Promise<string | undefined>,
) {
  const suffix = binding.then(
    (session) => `:session:${session ?? "signed-out"}`,
  );
  return {
    async get(key: string): Promise<T | undefined> {
      if (!(await binding)) return;
      return (await browser.storage.local.get(key + (await suffix)))[
        key + (await suffix)
      ] as T | undefined;
    },
    async set(key: string, job: T) {
      await browser.storage.local.set({
        [key + (await suffix)]: { ...job, authSession: await binding },
      });
    },
    async remove(key: string) {
      await browser.storage.local.remove(key + (await suffix));
    },
    async all(): Promise<Record<string, T>> {
      const session = await binding,
        ending = await suffix;
      if (!session) return {};
      return Object.fromEntries(
        Object.entries(await browser.storage.local.get(null))
          .filter(([key]) => key.startsWith(prefix) && key.endsWith(ending))
          .map(([key, value]) => [key.slice(0, -ending.length), value]),
      ) as Record<string, T>;
    },
  };
}
