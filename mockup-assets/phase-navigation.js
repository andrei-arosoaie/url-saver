(() => {
  function initialize() {
    const menu = document.querySelector("header.know-navigation .planning-layers");
    if (!menu) return;
    if (!menu.querySelector(".planning-phase14")) {
      const row = document.createElement("div");
      row.className = "planning-group planning-phase14";
      row.innerHTML = '<strong>Phase 14 - Share to Know<small>Android links &amp; categories</small></strong><nav aria-label="Phase 14 - Share to Know"><a href="phase-14.html">Specification</a><a href="phase-14.html#architecture">Architecture</a><a href="phase-14.html#implementation">Implementation guide</a><a href="phase-14.html#preview">Share mock</a></nav>';
      if (location.pathname.endsWith("/phase-14.html")) row.querySelector("a").setAttribute("aria-current", "page");
      menu.append(row);
    }
    if (!menu.querySelector(".planning-phase13")) {
      const row = document.createElement("div");
      row.className = "planning-group planning-phase13";
      row.innerHTML = '<strong>Phase 13 - Android application<small>Capacitor - Phone &amp; tablet</small></strong><nav aria-label="Phase 13 - Android application"><a href="phase-13.html">Specification</a><a href="phase-13.html#architecture">Architecture</a><a href="phase-13.html#implementation">Implementation guide</a><a href="phase-13.html#preview">Phone &amp; tablet preview</a></nav>';
      if (location.pathname.endsWith("/phase-13.html")) row.querySelector("a").setAttribute("aria-current", "page");
      menu.append(row);
    }
    if (!menu.querySelector(".planning-phase12")) {
      const row = document.createElement("div");
      row.className = "planning-group planning-phase12";
      row.innerHTML = '<strong>Phase 12 - Stremio progress<small>Movie &amp; TV addon</small></strong><nav aria-label="Phase 12 - Stremio progress"><a href="phase-12.html">Specification</a><a href="phase-12.html#architecture">Architecture</a><a href="phase-12.html#implementation">Implementation guide</a></nav>';
      if (location.pathname.endsWith("/phase-12.html")) row.querySelector("a").setAttribute("aria-current", "page");
      menu.append(row);
    }
    const groups = [...menu.children].filter((row) => row.classList.contains("planning-group"));
    const phase = (row) => Number([...row.classList].find((name) => /^planning-phase\d+$/.test(name))?.replace("planning-phase", "") || 0);
    groups.forEach((row) => {
      const number = phase(row);
      if (number < 1 || number > 14) return;
      const label = document.createElement("span");
      label.className = "phase-delivery-status";
      label.dataset.status = number <= 10 ? "implemented" : "planned";
      label.textContent = number === 13 ? "In progress" : number <= 10 ? "Implemented" : "Planned";
      label.title = number === 10
        ? "Implemented and verified locally; deployment is separate."
        : number === 9
          ? "Authentication is implemented. Latest Android and publishing refinements remain uncommitted; Mozilla signing is pending."
          : number <= 10
            ? "Implementation exists in the repository; this label does not certify current production configuration."
            : number === 14
              ? "Android link sharing with category selection and atomic backend save is planned, not implemented."
            : number === 13
              ? "Capacitor shell started; production authentication and backend integration remain incomplete."
            : number === 12
              ? "Stremio progress addon is designed but not implemented."
              : "Movie catalog integration is specified but not implemented.";
      row.querySelector("strong")?.append(label);
    });
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
