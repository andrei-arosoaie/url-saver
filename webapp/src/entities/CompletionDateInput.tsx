import { utcInput } from "./model";

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
          value={utcInput(value).slice(0, 23)}
          onChange={(event) => onChange(event.target.value)}
        />
      </label>
      <small>Choose a date and time in UTC.</small>
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
