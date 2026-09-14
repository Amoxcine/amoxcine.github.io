# RC : préparer le relais dans un monde neuf

Procédure à exécuter **par le parent uniquement**, pas par ce worker documentaire.
Version : `ascendant-lunar-encounter-0.1.2-candidate.jar`, SHA256
`34B08C74099E6EA3EC4F9CA65BEFED0CBBB6C34DB8E76B61D9DEAD1BC814FD5E`.
Une seule version du mod ; conserver les dépendances et données validées du pack.

## Portée des preuves

Le parent rapporte **NATIVE230733-203 : build_site et validate PASS** à
`512 108 512`, avec 4144 ajouts uniquement dans l'air et 25 chunks non forcés.
Cela qualifie cette construction QA, **pas un combat joué**, ni le nouveau monde.
L'audit FTB NATIVE225914-988 a validé 20 quêtes, 27 tâches, les prédicats natifs de
dimension et le lot natif de huit cuivres partagé/manuellement réclamable, sans
craft joueur ni écriture de progression. Le preflight rencontre a aussi passé.
L'échec global de ce protocole concernait le helper survey parent, pas l'audit FTB.

## Prébootstrap, dans cet ordre

1. Serveur arrêté, préparer le package propre et choisir un `level-name` dont le
   dossier de sauvegarde **n'existe pas**. Fixer `level-seed=8675309` dans
   `server.properties` **avant la première génération**. Garder la rencontre
   désactivée : fichier absent ou `enabled=false`. Ne copier aucun monde QA.
2. Démarrer ce monde neuf pour le générer. Charger normalement les chunks lunaires
   autour de `512 108 512`, sans forçage permanent ni claim. Vérifier que le relais
   est accessible depuis l'atterrissage normal en fusée. Le constructeur ne charge
   ni ne génère lui-même les chunks. Console opérateur, sans slash :

   ```text
   lunar_encounter preflight 512 108 512
   ```

   Exiger PASS sur ce monde neuf. La même graine ne dispense pas du contrôle si les
   mods ou données de génération diffèrent. Les 4144 ajouts observés en QA ne sont
   pas un nombre imposé au nouveau terrain. En cas de refus, arrêter la procédure :
   ne pas détruire un obstacle, effacer un claim ou importer la plateforme QA pour
   contourner le contrôle.
3. Après PASS, arrêter le serveur. Dans **le dossier réel de ce nouveau monde**, créer
   `serverconfig/lunar-encounter.properties` avec les valeurs suivantes. Ne pas
   conserver les anciennes coordonnées `1024/100/1024` du fichier exemple.

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

   **L'identité du site est scellée dès ce premier démarrage activé**, avant le
   combat et avant `build_site`. Le fichier est lu au démarrage, pas par `/reload`.
4. Redémarrer, recharger normalement les chunks et garder les joueurs hors combat.
   Il ne doit exister **aucune tentative historique**. Console :

   ```text
   lunar_encounter status
   lunar_encounter preflight 512 108 512
   lunar_encounter build_site confirm
   lunar_encounter validate
   ```

   Exiger service sain, zéro run, puis PASS à chaque contrôle. Le constructeur
   revalide avant les ajouts ; il préserve les blocs existants et n'ajoute que dans
   l'air, sans oxygène ni acteurs. Un échec partiel conserve les ajouts : examiner
   le résultat, ne pas lancer de nettoyage ni les anciennes commandes de clear.
5. Arrêter normalement, passer **seulement** `allowSiteBootstrap=false`, conserver
   `enabled=true` et l'identité inchangée, puis redémarrer. Une fois les chunks
   chargés, refaire `status` et `validate`. Archiver les résultats. Fournir le guide
   joueur et tester le parcours non-OP, sans commande administrative par combat.

## Package, migration et limites

Transférer uniquement les mods/configurations/données et quêtes revus ; inclure les
quatre scripts de quêtes exacts et les recettes/ressources communes sous `kubejs/data`.
Ne pas embarquer de helper de qualification ni de copie concurrente des datapacks QA.
Le package et le monde RC restent la responsabilité du parent.

Ne copier ni `renaissance-lab`, ni inventaires/advancements, équipes/claims, tombes,
entités de test, journaux SQLite/WAL/verrous ou marqueurs de fixture. Le nouveau
monde doit créer son propre `data/ascendant-lunar-encounter-v1/`. Le bootstrap sur
ce nouveau monde n'est pas une réinstallation du site QA.

Après scellement, changer `site/x/y/z` provoque un refus : aucune migration
automatique n'est fournie. Ne pas effacer `site.identity` ou le journal pour
forcer une nouvelle identité ; toute migration d'un monde existant exige une
procédure hors ligne distincte. La compatibilité des journaux 0.1.1/0.1.2 ne justifie
pas leur import dans RC ; les anciennes remises ne doivent pas être rejouées.

En 0.1.2, **FTB Quests seul** remet le premier lot : quête `6C13020000000013`,
reward `6C13040000000001`, cuivre8, `team_reward=true`, `auto=disabled`, non répétable.
Pas de `/claim` du mod, de paiement Java par victoire ni de sauvegarde globale
forcée ; persistance Minecraft/FTB ordinaire, sans garantie globale exactement-une-fois
après crash. Restent à qualifier : combat/IA, oxygène en situation, vraie victoire,
claim partagé et inventaire plein, multijoueur/reconnexion et retour en fusée.
