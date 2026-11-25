<p align="center">
  <img width="512" height="512" alt="logo" src="https://github.com/user-attachments/assets/f47720a7-27af-416c-8b32-a41211df4588"/>
</p>

<p align="center"><i>GregTech and AE2 integration with ME Assembly Line buses, processing pattern providers and buffers</i></p>
<h1 align="center">
    <a href="https://www.curseforge.com/minecraft/mc-mods/gregified-energistics"><img src="https://img.shields.io/badge/Available%20for-MC%201.12.2%20-informational?style=for-the-badge" alt="Supported Versions"></a>
    <a href="https://github.com/WALHAY/Gregified-Energistics/blob/main/LICENSE.md"><img src="https://img.shields.io/github/license/WALHAY/Gregified-Energistics?style=for-the-badge" alt="License"></a>
    <br>
    <a href="https://www.curseforge.com/minecraft/mc-mods/gregified-energistics"><img src="https://cf.way2muchnoise.eu/1333309.svg?badge_style=for_the_badge" alt="CurseForge"></a>
    <a href="https://modrinth.com/mod/gregified-energistics"><img src="https://img.shields.io/modrinth/dt/gregified-energistics?logo=modrinth&label=&suffix=%20&style=for-the-badge&color=2d2d2d&labelColor=5ca424&logoColor=1c1c1c" alt="Modrinth"></a>
    <a href="https://github.com/WALHAY/Gregified-Energistics/releases"><img src="https://img.shields.io/github/downloads/wALHAY/Gregified-Energistics/total?sort=semver&logo=github&label=&style=for-the-badge&color=2d2d2d&labelColor=545454&logoColor=FFFFFF" alt="GitHub"></a>
</h1>

## Requirements

Minecraft 1.12.2 with Forge and the following mods. These versions were checked together in the development dedicated server:

| Mod | Tested version | Forge mod ID |
| --- | --- | --- |
| GregTech, VoidStudio fork | `2.9.0-voidstudio` | `gregtech` |
| AE2 Extended Life | `v0.56.7` | `appliedenergistics2` |
| AE2 Fluid Crafting, unofficial | `2.7.10-unofficial` | `ae2fc` |
| ModularUI | `3.1.5` | `modularui` |
| MixinBooter | `10.2` | `mixinbooter` |

