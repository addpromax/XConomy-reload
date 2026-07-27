# Changelog

## [2.26.8]

- Added automatic migration for missing `Transaction-Tracking` settings in existing `config.yml` files.
- Fixed transaction tracking showing `null` when tracking is unavailable; the status message now uses the configured language file.
- Improved legacy transaction display so missing tracking metadata is shown as unknown instead of a literal `null` value.
- Updated Modrinth checks to select the matching platform artifact only: Bukkit, Paper-family, Sponge 7, or Sponge 8.
- Migrated Sponge update checks from Ore to Modrinth and direct platform-specific download files.
- Log update-check results during startup and send administrators a clickable in-game link to the matching download file.
- Updated the release workflow to build with Java 21, which is required by the bundled Adventure libraries.

Use `[beta]` in the release commit message to publish a Modrinth beta and a GitHub prerelease.
Use `[release]` to publish a Modrinth release and a normal GitHub release.
