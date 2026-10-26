import { useEffect, useState } from "react";
export type ThemePreference = "system" | "light" | "dark";
export function useTheme() {
  const [preference, setPreference] = useState<ThemePreference>(() => {
    try {
      const saved = localStorage.getItem("know-theme");
      if (saved === "light" || saved === "dark") return saved;
    } catch {
      /* Storage is optional. */
    }
    return "system";
  });
  const [osDark, setOsDark] = useState(
    () => window.matchMedia?.("(prefers-color-scheme: dark)").matches ?? false,
  );
  const resolved =
    preference === "system" ? (osDark ? "dark" : "light") : preference;
  useEffect(() => {
    if (!window.matchMedia) return;
    const media = window.matchMedia("(prefers-color-scheme: dark)");
    const change = () => setOsDark(media.matches);
    change();
    media.addEventListener("change", change);
    return () => media.removeEventListener("change", change);
  }, []);
  useEffect(() => {
    document.documentElement.dataset.theme = resolved;
    try {
      localStorage.setItem("know-theme", preference);
    } catch {
      /* Theme still works without persistence. */
    }
  }, [preference, resolved]);
  return { preference, setPreference, resolved };
}