GT, AE2 and AE2FC are required at the listed versions because their recipe and crafting APIs are used directly. ModularUI requires at least 3.1.5 and MixinBooter at least 10.2. JEI is optional. Use the VoidStudio GregTech fork; the development JAR and sources under `libs/` are the build dependencies. The unofficial AE2FC build is downloaded from [CurseForge file 7742544](https://www.curseforge.com/minecraft/mc-mods/ae2fluidcraft-rework-unofficial/files/7742544) through CurseMaven and deobfuscated by Gradle; install that version on both client and server.

## Machines

| Machine | Tier | Purpose | Acquisition |
| --- | --- | --- | --- |
| ME Assembly Line Bus | LuV | Provides recipes from Data Sticks or encoded AE2 patterns; distributes ingredients across ordered input buses | Assembly Line, scanner research |
| ME Assembly Line Optical Bus | ZPM | Provides Assembly Line recipes from a Data Bank through optical pipes | Assembly Line upgrade, Research Station |
| ME Pattern Buffer | ZPM | Holds 36 processing patterns and their item/fluid inputs for compatible GregTech recipe multiblocks | Assembly Line, Research Station |
| ME Pattern Provider | IV–UHV | Holds 36/45/63/81/99 processing patterns by tier, with a separate unlimited item buffer and ghost circuit per pattern; supports items only | IV in the assembler; higher tiers upgrade the previous provider on the Assembly Line |

The buffer and providers require a multiblock that accepts their import-item ability to act as input buses. The UHV provider recipe uses UV mechanical components and UV recipe voltage, plus a UHV hull and circuits, so it is available with GregTech high-tier mode disabled. Research consumes the supplied machine; research and upgrade recipes require an item without saved NBT.

Connect the optical bus to a Data Bank's optical transmitter through optical pipes. A screwdriver changes the bus's optical side. In Assembly Line buses, sneak-right-click with a Memory Card to copy substitution choices and right-click to paste. Each pattern slot stores a recipe source; the ingredients of that recipe go to input buses in order.

Assembly Line buses include every recipe fluid in the AE2 crafting plan, including recipes from Data Sticks and the optical Data Bank. Fluids appear in the order preview and can be autocrafted through other processing patterns. Encoded AE2FC processing patterns can specify items and fluids together: items go to input buses in item order, fluids to input hatches in fluid order. Each fluid input requires its corresponding hatch to accept the full amount before the bus accepts a craft. The bus uses fluids supplied by the crafting CPU; fluid ordering is always enabled.

Pattern Buffers and Pattern Providers display nine pattern slots per row. Hover over a pattern slot and press **B** to inspect its inputs and configure its ghost circuit. When a pattern is removed or changed, its remaining inputs are returned to ME. If ME cannot accept them, the container stays pending until they can be returned or removed through the GUI. The replacement pattern starts accepting crafts once the old contents are empty. Providers reject patterns with fluid inputs or outputs.

## Persistence and updating

Dismantling a Pattern Buffer keeps its patterns, items and fluids in the dropped machine item. Pattern Providers retain their patterns and items in the same way. Assembly Line buses retain their pending item queue and CPU-supplied fluids in their dropped item. Place the item again to recover its contents. Large item quantities use an integer in saved NBT; the GUI shows ordinary stack sizes and reports the full stored count separately.

When updating to fluid-aware Assembly Line crafting, saved recipe jobs that did not reserve fluids are canceled through AE2 and must be ordered again. Already extracted fluid remainders in buses are retained; old unfulfilled direct-extraction requests are discarded. Ingredients already delivered to the Assembly Line remain in its buses and hatches.

When updating from the older save format, crafting jobs containing only a numeric recipe ID or an unmarked processing pattern with an integrated circuit are canceled through AE2 after CPU initialization. CPU-owned inputs return through AE2; inputs already delivered to machines remain there. New jobs persist recipe identities, selected substitutions and ghost circuit wrappers. Quantities already lost by the old byte-sized item serializer cannot be reconstructed.

## Configuration

The Forge configuration is `run/config/gregifiedenergistics.cfg` in development, or `config/gregifiedenergistics.cfg` in a modpack.

| Setting | Default | Behaviour |
| --- | --- | --- |
| Pattern Handler Size | 36 | Assembly Line Bus pattern slots, 1–4096; requires restart. Occupied saved overflow slots are retained when reducing it |
| Slots per line in pattern grid | 9 | 0 selects up to 9 columns automatically; explicit values are 1–16 |
| Slots per line in substitution grid | 8 | 1–16 columns |

## Building and development

The Gradle wrapper requires JDK 17 or newer; CI uses JDK 25. Gradle provisions the Java 16 compiler and Java 8 runtime toolchains. Compiled mod classes target Java 8.

```sh
./gradlew build
./gradlew spotlessApply  # Explicitly format source when needed
./gradlew runClient
./gradlew runServer
```

`build` includes tests and formatting checks and leaves source files unchanged. Development run files are under `run/`. Server startup requires an accepted Minecraft EULA in that directory. Built JARs are under `build/libs/`.

Dependency versions are declared in the Gradle scripts. The wrapper distribution has its own SHA-256 pin. Generated files and build reports are excluded from Git. Publishing is disabled by default; configured uploads use `MODRINTH_TOKEN` or `CURSEFORGE_TOKEN` and respect `publish_with_changelog`.

## Screenshots

<img width="1728" height="941" alt="2025-08-24_11 31 13" src="https://github.com/user-attachments/assets/803aaff9-087a-4dc3-a56e-86f281c67a13" />

<img width="1728" height="941" alt="2025-08-24_11 46 15" src="https://github.com/user-attachments/assets/ff827ee5-4819-4642-9e00-1785afc79aa9" />
