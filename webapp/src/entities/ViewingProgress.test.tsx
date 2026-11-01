import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, expect, test } from "vitest";
import { ViewingProgress } from "./ViewingProgress";
afterEach(cleanup);
test("shows remaining released episodes and drops below full progress when another episode airs", () => {
  const { rerender } = render(
    <ViewingProgress title="Show" watchedCount={2} airedCount={2} />,
  );
  expect(screen.getByText("100%")).toBeTruthy();
  expect(screen.getByText("Caught up with all released episodes")).toBeTruthy();
  rerender(<ViewingProgress title="Show" watchedCount={2} airedCount={3} />);
  expect(screen.getByText("66%")).toBeTruthy();
  expect(screen.getByText("1 episode left to catch up")).toBeTruthy();
  expect((screen.getByRole("progressbar") as HTMLProgressElement).max).toBe(3);
});
test("never claims 100 percent while any released episode remains or when no episodes have aired", () => {
  const { rerender } = render(
    <ViewingProgress title="Show" watchedCount={999} airedCount={1000} />,
  );
  expect(screen.getByText("99%")).toBeTruthy();
  rerender(<ViewingProgress title="Show" watchedCount={0} airedCount={0} />);
  expect(screen.getByText("No released episodes yet")).toBeTruthy();
  expect(screen.queryByText("100%")).toBeNull();
});
