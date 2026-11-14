# OreBoost

Mod **Fabric server-side** pour Minecraft Java **26.3** qui multiplie (ou réduit) la fréquence de
génération des minerais **sans changer les couches où ils apparaissent**.

- Diamant et lapis **x2 par défaut**, chaque minerai réglable de `x0` à `x64` dans un fichier JSON.
- Hauteurs, taille des filons, deepslate, exposition à l'air, biomes : **tout reste vanilla**.
  Seul le nombre de tentatives de placement par chunk change.
- **Les clients vanilla se connectent sans rien installer** : aucun bloc, item, registre ou paquet
  réseau ajouté.
- Rechargement à chaud avec `/oreboost reload`.

## Sommaire

- [Installation](#installation)
- [Configuration](#configuration)
- [Commandes](#commandes)
- [Limites](#limites)
- [Résultats mesurés](#résultats-mesurés)
- [Fonctionnement](#fonctionnement)
- [Compiler et tester](#compiler-et-tester)
- [Licence](#licence)

## Installation

Prérequis : Minecraft **26.3**, Fabric Loader **0.19.5+**, [Fabric API](https://modrinth.com/mod/fabric-api), Java 25.

1. Télécharger `oreboost-<version>.jar` depuis la page [Releases](../../releases).
2. Le déposer dans le dossier `mods/` du serveur, à côté de Fabric API.
3. Démarrer le serveur : `config/oreboost.json` est créé avec les valeurs par défaut.

| Où le mod est installé | Effet |
|---|---|
| Serveur dédié | Actif. Les joueurs peuvent être en vanilla. |
| Solo (serveur intégré) | Actif sur les mondes locaux. |
| Client qui rejoint un serveur dédié | Aucun effet. |

> **Seuls les chunks générés après l'installation sont concernés.** Pour en profiter sur un monde
> existant, il faut explorer des zones encore jamais générées.

## Configuration

Fichier : `config/oreboost.json`.

```json
{
  "enabled": true,
  "debug": false,
  "ores": {
    "diamond": {
      "multiplier": 2.0,
      "placed_features": [
        "minecraft:ore_diamond",
        "minecraft:ore_diamond_medium",
        "minecraft:ore_diamond_large",
        "minecraft:ore_diamond_buried"
      ]
    },
    "lapis": {
      "multiplier": 2.0,
      "placed_features": ["minecraft:ore_lapis", "minecraft:ore_lapis_buried"]
    },
    "coal": {
      "multiplier": 1.0,
      "placed_features": ["minecraft:ore_coal_upper", "minecraft:ore_coal_lower"]
    }
  }
}
```

Le fichier généré contient aussi `iron`, `copper`, `gold`, `redstone`, `emerald`, `nether_quartz`,
`nether_gold` et `ancient_debris`, tous à `1.0`.

### Options

| Clé | Défaut | Rôle |
|---|---|---|
| `enabled` | `true` | `false` : le mod ne touche à rien, génération strictement vanilla. |
| `debug` | `false` | Active la commande de mesure `/oreboost count`. |
| `ores.<groupe>.multiplier` | `1.0` | Multiplicateur du groupe, de `0.0` à `64.0`. Hors bornes : valeur ramenée dans l'intervalle, avec un warning. |
| `ores.<groupe>.placed_features` | | IDs des placed features auxquelles le multiplicateur s'applique. |

### Comment lire un multiplicateur

| Valeur | Effet par chunk |
|---|---|
| `4.0` | Quatre passes de placement complètes. |
| `2.5` | Deux passes, plus une troisième une fois sur deux. |
| `1.0` | Vanilla. |
| `0.5` | Une passe une fois sur deux : minerai deux fois plus rare. |
| `0` | Le minerai n'est plus généré. |

### Minerais d'autres mods

Le nom d'un groupe est libre. Pour booster un minerai ajouté par un autre mod ou un datapack, créer
un groupe avec les IDs de ses placed features :

```json
"mon_minerai": {
  "multiplier": 3.0,
  "placed_features": ["autremod:ore_truc", "autremod:ore_truc_deep"]
}
```

### En cas d'erreur

- **ID inconnu** : warning dans les logs, l'ID est ignoré et listé par `/oreboost status`. Pas de crash.
- **JSON invalide** : erreur dans les logs, les valeurs par défaut sont utilisées en mémoire, et
  **le fichier n'est pas écrasé**.

## Commandes

Réservées au niveau de permission 3 (admin) et à la console.

| Commande | Effet |
|---|---|
| `/oreboost reload` | Relit `oreboost.json` et l'applique sans redémarrage aux chunks générés ensuite. |
| `/oreboost status` | Affiche `enabled`, le multiplicateur de chaque groupe et les IDs non résolus. |
| `/oreboost count <rayon>` | Avec `"debug": true` : compte les blocs de minerai par type, avec Y min et max, dans les chunks **chargés** à `<rayon>` chunks autour de la source (0 à 32). |

## Limites

- **Chunks existants** : jamais modifiés.
- **Filons géants de fer et de cuivre (ore veins)** : non concernés. Ils sont produits par le bruit
  du générateur de terrain, pas par des placed features.
- **Le multiplicateur agit sur les tentatives, pas sur les blocs.** Le nombre de blocs suit de très
  près (voir les mesures), mais pas exactement : filons qui se chevauchent, suppression à l'air libre.
- **Reproductibilité** : le mod n'utilise que le générateur aléatoire fourni par le jeu pour chaque
  placement, et ne fait aucun tirage quand le multiplicateur vaut `1.0`. Il n'ajoute donc aucune
  source d'aléa propre. En revanche, Minecraft lui-même ne régénère pas un monde au bloc près : sur
  le serveur de test, deux générations de la même seed diffèrent d'environ 0,1 % sur les comptages
  de minerais, y compris avec OreBoost désactivé.

## Résultats mesurés

Serveur dédié Fabric 26.3 avec une soixantaine de mods (dont C2ME et Lithium), seed fixe,
33×33 chunks générés, diamant et lapis à `4.0`.

| Minerai | Désactivé | x4 | Ratio | Y min → max en x4 |
|---|---|---|---|---|
| Diamant (deepslate) | 24 935 | 98 958 | x3,97 | -63 → 7 |
| Diamant (pierre) | 397 | 1 546 | x3,89 | -6 → 16 |
| **Diamant total** | **25 332** | **100 504** | **x3,97** | aucun au-dessus de Y=16 |
| Lapis (deepslate) | 14 226 | 56 844 | x4,00 | -63 → 7 |
| Lapis (pierre) | 10 618 | 42 813 | x4,03 | -6 → 64 |
| **Lapis total** | **24 844** | **99 657** | **x4,01** | même plage qu'en vanilla |

Charbon, fer, cuivre, or et redstone restent à x1,00.

## Fonctionnement

En 26.3, la chaîne de placement d'une placed feature s'exécute dans
`FeaturePlacer#place(PlacedFeature, RandomSource, BlockPos, boolean)`. OreBoost enveloppe cette
méthode avec un mixin :

1. Au démarrage du serveur, les IDs de la config sont résolus dans le registre `PLACED_FEATURE` et
   rangés dans un index `instance de PlacedFeature → multiplicateur`.
2. À chaque placement, si la feature est dans l'index avec un multiplicateur `m` :
   - le placement d'origine est rejoué `floor(m)` fois ;
   - plus une fois avec une probabilité `m - floor(m)`, tirée sur le `RandomSource` du placement.
3. Chaque passe rejoue **tous** les placement modifiers (`count`, `rarity_filter`, `in_square`,
   `height_range`, `biome`). Les positions sont donc retirées dans les plages de hauteur vanilla,
   et ça marche aussi pour les features sans `count` (débris antiques) ou avec `rarity_filter`
   (gros filons de diamant).

La config et l'index forment un objet immuable publié via une `AtomicReference` : les threads de
génération voient soit l'ancien état, soit le nouveau, jamais un mélange.

```
src/main/java/dev/oreboost/
  OreBoost.java                  entrypoint : config, événements serveur, commandes
  OreBoostCommand.java           /oreboost reload | status | count
  config/OreBoostConfig.java     records immuables, chargement et validation Gson
  config/ConfigHolder.java       AtomicReference vers la config et l'index résolu
  mixin/FeaturePlacerMixin.java  multiplication des passes de placement
```

## Compiler et tester

```bash
./gradlew build
```

Le jar est produit dans `build/libs/`. Java 25 requis.

`tools/measure.sh <label> <config.json>` automatise une mesure : il lance un serveur dans `run/`,
génère 32×32 chunks sur une seed fixe, puis écrit le résultat de `/oreboost count` dans
`run/measure-<label>.log`. Il attend un `fabric-server-launch.jar` et un `eula.txt` déjà présents
dans `run/`. Des configs d'exemple sont dans `tools/configs/`.

## Licence

[CC0 1.0](LICENSE) : domaine public.
