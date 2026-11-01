(() => {
  const states = new Map();
  const esc = (s) => String(s ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll('"', "&quot;");
  const seededIds = {"tv-severance":9001,"tv-bear":9002,"tv-dark":9003};
  const positionCode = (s) => s.position ? code(episodes.find((e) => e.id === s.position)) : "Not started";
  const providerId = (entity) => entity.tvmazeId ?? seededIds[entity.id] ?? null;
  const episodes = [
    { id: 1, season: 1, number: 1, name: "First light", date: "12 Oct", aired: true },
    { id: 2, season: 1, number: 2, name: "The signal", date: "19 Oct", aired: true },
    { id: 3, season: 1, number: 3, name: "A distant shore", date: "26 Oct", aired: true },
    { id: 4, season: 2, number: 1, name: "Return to orbit", date: "2 Nov", aired: false },
    { id: 5, season: 2, number: 2, name: "Beyond the horizon", date: "Air date unknown", aired: false, unknown: true },
  ];
  const code = (e) => `S${String(e.season).padStart(2, "0")}E${String(e.number).padStart(2, "0")}`;
  function state(entity) {
    if (!states.has(entity.id)) states.set(entity.id, {
      position: entity.id.startsWith("manual-") ? null : entity.status === "Seen" ? 3 : 1,
      watched: new Set(entity.id.startsWith("manual-") ? [] : entity.status === "Seen" ? [1, 2, 3] : [1]),
      season: 1,
      newAired: entity.id === "tv-dark",
      newAvailable: entity.id === "tv-dark",
      completions: entity.finished ? [entity.finished] : [],
      open: false,
    });
    return states.get(entity.id);
  }
  function list(s) { return episodes.map((e) => ({ ...e, aired: e.aired || (e.id === 4 && s.newAired) })); }
  function isIdentified(entity) { return Boolean(entity.tvmazeId) || ["tv-severance", "tv-bear", "tv-dark"].includes(entity.id); }
  function summary(entity) {
    const s = state(entity), aired = list(s).filter((e) => e.aired);
    if (aired.length && aired.every((e) => s.watched.has(e.id))) s.newAvailable = false;
    const current = episodes.find((e) => e.id === s.position);
    if (!current) return `<span class="tv-count">Not started · ${aired.filter((e) => s.watched.has(e.id)).length}/${aired.length}</span>`;
    return `<span class="tv-position-badges" aria-label="Season ${current.season}, episode ${current.number}"><span class="tv-season-badge">S${String(current.season).padStart(2, "0")}</span><span class="tv-episode-badge">E${String(current.number).padStart(2, "0")}</span></span> <span class="tv-count">· ${aired.filter((e) => s.watched.has(e.id)).length}/${aired.length}</span>${s.newAvailable ? '<span class="tv-new-episode-icon tv-new-badge" tabindex="0" aria-label="New episodes available" title="New episodes available">NEW<span class="tv-new-tooltip">New episodes available</span></span>' : ""}`;
  }
  function mount(panel, entity, store, render) {
    const s = state(entity), all = list(s), aired = all.filter((e) => e.aired);
    if (aired.length && aired.every((e) => s.watched.has(e.id))) s.newAvailable = false;
    const edit = panel.querySelector(".detail-form"), linked = panel.querySelector(".entity-linked");
    if (!isIdentified(entity)) {
      const empty=document.createElement("div");empty.className="tv-unmatched";empty.innerHTML="<p>Identify this show to load seasons and episodes.</p><button type=button class=small-action>Find show</button>";
      empty.querySelector("button").onclick=()=>openChange(entity,store,render);panel.insertBefore(empty,edit);document.getElementById("entity-tv-demo").hidden=true;return;
    }
    panel.querySelector("h3")?.remove();
    const wrapper = document.createElement("div");
    wrapper.className = "tv-entity-details";
    const catalog = window.KnowTVSearch?.catalog.find((item) => item.id === providerId(entity));
    const source = catalog?.imdb || entity.imdbId || null;
    wrapper.innerHTML = `<div class="tv-mini-hero"><div class="tv-mini-poster" role="img" aria-label="Illustrative poster"><svg viewBox="0 0 60 80" aria-hidden="true"><path d="M0 0H60V80H0Z" fill="#173759"/><circle cx="42" cy="18" r="8" fill="#b9ddff"/><path d="M0 65 18 23 37 59 60 35V80H0Z" fill="#326d9d"/></svg></div><div><span class="tv-eyebrow">TV SHOW</span><h3>${esc(entity.title)}${entity.alias ? `<span class="tv-title-alias"> · ${esc(entity.alias)}</span>` : ""}</h3><div class="tv-mini-meta">${catalog?.year || entity.providerYear || ""} · Drama · ${esc(catalog?.status || entity.providerStatus || "Running")} · 45 min</div><div class="tv-mini-links"><strong>★ 8.4 <span>Rating</span></strong>${source ? `<a href="https://www.imdb.com/title/${source}/" target="_blank" rel="noopener noreferrer">IMDb ↗</a>` : ""}</div></div></div><div class="tv-personal-line"><span class="status-chip ${entity.status === "Seen" ? "done" : entity.status === "Seeing" ? "progress" : ""}">${esc(entity.status)}</span><span>Added ${esc(entity.created)}</span></div>${s.newAvailable ? '<p class="tv-new-banner">New episodes available</p>' : ""}<details class="tv-mini-synopsis"><summary>Synopsis</summary><p>Illustrative synopsis for this interactive design. Show information will appear here.</p></details><div class="tv-position-line"><div class="tv-position-current"><span class="tv-eyebrow">LAST WATCHED</span><strong>${positionCode(s)}</strong></div><div class="tv-position-selectors"><label class="tv-position-season">Season<select aria-label="Last watched season">${[...new Set(all.map((e) => e.season))].map((n) => `<option value="${n}">${n}</option>`).join("")}</select></label><label class="tv-position-picker">Episode<select aria-label="Last watched episode"></select></label><button type="button" class="tv-icon-button" data-tv-position title="Save last watched position" aria-label="Save last watched position">✓</button></div></div><div class="tv-watch-meter"><span style="width:${aired.filter((e) => s.watched.has(e.id)).length / aired.length * 100}%"></span></div><div class="tv-progress-caption">${aired.filter((e) => s.watched.has(e.id)).length} of ${aired.length} aired episodes watched</div>${aired.every((e) => s.watched.has(e.id)) && entity.status !== "Seen" ? '<button type="button" class="small-action tv-seen-button" data-tv-seen>Mark show seen</button>' : ""}<details class="tv-episode-disclosure"${s.open ? " open" : ""}><summary>Season ${s.season} <span>${all.filter((e) => e.season === s.season && e.aired).length} aired · Episodes</span></summary><div class="tv-season-line"><label for="tv-entity-season">Season</label><select id="tv-entity-season">${[1, 2].map((n) => `<option value="${n}"${n === s.season ? " selected" : ""}>${n}</option>`).join("")}</select></div><div class="tv-mini-episodes">${all.filter((e) => e.season === s.season).map((e) => `<article class="tv-mini-episode"><div class="tv-episode-top"><label><input type="checkbox" data-tv-watched="${e.id}" aria-label="Watched ${code(e)}"${s.watched.has(e.id) ? " checked" : ""}${e.aired || e.unknown ? "" : " disabled"}><strong>${code(e)}</strong></label><span>${e.unknown ? "Air date unknown" : e.aired ? e.date : "Upcoming"} · 45 min</span></div><div class="tv-episode-copy"><svg viewBox="0 0 100 60" role="img" aria-label="Illustrative episode image"><path fill="#234769" d="M0 0H100V60H0Z"/><circle cx="76" cy="16" r="8" fill="#b3d9ef"/><path d="M0 60 27 15 55 49 80 25 100 60" fill="#437b99"/></svg><div><strong>${e.name}</strong><details><summary>Summary</summary><p>Illustrative episode summary, not live catalog information.</p></details></div></div></article>`).join("")}</div></details><p class="tv-next">${all.some((e) => !e.aired && !e.unknown) ? "Next · S02E01 · Return to orbit · 2 Nov" : "No upcoming episode announced"}</p><div class="tv-completion-note">${s.completions.length ? `${s.completions.length} show completion${s.completions.length > 1 ? "s" : ""} recorded` : "No show completions"}</div>`;
    panel.insertBefore(wrapper, edit);
    const editor = document.createElement("details");
    editor.className = "tv-entity-edit";
    editor.innerHTML = "<summary>Edit entity</summary>";
    if (isIdentified(entity)) {
      edit.elements.namedItem("title").readOnly = true;
      const label = document.createElement("label");
      label.innerHTML = `Alias <span class="tv-alias-hint">Optional · shown after the title</span><input name="alias" maxlength="1000" value="${esc(entity.alias || "")}" placeholder="Add an alias">`;
      edit.elements.namedItem("title").closest("label").after(label);
      edit.querySelector(".detail-actions .small").textContent = "Editable alias & status";
    }
    const changeButton = document.createElement("button");
    changeButton.type="button"; changeButton.className="tv-change-show"; changeButton.textContent=isIdentified(entity)?"Change show":"Identify show";
    edit.append(changeButton);
    changeButton.addEventListener("click",()=>openChange(entity,store,render));
    editor.append(edit);
    panel.append(editor);
    if (linked) {
      const sources = document.createElement("details");
      sources.className = "tv-entity-edit";
      sources.innerHTML = "<summary>Linked pages</summary>";
      sources.append(linked); panel.append(sources);
    }
    wrapper.querySelector(".tv-episode-disclosure").addEventListener("toggle", (event) => { s.open = event.target.open; });
    wrapper.querySelector("#tv-entity-season").addEventListener("change", (event) => { s.season = Number(event.target.value); s.open = true; render(); });
    wrapper.querySelectorAll("[data-tv-watched]").forEach((input) => input.addEventListener("change", () => {
      const id = Number(input.dataset.tvWatched);
      if (input.checked) s.watched.add(id); else s.watched.delete(id);
      s.open = true;
      if (input.checked && entity.status === "To be seen") store.update(entity.id, { status: "Seeing" }); else render();
      document.getElementById("entity-notice").textContent = "Episode updated. Saved position unchanged.";
    }));
    const positionSeason = wrapper.querySelector('.tv-position-season select');
    const positionEpisode = wrapper.querySelector('.tv-position-picker select');
    const positionSave = wrapper.querySelector('[data-tv-position]');
    positionSeason.value = String(s.positionSeason ?? episodes.find((e) => e.id === s.position)?.season ?? 1);
    function refreshPositionEpisodes() {
      const seasonNumber = Number(positionSeason.value);
      const choices = all.filter((e) => e.season === seasonNumber && e.aired);
      positionEpisode.innerHTML = choices.length ? choices.map((e) => `<option value="${e.id}"${e.id === s.position ? ' selected' : ''}>${String(e.number).padStart(2,'0')} · ${e.name}</option>`).join('') : '<option value="">No aired episodes</option>';
      positionEpisode.disabled = choices.length === 0;
      positionSave.disabled = choices.length === 0;
    }
    positionSeason.addEventListener('change', () => { s.positionSeason = Number(positionSeason.value); refreshPositionEpisodes(); });
    refreshPositionEpisodes();
    wrapper.querySelector("[data-tv-position]").addEventListener("click", () => {
      const selected = Number(positionEpisode.value);
      if (!selected || positionSave.disabled) return;
      const dialog = document.getElementById("entity-tv-confirm");
      document.getElementById("entity-tv-through-label").textContent = code(episodes.find((e) => e.id === selected));
      const save = (mark) => {
        s.position = selected;
        if (mark) aired.filter((e) => e.id <= selected).forEach((e) => s.watched.add(e.id));
        dialog.close();
        if (entity.status === "To be seen") store.update(entity.id, { status: "Seeing" }); else render();
        document.getElementById("entity-notice").textContent = mark ? "Position and watched episodes saved." : "Position saved. Checkmarks unchanged.";
      };
      document.getElementById("entity-tv-mark-through").onclick = () => save(true);
      document.getElementById("entity-tv-position-only").onclick = () => save(false);
      document.getElementById("entity-tv-cancel").onclick = () => dialog.close();
      dialog.showModal();
    });
    wrapper.querySelector("[data-tv-seen]")?.addEventListener("click", () => {
      s.completions.push(new Date().toISOString()); s.newAvailable = false;
      store.update(entity.id, { status: "Seen", finished: new Date().toISOString().slice(0, 10) });
    });
    const demo = document.getElementById("entity-tv-demo");
    demo.hidden = false;
    demo.onclick = () => {
      s.newAired = true; s.newAvailable = true; s.season = 2; s.open = true;
      if (entity.status === "Seen") store.update(entity.id, { status: "To be seen" }); else render();
    };
  }
  function openChange(entity, store, render) {
    const dialog=document.getElementById('entity-tv-change');
    const input=document.getElementById('tv-change-title');
    const form=document.getElementById('entity-tv-change-form');
    const warning=document.getElementById('tv-change-warning');
    const error=document.getElementById('tv-change-error');
    const confirm=document.getElementById('tv-change-confirm');
    if(!dialog._search)dialog._search=window.KnowTVSearch.attach(input,()=>{warning.hidden=false;confirm.disabled=false;error.textContent='';});
    dialog._search.reset();form.reset();input.value='';confirm.disabled=true;warning.hidden=true;error.textContent='';
    input.oninput=()=>{confirm.disabled=true;warning.hidden=true;};
    document.getElementById('tv-change-cancel').onclick=()=>dialog.close();
    form.onsubmit=(event)=>{
      event.preventDefault();const result=dialog._search.resolve();if(result.ambiguous||!result.item)return;
      const match=result.item;
      if(match.id===providerId(entity)){error.textContent='This is already the selected show.';return;}
      if(store.list().some(e=>e.id!==entity.id&&e.type==='tv'&&providerId(e)===match.id)){error.textContent='This show is already in your list.';return;}
      const previous=state(entity);
      states.set(entity.id,{position:null,watched:new Set(),season:1,newAired:false,newAvailable:false,completions:[...previous.completions],open:false});
      dialog.close();
      store.update(entity.id,{title:match.title,tvmazeId:match.id,providerYear:match.year,providerStatus:match.status,imdbId:match.imdb||null,status:'To be seen'});
      document.getElementById('entity-notice').textContent='Show changed. Progress reset; alias and completion history kept.';
    };
    dialog.showModal();input.focus();
  }
  const year = (entity) => entity.providerYear || window.KnowTVSearch?.catalog.find((item) => item.id === providerId(entity))?.year || null;
  window.KnowTVEntities = { mount, summary, state, isIdentified, providerId, year };
})();
