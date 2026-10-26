import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { TimelineList } from "./TimelineList";
afterEach(cleanup);
it("displays saved timestamps above their clocks and opens real article IDs", () => {
  const select = vi.fn();
  const item = {
    id: "actual-id",
    url: "https://example.com/page",
    title: "A captured page",
    savedAt: "2026-10-25T12:30:00Z",
    version: 0,
    hasContent: true,
    category: "OTHER",
    entityName: null,
  };
  render(
    <TimelineList
      items={[item]}
      selected={item.id}
      onSelect={select}
      now={new Date("2026-10-25T14:00:00Z")}
    />,
  );
  expect(screen.getByText("Today")).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: /A captured page/ }));
  expect(select).toHaveBeenCalledWith(item.id);
  expect(
    screen
      .getByRole("button", { name: /A captured page/ })
      .getAttribute("aria-pressed"),
  ).toBe("true");
});
it("uses category metadata when supplied and does not invent it for legacy responses", () => {
  const common = {
    url: "https://example.com/page",
    title: "Page",
    savedAt: "2026-10-25T12:30:00Z",
    version: 0,
    hasContent: false,
    category: "OTHER",
    entityName: null,
  };
  render(
    <TimelineList
      items={[
        {
          ...common,
          id: "one",
          entityName: "1984",
          categoryDisplay: {
            key: "BOOKS",
            displayName: "Books",
            color: "#168e91",
          },
        },
        { ...common, id: "two", title: "Legacy page" },
      ]}
      selected={null}
      onSelect={() => {}}
    />,
  );
  expect(screen.getAllByText("Books")).toHaveLength(1);
  expect(screen.getByRole("button", { name: /1984/ })).toBeTruthy();
  expect(screen.queryByText("Page")).toBeNull();
  expect(
    screen.getByRole("button", { name: /Legacy page/ }).textContent,
  ).not.toContain("Books");
  expect(
    screen.getByRole("button", { name: /Legacy page/ }).textContent,
  ).toContain("Link only");
});
