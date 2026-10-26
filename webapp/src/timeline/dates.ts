type DisplayOptions = { locale?: string; timeZone?: string };
function localDay(date: Date, options: DisplayOptions) {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: options.timeZone,
    year: "numeric",
    month: "numeric",
    day: "numeric",
  }).formatToParts(date);
  const number = (type: string) =>
    Number(parts.find((part) => part.type === type)!.value);
  return {
    year: number("year"),
    day: Date.UTC(number("year"), number("month") - 1, number("day")),
  };
}
export function timelineDate(
  value: string,
  now = new Date(),
  options: DisplayOptions = {},
) {
  const date = new Date(value);
  const saved = localDay(date, options);
  const current = localDay(now, options);
  const days = Math.round((current.day - saved.day) / 86_400_000);
  if (days === 0) return "Today";
  if (days === 1) return "Yesterday";
  return new Intl.DateTimeFormat(options.locale, {
    timeZone: options.timeZone,
    ...(days >= 2 && days <= 6
      ? { weekday: "long" as const }
      : {
          day: "numeric" as const,
          month: "short" as const,
          ...(saved.year === current.year ? {} : { year: "numeric" as const }),
        }),
  }).format(date);
}
export function timelineClock(value: string, options: DisplayOptions = {}) {
  return new Intl.DateTimeFormat(options.locale, {
    timeZone: options.timeZone,
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).format(new Date(value));
}
export function exactCaptureTime(value: string, options: DisplayOptions = {}) {
  return new Intl.DateTimeFormat(options.locale, {
    timeZone: options.timeZone,
    dateStyle: "full",
    timeStyle: "long",
  }).format(new Date(value));
}
