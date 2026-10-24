import { ArticleApi, ApiFailure } from "./api";
import type { Capture } from "./capture";
type Sender = { tab?: { id?: number; incognito?: boolean }; frameId?: number };
export function articleHandler(
  api: ArticleApi,
  getTab: (id: number) => Promise<{ active: boolean; url?: string }>,
) {
  return async (input: unknown, sender: Sender) => {
    if (!sender.tab?.id || sender.frameId !== 0 || sender.tab.incognito) return;
    if (
      !input ||
      typeof input !== "object" ||
      !("type" in input) ||
      input.type !== "know:article"
    )
      return;
    const message = input as {
      action?: unknown;
      capture?: Capture;
      id?: unknown;
      version?: unknown;
    };
    try {
      const tab = await getTab(sender.tab.id);
      if (!tab.active) throw new Error("Return to the active article to save.");
      const value = message.capture;
      if (
        !value ||
        typeof value.url !== "string" ||
        value.url !== tab.url ||
        !/^https?:\/\//.test(value.url) ||
        typeof value.title !== "string" ||
        (value.content !== undefined && typeof value.content !== "string")
      )
        throw new Error("The page changed. Click Save again.");
      if (message.action === "save")
        return { ok: true, data: await api.save(value) };
      if (message.action === "existing" && typeof message.id === "string")
        return { ok: true, data: await api.existing(message.id) };
      if (
        message.action === "update" &&
        typeof message.id === "string" &&
        typeof message.version === "number" &&
        Number.isSafeInteger(message.version) &&
        message.version >= 0
      )
        return {
          ok: true,
          data: await api.update(message.id, message.version, value),
        };
      throw new Error("Unsupported article operation.");
    } catch (error) {
      return {
        ok: false,
        status: error instanceof ApiFailure ? error.status : 0,
        reason: error instanceof ApiFailure ? error.reason : undefined,
        id: error instanceof ApiFailure ? error.existingArticleId : undefined,
        message:
          error instanceof Error
            ? error.message
            : "Save not confirmed. Click Save to retry.",
      };
    }
  };
}
