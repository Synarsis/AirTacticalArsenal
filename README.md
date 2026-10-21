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
- [Effects and light](#effects-and-light)
- [Commands](#commands)
- [Config](#config)
- [Project layout](#project-layout)
- [Mod compat](#mod-compat)
- [License](#license)
- [Credits](#credits)

## About

Adds long-range strike systems and recon UAVs to Minecraft 1.21.1. Shaheds are
launched from a terminal and the Iskander-M from its own launcher vehicle,
against coordinates; both fly across loaded chunks and explode on arrival. The recon drone has its own camera view with a night vision
shader. Drones can be shot down, and where you hit one decides what it does
next.

## Features

### Strike

- **Shahed-136** — kamikaze UAV, two attack profiles (low dive and high dive)
- **Iskander-M on the 9P78-1 launcher** — a drivable vehicle with two missiles
  under roof covers. You drive it, open the covers, load, prepare, aim from a
  console in the cab and fire one missile or a salvo of two
- chunk-aware flight: chunks along the flight path stay loaded
- launch and impact effects: smoke, dust, flash, shock wave, camera shake and
  real light on the world (see [Effects and light](#effects-and-light))

### Recon

- **Orlan-10** — recon drone with launch pad, control tablet and camera view
- night vision shader for the camera view
- waypoint route planning with terrain preview

### Control and detection

- **Shahed Control Terminal** — pick coordinates, see cost, launch
- **Air Defense Radar** — tactical map with zoom, pan and target markers
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

1. Place a Shahed Control Terminal.
2. Open it, type X and Z, set target height.
3. Make sure you have enough coins, then hit LAUNCH.

### Iskander-M (9P78-1 launcher vehicle)

Craft and place the **Iskander-M launcher** (the item `ata:iskander_tel`) on flat
ground, then get in.

- **Driving.** Move with the usual movement keys, Space is the handbrake, Ctrl
  the afterburner. It has a driver and a commander seat. It cannot work in water
  or on a steep slope.
- **Weight.** The vehicle is driven by the Jolt physics engine: 42 tonnes, a slow
  pull-away (about 9 s to 60 km/h), a top speed near 65 km/h, the body nods when
  it brakes and leans in turns. It climbs one-block steps and hills, stops at a
  two-block wall, rolls off a steep slope when left without a driver, is pushed
  by explosions, and can tip over: right click on its side with an iron block
  while sneaking to put it back on its wheels. Set `vehiclePhysics = "LEGACY"`
  in the server config to use the older built-in driving instead; it is also
  used when the native library cannot be loaded.
- **Console (`G`).** The fire-control panel opens from the cab. Top to bottom:
  - **Covers.** Open the roof covers; missiles can only be loaded while the
    covers are open. Load an Iskander missile into each bay from your inventory.
  - **Prepare.** Raise one missile, or both at once ("prepare both", from the
    travel position). Raising takes a few seconds and the machine puts its jacks
    down.
  - **Checks.** Before a launch the vehicle checks its surroundings: standing
    still, on the ground, not in water, level enough, room for the covers and
    for the missile to stand up, a clear sky above the launch, and a valid
    target. Each check shows ✔ or ✖ and the tooltip says which block is in the
    way.
  - **Targets per missile.** Each missile has its own X and Z. Targets can be
    copied from missile 1 to missile 2 or taken from the Control Tablet.
  - **Salvo.** Set the interval between the two missiles, 5 to 30 seconds. The
    first leaves after the countdown and the second after the interval.
  - **Fire** (press twice to confirm), **abort** during the countdown, then
    **lower** the missiles and **stow** the machine to drive off.
- **Headlights (`H`).** Switch the launcher's headlights and tail lights on and
  off. Everyone nearby sees them.

Missiles can also be spawned directly with `/ata attack iskander`.

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
| `G` | Launcher console (in the cab of the Iskander launcher) |
| `H` | Launcher headlights (in the cab) |

## Effects and light

Launches, flight and impacts have their own effects: smoke that drifts with the
wind, dust rings, flash, shock wave, camera shake. The mod also lights the world
itself, so headlights lay a cone on the grass, a launch lights up the pad and an
impact lights the surroundings, with shadows from blocks and heat haze over
flames. Light is eased so that nothing burns out to white.

Four client settings in `config/ata-client.toml`:

| Setting | Values | What it does |
|---|---|---|
| `fxQuality` | LOW / MEDIUM / HIGH | how many particles are drawn |
| `fxLighting` | OFF / LOW / MEDIUM / HIGH | how many lights, shadows from blocks, contact shadows and haze |
| `fxLightIntensity` | 0.0 – 2.0 | strength of all light |
| `fxHeatHaze` | true / false | heat haze (only with `fxLighting` HIGH) |

With an **Iris / Oculus shader pack** active, the mod's own light is switched
off, because the pack does its own lighting. Particles and everything else work
as usual.

## Commands

Everything lives under `/ata`, with `/shahed` as an alias. All of it needs
permission level 2 — a player without operator rights sees nothing.

```
/ata attack shahed <start> <target> low|high
/ata attack iskander <start> <target>   spawn a missile straight in the air
/ata config                        show current settings
/ata config <setting> ...          change one
/ata map rescan | status
/ata vehicle reload                reread config/ata/vehicles/iskander_tel.json
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

For looking at the effects and the light without firing anything:

```
/ata debug fx <launch|impact|shahed|wreck|flash> [power]   play a scene where you look
/ata debug lights on|off                list of lights, wireframes, pass time
/ata debug lights buffer                show the light buffer instead of the picture (toggle)
/ata debug lights voxels                outline the blocks that cast shadows (toggle)
/ata debug physics on|off              vehicle body, wheels with suspension and load, ground patch
```

The scenes are the very ones the battle plays, only for you, with no damage and
no change to the world.

## Config

- `config/ata-server.toml` — the rules: spawn coordinates, launch cost, block
  damage, whitelist and blacklist, durability, shootdown outcomes. Synced to
  clients; changing it needs operator rights.
- `config/ata/vehicles/iskander_tel.json` — the physics of the launcher: mass, centre of
  mass, body boxes, wheels, suspension, engine, gearbox, brakes, tyres, drag and the
  driving aids. Created with defaults on the first start, reread by `/ata vehicle
  reload` and sent to the players. To make it heavier or lighter change
  `body.massKg` together with `engine.maxTorqueNm`; to change the pull-away
  `gearbox.forward` and `drivetrain.finalDrive`; `brakes.serviceTorqueNm` for the
  stopping distance; `suspension.*` and `body.centreOfMass` for the lean and nod;
  `assist.slopeRelief` for how much hills slow it down. Delete the file to get the
  new defaults after an update. The server config also has `vehiclePhysics`
  (JOLT or LEGACY) and `vehicleGravityScale`.
- `config/ata-client.toml` — your own preferences: hit marker, its sound and
  size, section labels.

Client settings also have a screen: the **Config** button next to the mod in the
mod list, or the settings key.

## Project layout

```
src/main/java/com/synarsis/airtacticalarsenal
├── AirTacticalArsenal.java     entry point, @Mod("ata")
├── AirTacticalArsenalClient.java
├── block/                      Shahed terminal, radar, siren
├── entity/                     Shahed, Iskander, Orlan, paint stand
│   └── tel/                    the 9P78-1 launcher vehicle: state machine, driving, checks
├── item/                       coins, rockets, drones, tablets
├── combat/
│   ├── hitbox/                 oriented boxes traced from the model
│   ├── sweep/                  swept detection for TacZ rounds
│   ├── damage/                 per-section durability and routing
│   ├── outcome/                fall modes, fuze, impact
│   └── effect/                 debris and trails
├── client/
│   ├── gui/                    terminal, radar, route, settings, launcher console
│   ├── fx/                     launch and impact effects, smoke, screen flash
│   │   └── light/              the lighting engine: lights, light pass, block shadows
│   ├── hud/                    hit marker
│   ├── debug/                  hitbox wireframe
│   ├── radar/ shader/ model/ renderer/ sound/
├── vehicle/physics/            Jolt world, ground patch, vehicle body, vehicle settings
├── command/                    /ata and /shahed
├── compat/                     Create, TacZ, Superb Warfare, Explosion Overhaul
├── config/                     ata-server.toml and ata-client.toml specs
├── chunk/                      forced chunk loading for long flights
└── network/                    client-server packets
src/main/resources
├── assets/ata/                 textures, lang, models, shaders, sounds
└── data/ata/                   loot tables, recipes
src/test/java                   unit tests for the geometry, the outcome model, the launcher and the light
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

## Credits

- Iskander-M missile and 9P78-1 launcher — Model by Zinaida
- Vehicle physics — [Jolt Physics](https://github.com/jrouwe/JoltPhysics) by Jorrit Rouwe (MIT) through [jolt-jni](https://github.com/stephengold/jolt-jni) by Stephen Gold (MIT), license texts in `licenses/`
