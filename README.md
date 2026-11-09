# ZenithProxy

<p align="center">
  <img src="https://img.shields.io/badge/MC-1.21.4-brightgreen.svg" alt="Minecraft"/>
  <img src="https://img.shields.io/endpoint?url=https%3A%2F%2Ftokei.kojix2.net%2Fbadge%2Fgithub%2Frfresh2%2FZenithProxy%2Flines" alt="Lines of Code"/>
</p>

ZenithProxy is a Minecraft bot that players can also log into and control ingame.<br>The bot can be controlled remotely through a Discord bot, a terminal, or ingame.<br>It's designed for use on [2b2t.org](https://www.2b2t.org/) but works on any MC server.

This is a fork by [EvModder](https://github.com/EvModder), adapted for my own personal use. Not an official release.<br>For full usage, docs, license, and attribution details, see the [original README](https://github.com/rfresh2/ZenithProxy#readme).

### [Download latest jar](https://github.com/EvModder/ZenithProxy/releases/download/latest-1.21.4/ZenithProxy.jar)
^ Manual download; this fork does not use the upstream's auto-update protocol.

## Changes in this fork

- **Restart notices:** Initial/final-minute/restart alerts, shared per server/channel across local proxies.
- **Discord presence:** Shared bot descriptions, nonblocking updates, fewer timestamps.
- **Discord notifications:** Account labels, less spam, optional command logging.
- **Periodic clicks:** Allows left/right click intervals above 100 ticks.
- **Autofishing:** Reliable bite detection, two-attempt hooked-loot recovery, clearer warnings.
- **AutoEat:** Uses offhand/hotbar food in place and restores the previous item after eating.
- **Totem disconnect:** Checks pops and inventory updates without the one-second delay.
- **Health disconnect:** Non-priority health logouts pause repeat checks until recovery or fresh damage.
- **Visual range:** Suppresses leave notices caused by our own logout.
- **Spectator inventory:** Live, read-only account inventory, default on (`spectator inventory off` to hide).
- **Spectator cats:** Attached heads, bundled pose updates, tight idle fit; self-cats hidden by default.
- **Death notices:** Combines death location and death message into one notification.
- **Replay notices:** Start/stop notices default off; uploads and failures remain visible.
- **Pearl alerts:** Teleports correlated with pearl damage, enabled while unattended.

To rfresh: Feel free to copy anything here you like, or DM for a PR.
