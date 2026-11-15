/** Hardware Back respects the same modal and route hierarchy as the web UI. */
export function handleAndroidBack({ canGoBack, dismissDialog, goBack, minimize }) {
  if (dismissDialog()) return;
  if (canGoBack) goBack();
  else minimize();
}
