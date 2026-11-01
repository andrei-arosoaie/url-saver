import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, expect, test, vi } from "vitest";
import { CompletionDateInput } from "./CompletionDateInput";
afterEach(cleanup);
test("provides a native date/time picker without rounding an untouched microsecond timestamp", () => {
  const change = vi.fn();
  render(
    <CompletionDateInput
      label="Completion time (UTC)"
      value="2025-11-01T13:20:52.763155"
      onChange={change}
      disabled={false}
    />,
  );
  const picker = screen.getByLabelText(
    "Completion time (UTC)",
  ) as HTMLInputElement;
  expect(picker.type).toBe("datetime-local");
  expect(picker.value).toBe("2025-11-01T13:20:52.763");
  expect(
    (
      screen.getByLabelText(
        "Exact timestamp for Completion time (UTC)",
      ) as HTMLInputElement
    ).value,
  ).toBe("2025-11-01T13:20:52.763155");
  expect(change).not.toHaveBeenCalled();
  fireEvent.change(picker, { target: { value: "2026-02-03T12:34:56.789" } });
  expect(change).toHaveBeenCalledWith("2026-02-03T12:34:56.789");
});
