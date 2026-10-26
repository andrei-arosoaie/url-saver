import type { ThemePreference } from "./useTheme";
import styles from "./ThemePicker.module.css";
export function ThemePicker({
  preference,
  onChange,
}: {
  preference: ThemePreference;
  onChange: (value: ThemePreference) => void;
}) {
  return (
    <div className={styles.picker} role="group" aria-label="Theme">
      {(["system", "light", "dark"] as const).map((value) => (
        <button
          key={value}
          type="button"
          aria-label={`${value} theme`}
          title={`${value} theme`}
          aria-pressed={preference === value}
          onClick={() => onChange(value)}
        >
          <svg
            viewBox="0 0 24 24"
            aria-hidden="true"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.6"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            {value === "system" ? (
              <>
                <rect x="3" y="4" width="18" height="12" rx="2" />
                <path d="M8 21h8M12 16v5" />
              </>
            ) : value === "light" ? (
              <>
                <circle cx="12" cy="12" r="4" />
                <path d="M12 2v2M12 20v2M2 12h2M20 12h2M5 5l1.5 1.5M17.5 17.5 19 19M5 19l1.5-1.5M17.5 6.5 19 5" />
              </>
            ) : (
              <path d="M20.5 14A8.5 8.5 0 0 1 10 3.5 8.5 8.5 0 1 0 20.5 14Z" />
            )}
          </svg>
        </button>
      ))}
    </div>
  );
}
