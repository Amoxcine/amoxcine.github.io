# Préparer les fichiers du serveur

Double-cliquer sur `prepare-server-upload.bat` depuis Windows.

Le script :

1. vérifie que Java est disponible pour exécuter Packwiz ;
2. télécharge et contrôle `packwiz-installer-bootstrap` ;
3. crée ou met à jour le sous-dossier `server-upload` ;
4. télécharge uniquement les fichiers marqués `both` ou `server` dans Packwiz.

Une fois terminé, téléverser avec WinSCP le **contenu** de `server-upload` vers la racine du serveur Minecraft.

Le script ne télécharge pas le serveur NeoForge lui-même et ne touche pas au monde. Faire un snapshot avant de remplacer les fichiers sur le serveur réel.

Le téléchargement fonctionne avec le Java actuellement configuré, mais le **serveur Minecraft NeoForge 1.21.1 doit être lancé avec Java 21**.

Pour tester une autre adresse Packwiz :

```bat
prepare-server-upload.bat https://exemple.invalid/pack.toml
```
