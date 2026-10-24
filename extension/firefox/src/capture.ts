import { Readability } from "@mozilla/readability";
import type { components } from "./generated/api";
export type Capture = components["schemas"]["SaveArticleRequest"];
export function capture(document: Document, url: string): Capture {
  const clone = document.cloneNode(true) as Document;
  clone
    .querySelectorAll("[data-know-extension]")
    .forEach((node) => node.remove());
  let article: ReturnType<Readability["parse"]> = null;
  try {
    article = new Readability(clone).parse();
  } catch {
    /* Offer an explicit link-only choice. */
  }
  return {
    url,
    title: article?.title?.trim() || document.title.trim() || url,
    ...(article?.textContent?.trim() && article.content
      ? { content: article.content }
      : {}),
  };
}
