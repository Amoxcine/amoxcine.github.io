# Premier relais lunaire : guide joueur

Rencontre 0.1.2. Le site de référence est sur la Lune, en **X=512, Y=108, Z=512**.
`/lunar_encounter site` affiche les coordonnées réellement configurées ; cette
commande ne téléporte pas. Rejoignez le relais par le voyage normal en fusée.

## Avant de commencer

Gardez le scaphandre, l'oxygène chargé et les réserves du retour. Le relais ne
fournit ni atmosphère, ni recharge, ni secours automatique. Préparez votre refuge
et votre pas de tir avant cette sortie ; la quête du relais dépend de ces étapes.
Ne démontez ni ne revendiquez le site avec FTB Chunks.

En solo, au site : `/lunar_encounter ready`, puis `/lunar_encounter start`.
En groupe : un à huit joueurs de la même équipe FTB choisissent un chef.
**Chaque membre, chef compris**, saisit `/lunar_encounter ready NomDuChef`.
Le chef lance `/lunar_encounter start` dans les 60 secondes. La liste des
participants et l'équipe sont fixées pour cette tentative. Aucun droit OP requis.

## Résoudre le relais

1. Lisez les signaux du chat : numéro du circuit, récepteur et action demandée.
   Pendant la lecture, les confirmations sont fermées.
2. À l'ouverture du routage, neutralisez le défenseur du circuit. Les bornes en
   cuivre sont **A à l'ouest**, **B à l'est**, **C au sud**, à 12 blocs du centre.
3. Approchez la borne demandée à moins de trois blocs, en ligne de vue, puis
   faites un clic droit dessus. Dans le chat, cliquez sur l'action du bon circuit.
   Le premier relais demande **Transférer** ; les autres choix affichés ne sont
   pas une correction automatique. En solo, suivez l'ordre des circuits.
4. Un mauvais récepteur ou une mauvaise action retire une marge de stabilité et
   relance la lecture ; trois pertes terminent l'opération. Un défenseur qui
   accomplit son sabotage peut aussi retirer une marge. Relisez les nouveaux
   signaux ; un ancien bouton périmé est refusé sans pénalité.
5. Une fois le routage terminé, attaquez le **module exposé**. Après sa destruction,
   attendez l'avertissement final, puis neutralisez le **cœur exposé**. La victoire
   doit être confirmée par le relais : tuer un ennemi quelconque ne suffit pas.

## Lot, échec et retour

La première victoire authentifiée alimente la quête « Réactiver le premier relais ».
**Un membre réclame les huit lingots de cuivre pour l'équipe dans FTB Quests** :
récompense partagée, manuelle et non répétable. Aucun `/lunar_encounter claim`
n'existe en 0.1.2 ; refaire le combat ne redonne pas ce premier lot.

`/lunar_encounter last` affiche la dernière tentative. Après une reconnexion,
`/lunar_encounter observe UUID` peut réafficher une victoire déjà enregistrée et
authentifiée pour vous ; cela ne crée ni victoire ni objet. Le chef peut abandonner
avec `/lunar_encounter abort`. Pour recommencer après la fin d'une tentative,
chacun renouvelle `ready`, puis le chef lance `start`.

L'échec ne prélève pas de mise, mais n'annule pas les risques normaux du combat ou
du vide. Repartez avant d'épuiser vos réserves, avec la fusée et le carburant gardés
pour le retour ; laissez l'industrie sur la Lune.

**Qualification :** construction et validation native du site confirmées par le
parent ; le combat réel, la remise du lot et ce parcours joueur restent à tester.
