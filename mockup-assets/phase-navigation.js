(() => {
  function initialize() {
    const menu = document.querySelector("header.know-navigation .planning-layers");
    if (!menu) return;
    const groups = [...menu.children].filter((row) => row.classList.contains("planning-group"));
    const phase = (row) => Number([...row.classList].find((name) => /^planning-phase\d+$/.test(name))?.replace("planning-phase", "") || 0);
    groups.sort((a, b) => phase(b) - phase(a)).forEach((row) => menu.append(row));
    menu.setAttribute("tabindex", "0");
    menu.setAttribute("role", "region");
    menu.setAttribute("aria-label", "Phases, newest first. Scroll for earlier phases.");
    function resize() {
      const visible = groups.slice(0, 3);
      const gap = Number.parseFloat(getComputedStyle(menu).rowGap) || 0;
      const height = visible.reduce((sum, row) => sum + row.getBoundingClientRect().height, 0) + Math.max(0, visible.length - 1) * gap;
      if (height > 0) menu.style.setProperty("--phase-menu-height", `${Math.ceil(height)}px`);
    }
    const observer = new ResizeObserver(resize);
    groups.forEach((row) => observer.observe(row));
    observer.observe(menu);
    resize();
    document.fonts?.ready.then(resize);
  }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", initialize, { once: true });
  else initialize();
})();
