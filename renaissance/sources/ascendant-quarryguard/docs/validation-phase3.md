# QuarryGuard : persistance et compatibilite, phase 3

Date : 4 septembre 2026, heures Europe/Paris.

**Bilan historique de phase 3 : persistance/reprise validees, mais defaut de
suppression des cadres reproduit sur ce binaire. Pas de livraison.**
Depuis, l'utilisateur a valide A et sa correction est testee dans le
laboratoire : [suivi de phase 4](validation-phase4-cadres.md).
Aucun deploiement, push GitHub, changement du client ou migration du serveur
des joueurs.

## Binaire des tests de persistance

`integration/build/phase3-ef26/ascendant-quarryguard-0.1.0-lab.jar`

```text
SHA-256 EF26E953709A95FF13376429185D151B148A2D165B6A50595A98988AA9CAF0C5
Build   classes-20260904151851604
```

Les cinq lancements suivants portent tous sur ce meme binaire, les 145 mods
serveur copies, 4 Gio maximum, une JVM a la fois et des ports loopback controles.
Tous sauvegardent et quittent avec le code 0, sans terminaison forcee.

| Suite finale | Heure du PASS | Journal |
|---|---|---|
| Preparation de reprise utile | 17:19:49 | [Preuve](../results/runtime-full-runtime-restart-prepare-baseline0-20260904-151915.log) |
| Reprise utile, nouvelle JVM | 17:20:24 | [Preuve](../results/runtime-full-runtime-restart-check-baseline0-20260904-151950.log) |
| Regression complete de phase 2 | 17:21:39 | [Preuve](../results/runtime-full-runtime-regression-baseline0-20260904-152105.log) |
| Preparation legacy et quarantaine | 17:22:15 | [Preuve](../results/runtime-full-runtime-legacy-prepare-baseline0-20260904-152141.log) |
| Legacy, quarantaine, attribution reversible | 17:22:50 | [Preuve](../results/runtime-full-runtime-legacy-check-baseline0-20260904-152216.log) |

La baseline de charge n'est pas rejouee sur ce candidat : les mesures
chronometrees restent celles de phase 2, avec leur propre empreinte et leurs
limites. Ne pas presenter ces cinq essais fonctionnels comme une mesure MSPT.

Un binaire de diagnostic ulterieur ajoute `FrameChecks` et reproduit le defaut
des cadres a 17:29:54. Les cinq PASS ci-dessus ne couvrent pas ce chemin et
ne permettent donc pas de livrer le correctif en l'etat.

## Objectif

Completer les preuves de la [phase 2](validation-phase2.md) : reprise utile
apres redemarrage, anciennes quarries sans proprietaire, sauvegardes
incoherentes et interactions concretes avec les mods installes.

La logique de protection n'est pas modifiee par les premiers essais : les
changements portent sur le banc, ses commandes reservees au laboratoire et
le refus de lancer la baseline pendant une fixture persistante inachevee.

## Sauvegardes anciennes et quarantaine

Premier essai complet reussi avec le JAR de SHA-256
`EBE1C4E2CFC9CBC03C12769B1BF29CADAFA246378E426A9ED91646309FFE7DF1` :

| Etape | Resultat | Preuve |
|---|---|---|
| Preparation et chargement NBT natif | PASS 17:09:38 | [Journal](../results/runtime-full-runtime-legacy-prepare-baseline0-20260904-150903.log) |
| Rechargement depuis le disque dans une nouvelle JVM | PASS 17:10:35 | [Journal](../results/runtime-full-runtime-legacy-check-baseline0-20260904-151001.log) |

Six cas : quarry normale et avancee, chacune sans proprietaire, avec cible
hors emprise ou avec emprise sans interieur. Chargement par
`loadWithComponents`, puis sauvegarde/rechargement reels des chunks, pas
appel direct au helper du garde. Chaque cas subit 25 ticks natifs bloques
avant et apres redemarrage, sans recharge pendant ces ticks.

Verifications : absence d'auto-attribution, UUID et motif de quarantaine
conserves lorsque connus, etat/cible/emprise/energie/stockage conserves,
7 diamants et 3 seaux d'eau dans chaque machine, donnees persistantes
etrangeres preservees, volume voisin inchange et protection globale prete.
Les 145 JAR serveur sont conserves, avec le prototype ajoute dans la copie.
Sauvegardes et arrets propres, codes de sortie 0, aucune terminaison forcee.

Limite : les NBT anormaux sont fabriques explicitement dans le laboratoire.
Ce n'est pas un echantillon du monde des joueurs, ni un test de toute forme
de corruption possible.

