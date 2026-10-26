import { useEffect, useRef } from "react";
import { readerDocument } from "./readerDocument";
export function ArticleBody({
  html,
  theme,
}: {
  html: string;
  theme?: "light" | "dark";
}) {
  const ref = useRef<HTMLIFrameElement>(null);
  useEffect(() => {
    const frame = ref.current;
    if (!frame) return;
    let observer: ResizeObserver | undefined;
    const loaded = () => {
      observer?.disconnect();
      const body = frame.contentDocument?.body;
      if (!body) return;
      body.querySelectorAll("a").forEach((a) => {
        a.target = "_blank";
        a.rel = "noopener noreferrer";
      });
      const resize = () => {
        frame.style.height = `${Math.max(100, body.scrollHeight + 24)}px`;
      };
      resize();
      if (typeof ResizeObserver !== "undefined") {
        observer = new ResizeObserver(resize);
        observer.observe(body);
      }
    };
    frame.addEventListener("load", loaded);
    loaded();
    return () => {
      frame.removeEventListener("load", loaded);
      observer?.disconnect();
    };
  }, [html]);
  const document = readerDocument(html, theme);
  return (
    <iframe
      ref={ref}
      title="Saved article content"
      sandbox="allow-same-origin allow-popups allow-popups-to-escape-sandbox"
      referrerPolicy="no-referrer"
      srcDoc={document}
      style={{ display: "block", width: "100%", border: 0, minHeight: 120 }}
    />
  );
}
