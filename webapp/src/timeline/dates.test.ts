import { describe, expect, it } from "vitest";
import { exactCaptureTime, timelineClock, timelineDate } from "./dates";
const utc = { locale: "en-GB", timeZone: "UTC" };
describe("timeline calendar labels", () => {
  it("shows Today and Yesterday across a year boundary", () => {
    const now = new Date("2026-01-01T12:00:00Z");
    expect(timelineDate("2026-01-01T00:01:00Z", now, utc)).toBe("Today");
    expect(timelineDate("2025-12-31T23:59:00Z", now, utc)).toBe("Yesterday");
  });
  it("uses the viewer calendar rather than UTC date boundaries", () => {
    const viewer = { locale: "en-GB", timeZone: "America/Los_Angeles" };
    expect(
      timelineDate(
        "2025-12-31T23:30:00Z",
        new Date("2026-01-01T07:00:00Z"),
        viewer,
      ),
    ).toBe("Today");
    expect(timelineClock("2026-01-01T07:00:00Z", viewer)).toBe("23:00");
  });
  it("uses weekdays only for captures two through six calendar days ago", () => {
    const now = new Date("2026-10-25T12:00:00Z");
    expect(timelineDate("2026-10-23T12:00:00Z", now, utc)).toBe("Friday");
    expect(timelineDate("2026-10-19T12:00:00Z", now, utc)).toBe("Monday");
    expect(timelineDate("2026-10-18T12:00:00Z", now, utc)).toBe("18 Oct");
  });
  it("includes the year for older captures outside the viewer current year", () => {
    expect(
      timelineDate(
        "2025-05-12T10:00:00Z",
        new Date("2026-10-25T12:00:00Z"),
        utc,
      ),
    ).toBe("12 May 2025");
  });
  it("handles a 23-hour spring daylight-saving day", () => {
    expect(
      timelineDate("2026-03-08T05:15:00Z", new Date("2026-03-09T04:05:00Z"), {
        ...utc,
        timeZone: "America/New_York",
      }),
    ).toBe("Yesterday");
  });
  it("handles a 25-hour autumn daylight-saving day", () => {
    expect(
      timelineDate("2026-11-01T04:15:00Z", new Date("2026-11-02T05:05:00Z"), {
        ...utc,
        timeZone: "America/New_York",
      }),
    ).toBe("Yesterday");
  });
  it("shows midnight as 00:00 and retains an exact accessible timestamp", () => {
    expect(timelineClock("2026-10-25T00:00:00Z", utc)).toBe("00:00");
    expect(exactCaptureTime("2026-10-25T13:42:00Z", utc)).toContain("13:42:00");
  });
});
