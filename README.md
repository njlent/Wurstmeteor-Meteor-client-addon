# Wurst for Meteor Client Addon

Meteor addon that ports selected Wurst features to Fabric + Meteor Client for Minecraft `1.21.10`.

## Included Modules

- `AntiSpam`
- `ArrowDMG`
- `AutoFarm`
- `AutoLibrarian`
- `BarrierESP`
- `PotionSaver`
- `CreativeFlight`
- `InvWalk`
- `Trajectories`
- `HealthTags`
- `FeedAura`
- `MaceDMG`
- `NewChunks`
- `MultiAura`
- `BonemealAura`
- `Criticals`
- `ItemESP`

## Stack

- Minecraft: `1.21.10`
- Yarn: `1.21.10+build.3`
- Fabric Loader: `0.18.4`
- Meteor Client: `1.21.10-SNAPSHOT`
- Java: `21`

## Build

```bash
./gradlew build
```

Output jar:

- `build/libs/wurst-meteor-addon-<version>.jar`

## Dev Notes

- Addon category: `Wurst`
- Package root: `de.njlent.wurstmeteor`
- Module source split:
  - `modules/combat`
  - `modules/world`
  - `modules/render`

The implementation is structured for easy extension: each module is isolated and uses Meteor-native events/utilities.
