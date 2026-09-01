![img.png](img.png)

[English](README.md) | [中文](README_zh.md)

# XConomy Reload

A basic economy plugin. It runs on Vault and provides economy support for plugins
that use the Vault API, such as BossShop and QuickShop.
The Sponge version uses Sponge's built-in economy API.

The original author stopped updating due to academic reasons, so this fork
continues the work.

## Features

- Four storage backends: SQLite, MySQL, MariaDB, and PostgreSQL
- Player income and expense history with money flow tracing
- Data synchronisation across BungeeCord / Velocity servers, or through Redis
- Built-in caching to reduce database load
- Full Folia support
- Modern MiniMessage formatting
- Amount abbreviations in every command: `10k`, `1.3k`, `1m`
- New configuration entries are filled in automatically, so upgrades never
  require regenerating your files
- Economy data can be migrated between databases
- Actively maintained and kept up to date with the latest versions

## Requirements

| Item       | Requirement                                                       |
| ---------- | ----------------------------------------------------------------- |
| Server     | Paper 1.20+ / Spigot 1.13+ / Sponge 7 / Sponge 8 / Folia          |
| Java       | Java 21 for the Paper build, Java 8 or later for the Spigot build |
| Dependency | Vault (required on Bukkit-based servers, not needed on Sponge)    |
| Database   | Optional. SQLite is used by default and needs no setup            |

The connection pool requires `slf4j` to be available and Java 11 or later.
MariaDB may require `DatabaseDrivers` on some platforms.

## Commands

Player commands:

| Command                                   | Description                        |
| ----------------------------------------- | ---------------------------------- |
| `/money`                                  | View your own balance              |
| `/money look <player>`                    | View another player's balance      |
| `/pay <player> <amount>`                  | Send money to a player             |
| `/balancetop [page]`                      | View the balance leaderboard       |
| `/paytoggle [player]`                     | Toggle whether you accept payments |
| `/xconomy track <income\|expense> [page]` | View your own transaction history  |

Admin commands:

| Command                                                 | Description                              |
| ------------------------------------------------------- | ---------------------------------------- |
| `/money give <player> <amount> [-s] [-q] [-r <reason>]` | Add money                                |
| `/money take <player> <amount> [-s] [-q] [-r <reason>]` | Remove money                             |
| `/money set <player> <amount> [-s] [-q] [-r <reason>]`  | Set a balance                            |
| `/money give * <all\|online> <amount>`                  | Add money in bulk                        |
| `/money take * <all\|online> <amount>`                  | Remove money in bulk                     |
| `/money set * <all\|online> <amount>`                   | Set balances in bulk                     |
| `/balancetop <hide\|display> <player>`                  | Manage leaderboard visibility            |
| `/paypermission set <player\|*> <true\|false>`          | Set payment permission                   |
| `/paypermission remove <player>`                        | Reset payment permission                 |
| `/xconomy track <player> <income\|expense> [page]`      | View a player's history                  |
| `/xconomy track cleanup [days]`                         | Clean up expired transaction records     |
| `/xconomy reload`                                       | Reload the configuration                 |
| `/xconomy deldata <player>`                             | Delete a player's economy data           |
| `/xconomy migrate <SQLite\|MySQL\|MariaDB\|PostgreSQL>` | Migrate economy data to another database |

Command flags: `-s` skips notifying the target player, `-q` suppresses the reply
to the sender, and `-r <reason>` records a reason for the operation.

Aliases: `/xc` for `/xconomy`, `/bal` for `/balance`, `/baltop` for `/balancetop`.
Enabling `eco-command` also registers `/economy`, `/eco`, `/ebalancetop`,
`/ebaltop`, and `/eeconomy`.

Amounts accept the suffixes `k`, `m`, and `b` in either case, so
`/pay Notch 10k` is the same as `10000`. This is controlled by
`Settings.amount-abbreviations`.

## Permissions

| Node                          | Default  | Description                                                    |
| ----------------------------- | -------- | -------------------------------------------------------------- |
| `xconomy.user.balance`        | everyone | View your own balance                                          |
| `xconomy.user.balance.other`  | everyone | View other balances                                            |
| `xconomy.user.pay`            | everyone | Send payments                                                  |
| `xconomy.user.pay.receive`    | everyone | Receive payments                                               |
| `xconomy.user.balancetop`     | everyone | View the leaderboard                                           |
| `xconomy.user.paytoggle`      | everyone | Toggle your own payment acceptance                             |
| `xconomy.admin.give`          | op       | Add money                                                      |
| `xconomy.admin.take`          | op       | Remove money                                                   |
| `xconomy.admin.set`           | op       | Set balances                                                   |
| `xconomy.admin.balancetop`    | op       | Manage leaderboard visibility                                  |
| `xconomy.admin.paytoggle`     | op       | Manage other players' payment acceptance                       |
| `xconomy.admin.permission`    | op       | Manage payment permissions                                     |
| `xconomy.admin.hidden`        | op       | Players with this node are hidden from the leaderboard on join |
| `xconomy.admin.track.other`   | op       | View other players' transaction history                        |
| `xconomy.admin.track.cleanup` | op       | Clean up transaction records                                   |
| `xconomy.admin.op`            | op       | Receive update notifications on join                           |

`/xconomy reload`, `deldata`, and `migrate` are restricted to operators and have
no separate permission nodes.

## Placeholders

Requires PlaceholderAPI.

| Placeholder                              | Description                                           |
| ---------------------------------------- | ----------------------------------------------------- |
| `%xconomy_balance%`                      | Balance, using the display format                     |
| `%xconomy_balance_value%`                | Balance, raw value                                    |
| `%xconomy_balance_formatted%`            | Balance, using the abbreviated format                 |
| `%xconomy_top_player_<rank>%`            | Player name at the given leaderboard rank             |
| `%xconomy_top_balance_<rank>%`           | Balance at the given leaderboard rank                 |
| `%xconomy_top_balance_value_<rank>%`     | Same, raw value                                       |
| `%xconomy_top_balance_formatted_<rank>%` | Same, abbreviated format                              |
| `%xconomy_top_rank%`                     | Your own rank                                         |
| `%xconomy_top_rank_<player>%`            | A specific player's rank                              |
| `%xconomy_top_hidden%`                   | Whether you are hidden from the leaderboard, 1 if yes |
| `%xconomy_sum_balance%`                  | Server total balance                                  |
| `%xconomy_sum_balance_value%`            | Server total balance, raw value                       |
| `%xconomy_paytoggle%`                    | Whether you accept payments, 1 if yes                 |
| `%xconomy_paypermission%`                | Payment permission: 1, 0, or DEFAULT                  |
| `%xconomy_paypermission_global%`         | Global payment switch, 1 if enabled                   |

## Download

Only published on these websites:

Modrinth: https://modrinth.com/plugin/xconomy-reload

GitHub: https://github.com/ShiratamacoMc/XConomy-reload/releases

Minebbs: https://www.minebbs.com/resources/xconomy-reload-bc-spigot-sponge.17420/

Spigot: https://www.spigotmc.org/resources/xconomy_reload.137358/

Klpbbs: https://klpbbs.com/thread-172323-1-1.html
