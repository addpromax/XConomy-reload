# Changelog

## [2.26.9]

### Added

- Added PostgreSQL as a supported storage type, including configuration, bundled JDBC driver, and cross-database SQL handling.
- Rewrote `/xconomy migrate` to move complete economy data between SQLite, MySQL, MariaDB, and PostgreSQL. Player balances, non-player accounts, UUID mappings, transaction records, and login data are all migrated, and the target database is written only after every table succeeds.
- Added amount abbreviations for every command that takes an amount: `k`, `m`, and `b` are accepted in either case, so `/pay Notch 10k` and `/money give Notch 1.3m` both work. Controlled by `Settings.amount-abbreviations`.
- Replaced per-field configuration migration with template-based merging. Missing entries in an existing `config.yml` or `database.yml` are now filled in automatically from the bundled templates, and existing values are never overwritten.

### Fixed

- Fixed PostgreSQL failing to start on Paper with `No suitable driver` by setting the driver class explicitly, since the shaded jar filters JDBC service registrations.
- Fixed offline pay tips querying the transaction table while `transaction-record` was disabled.
- Fixed the connection pool state not being reset on reload, and stopped treating remote databases as SQLite when deciding on async access and transaction tracking.
- Fixed `CConfig(URL)` being unable to read templates bundled inside the plugin jar.
- Fixed data import mode being unusable on Paper. The `/xconomy` import command is now registered through the command map instead of relying on a `plugin.yml` declaration.
- Import data is now keyed by UUID instead of player name, so renames and duplicate names in offline mode no longer read the wrong balance. Existing name-keyed `data.yml` files are still read.
- Fixed imported balances being unreadable for names containing a dot, and skipped profiles without a valid UUID instead of writing a `null` entry.
- Import now reports how many entries were imported, skipped, and failed, and refuses to start a second import while one is running.

### Importing balances from another economy plugin

Import mode reads balances through Vault from the economy plugin currently
providing the service, and stores them in `XConomy/importdata/data.yml`.
Vault and a working economy provider are required.

1. Back up the `XConomy` plugin folder, and the database if it is stored elsewhere.
2. Set `Importdata-mode: true` in `XConomy/config.yml`.
3. Restart the server. XConomy loads in import mode only and does not take over
   the economy. The log should show `Convertion mode enable`.
4. Run `/xconomy` as an operator. The import reports how many entries were
   imported, skipped, and failed.
5. Check `XConomy/importdata/data.yml` against the source plugin.
6. Set `Importdata-mode: false` and restart. XConomy now takes over the economy.

Balances are not written to the database in bulk. Each player's balance is
restored from `data.yml` when their account is created on first join, so keep
the file until every player has logged in at least once. Only players present in
the server's offline player data are exported; players who never joined this
server are not included.

If the log shows `Conversion mode enable error`, Vault could not find a
registered economy provider, and no data was exported.

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
