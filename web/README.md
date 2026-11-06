# Mochy Music Web

JavaScript web adaptation for desktop and mobile. This is a functional local-music edition, not feature parity with the Android app.

## Run

`npm ci` then `npm run build`. Serve the `dist` directory over HTTP or HTTPS (for example `python3 -m http.server 8080 --directory dist`). Do not open index.html via file://. HTTPS or localhost is needed for service workers and secure browser APIs.

No API keys or backend are required. The music-metadata parser is bundled in app.js so playback and tag parsing do not depend on a CDN. Original Android project: https://github.com/hunghecker/Mochy-Music . Metadata parsing library: music-metadata (MIT), see package-lock.json and the installed dependency license.

## Implemented

- Responsive desktop sidebar and mobile navigation, library search and sort.
- Local file import and drag/drop; metadata title, artist, album, cover, duration and embedded lyrics (including ID3 native USLT/TXXX fallback).
- HTML audio playback, seeking, volume, previous/next, queue management, shuffle and repeat.
- IndexedDB stores original blobs and metadata on the device. No audio uploads.
- Favorites and playlists: create, rename, delete, add/remove songs; export playlist listing as JSON (listing only, not an audio backup).
- Embedded plain lyrics and LRC timing, manual LRC/TXT import, lyric offset and click to seek.
- 5-band Web Audio EQ, presets, playback speed and sleep timer.
- Media Session controls where the browser supports them.
- Direct HTTPS audio URLs (provider CORS/codec restrictions apply; URL sources are online only).
- Service worker caches the web shell after the first online load; local audio is held separately in IndexedDB.

## Not yet ported

Android provider modules (YouTube, Apple Music and others), online search/download, service accounts, AI recommendations, lyric translations/recognition, video/canvas, Cast, Android plugins and USB passthrough. Those features need separate provider APIs/backends, credentials or native alternatives. This web edition does not expose fake controls for them.

## Browser limits

Codec playback varies by browser; MP3 is the most compatible choice. Browser storage quotas and eviction apply. Clearing site data removes the imported copies. Background/lock-screen playback and app installation depend on the browser and OS; a web page cannot guarantee native Android behavior. Private hosted access may need a network login if the shell has not been cached. AudioContext EQ can be unavailable for cross-origin streams. Import does not modify original files.

## Validation

`npm run check` checks JavaScript syntax. Desktop browser checks performed: import MP3, title/artist/duration, audio playback, USLT/TXXX embedded LRC display, persistence after reload, favorites filtering. Mobile CSS breakpoint is 760px; physical iOS/Android device playback and offline cold starts need device validation.

License: GPL-3.0, matching the source project. music-metadata is MIT licensed.
