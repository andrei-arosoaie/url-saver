import { describe, expect, it } from "vitest";
import { capture } from "./capture";
describe("article extraction", () => {
  it("extracts article HTML without modifying the page or capturing extension UI", () => {
    document.head.innerHTML = "<title>Original title</title>";
    document.body.innerHTML = `<article><h1>Database indexing</h1>${"<p>Indexes help readers locate records quickly, while transactions preserve consistency. This article explains the practical details of database design and query planning.</p>".repeat(15)}</article><div data-know-extension>KNOW extension controls</div>`;
    const before = document.documentElement.outerHTML;
    const result = capture(document, "https://example.com/article?q=1#section");
    expect(result.url).toBe("https://example.com/article?q=1#section");
    expect(result.content).toContain("Indexes help readers");
    expect(result.content).not.toContain("KNOW extension controls");
    expect(document.documentElement.outerHTML).toBe(before);
  });
  it("returns a link-only candidate when there is no readable prose", () => {
    document.head.innerHTML = "<title>Empty page</title>";
    document.body.innerHTML = "";
    expect(capture(document, "https://example.com/")).toEqual({
      url: "https://example.com/",
      title: "Empty page",
    });
  });
});
