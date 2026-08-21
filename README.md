# Air Tactical Arsenal

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.21.1-3C8527?style=for-the-badge" alt="Minecraft 1.21.1" />
  <img src="https://img.shields.io/badge/NeoForge-21.1%2B-F58220?style=for-the-badge" alt="NeoForge 21.1+" />
  <img src="https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21" />
  <img src="https://img.shields.io/badge/GeckoLib-4.7%2B-2D7FF9?style=for-the-badge" alt="GeckoLib 4.7+" />
</p>

NeoForge 1.21.1 mod that adds tactical drones, ballistic missiles, an air-defense
radar and alert sirens to Minecraft.

modId: `ata`

The Forge 1.20.1 version lives on the
[`1.20.1-forge`](https://github.com/Synarsis/AirTacticalArsenal/tree/1.20.1-forge)
branch.

## Contents

- [About](#about)
- [Features](#features)
- [Shooting them down](#shooting-them-down)
- [Requirements](#requirements)
- [Build](#build)
- [How to play](#how-to-play)
- [Commands](#commands)
- [Config](#config)
- [Project layout](#project-layout)
- [Mod compat](#mod-compat)
- [License](#license)

## About

Adds long-range strike systems and recon UAVs to Minecraft 1.21.1. Strikes are
launched from terminals against coordinates, fly across loaded chunks and
explode on arrival. The recon drone has its own camera view with a night vision
shader. Drones can be shot down, and where you hit one decides what it does
next.

## Features

### Strike

- **Shahed-136** — kamikaze UAV, two attack profiles (low dive and high dive)
- **Iskander-M** — ballistic missile, needs a 5x5 launch pad, 7-second
  countdown
- chunk-aware flight: chunks along the flight path stay loaded

### Recon

- **Orlan-10** — recon drone with launch pad, control tablet and camera view
- night vision shader for the camera view
- waypoint route planning with terrain preview

### Control and detection

- **Shahed Control Terminal** — pick coordinates, see cost, launch
- **Iskander Control Terminal** — unified launcher control, can manage several
  pads, shows ETA and status
- **Air Defense Radar** — tactical map with zoom, pan and target markers
- **Launch Interface Cable** — links launcher to terminal, up to 60 blocks
- **Alert Siren** — alarm block with sound

### Other

- **Coin** — currency used to pay for launches
- **Paint Stand** and **Spray Can** — repaint your gear
- per-player whitelist and blacklist
- GeckoLib animations and custom sounds
- English and Russian translations

## Shooting them down

A Shahed is not one hitbox. Its geometry is traced cube by cube from the model,
so a shot resolves to a section of the airframe — and a shot through the gap
between the wing and the fuselage is a clean miss.

Which section you knock out decides what happens:

| Hit | Result |
|---|---|
| Engine or propeller | Glides down on a shallow path, lands well short of its target |
| Wing | Rolls into the missing side and corkscrews down |
| Tail | Loses control authority, comes down end over end |
| Guidance | Keeps flying under power, but walks off its bearing and misses |
| Fuel tank | Flies on burning until the fire reaches something important |
| Warhead | May detonate in the air, or arrive as scrap |

A drone that is brought down is no longer deleted in mid-air: it falls, and the
ground finishes it. What happens there depends on the warhead:

- intact — detonates where it lands
- damaged — a partial blast, or nothing at all
- destroyed — the airframe comes to rest still loaded, and **shooting the wreck
  sets it off**

So "shot down" and "made safe" are different things.

TacZ rounds are picked up by a swept test against the same geometry. Vanilla
projectiles go through the normal damage path. Blasts and fire have no direction
to resolve, so they are spread across the airframe rather than dropped.

## Requirements

- Java 21
- NeoForge 21.1.0 or newer for Minecraft 1.21.1
- GeckoLib 4.7 or newer
- Internet for Gradle to grab dependencies

## Build

Build the jar:

```bat
.\gradlew.bat build
```

Output: `build\libs\`.

Run a dev client or server:

```bat
.\gradlew.bat runClient
.\gradlew.bat runServer
.\gradlew.bat runData
```

Unit tests run as part of `build`, or on their own:

```bat
.\gradlew.bat test
```

## How to play

### Shahed-136

1. Place a Shahed Control Terminal and a launch pad.
2. Use the Launch Interface Cable: right-click the pad, then right-click the
   terminal to link them.
3. Open the terminal, type X and Z, set target height.
4. Make sure you have enough coins, then hit LAUNCH.

### Iskander-M

1. Build a 5x5 launch pad on flat ground with open sky above.
2. Install the Iskander rocket on the pad.
3. Open the Iskander Control Terminal, pick the pad and the target.
4. Confirm — 7 second countdown starts.

### Orlan-10

1. Place the Orlan launch pad and spawn the drone.
2. Open the Orlan Tablet, plan the route.
3. Switch to the camera view. Toggle night vision if you need it.

### Keys

All rebindable under Controls, in the **Air Tactical Arsenal** category.

| Default | Action |
|---|---|
| `K` | Mod settings |
| `Left Alt` | Leave the drone camera |
| `R` | Lock on target |
| `N` | Night vision |

## Commands

Everything lives under `/ata`, with `/shahed` as an alias. All of it needs
permission level 2 — a player without operator rights sees nothing.

```
/ata attack shahed <start> <target> low|high
/ata attack iskander <start> <target>
/ata config                        show current settings
/ata config <setting> ...          change one
/ata map rescan | status
```

### Debug

For testing the shootdown model without hunting for a live drone:

```
/ata debug spawn [distance] [height]   put a drone in the air and hold it there
/ata debug parts                       durability of every section, fuze state
/ata debug break <part>                knock a section out outright
/ata debug damage <part> <amount>      partial damage to one section
/ata debug fuze armed|degraded|dead    set the warhead's condition
/ata debug heal                        repair and hand control back
/ata debug release [range]             let it go on a short flight to a point
/ata debug hitbox on|off               draw the hit geometry as a wireframe
/ata debug nolimits on|off             lift range, zone and firing-arc limits
```

`break`, `damage`, `heal`, `fuze` and `release` act on the drone you are looking
at, resolved against the real geometry, falling back to the nearest one.

## Config

- `config/ata-server.toml` — the rules: spawn coordinates, launch cost, block
  damage, whitelist and blacklist, durability, shootdown outcomes. Synced to
  clients; changing it needs operator rights.
- `config/ata-client.toml` — your own preferences: hit marker, its sound and
  size, section labels.

Client settings also have a screen: the **Config** button next to the mod in the
mod list, or the settings key.

## Project layout

```
src/main/java/com/synarsis/airtacticalarsenal
├── AirTacticalArsenal.java     entry point, @Mod("ata")
├── AirTacticalArsenalClient.java
├── block/                      terminals, launcher, radar, siren
│   └── entity/                 block entities
├── entity/                     Shahed, Iskander, Orlan, paint stand
├── item/                       coins, rockets, drones, tablets, cable
├── combat/
│   ├── hitbox/                 oriented boxes traced from the model
│   ├── sweep/                  swept detection for TacZ rounds
│   ├── damage/                 per-section durability and routing
│   ├── outcome/                fall modes, fuze, impact
│   └── effect/                 debris and trails
├── client/
│   ├── gui/                    terminal, radar, route, settings screens
│   ├── hud/                    hit marker
│   ├── debug/                  hitbox wireframe
│   ├── radar/ shader/ model/ renderer/ sound/
├── command/                    /ata and /shahed
├── compat/                     Create, TacZ, Superb Warfare, Explosion Overhaul
├── config/                     ata-server.toml and ata-client.toml specs
├── chunk/                      forced chunk loading for long flights
└── network/                    client-server packets
src/main/resources
├── assets/ata/                 textures, lang, models, shaders, sounds
└── data/ata/                   loot tables, recipes
src/test/java                   unit tests for the geometry and outcome model
```

## Mod compat

All optional. The mod runs fine on its own.

- **Explosion Overhaul** — when installed, it owns how a blast looks: this mod
  stops drawing its own fireball, raises the explosion once instead of five
  times, and borrows its sparks for hits
- **Create** — ATA blocks are protected from Create contraption movement
- **TacZ** — weapon integration, rounds resolved against the hit geometry
- **Superb Warfare** — target classification for the radar

## License

Custom license — see [LICENSE.txt](LICENSE.txt).

Short version:

- personal use: allowed
- non-commercial servers: allowed
- redistribution and forks: must keep the same license and attribution
- commercial use: needs written permission
- third-party libraries, fonts, sounds and textures stay under their upstream
  licenses
