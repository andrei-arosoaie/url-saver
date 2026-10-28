export type ViewportFormat = "png" | "jpeg";
export type CaptureTab = {
  id?: number;
  windowId: number;
  active: boolean;
  incognito?: boolean;
  url?: string;
};
export interface ViewportBrowser {
  tab(id: number): Promise<CaptureTab>;
  // Updated on activation and navigation events, including away-and-back races.
  generation(windowId: number): number;
  capture(
    windowId: number,
    options: { format: ViewportFormat; quality?: number },
  ): Promise<string>;
  hideControl(tabId: number): Promise<void>;
  restoreControl(tabId: number): Promise<void>;
}

// No format, quality or save-on-failure defaults: those are Phase 4 contract choices.
export async function captureViewport(
  browser: ViewportBrowser,
  tabId: number,
  expectedUrl: string,
  options: { format: ViewportFormat; quality?: number },
): Promise<string> {
  const tab = await browser.tab(tabId);
  if (
    !tab.active ||
    tab.incognito ||
    tab.url !== expectedUrl ||
    !/^https?:\/\//.test(expectedUrl)
  )
    throw new Error(
      "Return to the active article before capturing its viewport.",
    );
  const generation = browser.generation(tab.windowId);
  const assertSamePage = async () => {
    const current = await browser.tab(tabId);
    if (
      !current.active ||
      current.incognito ||
      current.windowId !== tab.windowId ||
      current.url !== expectedUrl ||
      browser.generation(tab.windowId) !== generation
    )
      throw new Error(
        "The active page changed. Screenshot capture was discarded.",
      );
  };
  try {
    await browser.hideControl(tabId);
    await assertSamePage();
    const image = await browser.capture(tab.windowId, options);
    await assertSamePage();
    if (!image.startsWith(`data:image/${options.format};base64,`))
      throw new Error(
        "Firefox did not return the requested screenshot format.",
      );
    return image;
  } finally {
    // Navigation/closed-tab failures must not replace the actual capture result.
    await browser.restoreControl(tabId).catch(() => {});
  }
}
