// Demo catalog. Audio is the freely distributable SoundHelix example set;
// cover art is generated from each album's gradient, so no image assets are needed.
const SONG_URL = (n) => `https://www.soundhelix.com/examples/mp3/SoundHelix-Song-${n}.mp3`;

const ALBUMS = [
  { id: "a1", title: "Midnight Circuits", artist: "Neon Harbor", year: 2024, colors: ["#e8115b", "#3a0ca3"] },
  { id: "a2", title: "Golden Hour Drive", artist: "Saffron Skies", year: 2023, colors: ["#f59b23", "#c0392b"] },
  { id: "a3", title: "Deep Blue Static", artist: "Tidal Echo", year: 2025, colors: ["#1e3264", "#2d9cdb"] },
  { id: "a4", title: "Forest Tapes", artist: "Moss & Pine", year: 2022, colors: ["#148a08", "#0b3d2e"] },
  { id: "a5", title: "Chai at 2AM", artist: "Lo-Fi Nagesh", year: 2025, colors: ["#8d67ab", "#503750"] },
  { id: "a6", title: "Monsoon Signals", artist: "Rain Theory", year: 2024, colors: ["#27856a", "#1d4ed8"] },
];

const TRACKS = [
  { id: "t1", title: "Voltage Bloom", album: "a1", n: 1 },
  { id: "t2", title: "Afterglow Protocol", album: "a1", n: 2 },
  { id: "t3", title: "Sunset Freeway", album: "a2", n: 3 },
  { id: "t4", title: "Copper Clouds", album: "a2", n: 4 },
  { id: "t5", title: "Undertow", album: "a3", n: 5 },
  { id: "t6", title: "Pressure Waves", album: "a3", n: 6 },
  { id: "t7", title: "Canopy", album: "a4", n: 7 },
  { id: "t8", title: "Old Cassette Trail", album: "a4", n: 8 },
  { id: "t9", title: "Steam & Static", album: "a5", n: 9 },
  { id: "t10", title: "Window Seat", album: "a5", n: 10 },
  { id: "t11", title: "First Rain", album: "a6", n: 11 },
  { id: "t12", title: "Petrichor Loop", album: "a6", n: 12 },
  { id: "t13", title: "Night Market", album: "a1", n: 13 },
  { id: "t14", title: "Saffron Road", album: "a2", n: 14 },
  { id: "t15", title: "Lighthouse", album: "a3", n: 15 },
  { id: "t16", title: "Firefly Field", album: "a4", n: 16 },
].map((t) => {
  const album = ALBUMS.find((a) => a.id === t.album);
  return { ...t, artist: album.artist, src: SONG_URL(t.n) };
});

const GENRES = [
  { name: "Electronic", color: "#e8115b" },
  { name: "Chill", color: "#8d67ab" },
  { name: "Indie", color: "#148a08" },
  { name: "Focus", color: "#1e3264" },
  { name: "Workout", color: "#f59b23" },
  { name: "Ambient", color: "#27856a" },
  { name: "Bollywood", color: "#dc148c" },
  { name: "Telugu Hits", color: "#ba5d07" },
];

const DEFAULT_PLAYLISTS = [
  { id: "p-daily", name: "Daily Mix 1", description: "Neon Harbor, Tidal Echo and more", tracks: ["t1", "t5", "t9", "t13", "t15"], colors: ["#509bf5", "#1e3264"] },
  { id: "p-chill", name: "Chill Vibes", description: "Kick back and unwind", tracks: ["t9", "t10", "t7", "t11", "t12"], colors: ["#8d67ab", "#27856a"] },
  { id: "p-drive", name: "Road Trip", description: "Windows down, volume up", tracks: ["t3", "t4", "t14", "t2", "t6"], colors: ["#f59b23", "#e8115b"] },
];
