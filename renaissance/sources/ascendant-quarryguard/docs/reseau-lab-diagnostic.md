# QuarryGuard full-runtime : diagnostic reseau borne

**Resolution ulterieure par le parent :** le dump de threads a identifie
`LanServerPinger`. Desactiver `advertiseDedicatedServerToLan` dans la copie
NeoForge a supprime l'ecoute UDP wildcard ; les essais suivants ont passe le
controle des ports, sans autoriser de wildcard. Voir les preuves dans
[validation-phase2.md](validation-phase2.md). Les hypotheses ci-dessous sont
conservees comme diagnostic initial, pas comme attribution finale.

Date : 2026-09-04. Audit statique du run `full-runtime-selftest-baseline0-20260904-125735`, PID 25760.

## Decision immediate

- Accepter la representation IPv4-mappee de **127.0.0.1 uniquement**, sur le port UDP autorise 25586, apres normalisation d'adresse. Ce n'est pas autoriser `::`.
- **Ne pas autoriser `[::]:53906`** : son proprietaire processus est connu, son origine applicative et sa fonction ne sont pas attribuees par les preuves disponibles.
- La piste **DNS Netty n'est pas etayee** par les JAR inspectes. Un autre fait est etabli : **Veinminer execute encore son controle de version par HTTP avec `autoUpdate=false`**. Une resolution de noms via le JDK/Windows reste une piste, pas l'explication prouvee de ce port.
- Une capture automatique de threads avant l'arret vaut la peine pour orienter la suite. **Elle ne suffit pas a justifier une exception wildcard.** La preuve forte relie une ouverture/utilisation de socket a son endpoint, son processus, son horodatage et sa pile d'appel.

## Perimetre respecte

Aucun serveur, Java, jcmd, javap, build ou test lance par cet audit. Lecture des configurations, scripts, journaux et archives existantes ; inspection ZIP et bytecode en memoire sans extraction ni chargement de classes Java. Aucun changement de production, du runner, des JAR ou des configurations. Seul ce document est ecrit. Les sources synchronisees restent intactes. Le profil minimal execute par le parent n'est ni pilote ni arrete ici.

Les recommandations ci-dessous sont **a integrer et executer ulterieurement par le parent**, dans le laboratoire seulement. Elles ne constituent pas un resultat de nouveau test fullpack.

## 1. Ce que prouve le run conserve

Le [releve des endpoints](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/results/listeners-full-runtime-selftest-baseline0-20260904-125735.json:1) contient :

| PID | Protocole | Adresse locale | Port | Conclusion |
| --- | --- | --- | --- | --- |
| 25760 | TCP Listen | 127.0.0.1 | 25585 | Endpoint attendu |
| 25760 | UDP | ::ffff:127.0.0.1 | 25586 | Representation du loopback IPv4 attendu |
| 25760 | UDP | :: | 53906 | Bind wildcard ; fonction inconnue |

Le port eleve est compatible avec une allocation ephemere, mais le relevement unique ne prouve ni une allocation automatique ni une duree de vie courte. Il ne contient ni destination distante, ni identifiant de socket, ni pile, ni date de creation d'endpoint. Une ligne UDP ne distingue pas un serveur applicatif d'un socket de client lie localement ; l'API utilisee inventorie des endpoints UDP. [Documentation Microsoft](https://learn.microsoft.com/en-us/powershell/module/nettcpip/get-netudpendpoint?view=windowsserver2025-ps).

Le [runner inspecte](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/integration/run-lab.ps1:64) procede ainsi : detection de `QuarryGuard laboratory ready:`, lecture des endpoints TCP/UDP du PID, sauvegarde JSON, comparaison textuelle stricte aux deux endpoints, exception si anomalie. Les commandes `quarryguard status` et `quarryguard selftest` ne sont envoyees qu'ensuite. Le `finally` envoie `stop`, puis force l'arret apres 30 secondes si necessaire. Le rejet du mapped-loopback et celui de 53906 sont donc deux sujets distincts.

Chronologie dans le [journal debug](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/full-runtime/logs/debug.log:16850), heures locales CEST :

| Ligne | Heure | Fait |
| --- | --- | --- |
| 3 | 14:57:36.828 | Demarrage ModLauncher, Java Microsoft 21.0.7 |
| 14577 | 14:57:52.003 | Veinminer : `veinminer is up to date` |
| 16850 | 14:58:07.020 | Minecraft : `Done (1.887s)!` |
| 16899 | 14:58:07.571 | `QuarryGuard laboratory ready: 0 claims` |
| 16901 | 14:58:07.591 | Voice Chat : implementation de socket par defaut |
| 16904 | 14:58:07.594 | Voice Chat demarre sur 127.0.0.1:25586 |
| 17066 | 14:58:08.487 | Arret du serveur |

