import { fromUtcInput } from "./model";

export function localPickerValue(value: string) {
  if (!value) return "";
  try {
    const date = new Date(fromUtcInput(value));
    return new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
      .toISOString()
      .slice(0, 23);
  } catch {
    return "";
  }
}
export function localPickerTimestamp(value: string) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "invalid";
  // Reject nonexistent local times during daylight-saving transitions.
  if (localPickerValue(date.toISOString()).slice(0, value.length) !== value)
    return "invalid";
  return date.toISOString();
}
export function CompletionDateInput({
  label,
  value,
  onChange,
  disabled,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  disabled: boolean;
}) {
  return (
    <>
      <label>
        {label}
        <input
          type="datetime-local"
          step="0.001"
          required
          disabled={disabled}
          value={localPickerValue(value)}
          onChange={(event) =>
            onChange(localPickerTimestamp(event.target.value))
          }
        />
      </label>
      <small>Choose a date and time in your local time zone.</small>
      <details>
        <summary>Exact UTC timestamp</summary>
        <label>
          Exact timestamp for {label}
          <input
            type="text"
            placeholder="YYYY-MM-DDTHH:mm:ss.ffffff"
            value={value}
            disabled={disabled}
            onChange={(event) => onChange(event.target.value)}
          />
        </label>
      </details>
    </>
  );
}
