<p align="center">
  <img src="docs/banner.png" alt="Trajectory Projected" width="100%">
</p>

<p align="center">
  <a href="https://github.com/WilyFoxM/TrajectoryProjected/actions/workflows/build.yml"><img src="https://img.shields.io/github/actions/workflow/status/WilyFoxM/TrajectoryProjected/build.yml?branch=main&label=build&labelColor=2a2b2a" alt="Build"></a>
  <img src="https://img.shields.io/badge/Minecraft-26.2-ef6351?labelColor=2a2b2a" alt="Minecraft 26.2">
  <img src="https://img.shields.io/badge/loader-Fabric-ef6351?labelColor=2a2b2a" alt="Fabric">
  <img src="https://img.shields.io/badge/Java-25-ef6351?labelColor=2a2b2a" alt="Java 25">
  <img src="https://img.shields.io/badge/license-MIT-ef6351?labelColor=2a2b2a" alt="MIT">
</p>

# Trajectory Projected

**See where it lands, and how far the spread can carry it.**

Trajectory Projected is a client-side mod for Minecraft **26.2 / Fabric** that
shows where your bow, crossbow, trident or thrown item will land. The flight is
simulated tick by tick in the order vanilla uses, from the same position and
rotation the server will fire with, so the line follows the real arrow rather
than an approximation of it.

Every shot also carries the server's random spread, which no line can show. Around
the hit, a spread zone falls on the blocks like a shadow and marks where the
projectile can actually land.

Nothing is released yet; the first version is in preparation.

## What it shows

**Projectiles**

- **Bow** while drawn, at the draw it would fire with.
- **Crossbow** when charged: arrows, Multishot volleys with every arrow on its own
  path, and firework rockets.
- **Trident** while readied. A trident with Riptide, which launches you instead,
  shows nothing.
- **Thrown items**: snowballs, eggs, ender pearls, splash and lingering potions,
  bottles o' enchanting and wind charges.

**The line**

- A dotted path that keeps an even spacing on screen, near and far, and stays
  steady while you move.
- Parts hidden behind blocks are drawn faint and outlined, so the path never
  disappears behind a wall.
- In first person the line starts beside the view, from the hand that throws, and
  bends into the flight. Crossbow lines start at the eyes.
- A rocket's lifetime is random: its line fades out after the earliest moment it
  can explode.

**The target**

- A hit marker on the surface the projectile will meet.
- On a mob or a player the marker becomes a cross, the line pulses gently, and the
  crosshair turns into a cross.
- **Spread zone**: a soft shade with a thin rim on the blocks around the hit. It
  wraps over steps and walls, leaves out what other blocks shield from the
  projectile, and its inner ring marks where about half of the shots land.

**Honest by design**

- Only what the client already knows is shown. The mod never aims, shoots or picks
  targets for you.
- Where the outcome is unknown, it shows less rather than a confident wrong line:
  the path stops at chunks that are not loaded, and Multishot volleys whose spread
  depends on conditions only the server can check show only the arrow that flies
  straight regardless.

## Getting started

Install **Fabric Loader 0.19.3+**, **Fabric API 0.161.0+26.2** and **Java 25**,
then place the Trajectory Projected JAR in your Minecraft instance's `mods` folder.
Nothing needs to be installed on the server.

Press **F7** in-game to open settings. A key to turn trajectories on and off can be
set under **Options → Controls → Key Binds → Trajectory Projected**.

Settings are grouped into General, Line and Target: the lead-in from the side, dot
size and spacing, dimming far away, the path behind blocks, the spread zone, the
hit marker and the cues on living targets each have their own switch. Search works
across all pages; hover a setting for details. Changes save automatically. English
and Russian translations are included.

## Compatibility

Built for Minecraft **26.2** with Fabric. I play with **Sodium** (0.9.2 tested).

With **Iris** (1.11.4 tested) and a shader pack active, the line, markers and spread
zone are drawn over the pack's finished image, so they look the same as without
shaders and stay sharp; they are not lit or fogged by the pack. Checked with
Complementary Reimagined r5.8.1 and BSL v10.1.8. Without a pack, Iris changes
nothing.

Other rendering mods have not been tested, nor have multiplayer servers, where
latency and plugins can change what the server fires.

## Accuracy

The simulation follows vanilla's own code: the launch, the order of operations in
each tick, water, entity hitboxes and the random spread. Unit tests compare it with
vanilla's formulas, and in singleplayer the launch matches the projectile the server
creates, with every test shot landing inside its spread zone.

Accuracy has not been measured in a field test yet, and multiplayer is untested.
Results will be published here with the data once they exist.

## License

[MIT](LICENSE)