Le `Done (1.887s)` n'est pas le temps total du processus : le chargement complet est ici d'environ 31 secondes. Le [stdout conserve](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/results/runtime-full-runtime-selftest-baseline0-20260904-125735.log:1028) contient les memes etapes et aucun `QG-LAB-RESULT:`. Ce n'est pas un selftest execute puis echoue.

Attention a l'ordre : Voice Chat annonce son ouverture **23 ms apres** le marqueur QuarryGuard. Un dump pris avant ce marqueur ne garantit pas de voir ce thread deja en reception. Le controle au seul instant `ready` ne constitue pas non plus une surveillance de toute la duree de vie du processus.

## 2. Sources exactes inspectees

### Voice Chat : endpoint attendu et repli a surveiller

[Configuration](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/full-runtime/config/voicechat/voicechat-server.properties:9) : `port=25586`, `bind_address=127.0.0.1`, `voice_host=127.0.0.1:25586`, `allow_pings=false`. Le host annonce n'est pas a lui seul une contrainte de bind.

Dans [voicechat-neoforge-1.21.1-2.6.22.jar](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/full-runtime/mods/voicechat-neoforge-1.21.1-2.6.22.jar), bytecode de `de/maxhenkel/voicechat/plugins/impl/VoicechatSocketImpl.class`, methode `open(int,String)` :

- offsets 23-34 : resolution de l'adresse de bind par `InetAddress.getByName` ; ici la configuration donne une adresse numerique ;
- offsets 66-76 : construction de `DatagramSocket(port, adresse)` ;
- offsets 100-127 : en cas d'echec du bind specifique, journalisation du repli wildcard puis `DatagramSocket(port)` ; **le port reste celui fourni**, pas un port 0 ;
- `checkCorrectHost()` verifie la syntaxe du host/port annonce ; ce chemin ne cree pas un second DatagramSocket.

Le repli existe donc dans cette version et justifie de conserver le controle effectif des endpoints, meme avec une configuration correcte. Il n'explique pas le port 53906 par ce chemin : le port configure est 25586, observe en loopback. Les logs indiquent egalement zero plugin Voice Chat charge (stdout lignes 783-786).

Autre chemin reel : `ClientVoicechatSocketImpl.open()` construit `new DatagramSocket()` aux offsets 1-8, donc peut obtenir un endpoint ephemere. **La presence de cette classe client ne prouve pas son execution sur le serveur dedie**. A verifier dans une capture si un thread client Voice Chat apparait ; ne pas l'attribuer sans trace.

### WebDisplays : MiniServ est TCP et son zero est bien un interrupteur

[Configuration](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/full-runtime/config/webdisplays_common.toml:14) : `[mini_server] miniserv_port = 0`, commentaire explicite de desactivation.

Dans [webdisplays-2.6.0-1.21.1.jar](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/full-runtime/mods/webdisplays-2.6.0-1.21.1.jar) :

- `net/montoyo/wd/WebDisplays.onWorldLoad(...)`, offsets 197-201 : lecture de `miniservPort`, saut direct au retour 266 si zero ; l'appel `Server.start()` est a l'offset 251 ;
- `net/montoyo/wd/miniserv/server/Server.start()`, offsets 30-54 : `ServerSocketChannel.open()` puis `bind(new InetSocketAddress(port))` ; thread nomme `MiniServServer` ;
- il s'agit du chemin d'un serveur **TCP**, pas d'un service UDP. Zero dans la configuration est un interrupteur applicatif, pas ici la demande de port ephemere au systeme ;
- le debug confirme le chargement de `webdisplays_common.toml` a la ligne 15237 ; aucun autre TCP Listen n'est dans le releve.

Pas de raison etayee de changer cette configuration pour traiter 53906.

### Netty : ne pas confondre presence de Netty et presence de son resolveur DNS

Inspection de **146 JAR de premier niveau** (145 mods plus le JAR laboratoire), **152 archives JAR imbriquees**, **87 062 classes** : recherche des references DatagramSocket, MulticastSocket, DatagramChannel, resolver/dns, DnsNameResolver, org/xbill/DNS et com/sun/jndi/dns. La recherche porte sur les classes decompressees, pas les octets ZIP compresses. Aucun echec de lecture signale.

