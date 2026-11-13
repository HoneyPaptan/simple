# One WebSocket per host

Date: 2026-11-04

All requests, results and events between the app and `simpled` travel on one WebSocket per paired host, with a sequence number and a daemon epoch so a reconnect replays only what was missed. Bulk bytes (images, files, fonts, preview) use plain HTTPS routes with the same token.

Why: one TLS handshake per host keeps reconnect under the 500 ms budget, and replay from a sequence number lets the app render from its store instantly.
