# Relais lunaire : preparation du monde RC neuf

Procedure pour le parent uniquement. Cette livraison ajoute des notices, sans
code, boot, installation ou modification de monde.

## Reference qualifiee et separation QA

Retour natif du parent **`230733-203`** : encounter 0.1.2, `build_site` et
`validate` PASS au centre **512,108,512**, **4144 ajouts uniquement dans l'air**,
**25 chunks sans chargement force**. Acces terrain repere : **482,108,480**.
Ce resultat qualifie la preparation du site dans le lab, pas un combat joue.

Le parent prepare une sauvegarde finale **neuve**, avec la meme graine
**8675309** et les memes mods/configurations de generation. Ne pas distribuer
`renaissance-lab` ni copier un monde QA. Ne reprendre ni joueurs/inventaires,
equipes/progression FTB, avancements, tombes, ni journal/SQLite/WAL/fence de test.
La graine seule ne remplace pas l'identite des mods et de la generation.

## Avant la premiere generation

Serveur arrete, choisir un nom de sauvegarde encore inexistant, par exemple :

```properties
level-name=lunar-rc-clean-8675309
level-seed=8675309
```

Ces valeurs vont dans `server.properties` du serveur RC dedie. Pour une creation
solo, saisir 8675309 dans le champ graine du nouveau monde. Changer level-seed
sur un monde deja genere ne le recree pas.

Ne conserver qu'un seul JAR encounter, **0.1.2**, plus le fragment FTB revise.
Ne pas installer 0.1.1 et 0.1.2 ensemble. Les anciens artefacts de preuve restent
archives en dehors des mods actifs ; aucune migration de monde QA vers la RC.

## Preflight, puis activation

1. Premier boot du monde neuf avec rencontre absente de la configuration, ou
   `enabled=false`. Le JAR est present mais la rencontre reste desactivee ; aucun
   site n'est encore scelle. Charger/visiter normalement la zone lunaire, sans
   installer de claim dans le perimetre. Le helper ne genere ni ne force les chunks.
2. Console, sans slash :

   ```text
   lunar_encounter status
   lunar_encounter preflight 512 108 512
   ```

   Exiger PASS avant toute activation. Le volume controle est borne a 65x65x5,
   avec au plus 25 chunks charges/non claims. Un obstacle, une entite de bloc,
   un sol dangereux ou un chunk manquant impose l'arret de la procedure, pas un
   contournement. Les 4144 ajouts du lab sont un repere, pas une preuve a substituer
   au preflight du nouveau monde. Verifier aussi l'acces depuis le trajet en fusee.
3. Apres PASS, **arreter le serveur**, puis creer dans CETTE sauvegarde neuve
   `serverconfig/lunar-encounter.properties` avec les valeurs suivantes :

   ```properties
   enabled=true
   allowSiteBootstrap=true
   site=lunar_relay_01
   x=512
   y=108
   z=512
   readingTicks=160
   windowTicks=400
   warningTicks=100
   timeoutTicks=12000
   absenceTicks=600
   ```

   Ne pas reprendre les anciennes coordonnees 1024,100,1024 de l'exemple generique.
   L'identite du site est scellee au premier demarrage ACTIVE, avant le premier
   combat : les coordonnees doivent donc etre correctes avant ce redemarrage.
4. Redemarrer, charger normalement les chunks du site, puis en console :

   ```text
   lunar_encounter preflight 512 108 512
   lunar_encounter build_site confirm
   lunar_encounter validate
   ```

   Exiger PASS et conserver les logs du monde final. Le builder exige zero run
   historique, refait le preflight, preserve le sol existant et ajoute uniquement
   dans l'air. Maximum 4228 ajouts ; jamais de clear ni de remplacement non-air.
   En cas d'echec partiel, inspecter les ajouts conserves et refaire le preflight.
   Ne pas utiliser le mcfunction historique de reference qui efface le volume.
5. Arreter normalement, passer **uniquement `allowSiteBootstrap=false`**, garder
   `enabled=true` et toutes les valeurs d'identite, puis redemarrer pour livraison.
   Les joueurs trouvent ensuite les coordonnees avec `/lunar_encounter site` et
   lancent `ready/start` sans OP. Aucun bootstrap par joueur ou par combat.

## Persistance et limites

- Pas de rechargement a chaud : changer configuration/temporisations serveur
  arrete, puis redemarrer. Changer site ou coordonnees apres scellement est refuse ;
  ne jamais supprimer `site.identity` ou le journal pour contourner ce refus.
- La compatibilite 0.1.1 -> 0.1.2 sert aux tests du lab existant, pas a importer
  son etat dans le monde final. Ne pas restaurer un ancien paiement personnalise.
- Premier lot FTB : quete `6C13020000000013`, avancement
  `ascendant_lunar_encounter:relay_complete`, critere `durable_success`, recompense
  `6C13040000000001` : cuivre x8, partage equipe, reclamation manuelle, non repetable.
- La rencontre conserve victoire et roster, sans prelevement d'objet initial,
  `/claim` personnalise, recu d'inventaire ou sauvegarde complete forcee. Comportement
  FTB/Minecraft normal assume ; aucune garantie globale exactly-once apres crash.
- Ne pas accorder artificiellement victoire/quete/recompense pour preparer le monde
  final. Les essais de combat non-OP, oxygenation, mort/reconnexion et remise FTB
  reelle restent a qualifier sur une copie de test, sans injecter cette progression
  dans la sauvegarde finale propre.
