# Air Tactical Arsenal

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.20.1-3C8527?style=for-the-badge" alt="Minecraft 1.20.1" />
  <img src="https://img.shields.io/badge/Forge-47%2B-F58220?style=for-the-badge" alt="Forge 47+" />
  <img src="https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 17" />
  <img src="https://img.shields.io/badge/GeckoLib-4.4.9-2D7FF9?style=for-the-badge" alt="GeckoLib 4.4.9" />
</p>

Forge 1.20.1 mod that adds tactical drones, ballistic missiles, an air-defense
radar and alert sirens to Minecraft. Made for the **KrutEvent** server.

modId: `ata`

## Contents

- [About](#about)
- [Features](#features)
- [Requirements](#requirements)
- [Build](#build)
- [How to play](#how-to-play)
- [Project layout](#project-layout)
- [Mod compat](#mod-compat)
- [License](#license)

## About

Adds long-range strike systems and recon UAVs to Minecraft 1.20.1. Strikes
are launched from terminals against coordinates, fly across loaded chunks
and explode on arrival. The recon drone has its own FPV view with a night
vision shader.

## Features

### Strike

- **Shahed-136** — kamikaze UAV, two attack profiles (low dive and high dive)
- **Iskander-M** — ballistic missile, needs a 5x5 launch pad, 7-second
  countdown
- chunk-aware flight: chunks along the flight path stay loaded

### Recon

- **Orlan-10** — recon drone with launch pad, control tablet and FPV camera
- night vision shader for the FPV view
- waypoint route planning with terrain preview

### Control and detection

- **Shahed Control Terminal** — pick coordinates, see cost, launch
- **Iskander Control Terminal** — unified launcher control, can manage
  several pads, shows ETA and status
- **Air Defense Radar** — tactical map with zoom, pan and target markers
- **Launch Interface Cable** — links launcher to terminal, up to 60 blocks
- **Alert Siren** — alarm block with sound

### Other

- **Coin** — currency used to pay for launches
- **Paint Stand** and **Spray Can** — repaint your gear
- `/shahed` command for config and admin launches
- per-player whitelist and blacklist
- GeckoLib animations and custom sounds
- English and Russian translations

## Requirements

- Java 17
- Forge 1.20.1
- Internet for Gradle to grab dependencies

## Build

Build the jar:

```bat
.\gradlew.bat build
```

Output: `build\libs\`.

Run a dev client:

```bat
.\gradlew.bat runClient
```

There is also a small helper script: `build_shahed.bat`.

Other tasks:

```bat
.\gradlew.bat runServer
.\gradlew.bat runData
```

## How to play

### Shahed-136

1. Place a Shahed Control Terminal and a launch pad.
2. Use the Launch Interface Cable: right-click the pad, then right-click
   the terminal to link them.
3. Open the terminal, type X and Z, set target height.
4. Make sure you have enough coins, then hit LAUNCH.

Admin shortcut: `/shahed launch low` or `/shahed launch high`.

### Iskander-M

1. Build a 5x5 launch pad on flat ground with open sky above.
2. Install the Iskander rocket on the pad.
3. Open the Iskander Control Terminal, pick the pad and the target.
4. Confirm — 7 second countdown starts.

### Orlan-10

1. Place the Orlan launch pad and spawn the drone.
2. Open the Orlan Tablet, plan the route.
3. Switch to FPV view. Toggle NVG if you need it.

### Config

- `/shahed config` for live tweaks
- `config/ata-common.toml` for persistent settings (spawn coords,
  launch cost, block damage, whitelist/blacklist)

## Project layout

```
src/main/java/com/synarsis/airtacticalarsenal
├── ShahedMod.java              entry point, @Mod("ata")
├── block/                      blocks: terminals, launcher, radar, siren
│   └── entity/                 block entities
├── entity/                     Shahed, Iskander, Orlan, paint stand
├── item/                       coins, rockets, drones, tablets, cable
├── client/
│   ├── gui/                    terminal, radar, route screens
│   ├── radar/                  terrain map and route preview
│   ├── shader/                 night vision shader
│   ├── model/ renderer/ sound/ rendering and audio
├── command/                    /shahed command
├── compat/                     Create / TacZ / Superb Warfare
├── config/                     ata-common.toml spec
├── chunk/                      forced chunk loading for long flights
└── network/                    client-server packets
src/main/resources
├── assets/ata/                 textures, lang, models, shaders, sounds
└── META-INF/mods.toml          mod metadata
```

## Mod compat

- **Create** — ATA blocks are protected from Create contraption movement
- **TacZ** — optional soft dep, weapon integration (see `libs/README.txt`)
- **Superb Warfare** — optional soft dep

All compat is optional. Mod runs fine on its own.

## License

Custom license — see [LICENSE](LICENSE).

Short version:

- personal use: allowed
- non-commercial servers: allowed
- redistribution and forks: must keep the same license and attribution
- commercial use: needs written permission
- KrutEvent has an extended grant (see section 5 of the license)
- third-party libraries, fonts, sounds and textures stay under their
  upstream licenses
