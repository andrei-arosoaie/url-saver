import { expect, it, vi } from "vitest";
import { captureViewport, type CaptureTab } from "./viewportCapture";

const tab: CaptureTab = {
  id: 7,
  windowId: 3,
  active: true,
  url: "https://example.test/article",
};
function fixture() {
  return {
    tab: vi.fn().mockResolvedValue(tab),
    generation: vi.fn().mockReturnValue(0),
    capture: vi.fn().mockResolvedValue("data:image/png;base64,dGVzdA=="),
    hideControl: vi.fn().mockResolvedValue(undefined),
    restoreControl: vi.fn().mockResolvedValue(undefined),
  };
}
it("hides Know before capture and restores it after verifying the same active page", async () => {
  const browser = fixture();
  await expect(
    captureViewport(browser, 7, tab.url!, { format: "png" }),
  ).resolves.toContain("data:image/png");
  expect(browser.capture).toHaveBeenCalledWith(3, { format: "png" });
  expect(browser.hideControl.mock.invocationCallOrder[0]).toBeLessThan(
    browser.capture.mock.invocationCallOrder[0]!,
  );
  expect(browser.restoreControl.mock.invocationCallOrder[0]).toBeGreaterThan(
    browser.capture.mock.invocationCallOrder[0]!,
  );
});
it.each([
  { ...tab, active: false },
  { ...tab, incognito: true },
  { ...tab, url: "https://other.test/" },
  { ...tab, url: "about:config" },
])(
  "does not capture an inactive, private, protected or changed tab",
  async (current) => {
    const browser = fixture();
    browser.tab.mockResolvedValue(current);
    await expect(
      captureViewport(browser, 7, tab.url!, { format: "png" }),
    ).rejects.toThrow();
    expect(browser.capture).not.toHaveBeenCalled();
    expect(browser.hideControl).not.toHaveBeenCalled();
  },
);
it("rejects activation/navigation away-and-back even when the final URL and active tab match", async () => {
  const browser = fixture();
  browser.generation.mockReturnValueOnce(0).mockReturnValue(1);
  await expect(
    captureViewport(browser, 7, tab.url!, { format: "png" }),
  ).rejects.toThrow("changed");
  expect(browser.capture).not.toHaveBeenCalled();
  expect(browser.restoreControl).toHaveBeenCalledWith(7);
});
it("discards a screenshot if the page changes while Firefox is capturing", async () => {
  const browser = fixture();
  browser.tab
    .mockResolvedValueOnce(tab)
    .mockResolvedValueOnce(tab)
    .mockResolvedValue({ ...tab, url: "https://other.test/" });
  await expect(
    captureViewport(browser, 7, tab.url!, { format: "png" }),
  ).rejects.toThrow("changed");
  expect(browser.capture).toHaveBeenCalledTimes(1);
  expect(browser.restoreControl).toHaveBeenCalledWith(7);
});
it("restores controls after a failed capture or hide, preserving the original failure", async () => {
  for (const stage of ["hideControl", "capture"] as const) {
    const browser = fixture();
    browser[stage].mockRejectedValue(new Error("Original failure"));
    browser.restoreControl.mockRejectedValue(new Error("Tab closed"));
    await expect(
      captureViewport(browser, 7, tab.url!, { format: "png" }),
    ).rejects.toThrow("Original failure");
    expect(browser.restoreControl).toHaveBeenCalledWith(7);
  }
});
