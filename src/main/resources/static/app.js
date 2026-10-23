(() => {
  "use strict";

  const $ = (sel, root = document) => root.querySelector(sel);
  const audio = $("#audio");
  const view = $("#view");

  // ---------- Server API ----------
  // The catalog, likes, playlists and recently played songs live in the Spring Boot backend.
  async function api(method, path, body) {
    const res = await fetch(`/api${path}`, {
      method,
      headers: body === undefined ? {} : { "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw Object.assign(new Error(err.detail || res.statusText), { status: res.status });
    }
    return res.status === 204 ? null : res.json();
  }
  const failed = (err) => toast(err.message || "Something went wrong — is the server running?");

  let ALBUMS = [];
  let TRACKS = [];
  let GENRES = [];

  // ---------- Persistence ----------
  // Only the volume is a per-browser setting; everything else is saved on the server.
  const STORE_KEY = "nageshspotify:v2";
  function load() {
    try {
      return JSON.parse(localStorage.getItem(STORE_KEY)) || {};
    } catch {
      return {};
    }
  }
  const saved = load();
  const state = {
    liked: new Set(),
    playlists: [],
    recent: [],
    volume: saved.volume ?? 0.7,
    queue: [],
    index: -1,
    shuffle: false,
    repeat: "off", // off | all | one
    history: [],
    future: [],
    route: null,
  };
  function persist() {
    try {
      localStorage.setItem(
        STORE_KEY,
        JSON.stringify({ volume: state.volume })
      );
    } catch {
      /* storage unavailable: keep working in memory */
    }
  }

  // ---------- Helpers ----------
  const trackById = (id) => TRACKS.find((t) => t.id === id);
  const albumById = (id) => ALBUMS.find((a) => a.id === id);
  const playlistById = (id) => state.playlists.find((p) => p.id === id);
  const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
  const fmt = (sec) => {
    if (!isFinite(sec)) return "0:00";
    const m = Math.floor(sec / 60);
    return `${m}:${String(Math.floor(sec % 60)).padStart(2, "0")}`;
  };
  const initials = (name) => name.split(/\s+/).map((w) => w[0]).join("").slice(0, 2).toUpperCase();
  const songs = (n) => `${n} ${n === 1 ? "song" : "songs"}`;
  const gradient = ([a, b]) => `linear-gradient(135deg, ${a}, ${b})`;
  const coverStyle = (colors) => `background:${gradient(colors)}`;
  const colorsForTrack = (t) => albumById(t.album).colors;
  const coverFor = (item) => {
    if (item.kind === "liked") return `<div class="cover" style="background:linear-gradient(135deg,#450af5,#c4efd9)">♥</div>`;
    return `<div class="cover" style="${coverStyle(item.colors)}">${esc(initials(item.title || item.name))}</div>`;
  };
  const ICON_PLAY = `<svg viewBox="0 0 24 24"><path d="M8 5v14l11-7z"/></svg>`;
  const ICON_HEART = `<svg viewBox="0 0 24 24"><path d="M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z"/></svg>`;
  const ICON_PLUS = `<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M12 8v8M8 12h8"/></svg>`;

  let toastTimer;
  function toast(msg) {
    const el = $("#toast");
    el.textContent = msg;
    el.classList.remove("hidden");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => el.classList.add("hidden"), 2200);
  }

  // ---------- Collections ----------
  function collection(kind, id) {
    if (kind === "album") {
      const a = albumById(id);
      return { kind, id, title: a.title, sub: `${a.artist} • ${a.year}`, colors: a.colors, tracks: TRACKS.filter((t) => t.album === id).map((t) => t.id) };
    }
    if (kind === "playlist") {
      const p = playlistById(id);
      return p && { kind, id, title: p.name, sub: p.description || "", colors: p.colors, tracks: p.tracks };
    }
    if (kind === "liked") return { kind, id: "liked", title: "Liked Songs", sub: "Songs you've liked", tracks: [...state.liked] };
    if (kind === "artist") {
      const albums = ALBUMS.filter((a) => a.artist === id);
      return { kind, id, title: id, sub: "Artist", colors: albums[0].colors, tracks: TRACKS.filter((t) => t.artist === id).map((t) => t.id) };
    }
  }

  // ---------- Routing ----------
  function navigate(route, { push = true } = {}) {
    if (push && state.route) {
      state.history.push(state.route);
      state.future = [];
    }
    state.route = route;
    render();
  }
  $("#back").addEventListener("click", () => {
    if (!state.history.length) return;
    state.future.push(state.route);
    state.route = state.history.pop();
    render();
  });
  $("#forward").addEventListener("click", () => {
    if (!state.future.length) return;
    state.history.push(state.route);
    state.route = state.future.pop();
    render();
  });
  document.querySelectorAll(".nav-item").forEach((b) => b.addEventListener("click", () => navigate({ view: b.dataset.view })));

  function render() {
    const r = state.route;
    document.querySelectorAll(".nav-item").forEach((b) => b.classList.toggle("active", b.dataset.view === r.view));
    $("#search-box").classList.toggle("hidden", r.view !== "search");
    $("#back").disabled = !state.history.length;
    $("#forward").disabled = !state.future.length;
    if (r.view === "home") renderHome();
    else if (r.view === "search") renderSearch();
    else if (r.view === "library") renderLibrary();
    else if (r.view === "collection") renderCollection(r.kind, r.id);
    renderSidebar();
    $("#main").scrollTop = 0;
  }

  // ---------- Views ----------
  function greeting() {
    const h = new Date().getHours();
    return h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening";
  }

  function card(kind, id, title, sub, colors) {
    return `<div class="card" data-open="${kind}:${esc(id)}">
      ${coverFor({ kind, title, colors })}
      <button class="hover-play" data-play="${kind}:${esc(id)}" aria-label="Play ${esc(title)}">${ICON_PLAY}</button>
      <div class="c-title">${esc(title)}</div>
      <div class="c-sub">${esc(sub)}</div>
    </div>`;
  }

  function renderHome() {
    const quick = [
      { kind: "liked", id: "liked", title: "Liked Songs" },
      ...state.playlists.slice(0, 3).map((p) => ({ kind: "playlist", id: p.id, title: p.name, colors: p.colors })),
      ...ALBUMS.slice(0, 2).map((a) => ({ kind: "album", id: a.id, title: a.title, colors: a.colors })),
    ];
    const recentTracks = state.recent.map(trackById).filter(Boolean);
    const artists = [...new Set(ALBUMS.map((a) => a.artist))];
    view.innerHTML = `
      <h1 class="greeting">${greeting()}, Nagesh</h1>
      <div class="quick-grid">
        ${quick
          .map(
            (q) => `<div class="quick" data-open="${q.kind}:${esc(q.id)}">${coverFor(q)}<span>${esc(q.title)}</span>
              <button class="hover-play" data-play="${q.kind}:${esc(q.id)}" aria-label="Play">${ICON_PLAY}</button></div>`
          )
          .join("")}
      </div>
      ${
        recentTracks.length
          ? `<h2 class="row-title">Recently played</h2>${trackTable(recentTracks, { context: "recent" })}`
          : ""
      }
      <h2 class="row-title">Made for Nagesh</h2>
      <div class="card-row">${state.playlists.map((p) => card("playlist", p.id, p.name, p.description || songs(p.tracks.length), p.colors)).join("")}</div>
      <h2 class="row-title">Popular albums</h2>
      <div class="card-row">${ALBUMS.map((a) => card("album", a.id, a.title, `${a.year} • ${a.artist}`, a.colors)).join("")}</div>
      <h2 class="row-title">Popular artists</h2>
      <div class="card-row">${artists
        .map((name) => card("artist", name, name, "Artist", ALBUMS.find((a) => a.artist === name).colors))
        .join("")}</div>`;
  }

  let searchSeq = 0;
  async function renderSearch() {
    const input = $("#search-input");
    const q = input.value.trim();
    const seq = ++searchSeq;
    input.focus();
    if (!q) {
      view.innerHTML = `<h2 class="row-title">Browse all</h2>
        <div class="genre-grid">${GENRES.map((g) => `<div class="genre" data-genre="${esc(g.name)}" style="background:${g.color}">${esc(g.name)}</div>`).join("")}</div>`;
    } else {
      let results;
      try {
        results = await api("GET", `/search?q=${encodeURIComponent(q)}`);
      } catch (err) {
        return failed(err);
      }
      // Ignore stale responses: the user kept typing or left the search page
      if (seq !== searchSeq || state.route.view !== "search") return;
      const { tracks, albums, playlists } = results;
      view.innerHTML = `
        <h2 class="row-title">Songs</h2>
        ${tracks.length ? trackTable(tracks, { context: "search" }) : `<div class="empty">No songs found for “${esc(q)}”.</div>`}
        ${albums.length ? `<h2 class="row-title">Albums</h2><div class="card-row">${albums.map((a) => card("album", a.id, a.title, a.artist, a.colors)).join("")}</div>` : ""}
        ${playlists.length ? `<h2 class="row-title">Playlists</h2><div class="card-row">${playlists.map((p) => card("playlist", p.id, p.name, p.description, p.colors)).join("")}</div>` : ""}`;
    }
  }
  let searchTimer;
  $("#search-input").addEventListener("input", () => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => state.route.view === "search" && renderSearch(), 120);
  });

  function renderLibrary() {
    const filter = state.route.filter || "all";
    const items = [];
    if (filter !== "albums") {
      items.push(card("liked", "liked", "Liked Songs", `Playlist • ${songs(state.liked.size)}`));
      state.playlists.forEach((p) => items.push(card("playlist", p.id, p.name, `Playlist • ${songs(p.tracks.length)}`, p.colors)));
    }
    if (filter !== "playlists") ALBUMS.forEach((a) => items.push(card("album", a.id, a.title, `Album • ${a.artist}`, a.colors)));
    view.innerHTML = `
      <h1 class="greeting">Your Library</h1>
      <div class="filters">
        ${["all", "playlists", "albums"].map((f) => `<button class="chip ${f === filter ? "active" : ""}" data-filter="${f}">${f[0].toUpperCase() + f.slice(1)}</button>`).join("")}
      </div>
      <div class="card-row">${items.join("")}</div>`;
  }

  function trackTable(tracks, { context, collectionId } = {}) {
    const current = state.queue[state.index];
    return `<table class="tracklist">
      <thead><tr><th>#</th><th>Title</th><th class="col-album">Album</th><th></th></tr></thead>
      <tbody>${tracks
        .map((t, i) => {
          const album = albumById(t.album);
          const liked = state.liked.has(t.id);
          return `<tr class="row ${current === t.id ? "playing" : ""}" data-track="${t.id}" data-index="${i}">
            <td class="idx">${i + 1}</td>
            <td><div class="t-main"><div class="cover" style="${coverStyle(album.colors)}">${esc(initials(t.title))}</div>
              <div><div class="t-title">${esc(t.title)}</div><div>${esc(t.artist)}</div></div></div></td>
            <td class="col-album">${esc(album.title)}</td>
            <td>
              <button class="icon-btn add-btn" data-add="${t.id}" aria-label="Add to playlist">${ICON_PLUS}</button>
              <button class="icon-btn like-btn ${liked ? "on" : ""}" data-like="${t.id}" aria-label="${liked ? "Unlike" : "Like"}">${ICON_HEART}</button>
              ${context === "playlist" ? `<button class="icon-btn" data-remove="${t.id}" data-from="${esc(collectionId)}" aria-label="Remove from playlist"><svg viewBox="0 0 24 24"><path d="M6 6l12 12M18 6 6 18"/></svg></button>` : ""}
              <span class="dur" data-dur="${t.id}">${durations[t.id] ? fmt(durations[t.id]) : "--:--"}</span>
            </td>
          </tr>`;
        })
        .join("")}</tbody>
    </table>`;
  }

  function renderCollection(kind, id) {
    const c = collection(kind, id);
    if (!c) return navigate({ view: "library" }, { push: false });
    const tracks = c.tracks.map(trackById).filter(Boolean);
    const headColor = kind === "liked" ? "#450af5" : c.colors[0];
    const kindLabel = { album: "Album", playlist: "Playlist", liked: "Playlist", artist: "Artist" }[kind];
    view.innerHTML = `
      <div class="collection-head" style="background:linear-gradient(to bottom, ${headColor}, transparent)">
        ${coverFor({ kind, title: c.title, colors: c.colors })}
        <div>
          <div class="kind">${kindLabel}</div>
          <h1>${esc(c.title)}</h1>
          <div class="meta">${c.sub ? esc(c.sub) + " • " : ""}<b>${songs(tracks.length)}</b></div>
        </div>
      </div>
      <div class="action-bar">
        <button class="big-play" data-play="${kind}:${esc(id)}" aria-label="Play">${ICON_PLAY}</button>
        <button class="icon-btn ${state.shuffle ? "on" : ""}" data-shuffle-play="${kind}:${esc(id)}" aria-label="Shuffle play"><svg viewBox="0 0 24 24"><path d="M3 7h3c4 0 6 10 10 10h4M3 17h3c1.6 0 2.8-1.6 3.9-3.6M14 8.6C14.9 7.6 15.8 7 17 7h3M18 4l3 3-3 3M18 14l3 3-3 3"/></svg></button>
        ${kind === "playlist" ? `<button class="text-btn" data-rename="${esc(id)}">Rename</button><button class="text-btn" data-delete="${esc(id)}">Delete</button>` : ""}
      </div>
      ${tracks.length ? trackTable(tracks, { context: kind, collectionId: id }) : `<div class="empty">${kind === "liked" ? "Songs you like will appear here. Tap the heart on any song." : "This playlist is empty. Use the ⊕ button on any song to add it here."}</div>`}`;
  }

  function renderSidebar() {
    const items = [{ kind: "liked", id: "liked", title: "Liked Songs", sub: songs(state.liked.size) }, ...state.playlists.map((p) => ({ kind: "playlist", id: p.id, title: p.name, sub: songs(p.tracks.length), colors: p.colors }))];
    $("#sidebar-playlists").innerHTML = items
      .map(
        (it) => `<li data-open="${it.kind}:${esc(it.id)}">
          <div class="mini-cover" style="${it.kind === "liked" ? "background:linear-gradient(135deg,#450af5,#c4efd9)" : coverStyle(it.colors)}"></div>
          <div><div class="pl-name">${esc(it.title)}</div><div class="pl-sub">Playlist • ${esc(it.sub)}</div></div></li>`
      )
      .join("");
  }

  // ---------- Durations (loaded lazily from metadata) ----------
  const durations = {};
  function probeDurations() {
    TRACKS.filter((t) => !durations[t.id]).forEach((t) => {
      const a = new Audio();
      a.preload = "metadata";
      a.src = t.src;
      a.addEventListener("loadedmetadata", () => {
        durations[t.id] = a.duration;
        document.querySelectorAll(`[data-dur="${t.id}"]`).forEach((el) => (el.textContent = fmt(a.duration)));
        a.src = "";
      }, { once: true });
    });
  }

  // ---------- Playback ----------
  function playCollection(kind, id, { startId, shuffle } = {}) {
    const c = collection(kind, id);
    if (!c || !c.tracks.length) return toast("Nothing to play here yet");
    let ids = [...c.tracks];
    if (shuffle) state.shuffle = true;
    if (state.shuffle) {
      ids = shuffleArr(ids);
      if (startId) ids = [startId, ...ids.filter((x) => x !== startId)];
    }
    state.queue = ids;
    state.index = startId ? ids.indexOf(startId) : 0;
    loadAndPlay();
    syncButtons();
  }
  function playList(ids, startIndex) {
    state.queue = [...ids];
    state.index = startIndex;
    loadAndPlay();
  }
  function shuffleArr(arr) {
    const a = [...arr];
    for (let i = a.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [a[i], a[j]] = [a[j], a[i]];
    }
    return a;
  }
  function loadAndPlay() {
    const t = trackById(state.queue[state.index]);
    if (!t) return;
    audio.src = t.src;
    audio.play().catch(() => toast("Couldn't start playback — check your connection"));
    state.recent = [t.id, ...state.recent.filter((x) => x !== t.id)].slice(0, 6);
    api("POST", "/recent", { trackId: t.id })
      .then((recent) => (state.recent = recent))
      .catch(() => {
        /* recently played is best-effort: keep playing even if the server can't be reached */
      });
    updateNowPlaying();
  }
  function togglePlay() {
    if (state.index < 0) return playCollection("playlist", state.playlists[0]?.id || "p-daily");
    audio.paused ? audio.play() : audio.pause();
  }
  function next(auto = false) {
    if (!state.queue.length) return;
    if (auto && state.repeat === "one") {
      audio.currentTime = 0;
      return audio.play();
    }
    if (state.index < state.queue.length - 1) state.index++;
    else if (state.repeat === "all" || !auto) state.index = 0;
    else return audio.pause();
    loadAndPlay();
  }
  function prev() {
    if (!state.queue.length) return;
    if (audio.currentTime > 3) {
      audio.currentTime = 0;
      return;
    }
    state.index = state.index > 0 ? state.index - 1 : state.queue.length - 1;
    loadAndPlay();
  }

  function updateNowPlaying() {
    const t = trackById(state.queue[state.index]);
    if (!t) return;
    $("#np-title").textContent = t.title;
    $("#np-artist").textContent = t.artist;
    $("#np-cover").style.background = gradient(colorsForTrack(t));
    $("#np-like").classList.toggle("on", state.liked.has(t.id));
    document.title = `${t.title} • ${t.artist} — NageshSpotify`;
    document.querySelectorAll(".tracklist tr.row").forEach((r) => r.classList.toggle("playing", r.dataset.track === t.id));
    if ("mediaSession" in navigator) {
      navigator.mediaSession.metadata = new MediaMetadata({ title: t.title, artist: t.artist, album: albumById(t.album).title });
    }
    renderQueue();
  }

  function syncButtons() {
    $("#shuffle").classList.toggle("on", state.shuffle);
    const rep = $("#repeat");
    rep.classList.toggle("on", state.repeat !== "off");
    rep.classList.toggle("one", state.repeat === "one");
  }

  function setRangePct(el) {
    el.style.setProperty("--pct", `${((el.value - el.min) / (el.max - el.min)) * 100}%`);
  }

  // Audio events
  audio.addEventListener("play", () => $("#play").classList.add("playing"));
  audio.addEventListener("pause", () => $("#play").classList.remove("playing"));
  audio.addEventListener("ended", () => next(true));
  audio.addEventListener("loadedmetadata", () => ($("#dur-time").textContent = fmt(audio.duration)));
  let seeking = false;
  audio.addEventListener("timeupdate", () => {
    if (seeking || !audio.duration) return;
    const seek = $("#seek");
    seek.value = (audio.currentTime / audio.duration) * 1000;
    setRangePct(seek);
    $("#cur-time").textContent = fmt(audio.currentTime);
  });

  // Player controls
  $("#play").addEventListener("click", togglePlay);
  $("#next").addEventListener("click", () => next());
  $("#prev").addEventListener("click", prev);
  $("#shuffle").addEventListener("click", () => {
    state.shuffle = !state.shuffle;
    if (state.queue.length) {
      const cur = state.queue[state.index];
      const rest = state.queue.filter((_, i) => i !== state.index);
      state.queue = [cur, ...(state.shuffle ? shuffleArr(rest) : rest)];
      state.index = 0;
      renderQueue();
    }
    syncButtons();
  });
  $("#repeat").addEventListener("click", () => {
    state.repeat = { off: "all", all: "one", one: "off" }[state.repeat];
    syncButtons();
  });
  const seek = $("#seek");
  seek.addEventListener("input", () => {
    seeking = true;
    setRangePct(seek);
    if (audio.duration) $("#cur-time").textContent = fmt((seek.value / 1000) * audio.duration);
  });
  seek.addEventListener("change", () => {
    if (audio.duration) audio.currentTime = (seek.value / 1000) * audio.duration;
    seeking = false;
  });
  const vol = $("#volume");
  let lastVolume = state.volume || 0.7;
  function setVolume(v) {
    state.volume = v;
    audio.volume = v;
    vol.value = Math.round(v * 100);
    setRangePct(vol);
    $("#mute").classList.toggle("on", v === 0);
    persist();
  }
  vol.addEventListener("input", () => setVolume(vol.value / 100));
  $("#mute").addEventListener("click", () => {
    if (state.volume > 0) {
      lastVolume = state.volume;
      setVolume(0);
    } else setVolume(lastVolume || 0.7);
  });
  $("#np-like").addEventListener("click", () => {
    const id = state.queue[state.index];
    if (id) toggleLike(id);
  });

  // Queue panel
  $("#queue-btn").addEventListener("click", () => {
    $("#queue-panel").classList.toggle("hidden");
    $("#queue-btn").classList.toggle("on");
    renderQueue();
  });
  $("#queue-close").addEventListener("click", () => {
    $("#queue-panel").classList.add("hidden");
    $("#queue-btn").classList.remove("on");
  });
  function renderQueue() {
    const list = $("#queue-list");
    if (!state.queue.length) {
      list.innerHTML = `<div class="empty">Your queue is empty.</div>`;
      return;
    }
    const item = (id, i) => {
      const t = trackById(id);
      return `<div class="q-item ${i === state.index ? "current" : ""}" data-qi="${i}">
        <div class="cover" style="${coverStyle(colorsForTrack(t))}">${esc(initials(t.title))}</div>
        <div><div class="q-title">${esc(t.title)}</div><div class="q-artist">${esc(t.artist)}</div></div></div>`;
    };
    const upcoming = state.queue.slice(state.index + 1);
    list.innerHTML = `<div class="queue-label">Now playing</div>${item(state.queue[state.index], state.index)}
      <div class="queue-label">Next up</div>${upcoming.length ? upcoming.map((id, k) => item(id, state.index + 1 + k)).join("") : `<div class="empty">Nothing queued next.</div>`}`;
  }
  $("#queue-list").addEventListener("click", (e) => {
    const el = e.target.closest("[data-qi]");
    if (!el) return;
    state.index = Number(el.dataset.qi);
    loadAndPlay();
  });

  // ---------- Library actions ----------
  async function toggleLike(id) {
    const wasLiked = state.liked.has(id);
    try {
      state.liked = new Set(await api(wasLiked ? "DELETE" : "PUT", `/liked/${encodeURIComponent(id)}`));
    } catch (err) {
      return failed(err);
    }
    toast(wasLiked ? "Removed from Liked Songs" : "Added to Liked Songs");
    document.querySelectorAll(`[data-like="${id}"]`).forEach((b) => b.classList.toggle("on", state.liked.has(id)));
    if (state.queue[state.index] === id) $("#np-like").classList.toggle("on", state.liked.has(id));
    if (state.route.view === "collection" && state.route.kind === "liked") renderCollection("liked", "liked");
    renderSidebar();
  }

  async function createPlaylist(withTrack) {
    const name = prompt("Playlist name", `My Playlist #${state.playlists.length + 1}`);
    if (!name || !name.trim()) return;
    let p;
    try {
      p = await api("POST", "/playlists", { name: name.trim(), trackId: withTrack });
    } catch (err) {
      return failed(err);
    }
    state.playlists.push(p);
    toast(`Created “${p.name}”`);
    if (!withTrack) navigate({ view: "collection", kind: "playlist", id: p.id });
    else renderSidebar();
  }
  $("#new-playlist").addEventListener("click", () => createPlaylist());

  // Swap in the server's copy of a playlist after a change
  function replacePlaylist(updated) {
    state.playlists = state.playlists.map((p) => (p.id === updated.id ? updated : p));
    return updated;
  }

  async function addToPlaylist(pid, tid) {
    try {
      const p = replacePlaylist(await api("POST", `/playlists/${encodeURIComponent(pid)}/tracks`, { trackId: tid }));
      renderSidebar();
      toast(`Added to “${p.name}”`);
    } catch (err) {
      failed(err);
    }
  }

  function closeMenu() {
    document.querySelector(".menu")?.remove();
  }
  function openAddMenu(tid, anchor) {
    closeMenu();
    const menu = document.createElement("div");
    menu.className = "menu";
    menu.innerHTML = `<div class="menu-label">Add to playlist</div>
      <button data-menu-new>+ New playlist</button>
      ${state.playlists.map((p) => `<button data-menu-pl="${esc(p.id)}">${esc(p.name)}</button>`).join("")}`;
    document.body.appendChild(menu);
    const r = anchor.getBoundingClientRect();
    menu.style.top = `${Math.min(r.bottom + 4, window.innerHeight - menu.offsetHeight - 8)}px`;
    menu.style.left = `${Math.max(8, r.right - menu.offsetWidth)}px`;
    menu.addEventListener("click", (e) => {
      const b = e.target.closest("button");
      if (!b) return;
      if (b.hasAttribute("data-menu-new")) createPlaylist(tid);
      else addToPlaylist(b.dataset.menuPl, tid);
      closeMenu();
    });
  }

  // ---------- Delegated clicks ----------
  document.addEventListener("click", (e) => {
    if (!e.target.closest(".menu") && !e.target.closest("[data-add]")) closeMenu();
    const t = e.target;
    const play = t.closest("[data-play]");
    if (play) {
      e.stopPropagation();
      const [kind, id] = play.dataset.play.split(/:(.*)/s);
      return playCollection(kind, id);
    }
    const sp = t.closest("[data-shuffle-play]");
    if (sp) {
      const [kind, id] = sp.dataset.shufflePlay.split(/:(.*)/s);
      return playCollection(kind, id, { shuffle: true });
    }
    const like = t.closest("[data-like]");
    if (like) return toggleLike(like.dataset.like);
    const add = t.closest("[data-add]");
    if (add) return openAddMenu(add.dataset.add, add);
    const rm = t.closest("[data-remove]");
    if (rm) {
      const { from, remove } = rm.dataset;
      api("DELETE", `/playlists/${encodeURIComponent(from)}/tracks/${encodeURIComponent(remove)}`)
        .then((p) => {
          replacePlaylist(p);
          toast(`Removed from “${p.name}”`);
          render();
        })
        .catch(failed);
      return;
    }
    const ren = t.closest("[data-rename]");
    if (ren) {
      const p = playlistById(ren.dataset.rename);
      const name = prompt("Rename playlist", p.name);
      if (name && name.trim()) {
        api("PATCH", `/playlists/${encodeURIComponent(p.id)}`, { name: name.trim() })
          .then((updated) => {
            replacePlaylist(updated);
            render();
          })
          .catch(failed);
      }
      return;
    }
    const del = t.closest("[data-delete]");
    if (del) {
      const p = playlistById(del.dataset.delete);
      if (confirm(`Delete “${p.name}”?`)) {
        api("DELETE", `/playlists/${encodeURIComponent(p.id)}`)
          .then(() => {
            state.playlists = state.playlists.filter((x) => x.id !== p.id);
            toast("Playlist deleted");
            navigate({ view: "library" });
          })
          .catch(failed);
      }
      return;
    }
    const row = t.closest("tr.row[data-track]");
    if (row) {
      const r = state.route;
      if (r.view === "collection") return playCollection(r.kind, r.id, { startId: row.dataset.track });
      const ids = [...row.closest("tbody").querySelectorAll("tr.row")].map((x) => x.dataset.track);
      return playList(ids, Number(row.dataset.index));
    }
    const open = t.closest("[data-open]");
    if (open) {
      const [kind, id] = open.dataset.open.split(/:(.*)/s);
      return navigate({ view: "collection", kind, id });
    }
    const genre = t.closest("[data-genre]");
    if (genre) {
      const name = genre.dataset.genre;
      api("GET", `/genres/${encodeURIComponent(name)}/mix`)
        .then((pick) => {
          playList(pick, 0);
          toast(`Playing a ${name} mix`);
        })
        .catch(failed);
      return;
    }
    const chip = t.closest("[data-filter]");
    if (chip) navigate({ view: "library", filter: chip.dataset.filter }, { push: false });
  });

  // Keyboard shortcuts
  document.addEventListener("keydown", (e) => {
    if (e.target.matches("input[type=search], input[type=text]")) return;
    if (e.code === "Space") {
      e.preventDefault();
      togglePlay();
    } else if (e.code === "ArrowRight" && e.shiftKey) next();
    else if (e.code === "ArrowLeft" && e.shiftKey) prev();
  });

  if ("mediaSession" in navigator) {
    navigator.mediaSession.setActionHandler("play", () => audio.play());
    navigator.mediaSession.setActionHandler("pause", () => audio.pause());
    navigator.mediaSession.setActionHandler("nexttrack", () => next());
    navigator.mediaSession.setActionHandler("previoustrack", prev);
  }

  // ---------- Boot ----------
  setVolume(state.volume);
  setRangePct(seek);
  syncButtons();
  Promise.all([api("GET", "/catalog"), api("GET", "/library")])
    .then(([catalog, library]) => {
      ({ albums: ALBUMS, tracks: TRACKS, genres: GENRES } = catalog);
      state.liked = new Set(library.liked);
      state.playlists = library.playlists;
      state.recent = library.recent;
      navigate({ view: "home" }, { push: false });
      probeDurations();
    })
    .catch(() => {
      view.innerHTML = `<div class="empty">Couldn't reach the NageshSpotify server. Start it with <code>./mvnw spring-boot:run</code> and reload.</div>`;
    });
})();
