(() => {
  // Design fixtures only. No provider requests or API credentials.
  const catalog = [
    { id: 9001, title: "Severance", year: 2022, status: "Running", imdb: "tt11280740" },
    { id: 9002, title: "The Bear", year: 2022, status: "Running", imdb: "tt14452776" },
    { id: 9003, title: "Dark", year: 2017, status: "Ended", imdb: "tt5753856" },
    { id: 9004, title: "Dark", year: 2009, status: "Ended" },
    { id: 9005, title: "The Office", year: 2005, status: "Ended" },
    { id: 9006, title: "The Office", year: 2001, status: "Ended" },
    ...Array.from({ length: 10 }, (_, i) => ({ id: 9100 + i, title: `Northstar${i ? `: Chapter ${i + 1}` : ""}`, year: 2024 - i, status: "Running" })),
  ];
  const escape = (value) => String(value).replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll('"', "&quot;");
  function attach(input, onSelect) {
    const host = document.createElement("div");
    host.className = "tv-autocomplete";
    const list = document.createElement("div");
    list.className = "tv-search-results";
    list.id = `${input.id || "tv-title"}-suggestions`;
    list.setAttribute("role", "listbox");
    list.setAttribute("aria-label", "Matching TV shows");
    list.hidden = true;
    input.after(host); host.append(list);
    const hint = document.createElement("span");
    hint.className = "tv-search-hint";
    hint.setAttribute("role", "status");
    host.append(hint);
    input.setAttribute("role", "combobox");
    input.setAttribute("aria-autocomplete", "list");
    input.setAttribute("aria-controls", list.id);
    input.setAttribute("aria-expanded", "false");
    input.autocomplete = "off";
    let timer, generation = 0, matches = [], limit = 8, active = -1, selected = null, enabled = true;
    function close() { list.hidden = true; input.setAttribute("aria-expanded", "false"); input.removeAttribute("aria-activedescendant"); }
    function choose(item) {
      selected = item; input.value = item.title;
      close(); hint.textContent = `Selected · ${item.title} (${item.year})`;
      onSelect?.(item);
    }
    function paint() {
      const visible = matches.slice(0, limit);
      list.innerHTML = visible.map((item, index) => `<button type="button" role="option" id="${list.id}-${index}" aria-selected="${index === active}" data-tv-search-index="${index}"><span class="tv-result-poster" aria-hidden="true">${escape(item.title[0])}</span><span><strong>${escape(item.title)}</strong><small>${item.year} · ${item.status}</small></span><span class="tv-result-arrow" aria-hidden="true">↗</span></button>`).join("");
      if (matches.length > limit) list.insertAdjacentHTML("beforeend", '<button type="button" class="tv-search-more">More matches</button>');
      list.querySelectorAll("[data-tv-search-index]").forEach((button) => {
        button.addEventListener("click", () => choose(visible[Number(button.dataset.tvSearchIndex)]));
      });
      list.querySelector(".tv-search-more")?.addEventListener("click", () => { limit += 8; paint(); });
      list.hidden = matches.length === 0;
      input.setAttribute("aria-expanded", String(matches.length > 0));
      if (active >= 0) input.setAttribute("aria-activedescendant", `${list.id}-${active}`);
    }
    input.addEventListener("input", () => {
      if (!enabled) return;
      selected = null; generation++; const request = generation;
      clearTimeout(timer); close(); active = -1; limit = 8;
      const query = input.value.trim().toLocaleLowerCase();
      if (query.length < 2) { hint.textContent = "Type at least 2 characters"; return; }
      hint.textContent = "Searching…";
      timer = setTimeout(() => {
        if (request !== generation) return;
        matches = catalog.filter((item) => item.title.toLocaleLowerCase().includes(query));
        hint.textContent = matches.length ? `${matches.length} matches · choose the title and year` : "No matches. You can keep this as a manual entry.";
        paint();
      }, 300);
    });
    input.addEventListener("keydown", (event) => {
      if (event.key === "Escape") { close(); return; }
      if (list.hidden) return;
      if (event.key === "ArrowDown" || event.key === "ArrowUp") {
        event.preventDefault();
        const count = Math.min(limit, matches.length);
        active = (active + (event.key === "ArrowDown" ? 1 : -1) + count) % count;
        paint();
      } else if (event.key === "Enter" && active >= 0) { event.preventDefault(); choose(matches[active]); }
    });
    return {
      get selected() { return selected; },
      setEnabled(value) { enabled = value; host.hidden = !value; if (!value) { close(); input.removeAttribute("role"); } else input.setAttribute("role", "combobox"); },
      reset() { clearTimeout(timer); generation++; selected = null; matches = []; hint.textContent = ""; close(); },
      resolve() {
        if (selected && selected.title === input.value) return { item: selected };
        const exact = catalog.filter((item) => item.title.toLocaleLowerCase() === input.value.trim().toLocaleLowerCase());
        if (exact.length === 1) return { item: exact[0] };
        if (exact.length > 1) { hint.textContent = "Several shows share this name. Choose a title and year."; matches = exact; paint(); input.focus(); return { ambiguous: true }; }
        return { item: null };
      },
    };
  }
  window.KnowTVSearch = { attach, catalog };
})();
