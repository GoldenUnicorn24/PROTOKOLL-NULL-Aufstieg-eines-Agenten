# CLAUDE CODE – PROTOKOLL NULL v0.7.0-beta: Shadow Operations

## Mission
Du bist Senior Android Game Engineer für PROTOKOLL NULL – Aufstieg eines Agenten, Repository https://github.com/GoldenUnicorn24/PROTOKOLL-NULL-Aufstieg-eines-Agenten. Implementiere die echte, installierbare 0.7.0-beta auf der aktuellsten stabilen main-Basis. Kein Konzept-only, kein Neuanfang und keine Attrappen-Funktionen.

## Vorbedingung
1. Lies README, docs/LIVING_DIVISION.md, docs/V06_ROMANCE_UPDATE.md und docs/CAST_BUNDLE_GUIDE.md sowie tatsächlich alle relevanten Dateien unter engine/Missions.kt, MissionGenerator.kt, Tactical.kt, World.kt, Life.kt, model/GameState.kt und ui/screens/MissionScreens.kt / TacticalScreen.kt.
2. Warte, bis die zentrale Serienbild-Verwaltung aus dem GitHub-PR für settings-bulk-cast-portraits vollständig getestet und in main gemergt ist. Sobald erledigt: neuen Branch feature/v0.7-shadow-operations anlegen.
3. Erhalte alle v0.6-Funktionen: unabhängige NPCs, Gefährtenlebensläufe, freiwillige altersgerechte 16/17- und 18/16-Spielerromanzen nur ohne explizite Inhalte, Alex-Story, HQ-Events, Karriere, Zeitraffer, Portraits, Prolog, Story, vier Enden und alte Savegames.
4. Die App bleibt nativ Kotlin 2 / Jetpack Compose, Android minSdk26, offline, Hochformat, 2D. Keine 3D-Engine, WebView, Cloud-API, Onlinepflicht und keine personenbezogenen Daten in GitHub.

## Priorität P0 – Missionsdirektor 2.0
- Der bisherige MissionGenerator (drei taktische Vorlagen, wiederkehrende Auftragstypen) bekommt eine deterministische Missionsgenerierung aus modularen Orten, Einstiegssituationen, Auftraggebern, Zielen, Nebenbedingungen, Konsequenzen und Handlungsketten. Statische Storymissionen M001–M022 bleiben erhalten.
- Wenigstens 8–10 differenzierte Missionsarchetypen: Observation, Undercover, Aufklärung, Verhandlung, Datenauswertung, Rettung, Informationsbeschaffung, Eskorte, Schutz, koordinierter Einsatz. Ungefährliche Aktivitäten für Minderjährige bleiben unverändert eingeschränkt.
- Jede dynamische Mission erhält eigenes Briefing, mindestens zwei alternative Vorgehensweisen, erkennbare Risiken, missionsspezifische Belohnungen und Konsequenzen für Folgeaufträge. Keine bloß zufällig ausgetauschten Ortsnamen.
- Missionen dürfen über mehrere Spieltage laufen und auf Vertagen, Abbruch, Zeitablauf und Erfolg plausibel reagieren.
- Missionsboard: Filter nach Region, Schwierigkeitsgrad, Dringlichkeit, Einsatzart, Team und verfügbarer Zeit. Keine Mission, die ohne Begründung einfach verschwindet.
- Seed und Spielzustand bestimmen reproduzierbar den Missionspool; erneutes Öffnen oder Laden dupliziert keine Missionen.

## Priorität P0 – 2D-Taktik 2.0
- Erhalte den existierenden rundenbasierten 2D-Kern: BFS-Bewegung, Action Points, Deckung, Reichweite, Trefferchance, Teamfähigkeiten, Gegner-KI.
- Erweitere auf mindestens 12 wirklich unterschiedliche spielbare Kartenlayouts als erste v0.7-Etappe; mindestens vier Umgebungstypen. Karten sollen nicht lediglich umbenannte Varianten des bisherigen 3-Karten-Systems sein.
- Implementiere korrekte Sichtlinien, Hindernisprüfungen und verständliche Anzeige für Deckung, Sicht und Bewegungsreichweite. Keine Angriffe durch massive Wände.
- Ziele wie sichere Evakuierung, Informationssicherung, Aufklärungspunkte, Rendezvous, Schutzpositionen; Taktik darf nicht nur 'alle Gegner ausschalten' bedeuten.
- Gegner-KI mit einfachen taktischen Rollen, Bewegung/Deckung, Schutz wichtiger Punkte, nachvollziehbarem Schwierigkeitsgrad. Einsetzbare verbündete NPCs sollen ihre Kompetenzwerte aus Life.kt und individuelle Fähigkeiten mitbringen.
- Gute Touchsteuerung im Hochformat, responsive bei 360–430dp, übersichtliche AP/Bewegung/Zielanzeige, eindeutiger Einsatzstatus. Kein Textoverlay über dem Spielfeld.
- Einheitstests für Kartenvalidität, BFS, Line-of-Sight, Deckungswirkungen, Missionserfüllung, Stalemate-Erkennung und Reproduzierbarkeit.