Les seules classes des mods contenant `DatagramSocket` sont `VoicechatSocketBase`, `VoicechatSocketImpl` et `ClientVoicechatSocketImpl`. Aucun `resolver/dns/` ou `DnsNameResolver` trouve, y compris dans les JAR imbriques. Cette inspection ne couvre pas les appels reseau natifs des DLL, ni tous les chemins reflechis : ce n'est pas une preuve d'absence universelle d'UDP.

Le [fichier de lancement](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/full-runtime/launch.args:12) utilise Netty 4.1.97.Final depuis les bibliotheques de production, lues seulement. Il reference `netty-resolver`, mais pas `netty-resolver-dns`. Une inspection des archives `libraries/io/netty` confirme la presence des transports UDP generiques dans `netty-transport`, sans classe `DnsNameResolver` trouvee. Le JAR imbrique `netty-0.1.4.1.jar` de owo, vu dans le stdout ligne 172, n'est pas a lui seul une preuve du resolveur DNS Netty.

Le mecanisme existe dans Netty en general : son [DnsNameResolverBuilder](https://netty.io/4.1/xref/io/netty/resolver/dns/DnsNameResolverBuilder.html) courant utilise par defaut une adresse locale de port zero. **Cette source amont courante n'est pas une preuve que ce code soit installe ou execute ici.**

### Veinminer : trafic de mise a jour mal neutralise, fait concret

[settings.json](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/full-runtime/config/Veinminer/settings.json:10) contient `autoUpdate: false`. Pourtant le journal conserve annonce `veinminer is up to date` a 14:57:52.

Dans [veinminer-neoforge-2.11.2+1.21.1.jar](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/full-runtime/mods/veinminer-neoforge-2.11.2+1.21.1.jar) :

- `de/miraculixx/veinminer/Veinminer.<init>(IEventBus,ModContainer)` appelle `UpdateManager.startUpdateChecker(...)` a l'offset 282 ;
- `UpdateManager$startUpdateChecker$1.invokeSuspend(...)` appelle `checkForUpdates(...)` a l'offset 124 ;
- `UpdateManager.checkForUpdates(...)` construit une URL vers l'API Modrinth, puis appelle `URL.openConnection()` a l'offset 43 et `HttpURLConnection.getInputStream()` a l'offset 59 ;
- la branche de version identique journalise `is up to date` aux offsets 194-206 ;
- le meme chemin rejoint ensuite `update(...)`, appel a l'offset 266. Cette methode lit aussi un flux HTTP, puis ecrit le JAR dans le sous-dossier de configuration `Veinminer/update`. Aucun test `autoUpdate` ne figure dans ces deux methodes inspectees.

Cela prouve que `autoUpdate=false` n'a pas supprime ce trafic dans ce run et que ce composant emploie la pile HTTP du JDK, pas un resolveur DNS Netty explicite. **Aucun de ces appels ne fournit le lien vers l'endpoint 53906.** Des fichiers de mise a jour sont presents dans le laboratoire, mais leur presence seule ne date pas leur telechargement au run cible.

La note de preparation affirmant que l'updater Veinminer est desactive doit donc etre nuancee : l'option est modifiee, mais l'absence de trafic n'est pas obtenue. Ce document ne modifie pas cette note partagee. FML est distinct : `versionCheck=false` et message de desactivation observe dans le stdout ligne 764.

## 3. Capture conseillee au parent

### Premier niveau : dump integre, utile mais non attributif

**Oui a une capture `Thread.getAllStackTraces()` reservee au laboratoire.** La voie la plus simple est une capture synchrone juste avant l'emission du marqueur `QuarryGuard laboratory ready:`, vers un fichier distinct sous les resultats du run. Emettre le marqueur seulement apres fermeture du fichier. Cela evite de demander une commande apres que le runner a deja decide d'arreter.

Une commande lab est aussi possible, mais le runner actuel effectue son rejet **avant toute commande**. Il faudrait alors, dans un mode diagnostic explicite, demander la capture, attendre son accuse de fin avec un delai strict, puis arreter en conservant l'echec reseau. Ce n'est pas un mode qui permet de poursuivre le selftest avec un endpoint inconnu. Ne pas retarder volontairement l'arret sans confinement prealable si une observation plus longue est necessaire.

Contenu conseille : horodatage UTC de debut/fin, PID, instant de demarrage du processus, phase de capture, nom/id Java/etat/daemon de chaque thread et sa pile complete. Ne pas confondre id Java et TID Windows. Pas de dump d'environnement, de proprietes completes ou de contenu des requetes. Trier pour faciliter la comparaison. Plafonner taille et nombre de captures, et declarer explicitement toute troncature ou erreur.

Ecrire dans un fichier distinct evite aussi qu'un dump volumineux fasse disparaitre le marqueur du `Get-Content -Tail 160` actuel. Si une seconde capture apres ouverture Voice Chat est voulue, la synchroniser explicitement ; ne pas supposer que `ready` signifie deja "tous les mods ont fini leur reseau".

Signatures utiles a rechercher : `DatagramSocket.receive`, `DatagramChannelImpl.receive`, `ClientVoicechatSocketImpl`, `VoicechatSocketBase.read`, `com.sun.jndi.dns`, `InetAddress`, `UpdateManager`, et des piles HTTP/natives. Une pile Netty en `select` ou `NioEventLoop` seule est normale pour le TCP Minecraft et n'attribue aucun UDP.

Limites : le dump montre des piles **courantes**, pas la pile historique d'ouverture ; il ne livre pas les variables locales, ports ou handles. Un socket conserve apres une requete terminee peut n'avoir aucun thread Java actif dessus. En Java 21, cette API ne capture que les threads plateforme vivants, pas les threads virtuels. [Contrat Thread.getAllStackTraces](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html#getAllStackTraces()). L'absence de pile DNS ne refute donc pas une requete anterieure.

Le `jcmd` rapporte par le parent avec `AccessDenied` apres stop ne tranche pas la cause : processus termine, droits insuffisants ou restriction d'attachement restent a distinguer. Toute tentative ulterieure doit verifier l'identite du processus vivant et son heure de creation ; ne jamais reutiliser aveuglement le PID 25760. Le dump integre evite l'attachement externe, pas toutes les limites d'observation.

### Second niveau : preuve d'attribution du socket

1. Lors d'un prochain lancement autorise, commencer la collecte **avant** l'initialisation des mods. Conserver un identifiant de run, PID et heure de creation du processus ; suivre les endpoints du PID avec leurs heures de creation et des horodatages UTC. Ne pas filtrer seulement sur 53906 : le port peut changer.
2. Si la pile oriente vers une classe Java, instrumenter dans la copie labo le chemin d'ouverture/bind concerne. Apres bind reussi, journaliser l'adresse/port locaux reels, l'identite de l'objet, le thread, la pile appelante et le JAR d'origine des classes deja presentes. Enregistrer aussi fermeture et eventuelles destinations. Une trace `ClientVoicechatSocketImpl.open -> endpoint [::]:P`, correlee au releve systeme du meme run, serait une preuve ; le simple nom d'un thread ne l'est pas. Un hook uniquement au ready ne reconstitue pas une ouverture anterieure.
3. Si le socket est ouvert en code natif ou echappe aux hooks Java, privilegier une trace reseau Windows du processus depuis son demarrage : evenements avec PID, tuples locaux/distants, horodatages et piles natives quand disponibles. Process Monitor offre filtrage par processus et piles d'evenements ; la resolution de classes Java a partir d'une pile native n'est pas garantie. [Documentation Process Monitor](https://learn.microsoft.com/en-us/sysinternals/downloads/procmon).
4. Pour confirmer **DNS**, correler les evenements du bon PID/endpoint a une capture de paquets decodes en DNS : adresse du resolveur, port distant, identifiant de transaction et sens requete/reponse. Un port distant 53 seul ne prouve pas le protocole ; un paquet DNS seul ne prouve pas le JAR appelant. Pktmon peut collecter les paquets localement ; ne pas lui attribuer a lui seul une cartographie Java/PID absente de la capture. Si Sysmon est deja configure, son evenement 3 fournit PID/ProcessGuid et endpoints TCP/UDP, mais il est desactive par defaut et ne donne pas la pile Java. [Pktmon](https://learn.microsoft.com/en-us/windows-server/networking/technologies/pktmon/pktmon), [Sysmon](https://learn.microsoft.com/en-us/sysinternals/downloads/sysmon).

Collecte courte, locale, limitee au laboratoire autant que l'outil le permet, sans televersement. Ne pas installer un service persistant ou modifier globalement la politique d'audit dans le cadre de cet audit. Aucun trafic observe pendant une courte capture ne prouve pas l'innocuite d'un socket inactif.

## 4. Neutralisation possible, uniquement en copie diagnostique

- Conserver MiniServ a zero, query/RCON desactives et bind Voice Chat numerique. Pas de port 0 ou -1 invente comme interrupteur de Voice Chat ; pas d'elargissement aux adresses wildcard.
- Aucun interrupteur de configuration garantissant la suppression de **53906** n'a ete identifie. Ne pas annoncer de correction par configuration de ce socket avant attribution.
- Pour une experience ciblee : neutraliser uniquement le lancement de `UpdateManager.startUpdateChecker(...)` de Veinminer dans un profil diagnostique distinct, conditionne au drapeau labo, ou par une instrumentation labo de ce chemin. Garder tous les mods lorsque possible. Verifier que le controle HTTP a effectivement disparu, puis comparer les endpoints sur plusieurs demarrages autorises avec/sans cette neutralisation, sans changer simultanement Voice Chat ou le JDK. C'est une experience de causalite proposee, **pas une correction validee** ; ne pas changer simplement `autoUpdate`, deja faux.
- Retirer temporairement Voice Chat ou un autre mod d'une **autre copie jetable** peut servir a isoler un effet, jamais a declarer les 145 mods compatibles. Ne pas deplacer les JAR du full-runtime canonique pendant le travail du parent, ni toucher au repertoire de production.
- `-Djava.net.preferIPv4Stack=true` n'est pas une solution d'attribution ou de confinement : le debug rapporte deja cette valeur cote Netty a la ligne 16087, alors que le releve Windows montre deux endpoints IPv6. Cette ligne ne prouve pas comment chaque socket natif a choisi sa famille. Changer la representation de `::` en `0.0.0.0` ne rendrait pas le wildcard acceptable.

Pour le mapped-loopback, recommander une comparaison structuree : parser l'IP, n'appeler `MapToIPv4()` que si `IsIPv4MappedToIPv6`, puis exiger exactement `127.0.0.1` et le port autorise du protocole. Ne pas utiliser un prefixe textuel `::ffff:` comme autorisation generale, ni mapper indistinctement toute IPv6. Echec de parsing = rejet. Le traitement eventuel de `::1` reste une decision separee de la presente correction de representation.

**Conclusion :** poursuivre le profil minimal est compatible avec les faits disponibles. Le full-runtime reste bloque par un endpoint non attribue. Une capture de threads integree avant arret est la prochaine etape peu couteuse ; la preuve decisive demande le lien endpoint/pile ou endpoint/evenement natif, et idealement une comparaison controlee. Meme un socket confirme DNS ne rend pas automatiquement acceptable un bind wildcard sous la politique actuelle.

## Empreintes des preuves

SHA-256 des fichiers lus, pour distinguer ce diagnostic des modifications concurrentes ou runs suivants :

| Fichier, dans le laboratoire | SHA-256 |
| --- | --- |
| voicechat-neoforge-1.21.1-2.6.22.jar | `63116A4D21BD57221482D971DD85822F7C723C210B60411670CDB0AA26873CAC` |
| webdisplays-2.6.0-1.21.1.jar | `DCD57A96B36201282D5C4F3AB83E5271D68F4CA9FDDBF39E712996B636F3ED34` |
| veinminer-neoforge-2.11.2+1.21.1.jar | `2D70ECDBF24B7E5E3AB6F19715DCB37D956931E0B65E7026B4D5524E002C46E4` |
| listeners-full-runtime-selftest-baseline0-20260904-125735.json | `657219B815F0A2957F10FFD4D407E4A6C21AEF21FFF9B84428DF433961DD8C18` |
| runtime-full-runtime-selftest-baseline0-20260904-125735.log | `763A5944C3BDF03D39EEF6BE8108E920B6A47C50A719EB91796160388EB70272` |
| full-runtime/logs/debug.log | `5AFD6ADFD187785415AE8CD0B2686B18FD731D68FB54B68897F2435AB187B1A4` |
| integration/run-lab.ps1 | `0483C8934CBF6393E0EF0C75D2F6F8BC4531D008EF20A59C6B7576C7157B834A` |

Les offsets cites sont des positions d'instructions JVM dans les methodes des classes du JAR, pas des numeros de lignes de sources Java. Les noms de sources `VoicechatSocketImpl.java`, `Server.java`, `WebDisplays.java` et `UpdateManager.kt` proviennent des attributs SourceFile ; aucun decompilateur Java n'a ete execute. Les lignes du debug concernent l'empreinte ci-dessus et peuvent changer au prochain lancement.
