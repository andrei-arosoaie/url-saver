export function Brand() {
  return (
    <>
      <svg width="32" height="36" viewBox="0 0 40 44" aria-hidden="true">
        <defs>
          <linearGradient id="know-logo-top" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor="#78bdff" />
            <stop offset="1" stopColor="#2385ff" />
          </linearGradient>
        </defs>
        <path
          fill="url(#know-logo-top)"
          d="M3 7.5 18.2.7Q20-.2 21.8.7L37 7.5Q39 8.5 37 9.5L21.8 17Q20 17.8 18.2 17L3 9.5Q1 8.5 3 7.5Z"
        />
        <path
          fill="#1678ff"
          d="m3 19 5-2.5 10.2 5Q20 22.4 21.8 21.5l10.2-5 5 2.5q2 1 0 2L21.8 28.5q-1.8.9-3.6 0L3 21q-2-1 0-2Z"
        />
        <path
          fill="#0062f4"
          d="m3 30.5 5-2.5 10.2 5q1.8.9 3.6 0L32 28l5 2.5q2 1 0 2L21.8 40q-1.8.9-3.6 0L3 32.5q-2-1 0-2Z"
        />
      </svg>
      <span>Know</span>
    </>
  );
}

export function TimelineIcon() {
  return (
    <svg
      width="20"
      height="24"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M9.7 7.6 14.1 4.9M7.2 10.9 4.7 16M9.8 10.4 15.9 17" />
      <circle cx="8.3" cy="8.2" r="2.7" />
      <circle cx="16.8" cy="3.8" r="2.7" />
      <circle cx="3.5" cy="19" r="2.7" />
      <circle cx="18.1" cy="19.4" r="2.7" />
    </svg>
  );
}
