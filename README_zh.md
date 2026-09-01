![img.png](img.png)

[English](README.md) | [中文](README_zh.md)

# XConomy Reload

基础经济插件。以 Vault 为前置，为 BossShop、QuickShop 等使用 Vault API 的插件提供经济支持。
Sponge 版本使用 Sponge 内置的经济 API。

原作者因学业原因停止更新，本项目为继续维护的改版。

## 特性

- 支持 SQLite、MySQL、MariaDB、PostgreSQL 四种存储方式
- 玩家收入与支出查询，可追溯资金流向
- 支持 BungeeCord / Velocity 子服数据同步，也可通过 Redis 同步
- 内置缓存，减少数据库压力
- 完整支持 Folia
- 现代化 MiniMessage 消息格式
- 所有金额输入支持缩写：`10k`、`1.3k`、`1m`
- 配置文件自动补齐新增字段，升级不需要重新生成
- 支持跨数据库迁移经济数据
- 持续维护，支持最新版本

## 运行需求

| 项目   | 要求                                                       |
| ---- | -------------------------------------------------------- |
| 服务端  | Paper 1.20+ / Spigot 1.13+ / Sponge 7 / Sponge 8 / Folia |
| Java | Paper 版需要 Java 21，Spigot 版需要 Java 8 及以上                  |
| 前置插件 | Vault（Bukkit 系必需，Sponge 版不需要）                            |
| 数据库  | 可选。默认使用 SQLite，无需额外部署                                    |

使用连接池需要服务端提供 `slf4j`，并且 Java 11 及以上。
MariaDB 在部分平台需要额外安装 `DatabaseDrivers`。

## 命令

玩家命令：

| 命令                                      | 说明        |
| --------------------------------------- | --------- |
| `/money`                                | 查看自己的余额   |
| `/money look <玩家>`                      | 查看其他玩家余额  |
| `/pay <玩家> <金额>`                        | 向玩家转账     |
| `/balancetop [页码]`                      | 查看余额排行榜   |
| `/paytoggle [玩家]`                       | 开关是否接收转账  |
| `/xconomy track <income\|expense> [页码]` | 查看自己的收支记录 |

管理命令：

| 命令                                                      | 说明           |
| ------------------------------------------------------- | ------------ |
| `/money give <玩家> <金额> [-s] [-q] [-r <原因>]`             | 增加余额         |
| `/money take <玩家> <金额> [-s] [-q] [-r <原因>]`             | 扣除余额         |
| `/money set <玩家> <金额> [-s] [-q] [-r <原因>]`              | 设置余额         |
| `/money give * <all\|online> <金额>`                      | 批量增加余额       |
| `/money take * <all\|online> <金额>`                      | 批量扣除余额       |
| `/money set * <all\|online> <金额>`                       | 批量设置余额       |
| `/balancetop <hide\|display> <玩家>`                      | 管理玩家在排行榜的显示  |
| `/paypermission set <玩家\|*> <true\|false>`              | 设置转账权限       |
| `/paypermission remove <玩家>`                            | 重置转账权限       |
| `/xconomy track <玩家> <income\|expense> [页码]`            | 查看指定玩家收支记录   |
| `/xconomy track cleanup [天数]`                           | 清理过期交易记录     |
| `/xconomy reload`                                       | 重载配置         |
| `/xconomy deldata <玩家>`                                 | 删除玩家经济数据     |
| `/xconomy migrate <SQLite\|MySQL\|MariaDB\|PostgreSQL>` | 迁移经济数据到其他数据库 |

命令参数说明：`-s` 不通知目标玩家，`-q` 不向执行者回显，`-r <原因>` 记录操作原因。

别名：`/xc` 对应 `/xconomy`，`/bal` 对应 `/balance`，`/baltop` 对应 `/balancetop`。
开启 `eco-command` 后额外注册 `/economy`、`/eco`、`/ebalancetop`、`/ebaltop`、`/eeconomy`。

金额支持缩写后缀 `k`、`m`、`b`，大小写均可，例如 `/pay Notch 10k` 等同于 `10000`。
该功能由 `Settings.amount-abbreviations` 控制。

## 权限

| 权限节点                          | 默认  | 说明                  |
| ----------------------------- | --- | ------------------- |
| `xconomy.user.balance`        | 所有人 | 查看自己余额              |
| `xconomy.user.balance.other`  | 所有人 | 查看他人余额              |
| `xconomy.user.pay`            | 所有人 | 使用转账                |
| `xconomy.user.pay.receive`    | 所有人 | 接收转账                |
| `xconomy.user.balancetop`     | 所有人 | 查看排行榜               |
| `xconomy.user.paytoggle`      | 所有人 | 开关自己的转账接收           |
| `xconomy.admin.give`          | OP  | 增加余额                |
| `xconomy.admin.take`          | OP  | 扣除余额                |
| `xconomy.admin.set`           | OP  | 设置余额                |
| `xconomy.admin.balancetop`    | OP  | 管理排行榜显示             |
| `xconomy.admin.paytoggle`     | OP  | 管理他人转账接收            |
| `xconomy.admin.permission`    | OP  | 管理转账权限              |
| `xconomy.admin.hidden`        | OP  | 拥有该权限的玩家登录后自动从排行榜隐藏 |
| `xconomy.admin.track.other`   | OP  | 查看他人收支记录            |
| `xconomy.admin.track.cleanup` | OP  | 清理交易记录              |
| `xconomy.admin.op`            | OP  | 登录时接收插件更新提示         |

`/xconomy reload`、`deldata`、`migrate` 仅限 OP 使用，不提供单独的权限节点。

## 变量

需要安装 PlaceholderAPI。

| 变量                                     | 说明                 |
| -------------------------------------- | ------------------ |
| `%xconomy_balance%`                    | 余额，按显示格式           |
| `%xconomy_balance_value%`              | 余额，原始数值            |
| `%xconomy_balance_formatted%`          | 余额，使用缩写格式          |
| `%xconomy_top_player_<名次>%`            | 排行榜指定名次的玩家名        |
| `%xconomy_top_balance_<名次>%`           | 排行榜指定名次的余额         |
| `%xconomy_top_balance_value_<名次>%`     | 同上，原始数值            |
| `%xconomy_top_balance_formatted_<名次>%` | 同上，缩写格式            |
| `%xconomy_top_rank%`                   | 自己的排名              |
| `%xconomy_top_rank_<玩家>%`              | 指定玩家的排名            |
| `%xconomy_top_hidden%`                 | 是否已从排行榜隐藏，1 为是     |
| `%xconomy_sum_balance%`                | 服务器总余额             |
| `%xconomy_sum_balance_value%`          | 服务器总余额，原始数值        |
| `%xconomy_paytoggle%`                  | 是否接收转账，1 为是        |
| `%xconomy_paypermission%`              | 转账权限，1、0 或 DEFAULT |
| `%xconomy_paypermission_global%`       | 全局转账开关，1 为开启       |

# 

## 下载

仅在以下网站发布：

Modrinth: https://modrinth.com/plugin/xconomy-reload

GitHub: https://github.com/ShiratamacoMc/XConomy-reload/releases

Minebbs: https://www.minebbs.com/resources/xconomy-reload-bc-spigot-sponge.17420/

Spigot: https://www.spigotmc.org/resources/xconomy_reload.137358/

Klpbbs: https://klpbbs.com/thread-172323-1-1.html
