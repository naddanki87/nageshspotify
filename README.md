# NageshSpotify

A Spotify-style music player web app built from scratch with plain HTML, CSS and JavaScript. It has no build step and no dependencies.

## Run it

Open `index.html` in a browser, or serve the folder:

```sh
cd nageshspotify
python3 -m http.server 8080   # then visit http://localhost:8080
```

## Features

- **Home**: greeting, quick-access tiles, recently played, playlists, albums and artists
- **Search**: browse-by-genre tiles (each plays a random mix) and live search across songs, albums and playlists
- **Your Library**: filter by playlists or albums
- **Album / playlist / artist / Liked Songs pages**: track list with play, like, add-to-playlist and remove-from-playlist
- **Playlists**: create, rename and delete them, and add songs from any track list
- **Player bar**: play/pause, previous/next, shuffle, repeat (off → all → one), seek, volume and mute, a like button and a queue panel
- **Keyboard**: `Space` play/pause, `Shift+←/→` previous/next. Also works with OS media keys (Media Session API).
- Likes, playlists, recently played and volume are saved in `localStorage`

## Files

| File | Purpose |
| --- | --- |
| `index.html` | App shell: sidebar, main view, player bar, queue panel |
| `styles.css` | Dark theme and responsive layout |
| `data.js` | Demo catalog (albums, tracks, genres, default playlists) |
| `app.js` | Routing, rendering, playback, queue and persistence |

## Content

Audio streams from the freely distributable [SoundHelix](https://www.soundhelix.com/) example MP3s, so playback needs an internet connection. Album art is generated from CSS gradients. All album, artist and track names are fictional. To use your own music, edit `data.js` and point each track's `src` at your files.

This is an independent learning project. It is not affiliated with or endorsed by Spotify.
