# Ascendant

## Renaissance RC1

Cette branche `renaissance/rc1` contient la RC lunaire separee du pack public.

- [Code, configurations, quetes et guide Renaissance](renaissance/README.md)
- [Telecharger la preversion client et serveur](https://github.com/Amoxcine/amoxcine.github.io/releases/tag/renaissance-rc1)

Pour tester Renaissance, utiliser les ZIP de cette preversion, **pas** le lien
Ascendant ci-dessous. Le `pack.toml` racine et les pages HTML restent ceux de la
V1 : ils ne constituent pas un canal de mise a jour Renaissance. Aucun changement
n'est applique a `main` ou au site en production.

La suite de ce fichier documente le pack V1 conserve comme base historique.

## Installation pour les joueurs

1. Installer [Prism Launcher](https://prismlauncher.org/download/).
2. Telecharger [Ascendant-Prism.zip](https://amoxcine.github.io/downloads/Ascendant-Prism.zip).
3. Dans Prism, cliquer sur **Ajouter une instance**, puis **Importer**.
4. Selectionner `Ascendant-Prism.zip` et valider.
5. Lancer l'instance **Ascendant**.

Packwiz telecharge le modpack au premier lancement et verifie automatiquement les mises a jour aux lancements suivants.

En cas de ralentissement, consulter le [guide de performances](https://amoxcine.github.io/performances.html).

## Archives automatiques

Le workflow GitHub Actions `Build Prism archive` reconstruit une archive Prism vérifiée à chaque modification de `pack.toml` ou du modèle Prism. Le ZIP est disponible dans les artefacts de l'exécution GitHub Actions.

## Préparation du serveur

Sous Windows, lancer [`server-tools/prepare-server-upload.bat`](server-tools/prepare-server-upload.bat). Le script crée `server-tools/server-upload` avec uniquement les mods et dossiers destinés au serveur, prêts à être transférés avec WinSCP.
