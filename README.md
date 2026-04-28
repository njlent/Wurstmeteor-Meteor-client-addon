<div align="center">
 <h1> <img src="img/icon.png" width="200px"><br/>Wurstmeteor Addon</h1>

 <img src="https://img.shields.io/badge/Meteor Client Addon-6f1ab1?logo=meteor&logoColor=white"/> 
 <br>
  <img src="https://img.shields.io/badge/minecraft-26.1.2-red"/>
 <img src="https://img.shields.io/badge/minecraft-1.21.11-darkgreen"/>
 <img src="https://img.shields.io/badge/minecraft-1.21.10-darkgreen"/> 
 <img src="https://img.shields.io/badge/minecraft-1.21.5-darkgreen"/> 
 <img src="https://img.shields.io/badge/minecraft-1.21.4-darkgreen"/> 

</div>
<br/>

A [Meteor Client](https://github.com/MeteorDevelopment/meteor-client) addon that ports selected [Wurst Client](https://github.com/Wurst-Imperium/Wurst7) features to Meteor.

## Supported versions (check branches for legacy versions): 
- **Minecraft 26.1.2 ([latest](https://github.com/njlent/Wurstmeteor-Meteor-client-addon/releases))**
- **Minecraft 1.21.11 ([up to 0.1.23](https://github.com/njlent/Wurstmeteor-Meteor-client-addon/releases))**
- **Minecraft 1.21.10 ([up to 0.1.21](https://github.com/njlent/Wurstmeteor-Meteor-client-addon/releases/tag/v0.1.21))**
- **Minecraft 1.21.5 ([legacy v0.1.22](https://github.com/njlent/Wurstmeteor-Meteor-client-addon/releases/tag/v0.1.22))**
- **Minecraft 1.21.4 ([legacy v0.1.22](https://github.com/njlent/Wurstmeteor-Meteor-client-addon/releases/tag/v0.1.22))**

## Included Modules

- `AntiSpam`
- `ArrowDMG`
- `AutoFarm`
- `AutoLibrarian`
- `AutoMine`
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
- `TreeBot`

## Stack

- Minecraft: `26.1.2`
- Fabric Loader: `0.19.2`
- Fabric API: `0.146.1+26.1.2`
- Meteor Client: `26.1.2-SNAPSHOT`
- Loom: `1.16-SNAPSHOT`
- Gradle: `9.4.1`
- Java: `25`

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

<br>
<br>
<br>

> [!IMPORTANT]
> Check out my other Meteor addons:
>
> <table>
>   <tr>
>     <td valign="middle">
>       <img src="https://raw.githubusercontent.com/njlent/Minehop-Meteor-client-addon/refs/heads/main/img/icon.png" width="50" alt="WurstMeteor Addon icon">
>     </td>
>     <td valign="middle">
>       <a href="https://github.com/njlent/Minehop-Meteor-client-addon">Minehop Addon - Source Engine-style bunnyhopping</a>
>     </td>
>   </tr>
> </table>
