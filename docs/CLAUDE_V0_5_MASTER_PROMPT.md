# CLAUDE CODE – PROTOKOLL NULL v0.5.0-beta (vollständiger Implementierungsauftrag)

## 0. Mission
Du bist der verantwortliche Senior Android Game Engineer und Gameplay-Programmierer. Arbeite direkt im vorhandenen Repository:
https://github.com/GoldenUnicorn24/PROTOKOLL-NULL-Aufstieg-eines-Agenten
Hauptbranch main; lege für v0.5 einen eigenen Arbeitsbranch an (z. B. feature/v0.5-career-rebalance). Implementiere ein vollständig durchspielbares, gut getestetes großes Update v0.5.0-beta. KEINE Neuimplementierung bei null, KEINE Attrappen-Buttons, KEINE reine Planung. Tatsächliche GitHub-Commits, Gradle-Tests, APK und aktualisierte Dokumentation sind Pflicht.

## 1. Vorhandene Basis unbedingt prüfen und bewahren
Lies komplett README.md, docs/AI_WORKFLOW_AND_ROMANCE.md, docs/PRIVATE_CAST_PORTRAITS.md und die aktuellen Android-Quellen. Wichtig:
- Version 0.4: Prolog 13–16, 3 Kapitel, 22 Storymissionen, 19 Quests, 12 Ränge, 100 Level, 9 Skillbäume mit 36 Skills, 42 Items, 13 HQ-Räume, 4 Enden und Epilog, Missionen inklusive Taktik, 3 Speicherslots + Export/Import.
- Engine: engine/Core.kt (Clock/Ages), engine/World.kt (Zeitraffer/Routinen), engine/Progression.kt (Training/Ränge), engine/Inventory.kt, engine/Social.kt (Romance und NPCs), engine/Missions.kt, engine/MissionGenerator.kt, engine/Tactical.kt, engine/Director.kt, engine/Narrative.kt, engine/CompanionAssignment.kt.
- Daten: model/GameState.kt, data/GameData.kt, data/SaveStore.kt, data/ImportedPortraits.kt, alle app/src/main/assets/game/*.json.
- UI: ui/AgentApp.kt, ui/GameViewModel.kt, ui/screens/*, ui/Portraits.kt, ui/CastPortraitUpload.kt.
- Android: Kotlin 2, Compose Material3, minSdk 26, deutsch, offline, Hochformat; Gradle Wrapper + CI und Roborazzi-Render-Tests. Ein Wechsel auf eine andere Engine oder WebView ist ausgeschlossen.
- Kürzlich ergänzt: Alex Udinov als freiwillige Romanzenoption, altersgerechte 18/16-Beziehungen (ab 16 und höchstens 2 Jahre Unterschied), Stufen 3–5 erst bei Volljährigkeit beider. Tests und Dialoge bestehen. Nichts davon entfernen oder zurückdrehen.
- Ebenfalls integriert: ein privater **Offline-Serienporträt-Override**. Geräteseitiger Fotoimport pro Serienfigur und optionaler Ordner app/src/private/assets/cast/show_<id>.jpg, gitignored. Bei fehlendem Bild fällt die App auf die bestehenden CC-Fotos zurück.
- Serienstills sind NICHT frei verteilbare Repository-Assets. Es ist nicht deine Aufgabe, Bilder zu generieren oder aus dem Netz zu beschaffen. Du programmierst die Bildanzeige, Dateiprüfung und Platzhalter, arbeitest mit vorhandenen Bildern. Private Fotos des Nutzers und Cast-Overrides NICHT nach GitHub übertragen.
- GameState und gespeicherte Portrait-IDs müssen mit v0.4-Saves kompatibel bleiben; kleine Patches mit Migrationstests.

## 2. v0.5 Ziel: glaubwürdige Agentenkarriere statt Zahlengrind
Das zentrale Ergebnis soll sein: Ein unbekannter 16-jähriger PHOENIX-Rekrut wird über Training, Aufgaben, Prüfungen, Teamarbeit und echte Entscheidungen zu einem Division-Agenten. 1–100 Level und 12 Ränge bleiben. Neue Systeme müssen tatsächliche Gameplay-Entscheidungen bieten. Keine neue Story-Kampagne als Ersatz für die vorhandenen Kapitel.

## 3. Priorität P0 – Zeitraffer und Wochenroutine vollständig überarbeiten
Analysiere zuerst World.timeSkip(), World.passTime(), Clock, Regeneration, Sold und die bisherigen Routinen. Es gibt bisher potentiell unplausible passive XP- und Rufgewinne, global anwachsende Beziehungen und eine zu starke automatische Regeneration.
Ersetze den generischen "Wochen überspringen und alles steigt"-Mechanismus mit einem transparenten Simulationsplan:
- Wähle Tages-/Wochenschwerpunkte: Kampftraining, Technik, Aufklärung, Sozialleben, Erholung, Dienst, Prüfungsvorbereitung. Mit Mindestanforderungen, Energie, Zeit und Alter.
- Vergib Skillfortschritt/XP nur, wenn konkrete Tätigkeiten erfolgreich simuliert wurden; XP-/Ruf-Kurven mit sinkendem marginalem Ertrag und sinnvollen Obergrenzen.
- NPC-Vertrauen nur erhöhen, wenn NPC überhaupt bekannt, verfügbar und tatsächlich besucht/kontaktiert wurde. Abwesenheit/gescheiterte Interaktion kann Beziehung verändern.
- Verletzungen, Stress, Gesundheit, Schlaf und Klinikbehandlung über realistische Fortschrittsraten; schwere Verletzungen dürfen nicht spontan nach wenigen Klicks verschwinden.
- Simuliere Sold, Ausgaben, Schichten, Termine, Alter, Storyereignisse, Rekrutenankunft exakt einmal. Zeitsprung MUSS Geburtstage, die 18er Gefährteneinführung, Beförderungsbedingungen und Missions-Deadlines korrekt behandeln.
- Bei bedeutenden Ereignissen (z. B. Mentortermin, Abschlussprüfung, Geburtstag, Gefährtenankunft) den Sprung unterbrechen oder eine faire Entscheidungsnachbereitung anzeigen.
- Hochwertige Auswertung "30 Tage in Division" mit Trainingsergebnissen, Ereignissen, Erholung, finanzieller Entwicklung und Begegnungen; nachvollziehbare Protokolle im Save.
- Deterministisch: identischer Seed + identischer Plan ergeben denselben GameState.
Tests: 1/7/30/90/365 Tage, Grenze vor/nach 18, Zeitsprung an Kalender-/Storygrenzen, Ausrüstung, Heilung, XP-/Relationship-Exploits, maximale Level und Wiederholung eines Zeitplans.

## 4. Priorität P0 – Training & Karriere 2.0
Die Engine besitzt Attribute, XP, Skills, Prüfungen, Rangbedingungen und Training. Vertiefe sie:
- Trainingsplaner als echte UI: Trainingsart, Ausbilder, Dauer, Beanspruchung, Fortschritt, Kompetenzstufe und persönliche Ziele.
- 9 Bäume/36 Skills beibehalten; Vorteile klar sichtbar, Voraussetzungen und aktive Fähigkeitseffekte im Spiel nachweisbar.
- Trainerbonus durch Michael oder andere geeignete Ausbilder abhängig von Anwesenheit, Vertrauen, Mentoring und deren eigener Kompetenz.
- Beförderungen als Prüfungen mit zwei oder mehr Phasen und wenigstens einer spielerisch sinnvollen Entscheidung; keine reine zufällige Charisma-/Fitness-Probe, die sich sofort wiederholen lässt.
- Reale Folgen: Prüfung nicht bestanden → Vorbereitung/Auflagen, eine spätere Wiederholung, kein irreversibler Dead-End.
- Nachwuchsrekrut 16 bleibt kein vollwertiger Außendienstagent. Unter-18-Missionen beschränken sich wie bisher auf zulässige Story/Training/Aufklärung. Ab 18 regulär operative Missionen.
- Rangfreigaben für HQ, Ausrüstung, Teamgröße und Kontakte konsistent.
- Karrierejournal: Beförderungen, Ausbildungsnoten, persönliche Erfolge, Auszeichnungen, Mentorenbeurteilungen, bestandene Prüfungen.
- Verhindere Pay-to-level, unendlichen Trainingsexploit und unrealistisches Erreichen von Rang 12 durch Zeitraffer.

## 5. Priorität P1 – Inventar & Ausrüstung 2.0
Bewahre alle vorhandenen 42 Gegenstände und lokalen SVGs samt Lizenzen. Kein KI-Bilderzeugungsauftrag.
- 3–5 Ausrüstungsprofile für unterschiedliche Einsätze, z. B. Aufklärung, Analyse, Erste Hilfe, taktische Unterstützung; Profil wechseln beachtet Inventar, Gewicht, Dienstgrad, Missionssperren.
- Gegenstandsvergleich: Gewicht, Boni, Haltbarkeit, verfügbar/angefordert/ausgerüstet.
- Situative Gegenstandseffekte in mindestens 5 echten Missionsoptionen; begründete Unterschiede, keine erfundenen taktischen Realweltanleitungen.
- Reparatur, Verbrauch, Rückgabe, Lager/Spind und Ausrüstungskammer konsistent und getestet; verhindert Duplikations- und Kaufexploits.
- Items müssen erkennbare, vorhandene SVG- oder Original-Icons zeigen. Fehlt ein Bild, markiere ihn mit Asset-ID in einer Liste für späteres Erstellen durch einen Bildgenerator/Menschen, aber generiere selbst keine Bilder.
- Mehr Gewicht auf Designqualität als auf bloßes Erhöhen der Itemanzahl.

## 6. Priorität P1 – Qualitätssteigerung der bestehenden Hochformat-UI
Kein Re-Design aller Screens auf Verdacht. Zuerst dokumentierte v0.4-Referenzscreenshots lesen und konkrete UX-Probleme identifizieren.
- Zeitraffer als Assistent mit Plan/Ergebnisansicht.
- Training-/Prüfungs-/Karriereprofil verbessert, UI-Zustände lesbar und konsistent.
- Itemkacheln mit großen Bildern, einfach verständlichem Vergleich und Equip-Feedback.
- Layouts bei 360, 393, 430 dp; Dark Mode, gute Kontraste, ausreichend große Touchflächen und Textgrößen.
- Keine Fake-Buttons oder Platzhalter, die eine nicht implementierte Spielhandlung vortäuschen.
- Die vorhandenen serienspezifischen Porträtfotos und die nun verfügbare PrivateCastPortraits-Priorität erhalten. Zusätzliche Bilder kommen außerhalb dieses Coding-Auftrags.
- Roborazzi-Screenshot-Suite um mindestens 4 relevante v0.5-Ansichten ergänzen.

## 7. Priorität P1 – Fehlerfreiheit, Savegame-Stabilität, Balance-Tests
- Lokale sichere Speicherung; v0.4-Saves und Legacy-Import bleiben lesbar; alle neuen Felder besitzen sinnvolle Defaults oder eine explizite Migration.
- Export/Import und mehrere Slots funktionieren mit v0.5-Features weiter; private Darsteller-Bilder NICHT in Save-Exports einbetten oder in Git veröffentlichen.
- Kein unkontrolliertes Laden riesiger Bitmaps; NpcPortrait-Override nur für existierende bekannte Serienfiguren.
- Unit-Tests für sämtliche neuen Regeln plus eine mindestens 10-jährige automatische Spielverlaufs-Simulation, die Kalender, Beziehungspunkte, Verletzungen, Geld, Erfahrung, Ränge und Ereignisidentitäten prüft.
- Prüfe im gesamten Code unbeabsichtigte negative Bestände, zu häufige Events, Zustände ohne Ausweg, ausgefallene NPC-Begegnungen und unmögliche Beförderungsbedingungen.
- Keine Internetpflicht, keine externen API-Schlüssel, keine Logins und keine WebView.
- Dokumentiere Performance, Dateigröße und ggf. verbleibende Risiken.

## 8. Nicht Teil von v0.5 (für spätere Versionen vormerken)
v0.6: vollständige autonome NPC-Simulation, Gefährtenkarrieren und umfangreichere Alex-Route.
v0.7: viele neue Missionstypen, mehr Taktikkarten, dynamischer Missionsdirektor.
v0.8: ausgebaute Führung/Standorte/Privatleben.
v0.9: integrierte Langzeit-Beta und Endgame.
Kein Scope-Creep; die aktuelle Version muss zuerst hochwertig und zuverlässig werden.

## 9. Arbeitsweise und GitHub-Schutz
1. Repository, bestehende Inhalte und aktuellen main-Stand vollständig lesen. Arbeite mit vorhandenem Code, nicht per blindem Suchen/Ersetzen.
2. Lege einen Feature-Branch an und implementiere P0, dann P1 in kleinen nachvollziehbaren Commits. Keine parallelen Änderungen am selben File durch mehrere Agenten.
3. Nach jedem Paket: ./gradlew testDebugUnitTest assembleDebug; vor einem PR außerdem ./gradlew recordRoborazziDebug, sofern realisierbar. Fehler beheben, nicht Tests deaktivieren.
4. PR mit präzisem Changelog und Liste der verifizierten Features erstellen. Bestehende v0.4-Spielstände (inklusive Vorab-Gefährtenakte und Alex-Flag) absichern. Keine manuellen Änderungen am main ohne Review.
5. .github/workflows/android.yml muss erfolgreich laufen; Debug-APK als Artifact in GitHub Actions. Nutzer möchte eine APK zum Testen, nicht nur Source-Code.
6. Version erst nach erfolgreich bestandenem Build auf 0.5.0-beta setzen, versionCode erhöhen, README und Screenshots aktualisieren.
7. Abschlussbericht mit konkreten geänderten Dateien, Tests, offenem Rest, APK-Link auf Actions und reproduzierbaren Installationsschritten. Nicht behaupten, Dinge seien spielbar, wenn sie nur Platzhalter sind.
8. Falls du keine Berechtigung/Build-Umgebung hast: Änderungen in Branch als PR soweit möglich und konkrete, überprüfbare Einschränkung berichten. Keine erfundenen Testresultate.

## 10. Definition of Done für v0.5
- Zeitraffer ist eine nachvollziehbare Simulation und kein XP-/Beziehungs-Exploit.
- Karriereprüfungen sind echte Spielmechaniken und Beförderungen fühlen sich verdient an.
- Trainings- und Ausrüstungsmenüs bieten konkrete Handlungen, keine reinen Visualisierungen.
- Private Serienporträts bleiben lokal und werden an allen NPC-Porträtstellen dargestellt; Originalbilder/Fallbacks funktionieren.
- Alter, Partnereditor, Alex-Romanze und v0.4-Spielstände funktionieren unverändert.
- Unit-Tests, Datenvalidierung, Screenshot-Smoke-Test und assembleDebug erfolgreich.
- Eine installierbare v0.5-Debug-APK ist als GitHub Actions Artifact verfügbar.

BEGINNE JETZT MIT DER IMPLEMENTIERUNG IM REPO. Erstelle nicht nur ein Konzept. Lies die genannten Dateien, überprüfe bestehende Tests, formuliere im PR präzise Abnahmekriterien und arbeite die Aufgaben in der festgelegten Reihenfolge ab.