## Priorität P1 – Ermittlungen, Hinweisnetz und Konsequenzen
- Baue ein Beweis- und Ermittlungsjournal: unterschiedliche Hinweise, Quellenqualität, Widersprüche, Verbindung von Personen/Orten und 2–3 alternative Lösungswege. Reine Zufallsrätsel reichen nicht.
- Akten sollen nach Missionsentscheidungen neue Informationen und Storyflags freischalten; bereits bekannte Hinweise bleiben bei Save-Load erhalten.
- Fraktionen/Organisationen bekommen persistente Haltungen, Ressourcen-/Einflussindikatoren und wiederkehrende Konfliktbögen. Eine Entscheidung im Einsatz beeinflusst später Aufträge und Kontakte.
- Keine realweltlich operativ gefährlichen Anleitungstexte; fiktive, abstrakte Gameplay-Mechaniken und narrative Ergebnisse.

## Priorität P1 – Einbindung der lebendigen Division
- Missionsauswahl berücksichtigt HQ-Personalanwesenheit, Verletzungen, aktuelle Einsätze, Urlaub, Training und Teamzusammenstellung.
- Der Spieler kann NPCs mit passenden Fähigkeiten auswählen, soweit Rang und Verfügbarkeit es erlauben. Gefährtin startet mit 16 und darf vor 18 nur sichere Aufgaben übernehmen.
- Ein Einsatz verändert NPC-Missionsbilanz, Verletzungszustand, Vertrauen und persönliche Erinnerungen; doppelte Belohnung oder Endlosschleifen sind ausgeschlossen.
- Michael, Birkhoff, Alex, Nikita, Percy, Amanda sollen je nach Storystatus passende Angebote, Briefings und Reaktionen erhalten; nur dort, wo die Figur plausibel verfügbar ist.

## Priorität P1 – Story- und Content-Ausbau
- Bewahre die vorhandenen 22 Hauptstorymissionen. Ergänze mindestens 8–12 ausgearbeitete optionale Missionsketten/Story-Episoden mit echten Wahlfolgen statt Copy-Paste-Text.
- Erweitere die PHOENIX- und Divisionspolitik-Handlungsstränge mit Erkenntnissen, Verrat, Loyalität, Mentoring und schwierigen Entscheidungen, aber ohne den ursprünglichen Handlungsverlauf oder die vier Enden zu zerstören.
- Sichere Übergänge zwischen Story- und Sandbox-Missionen; keine unerreichbaren Storyflags oder versehentlich überschriebenen Saves.

## Priorität P1 – UI, Audio, Assets
- Neues Mission-Control im Dark-Spy-Stil: Briefing, Einsatzplanung, Ermittlungsjournal, Weltlage, taktische Karten, Debrief und langfristige Konsequenzen. Alles mit klarer Portrait-Navigation.
- Nutze vorhandene Hintergründe/Icons und originale oder lizenzierte Assets. Fehlt ein Bild, dokumentiere Dateiname, Format, Motiv und Zielauflösung für späteres Generieren; erzeuge oder beschaffe als Coding-Agent nicht selbst copyrighted Cast-Stills.
- Erhalte den Zentralimport aus Einstellungen → Serienbilder (ZIP, Einzelkorrektur und Zurücksetzen) und die automatische BuildConfig-Versionsanzeige. Keine Rückkehr zu Importknöpfen in den Charakterakten.
- Optional neue abstrakte Karten-/Symbolgrafik als Kotlin Compose Canvas oder SVG, aber kein unberechtigter Download von Bildmaterial.

## Qualitätssicherung
- Mindestens 20 neue gezielte Unit-Tests (Missionen, Taktik, Fraktionen, Teamstatus, Save-Migration, 10-Jahres-Spielverlauf und unter 18 verfügbare Missionen). Screen-Tests für mindestens 6 neue Ansichten.
- Alter, Partnerregeln, v0.4/v0.5/v0.6-Speicherstände, Save-Import/Export, Companion-Einführung und Alex-Flags müssen unverändert korrekt bleiben.
- Deterministische Engine und begrenzte Eventhistorien, keine Ressourcen-Exploits, keine KI-Würfe, die ohne Seed unvorhersehbar sind.
- Vor PR alle Tests und assembleDebug erfolgreich. CI muss grün sein. APK als GitHub Actions Artifact. VersionCode erhöhen und versionName auf 0.7.0-beta erst nach erfolgreicher Prüfung setzen.
- Entwickle in kleinen Commits und einem eigenen Feature-Branch, keine parallelen Änderungen auf main. Dokumentiere nicht implementierte Punkte transparent und teste die tatsächlichen Spielpfade.

## Definition of Done
Eine Beta mit neuen Missionen, bedeutungsvollen Konsequenzen, sichtbarer Weltentwicklung, hochwertiger 2D-Taktik und bereits bestehenden NPC-/Beziehungs-/Karrieresystemen. Alles offline und Android-nativ, Build grün, APK installiert testbar, stabile Saves und verständliche Dokumentation.

START: Repository prüfen, GitHub-PR für den Cast-ZIP-Import abwarten beziehungsweise nach grünem CI integrieren, dann v0.7 tatsächlich implementieren.