# CLAUDE – Vollständiger Implementierungsauftrag für PROTOKOLL NULL

## Rolle und Aufgabe
Du bist Lead Android Game Engineer, Senior Kotlin/Jetpack-Compose-Entwickler, UI/UX-Designer, Narrative-Designer und QA-Engineer. Arbeite DIREKT im bestehenden GitHub-Repository:
https://github.com/GoldenUnicorn24/PROTOKOLL-NULL-Aufstieg-eines-Agenten
Branch: main (optional Feature-Branch mit anschließendem PR). Das vorhandene Repository ist der Ausgangspunkt; nicht neu anfangen und bestehende Inhalte nicht blind löschen. Der Nutzer will eine **wirklich spielbare, umfangreiche native Android-Beta** und anschließend eine langfristig erweiterbare Vollversion.

Zuerst Repository vollständig lesen und den Android-Build reparieren/validieren. Einen reproduzierbaren **Gradle-Wrapper**, CI und eine erfolgreiche debug APK erstellen. Arbeite nicht nur an Mockups: alle primären UI-Aktionen müssen reale Game-State-Änderungen bewirken. Keine WebView, keine 3D-Engine, **Portraitmodus**, deutsche Oberfläche, minSdk 26, offline spielbar. Kotlin + Jetpack Compose + Navigation + Room + DataStore oder eine gleichwertig robuste, offlinefähige Architektur. Jetpack-Compose-Material3 mit eigenem hochwertigen Designsystem. Architektur MVVM/UseCases/Repositories, deterministische Simulation, versionierte Save-Migrationen.

## Bereits vorhanden (nicht neu erfinden)
- Gradle-/Kotlin-Skelett, Android Manifest, UI-Grundscreens in app/src/main/java/de/protokollnull/agent/ui/
- model/GameModels.kt: Spieler, NPC, Items, Fähigkeiten, Ränge, Story- und Missionsdaten, Partnerprofil, Spielstand.
- engine/GameRules.kt: XP, Level, Training, Tageswechsel, Inventarmanipulation, Vertrauen, Beförderungs-/Altersgates.
- data/GameContent.kt: typisierte JSON-Kataloge, data/SaveStore.kt: lokaler Save.
- app/src/main/assets/game/: agents.json (14 NPCs), items.json (42 Items), ranks.json (12), skills.json (27 in 9 Bäumen), places.json (15), factions.json (6), missions.json (16), story.json (14 Nodes), events.json (13).
- 32 echte lokal eingebundene Inventar-SVG-Dateien unter app/src/main/assets/items/. Credits in assets/ITEM_IMAGE_CREDITS.json.
- 11 echte, CC-lizenzierte Fotos von Nikita-Darstellern unter app/src/main/assets/cast/. Individuelle Lizenzdetails in assets/CAST_IMAGE_CREDITS.json.
- **Sehr wichtig:** assets/SERIES_LOOK_REFERENCES.json und assets/SERIES_STYLE_GUIDE.md definieren Originalaussehen der Figuren **wie in der CW-Serie Nikita**, inkl. Quellen echter Serien-Szenenbilder. Das sind visuelle Referenzen und keine frei verwendbaren Spielassets. Nicht aktuelle generische Headshots als finalen Ziel-Look setzen. Bereits lokale CC-Schauspielerfotos vorläufig nutzen. Keine urheberrechtlich geschützten CW/IMDb/TMDb-Promofotos aus dem Web ohne Erlaubnis ins öffentliche Repo kopieren oder zur Laufzeit hotlinken. Bei lizenzierten Serienstills später priorisiert lokales Asset show_<id>.jpg nutzen; dazu nachweisbare Lizenzdetails dokumentieren. Bei alternativen eigenständigen Artworks Serienära, Haarstil, Kleidung und Charakteridentität präzise treffen.
- Projekt ist ein privater Fan-Prototyp im Nikita-Universum. Für öffentliche/kommerzielle Releases urheberrechtliche Freigaben oder eigenständige Figuren/Organisation nötig.

