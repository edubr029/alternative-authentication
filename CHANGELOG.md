# Changelog

All notable changes to this project will be documented in this file.

---

## [1.4.0] - 2026-10-06

- Updated to support Minecraft 26.3.
- Bumped Fabric Loader to 0.19.5 and Fabric API to 0.162.0+26.3.
- Fixed a crash when the config file is empty or contains invalid JSON, it now logs a clear error instead.
- Fixed an unhandled exception when a provider has a malformed `checkUrl` or `profilesUrl`, the provider is now skipped with an error message.
- Fixed whitelist lookups skipping providers without a `profileUrl` instead of producing a broken request.
- Batch profile lookups now consult every provider for each page instead of stopping at the first non-empty response, so names that only exist on fallback providers are no longer missed.
- Increased the batch lookup page size from 2 to 10 names per request.
- Skin properties now fall back to the session response when a provider's texture endpoint fails or returns no properties.
- The default Ely.by `propertyUrl` now uses HTTPS.
- Moved server-side mixins to the `server` mixin section.
- Internal refactors and improvements.

---

## [1.3.2] - 2026-06-22

- Added `DedicatedServerMixin` to override `enforceSecureProfile` to `false`, fixing chat being disabled for all players when `ChatSessionUpdateMixin` is active.
- Bumped to 1.3.2.

---

## [1.3.1] - 2026-06-20

- Added `ChatSessionUpdateMixin` to prevent players with third-party authentication from being kicked for invalid profile key signatures.
- Bumped to 1.3.1.

---

## [1.3.0] - 2026-06-20

- Updated to support Minecraft 26.2 ("Chaos Cubed").
- Bumped Fabric Loader to 0.19.3, Fabric API to 0.152.2+26.2, Fabric Loom to 1.17-SNAPSHOT.
- Bumped Java compile target from 21 to 25.
- Bumped Gradle wrapper from 9.4.1 to 9.5.1.
- Fixed `fabric.mod.json` dependency version constraints.
- Added new `preventFallbackIfPlayerExists` option.
- Added config version system and reworked config related stuff.
- Added more detailed debug logs.
- Internal refactors and improvements.

--- 

## [1.2.0] - 2025-12-29

- Updated to support 1.21.5-1.21.11.
- Fixed whitelist functionality that was broken since 1.21.5.
- Fixed checking authentication functionality that was broken since 1.21.9.
- Internal refactors and performance improvements.
- Bumped to 1.2.0

---

## [1.1.1] - 2025-02-19

- `property_url` functionality has been returned since its absence broke skins for Ely.by players.
- Bumped to 1.1.1

---

## [1.1.0] - 2025-02-19

- Backported to support 1.14-1.21.4
- Internal refactors and performance improvements
- Bumped to 1.1.0

---
 
## [1.0.0] - 2024-09-26

- Initial release.