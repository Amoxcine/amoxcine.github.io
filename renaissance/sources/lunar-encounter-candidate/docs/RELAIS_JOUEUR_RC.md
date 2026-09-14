# Relais lunaire : guide joueur RC 0.1.2

## Rejoindre et lancer

Le relais `lunar_relay_01` se trouve sur la Lune (`ad_astra:moon`), en
**X=512, Y=108, Z=512**. `/lunar_encounter site` rappelle les coordonnees
effectivement configurees. Rejoignez la Lune par le voyage normal en fusee,
puis le site a pied ou avec vos moyens locaux ; cette commande ne teleporte pas.
Acces terrain repere dans le lab : **482, 108, 480**, a confirmer dans le monde RC.

Preparez votre oxygene, votre nourriture et votre equipement. Le relais ne fournit
ni air gratuit ni soin. Aucun objet d'entree n'est preleve ; consommation d'oxygene,
mort, reanimation et tombe restent celles du pack.

En solo, sur le site :

```text
/lunar_encounter ready
/lunar_encounter start
```

En groupe de **2 a 8**, tous doivent appartenir a la meme equipe FTB. Chaque
participant, chef compris, saisit `/lunar_encounter ready NomDuChef`, puis le chef
lance `/lunar_encounter start`. Le consentement expire apres 60 secondes.
Une seule rencontre peut occuper le site. Aucun droit OP n'est necessaire.

## Resoudre le relais

1. Lisez les signaux pendant les 8 secondes initiales. Solo : 1 circuit ;
   groupe de 2 a 4 : 2 circuits ; de 5 a 8 : 3 circuits.
2. Neutralisez les defenseurs indiques. Une surcharge est annoncee apres
   10 secondes de routage ; un defenseur encore actif a 15 secondes fait perdre
   une marge de stabilite. Restez sur la plateforme.
3. Rejoignez le recepteur demande : **A ouest (500,108,512)**,
   **B est (524,108,512)** ou **C sud (512,108,524)**. Faites un clic droit sur
   son bloc de cuivre, puis choisissez explicitement l'action dans le chat.
   Pour ce relais, le signal demande **Transferer**. La borne doit etre visible,
   a trois blocs au maximum. Un mauvais recepteur ou une mauvaise action coute
   une marge et relance la lecture ; un ancien bouton perime ne coute rien.
4. Tous les circuits valides ouvrent le module : attaquez-le pendant sa fenetre
   de 20 secondes. Puis attendez les 5 secondes d'ouverture finale et neutralisez
   le coeur. Une arme puissante ne remplace pas le routage.

Trois marges perdues font echouer la tentative. La limite active est de
10 minutes ; 30 secondes sans aucun participant apte present interrompent le run.

## Recompense et nouvelle tentative

La victoire authentifie l'avancement du relais. Dans **FTB Quests**, un membre
reclame manuellement le **premier lot partage de 8 lingots de cuivre** pour
l'equipe. Ce n'est pas huit lingots par joueur ou par combat. Rejouer ne remet
pas la quete a zero. La remise et l'inventaire suivent le comportement natif FTB.

Pour recommencer apres la fin ou un echec : `ready`, puis `start`, comme ci-dessus.
Le chef peut abandonner avec `/lunar_encounter abort`.
`/lunar_encounter last` affiche la derniere tentative. Conservez son UUID :
`/lunar_encounter observe UUID` peut restaurer l'affichage d'une victoire deja
enregistree apres une absence/reconnexion ; il ne donne aucun objet et ne force
aucune victoire. Il n'existe plus de commande `/lunar_encounter claim`.

**Qualification :** preparation native du site validee dans le lab par le parent
(`230733-203`). Le combat complet et la boucle de recompense jouee ne sont pas
declares testes par cette notice.