## Spielvision (unbedingt erhalten)
PROTOKOLL NULL – Aufstieg eines Agenten. Mit 13 verliert Hauptfigur Eltern bei einem Terroranschlag, danach drei Jahre streng geheime staatliche Ausbildung im Projekt PHOENIX. Mit 16 als unbekannter Rekrut in die geheime Division versetzt. Alte Identität verschlüsselt; nur wenige kennen Wahrheit. Michael als Ausbilder; Percy als Direktor; Nikita, Alex, Birkhoff, Amanda, Thom, Jaden, Owen, Ryan und Sonya als charaktergetreue, aber alternativ erzählbare NPCs. Der Spieler kann aufsteigen bis zum Direktor und dann Division selbst verwalten. Langfristig Karriere, Skills, Missionen, Beziehungen, Freundschaften, Rivalen, Inventar, Geld, Ausrüstung, eigene Wohnräume, ziviles Doppelleben, dynamische NPCs, mehrere Enden. Spielercharakter ist nicht von Anfang an allmächtig; PHOENIX-Vorteil verbirgt sich hinter nachvollziehbaren Stärken und Schwächen.

## Echte spielbare Kernsysteme
1. Start-/Fortsetzen-Menü, anpassbarer Agentencharakter mit Name, Deckname, Geschlecht, Auftreten, Aussehen/Avatar, Stärken/Hintergrund, Schwierigkeitsgrad; Intro 13-15 in erzählten Szenen; freie Spielwelt ab 16.
2. Tageskalender mit Stunden/Zeitslots, Routinen, Arbeitsplan, Energie, Stress, Wunden und Regeneration; altern in Jahren ohne versehentliche 365-fach-Schleife; persistenter Weltzustand.
3. Erkundbares 2D-HQ: Etagen und Räume als begehbare/listenartige 2D-Szenen; Standortwechsel; NPCs mit Tagesplänen; Raumaktionen und Zutrittsberechtigungen.
4. Missionsgenerator mit Seeds, Orten, Fraktionen, Zeugen, Risiko, Team, Intel, Nebenwirkungen. Mehrere wirkliche Missionstypen: Gespräch, Untersuchung, Undercover-Rollenspiel, Beobachtung, Rettung, rundenbasiertes **abstraktes** 2D-Taktikbrett (Deckung, Position, Ausdauer, Status, Teamfähigkeiten), ohne reale operative Terror- oder Waffenanleitungen. Vorbereitung/Briefing, Entscheidungen, Abbruch-/Fehlpfade, Belohnungen, Nachbesprechung und Konsequenzen. Die derzeitige 'Demo-Abschluss'-Schaltfläche muss durch Mission Gameplay ersetzt werden.
5. Inventar mit tatsächlichen **Gegenstandsbildern**, Equipment-Slots, Stapeln, Seltenheit, Zustand, Gewicht/Kapazität, begrenztem Zugriff je Rang, Erwerb/Verbrauch, Lager, Inventarfiltern, Detailansicht. Lokale SVGs mit Coil-SVG bereits vorhanden; QUELLEN und Creative-Commons-Attribution in Credits. Fehlende Bilder als selbst erstellte Icons ergänzen, nicht bloß Text/Emoji.
6. 100 Charakterlevel, 12 Ränge; 9 Skilltrees mit verbundenen Voraussetzungen und jeweils genug Perks, passive/aktive Skills, Trainingsaktionen, Skills je NPC. Beförderung nur bei Alter, Ruf, Prüfung, Vorgesetztem und Story. Ab 18 operativer Erwachsenenstatus (einige Ausbildungsmissionen 16-17).
7. Beziehungen: Freundschaft, Vertrauen, Loyalität, Respekt, Zuneigung, Konflikte, Erinnerungen, spezifische NPC-Lebensläufe, freie Dialoge als deterministische handgefertigte Verzweigungen mit variablen Bedingungschecks; keine leeren Fake-Chatinterfaces. Mit 16 gleichaltrige altersgerechte Teenager-Romanze möglich. **Ab 18 kann der Spieler eine neue 16-jährige Agentin oder einen neuen 16-jährigen Agenten erstellen** (standardmäßig jeweils anderes Geschlecht). Voller Editor: Name, Aussehen/Porträt, Charakterzüge, Hintergrund, Fähigkeiten, Startberuf, Interessen; diese Person kommt als eigenständiger NPC hinzu, ist nicht sofort Partner. Vertrauensvoller Beziehungsaufbau über Spielzeit; Altersdifferenz respektieren, keine sexualisierten Szenen mit Minderjährigen. Nach Erwachsenwerden der Beteiligten optional Zusammenziehen, Heirat, Familie, eigene Karriere der Figur.
8. PHOENIX-Verschwörung: Umfangreiche Missionen und mehraktige Story mit Michael, Percy, Amanda etc., alternative Entscheidungen. Handlung original genug, nicht einfach Episoden abschreiben.
9. Ab Teamleiter echte Teamkommandos und Personaleinsatz; später HQ-Ausbau, Budget und Abteilungsverwaltung, Mitarbeiterentwicklung, Fraktionen/Politik. Direktor-Endgame mit frei spielbaren Missionen.
10. Offline Savegames (3 Slots, Autosave), atomare Speicherung, Migrationen, Im-/Export, Setting für Schriftgröße, Animationen, SFX-Lautstärke, Barrierefreiheit, Hinweis auf KI/Medienrechte in Credits.

