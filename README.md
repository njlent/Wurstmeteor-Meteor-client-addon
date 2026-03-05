<div align="center">
 <h1> <img src="img/icon.png" width="200px"><br/>Wurstmeteor Addon</h1>

 <img src="https://img.shields.io/badge/Meteor Client Addon-6f1ab1?logo=meteor&logoColor=white"/> 
 <br>
 <img src="https://img.shields.io/badge/minecraft-1.21.11-red"/> 
 <img src="https://img.shields.io/badge/minecraft-1.21.10-green"/> 

</div>
<br/>

A [Meteor Client](https://github.com/MeteorDevelopment/meteor-client) addon that ports selected [Wurst Client](https://github.com/Wurst-Imperium/Wurst7) features to Meteor.

## Supported versions: 
- **Minecraft 1.21.10**

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
