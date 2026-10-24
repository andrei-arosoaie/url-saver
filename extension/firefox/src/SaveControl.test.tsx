import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SaveControl, type Reply, type Send } from "./SaveControl";
afterEach(cleanup);
const value = {
  url: "https://example.com/",
  title: "Article",
  content: "<p>Article text</p>",
};
describe("Know save interaction", () => {
  it("shows saving until server confirmation, then green success and blue saved", async () => {
    let finish!: (result: Reply) => void;
    const send = vi.fn<Send>(
      () =>
        new Promise((resolve) => {
          finish = resolve;
        }),
    );
    render(<SaveControl extract={() => value} send={send} />);
    fireEvent.click(screen.getByRole("button", { name: "Know: Save article" }));
    expect(screen.getByRole("status").textContent).toBe("Saving article…");
    expect(
      screen
        .getByRole("button", { name: "Know: Save article" })
        .hasAttribute("disabled"),
    ).toBe(true);
    finish({ ok: true });
    await waitFor(() =>
      expect(screen.getByRole("status").textContent).toBe("Article saved."),
    );
    const button = screen.getByRole("button", { name: "Know: Save article" });
    expect(button.className).toContain("success");
    await waitFor(() => expect(button.className).toContain("saved"));
  });
  it("requires consent for link-only fallback and can cancel without a request", () => {
    const send = vi.fn<Send>();
    render(
      <SaveControl
        extract={() => ({ url: value.url, title: value.title })}
        send={send}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Know: Save article" }));
    expect(
      screen.getByText("No readable article found. Save URL and title only?"),
    ).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
    expect(send).not.toHaveBeenCalled();
  });
  it("sends a link only after explicit confirmation", async () => {
    const send = vi.fn<Send>().mockResolvedValue({ ok: true });
    render(
      <SaveControl
        extract={() => ({ url: value.url, title: value.title })}
        send={send}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Know: Save article" }));
    fireEvent.click(screen.getByRole("button", { name: "Save link only" }));
    await waitFor(() =>
      expect(screen.getByRole("status").textContent).toBe("Link saved."),
    );
    expect(send).toHaveBeenCalledWith("save", {
      url: value.url,
      title: value.title,
    });
  });
  it("loads existing version but never updates before explicit consent", async () => {
    const send = vi
      .fn<Send>()
      .mockResolvedValueOnce({
        ok: false,
        status: 409,
        reason: "DUPLICATE_URL",
        id: "article-id",
      })
      .mockResolvedValueOnce({ ok: true, data: { version: 4 } })
      .mockResolvedValueOnce({ ok: true });
    render(<SaveControl extract={() => value} send={send} />);
    fireEvent.click(screen.getByRole("button", { name: "Know: Save article" }));
    const update = await screen.findByRole("button", {
      name: "Update existing",
    });
    expect(send).toHaveBeenCalledTimes(2);
    fireEvent.click(update);
    await waitFor(() =>
      expect(screen.getByRole("status").textContent).toBe("Article updated."),
    );
    expect(send).toHaveBeenLastCalledWith("update", value, "article-id", 4);
  });
  it("shows a clearly branded error and retries only on another click", async () => {
    const send = vi
      .fn<Send>()
      .mockResolvedValueOnce({ ok: false, message: "Server unavailable." })
      .mockResolvedValueOnce({ ok: true });
    render(<SaveControl extract={() => value} send={send} />);
    fireEvent.click(screen.getByRole("button", { name: "Know: Save article" }));
    await screen.findByRole("alert");
    expect(
      screen.getByRole("region", { name: "Know extension feedback" })
        .textContent,
    ).toContain("Firefox extension");
    expect(send).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole("button", { name: "Know: Save article" }));
    await waitFor(() => expect(send).toHaveBeenCalledTimes(2));
  });
  it("requires a fresh save after a version conflict", async () => {
    const send = vi
      .fn<Send>()
      .mockResolvedValueOnce({
        ok: false,
        status: 409,
        reason: "DUPLICATE_URL",
        id: "id",
      })
      .mockResolvedValueOnce({ ok: true, data: { version: 1 } })
      .mockResolvedValueOnce({
        ok: false,
        message: "Article changed. Reload before updating.",
      });
    render(<SaveControl extract={() => value} send={send} />);
    fireEvent.click(screen.getByRole("button", { name: "Know: Save article" }));
    fireEvent.click(
      await screen.findByRole("button", { name: "Update existing" }),
    );
    expect((await screen.findByRole("alert")).textContent).toContain(
      "Article changed",
    );
    expect(send).toHaveBeenCalledTimes(3);
    expect(
      screen.queryByRole("button", { name: "Update existing" }),
    ).toBeNull();
  });
});