## UI/UX – außergewöhnlich hochwertig
Portrait 360–430dp-Layouts, Tablets optional. Dunkler taktischer Look mit Navy/Graphit, Türkis-Akzent, subtilen Neonlinien und edlem Frosted-Glass-Effekt. Kein kitschiger Neon-SciFi-Overkill; moderne Spionage-Akte. Hochwertige Typografie (lizenzierte Appfonts), animierte XP-Balken, Agentenkarten, Missionsbriefings, Inventar 2-Spalten mit großen Itembildern, Auswahl- und Dialogszenen mit Charakterporträts, Karte mit HQ-Etagen, Timeline, Beziehungen in Tabs, Skilltree scroll/zoom, Dark-only Theme zunächst. Navigation: HQ, Einsatz, Inventar, Kontakte, Profil; Sekundärmenüs für Training, Skills, Ränge, Questlog, Director HQ, Kalender. UI-Daten ausschließlich aus echtem State; keine Dummys als endgültige Screens.
Bitte echte visuelle Screenshots der App pro Kernscreen in README dokumentieren.

## Daten/Qualität
- Datenkataloge typisiert halten, ids konsistent. Katalog mindestens 42 Items, 14 Charaktere, 27 Skills, 12 Ränge; bis Beta viel erweitern, aber Konsistenz > beliebige Menge.
- Automatische Validierung: alle Assetpfade, IDs, Quest-Ziele, Freigaben, Kapitelgraphen, nicht erreichte Nodes, Save-Migration.
- Einheitliche GameClock/SimulationClock, reproduzierbare RNG Seeds und persistierte NPC-Merkmale.
- Testfälle XP/level 100 cap, 18er Editor-Freigabe, 16er Teenromanze, Altersunterschied, Vertrauen clamp, Itemstacks, Gewichtsgrenzen, Rang 12 Voraussetzungen, Stress/Energie, Save round trip, dialog choices, Missionsfolge, Offline.
- Gradle Wrapper + GitHub Actions CI mit AssembleDebug und Tests. Kein Commit von APK ins Repo erforderlich; GitHub Actions Artifact zur Installation. Nutze Version Catalog wenn sinnvoll. Android SDK compile/target aktualisieren nur validiert.
- Build starten und WIRKLICH kompilieren/testen; auftretende Fehler direkt reparieren. APK als Workflow-Artifact bereitstellen.

## Arbeitspakete (in dieser Reihenfolge)
A: Build und Daten/Assetvalidierung; CI. 
B: Architektur State/DB, Startscreen/Charaktereditor, zentrales UX-Design.
C: Spielbare Story Kapitel 1 mit echten Dialogen und 10 charakterstarken Aufträgen, NPC-Tagesablauf.
D: Inventar, Training, Skilltrees, Wirtschaft, Rangsystem.
E: 2D-Missionssystem und taktische Begegnungen.
F: komplexes Beziehungsnetz + alterungsabhängiger Partnereditor.
G: Teamführung + Director-Management-Grundlage.
H: Qualitätspolitur, Tests, Screenshots, APK und vollständige Dokumentation.

## Definition of Done
Ein neuer Nutzer kann APK installieren, Agenten erstellen, das Prologkapitel spielen, HQ erkunden, echte Missionen mit sinnvoller Auflösung erfüllen, Items mit Bildern aufnehmen/ausrüsten/verbrauchen, trainieren, XP sammeln, Skills wählen, mit NPCs Beziehungen aufbauen und den Spielstand laden. Er kann durch glaubwürdigen Zeitfortschritt das Partnereditor-Feature mit 18 auslösen. Keine abstürzenden Buttons. Keine WebView, kein Internetbedarf für die Grundfunktionen. Keine Show-Stills ohne Nutzungsrecht.

**Deine Aufgabe:** Jetzt mit GitHub-Änderungen beginnen und so weit wie möglich produktiv implementieren. Nicht nur eine Planung schreiben, sondern funktionierende Commits, Builds und Tests. Bei langen Projekten in handlichen Commits arbeiten und in README changelog/noch-offene-Funktionen dokumentieren. Wenn etwas nicht implementiert ist, ehrlich als 'geplant' markieren.