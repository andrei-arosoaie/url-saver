import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, expect, test, vi } from "vitest";
import {
  CompletionDateInput,
  localPickerValue,
  localPickerTimestamp,
} from "./CompletionDateInput";
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
  expect(picker.value).toBe(localPickerValue("2025-11-01T13:20:52.763155"));
  expect(
    (
      screen.getByLabelText(
        "Exact timestamp for Completion time (UTC)",
      ) as HTMLInputElement
    ).value,
  ).toBe("2025-11-01T13:20:52.763155");
  expect(change).not.toHaveBeenCalled();
  fireEvent.change(picker, { target: { value: "2026-02-03T12:34:56.789" } });
  expect(change).toHaveBeenCalledWith(
    new Date("2026-02-03T12:34:56.789").toISOString(),
  );
});

test("converts a selected local time to UTC and rejects invalid dates", () => {
  const local = "2026-02-03T12:34:56.789";
  expect(localPickerTimestamp(local)).toBe(
    new Date(2026, 1, 3, 12, 34, 56, 789).toISOString(),
  );
  expect(localPickerValue(localPickerTimestamp(local))).toBe(local);
  expect(localPickerTimestamp("not a date")).toBe("invalid");
  expect(localPickerTimestamp("2026-02-30T12:00")).toBe("invalid");
});