## Attribution reversible

PASS a 17:13:55, apres preparation a 17:13:19. Les six cas precedents sont
rejoues sur le binaire
`592E9070E0700A5C30EAD1A8D25873A94347595F4030757783894FD2A559A9CC`.
Pour les deux machines sans owner, le test ajoute uniquement l'UUID du poseur
enregistre lors de la creation de la fixture. Le reste du NBT est compare.
La machine est autorisee en terrain libre, refusee pendant 25 ticks sous un
nouveau claim hostile, puis autorisee apres unclaim. Enfin, le NBT initial
est recharge : identique et sans proprietaire, donc machine a nouveau arretee.

L'ensemble des chunks forces globaux est identique avant/apres cette
operation. Cela ne couvre pas tous les tickets des autres mods, ni le
probleme natif de deux machines partageant une source de chargement.
Il n'y a pas de minage autorise pendant ce test d'attribution lui-meme.

- [Preparation](../results/runtime-full-runtime-legacy-prepare-baseline0-20260904-151245.log).
- [Rechargement et attribution reversible](../results/runtime-full-runtime-legacy-check-baseline0-20260904-151321.log).
- [Empreinte et parametres](../results/run-full-runtime-legacy-check-baseline0-20260904-151321.json).

Aucun outil d'adoption de production n'est livre par ce test et aucun
proprietaire n'est deduit du claim, du chef d'equipe ou d'un faux joueur.

Le noyau algorithmique est aussi rejoue : 915 730 assertions PASS, Java 21,
`-Xlint:all -Werror`. Une comparaison ZIP des deux binaires verifies trouve
21 classes de protection (garde, empreinte des marqueurs, noyau et mixins)
identiques octet pour octet a la phase 2 ; les changements de ce candidat
concernent les tests et leur enregistrement, pas ces interceptions.

## Reprise utile et compatibilite

L'agent de reprise a livre `RestartChecks`, relu et integre par le parent.
Le parent a deplace les nouvelles fixtures vers X=6000/6064, Z=6000, dans
la limite du monde de test, et reserve un nouveau fichier de preuve :
`quarryguard-restart-phase3-test.properties`. Le manifeste de phase 2 n'a
pas ete remplace et ses anciennes positions ne sont pas nettoyees.

Sur le candidat de SHA-256
`EF26E953709A95FF13376429185D151B148A2D165B6A50595A98988AA9CAF0C5` :

| Cas | Avant redemarrage | Apres redemarrage et unclaim |
|---|---|---|
| Quarry normale | 105 ticks, 1 bloc mine, 1 drop stocke | 4 ticks, 1 nouveau bloc mine, 1 nouveau drop |
| Quarry avancee | 78 ticks, 1 bloc mine, 1 drop stocke | 1 tick, 1 nouveau bloc mine, 1 nouveau drop |

Les deux demarrent avec 49 blocs d'or. Energie native consommee dans les
quatre phases, sans recharge ni changement d'etat/cible apres redemarrage.
Entre les phases autorisees : 25 ticks sous claim hostile par machine avant
et apres redemarrage, NBT complet/terrain/energie/stockage inchanges. Le
checkpoint avec cible de minage engagee est compare entre deux JVM.

Preparation PASS a 17:19:49, reprise PASS a 17:20:24, arrets propres code 0.
Le journal de fixture est ecrit avant les mutations ; un echec conserve les
preuves et n'autorise pas un nettoyage arbitraire. Les tests se limitent aux
modules absents et a des tickers natifs appeles synchroniquement, pas a une
longue session de jeu ou a la gestion de tickets partages.

- [Preparation](../results/runtime-full-runtime-restart-prepare-baseline0-20260904-151915.log).
- [Reprise utile](../results/runtime-full-runtime-restart-check-baseline0-20260904-151950.log).
- [Empreinte du candidat](../results/run-full-runtime-restart-check-baseline0-20260904-151950.json).

La revue des modules/deplacements est traitee separement. Aucun succes de
reprise n'est utilise comme preuve de compatibilite d'un mover non teste.

## Limites de livraison

Le JAR reste experimental. Aucun succes de laboratoire ne vaut validation
de huit joueurs, absence totale de surcout, compatibilite de tous les modules
ou autorisation de changer les quarries existantes.

Pour une prochaine recette avec un vrai client : utiliser une copie isolee,
ne pas ajouter le prototype au client, ne pas modifier la production. Verifier
la connexion, les interfaces, la pose refusee dans un claim adverse et la
pose autorisee avec les bons droits. Aucun serveur de test n'est laisse allume.
