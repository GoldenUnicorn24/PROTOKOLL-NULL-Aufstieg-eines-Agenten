# PROTOKOLL NULL – v1.0 FULL RELEASE
## VERBINDLICHER MASTER-AUFTRAG AN CLAUDE CODE: DAS GESAMTE SPIEL AUDITIEREN, ÜBERARBEITEN UND VERBESSERN

**Repository:** https://github.com/GoldenUnicorn24/PROTOKOLL-NULL-Aufstieg-eines-Agenten  
**Ausgangsbasis:** aktueller `main` mit v0.8.0-beta „Director's Legacy“; die tatsächliche Codebasis ist maßgeblich.  
**Ziel:** Direkt eine tatsächlich spielbare, umfangreiche, deutlich hochwertigere **v1.0** entwickeln – **nicht nur neue Inhalte hinzufügen, sondern ALLES auditieren, refaktorieren, spielerisch vertiefen und die komplette Benutzeroberfläche professionell überarbeiten**. Die anschließende Codex-Überprüfung ist ein zusätzlicher Qualitätsschritt und **kein Ersatz** dafür, dass Claude selbst erstklassige UI/UX und sauberen Code liefert.

### Grundregeln

1. **Nicht neu anfangen:** Kotlin/Jetpack Compose, lokale Offline-Simulation, Savegame-Struktur, Datenkataloge, vorhandene Story, Missionen, Beziehungssystem, Figureneditor, Taktik, Lebenssimulation, Privatleben, Führungsfunktionen, Bilderimport und Tests erhalten. Keine funktionierenden Features opfern.
2. **Code wirklich verstehen:** Lies README, alle relevanten Dateien unter `docs/`, `app/src/main/java/de/protokollnull/agent/{engine,model,data,ui,audio}`, die JSON-Inhaltskataloge und sämtliche Tests. Verifiziere Aussagen anhand des Codes, nicht nur anhand der Dokumentation.
3. **Selbstständig arbeiten:** Plane die Umsetzung nach Abhängigkeiten, arbeite in nachvollziehbaren Commits, behebe Probleme sofort, halte bestehende Funktionen intakt. Frage nur bei echten, nicht auflösbaren Blockern.
4. **Keine Fake-Fertigstellung:** Keine Mockup-Screens als fertige Features, keine dekorativen Buttons ohne Wirkung, keine erfundenen Tests, keine abgespeckten Inhaltskataloge, keine abgeschalteten Tests, keine Release-Bezeichnung ohne erfüllte Abnahmekriterien.
5. **Qualität vor Menge:** Falls Umfang und Stabilität kollidieren, priorisiere solide Kernmechaniken, echte Interaktionen, kohärente Story, verlässliche Savegames und hochwertige UI.
6. **Keine geschützten Serienstills ungefragt veröffentlichen:** Für den privaten Nikita-Fanprototyp vorhandenen privaten ZIP-Import behalten; nur berechtigt nutzbare Assets ins öffentliche Repo. Öffentliche/kommerzielle Veröffentlichung benötigt Rechtefreigaben oder eigenständige Figuren.

## A. ZUERST: UMFASSENDER IST-ZUSTAND UND AUDIT DES GESAMTEN SPIELS

Prüfe **jede** Hauptfunktion von Anfang bis Ende, inklusive der Verknüpfungen zwischen Systemen:

- Start, Fortsetzen, Charaktereditor, eigener Avatar/Fotopicker, Schwierigkeitsgrad, Prolog und alle drei Storykapitel samt Enden und Postgame.
- Jede UI-Seite, Navigation, Zurück-Logik, Dialoge, Insets, Scrollverhalten, leere Zustände, Fehlerbehandlung, Lesbarkeit, Touchflächen, Schriftgrößen, Kontrast, Performance und die unterschiedliche Darstellung auf 360–430 dp.
- Missionsboard, dynamische und handgeschriebene Missionen, Briefing, Vorbereitung, Teamwahl, Nachbesprechung, Ermittlungsjournal, Fraktionen, Auftragsketten.
- 2D-Taktik: Karten, Gelände, Zielbedingungen, Sichtlinien, Bewegung, AP, Gegner-KI, Verbündeten-Verhalten, Missionsabschluss und Patt-Szenarien.
- Inventar, 102 bestehende Items, Bilder, Ausrüstungskammer, Gegenstandsverbrauch, Tragkraft, Ranggrenzen, Profile, Boni, Ökonomie.
- Level 1–100, zwölf Dienstgrade, neun Skilltrees, Training, Prüfungen, Beurteilungen, Mentoring und Zeitraffer.
- NPCs, echte Tagesabläufe, Laufbahnen, Abwesenheit, Verletzungen, soziale Interaktionen, Rivalitäten, Freundschaften, freiwillige Romanzen, eigene Gefährten.
- Teamleitung, Delegation, Abteilungsleitung, Direktion, Standorte, HQ-Ausbau, Finanzen, Personal, Berichte, Langzeit-Endgame.
- Wohnungen, Stadt, Freizeit, Tarnjobs, Alltagsereignisse, Beziehungen zwischen Privatleben und Karriere.
- Speichern/Laden/Export/Import, alte Spielstände und Schema-Migration, Offlineverhalten, Audio, Medien-/Bildimport und lokale Assets.
- CI, Build, Testqualität, GitHub Actions, Screenshots, Gradle, eventuelle Bugs/Code-Duplikate.

**Audit-Ergebnis als `docs/V1_AUDIT.md`:** pro System tatsächlicher Ist-Zustand, reproduzierbare Probleme, Designschwächen, technische Risiken, die konkreten Verbesserungen und ihre Priorität. Fehlende oder falsche Funktionalität nicht schönreden. Danach den Audit wirklich abarbeiten – nicht bei einer reinen Liste stehen bleiben.

## B. VOLLSTÄNDIGES UI-/UX-REDESIGN – EINE DEINER WICHTIGSTEN AUFGABEN

**Claude muss die UI selbst umfangreich prüfen, neu gestalten und fertig verbessern – nicht auf die spätere Codex-Phase verschieben.**

Designziel: hochwertiges, modernes, cinematisches **Spionage-RPG** statt einfacher Verwaltungsoberfläche. Dark-Tactical-Look mit Graphit/Navy, zurückhaltendem Türkis, klarer Informationshierarchie, stilvoller Typografie, passenden Illustrationen, stimmigen Mikroanimationen und flüssiger Bedienung. Hochwertig, nicht überladen.

Verbindliche Arbeitsschritte:

1. Inventarisiere sämtliche Compose-Screens, Komponenten und Navigationsebenen. Identifiziere inkonsistente Layouts, zu kleine Textflächen, unklare Aktionen, schwache visuelle Hierarchie, überfüllte Bildschirme, unnötige Klickwege und nicht passende Gestaltungsstile.
2. Erstelle/verbessere ein wiederverwendbares Designsystem mit Farben, Typografie, Spacing, Cards, Dialogen, Tabellen, Tabs, Buttons, Icons, Statusanzeigen, Modal-Panels, leeren Zuständen, Lade- und Fehleranzeigen. Keine willkürlichen Einzel-Screen-Stile.
3. Überarbeite **alle** Kernseiten: Start/Charaktereditor, Prolog, Storydialog, HQ/Lageplan, Missionsboard, Briefing, Taktik, Missionsende, Inventar, Gegenstandsdetails, Ausrüstungskammer, Kontakte, NPC-Personalakte, Beziehungen, Gefährteneditor, Karriere, Prüfung, Skilltree, Kalender/Zeitraffer, Fraktionen/Akten/Ketten, Teamführung, Delegation, Direktorat, Standorte, Wochenbericht, Stadt, Wohnung und Einstellungen.
4. Verwende die tatsächlichen NPC-Porträts und vorhandenen Bilder richtig; der private ZIP-Bilderimport soll konsistent in allen relevanten Screens wirken. Keine generischen Platzhalter, wenn importierte Bilder existieren.
5. Stelle sicher, dass Nutzer im Alltag schnelle Aktionen finden können: klare Hauptnavigation, sinnvolle Dashboard-Informationen, Kontextaktionen, eindeutige Freigaben, verständliche Dialogauswahl, lesbare Fortschrittsanzeigen, brauchbare Filter und Berichte.
6. Jeder Screen muss mit kleinem Display, langer deutscher Beschriftung, großer eingestellter Schrift und Hochformat funktionieren. Achte auf TalkBack-Semantik, Touch-Ziele, hohen Kontrast, reduzierte Animationen und Systemleisten.
7. Sinnvolle Animationen für Dialoge, Missionsschritte, XP, Rangaufstieg, Erfolge und Panels einbauen. Performance und zugängliche Reduktionsoption beachten.
8. Taktikbrett visuell deutlich aufwerten: erkennbare Figuren, Deckung, Reichweiten, Ziele, Bewegungsrückmeldung, Lesbarkeit und logische Touch-Steuerung – alles an den echten State angebunden.
9. Aktualisiere **echte automatisiert gerenderte Screenshots** der Kernseiten und prüfe die sichtbaren Ergebnisse erneut. Führe danach **einen zweiten visuellen Reviewdurchlauf** durch und behebe eigene Schwächen.
10. Schreibe `docs/V1_UI_REVIEW.md` mit Screeninventar, Vorher-/Nachher-Problemen, neuen Komponenten, Screenshot-Verweisen, bekannten visuellen Restproblemen und nachvollziehbarer Qualitätsbewertung.

**UI-Abnahme:** Keine Hauptseite bleibt unüberarbeitet, nur weil sie „bereits funktioniert“. UI ist integraler Bestandteil dieses V1-Auftrags, nicht ein optionales Spätprojekt.

## C. STORY, INHALTE UND MISSIONS-ERLEBNIS

- Vorhandene 22 Storymissionen, drei Kapitel, vier Enden und alle wichtigen Charaktere bewahren; ursprüngliche Enden nicht löschen.
- Hauptgeschichte spürbar vertiefen: PHOENIX-Vergangenheit, Voss, Michaels Mentoring, Nikita, Alex, Birkhoff, Amanda, Percy, Kira, Helena, Loyalität, Verrat, Aufstieg, persönliche Konsequenzen und Entscheidungen, die später wieder aufgegriffen werden.
- Zielkorridor ungefähr **45–60 hochwertige Storymissionen** nur soweit sinnvoll und vollständig integrierbar; ansonsten Qualität und ein abgeschlossenes Spielerlebnis priorisieren.
- Mehr individuell geschriebene Dialoge, Nebenquests, verzweigte Charaktergeschichten und mindestens zwei sinnvolle zusätzliche Endvarianten, falls Story-Graph und Tests dies sauber tragen.
- Missionsdirektor 3.0: abwechslungsreiche Orts-, Einstieg-, Auftraggeber-, Ziel-, Gefahr-, Fraktions-, Team-, Zeit- und Folgekonfigurationen; Duplikate und repetitive Abläufe reduzieren; unterschiedliche Missionsarten fühlbar unterschiedlich spielen lassen.
- Mehrstufige Einsätze mit Information, Planung, Durchführung, Überraschungen, optionaler Taktik, Entscheidungen und echter Nachwirkung; ohne übertriebene Pflichtphasen bei kurzen Aufträgen.
- Taktik 3.0: verbesserte Gegner- und Verbündeten-KI, zusätzliche handgestaltete Karten (Ziel 20+), vielfältige Ziele, verlässliche Wegfindung, Deckung/LoS und klare Interaktion. Action und Waffen bleiben abstrakte fiktionale Spielmechanik.
- Ausbau von Fraktionskonflikten, Ermittlungsfällen, optionalen Ketten und Konsequenzsystemen. Keine NPCs anbieten, die laut Simulation abwesend, gesperrt oder verletzt sind.

## D. LEBENDIGE DIVISION, KARRIERE UND BEZIEHUNGEN

- NPCs sollen nachvollziehbar **eigenständig** trainieren, arbeiten, Freizeit haben, Einsätze übernehmen, Freundschaften und Rivalitäten entwickeln, fehlen, krank werden, sich erholen, die Karriere vorantreiben und eigene Ziele verfolgen.
- Bestehendes deterministisches Memory-/Relationship-System vertiefen: strukturierte Erinnerungen an Missions-, Dialog-, Privatleben- und Personalentscheidungen, mit Auswirkungen auf späteres Verhalten; alte Erinnerungen kompakt halten.
- Die selbst erstellte Gefährtin / der Gefährte bleibt eine **eigenständige zweite Hauptfigur** mit eigenen Charakterzügen, Werten, Interessen, Lebensziel, Berufswunsch, Karriere, sozialem Umfeld und Entscheidungsautonomie. Keine automatisch erzwungene Beziehung.
- Mehr einzigartige Szenen und gemeinsame Aktivitäten mit Alex, Michael, Nikita, Birkhoff, eigenem Gefährten und anderen wichtigen Kontakten.
- Karriere 3.0: detailliertere persönliche Ziele, Prüfungen, Mentoring, Beurteilungen, unterschiedliche Karrierewege und Konsequenzen. Rangaufstieg soll verdient sein, nicht nur durch Warten erfolgen.
- **Alters-/Romanzengrenzen unverändert erhalten:** Freiwillige nicht-sexuelle 16/17- und 18/16-Romanzen sind bei höchstens zwei Jahren Altersabstand bis Stufe 2 möglich; kein Zwang oder Machtmissbrauch. Zusammenziehen/Heirat erst bei zwei Volljährigen, Familienplanung gemäß bestehender 21er-Regel, Alex optional; Regressionstests bleiben Pflicht.

## E. DIREKTORAT, TEAMFÜHRUNG, STANDORTE UND PRIVATLEBEN

- Teamrollen, Personalzuordnung, Mentor:innen, Urlaubs-/Krankheitsstatus, Kohäsion und echte Delegation weiterentwickeln. Eine Person darf nicht gleichzeitig doppelt verplant werden.
- Bestehenden `DirectorState` / `OpsState` nutzen, nicht zweite konkurrierende Systeme einführen. Organisations- und Privatbudget sauber getrennt, Wochen- und Monatsabrechnung idempotent, auch bei Zeitraffer und Save/Load.
- Fünf vorhandene Abteilungen, 13 HQ-Räume, sechs HQ-Module und vier Außenstellen bewahren. Zusätzliche spezialisierte Abteilungen/Standorte nur mit echten Auswirkungen, Personal, Kosten und Integration.
- Strategische Direktoratskrisen, Rivalitäten, politische Richtungsentscheidungen, Projekte, Personalentwicklung und Fraktionseffekte vertiefen; Rang 12 muss **langfristig interessante Entscheidungen** bieten.
- Privatleben 3.0: Stadtkalender, Wohnungen, Möblierung, Hobbys, Treffen, Tarnjobs, Alltagsereignisse und zivile Kontakte in ein kohärentes Spielgefühl integrieren, mit tatsächlicher Auswirkung auf Zeit/Energie/Stress/Geld/Beziehungen.
- Mehr besuchbare Orte nur dann ergänzen, wenn sie spielbar und visuell sinnvoll eingebunden sind. Wohnung, Orte und Kontakte dürfen nicht ausschließlich abstrakte Einnahmen- oder Klickbuttons sein.

## F. INVENTAR, SKILLS, SPIELBALANCE

- Bereits vorhandene **102 Items inklusive 60 v0.7-Ergänzungen** und deren Bilder behalten. Neue sinnvolle Gegenstände (Ziel 120–160) nur mit tatsächlichem Nutzen. Bessere Item-Vergleiche, Ausrüstungsprofile, Inventar-UX, Symbolik, Rang-/Altersregeln, Verschleiß und Interaktionen.
- Level 1–100, zwölf Ränge, neun Skillbäume und 36 Skills beibehalten und die Einsatzmöglichkeiten vertiefen. Keine sinnlosen neuen Levelcaps.
- Balance über die gesamte Karriere: XP, Training, Rangfreigaben, Schwierigkeitsgrade, Geld, Wunden, Stress, NPC-Fortschritt, Teamleistung, Delegation, Führungsprojekte.
- Exploits verhindern: keine doppelte Entlohnung, keine mehrfachen Events, keine gratis Schicht ohne Leistung, keine doppelte Missionsabrechnung, keine unendlichen Belohnungen durch UI, Simulationssprung, Import oder Laden.
- Langzeitspielbarkeit mindestens 10 simulierte Jahre mit konsistenter Datenlage und ohne permanent blockierte Progression überprüfen.

## G. TECHNISCHE STABILITÄT, TESTS UND RELEASE

- Nativ Android, Kotlin 2/Compose, minSdk 26, Portrait und offline beibehalten. Keine WebView und keine Internetpflicht.
- 3 Speicherslots, Autosave, atomare Speicherung, Import/Export und Schema-Migration aus bisherigen Versionen bewahren. Insbesondere umfangreiche v0.8-Spielstände mit laufenden Missionen, Delegationen, Beziehungen, Wohnungen, Inventar, Fraktionen, Projekten migrieren.
- UI-Thread nicht mit Zeitsimulationen blockieren. CPU-/Speicherkosten, Compose-Recomposition, Assets, Bilder und Startup optimieren.
- Gegen Crashes, Navigation-Dead-Ends, ungültige IDs, verwaiste Dialoge, nicht erreichbare Questziele, NPC-Doppelbelegung, Rang-/Altersumgehungen und Datenverlust testen.
- Tests erweitern für **End-to-End-Spielpfade** (Prolog bis Endgame), alle Enden, Missionen/Taktik, NPC-Leben, Romanzenregeln, Management, Langzeitsimulation, Save-Migration, UI-Screen-Rendering und kritische UI-Interaktionen.
- Führe verbindlich aus:
  ```bash
  ./gradlew testDebugUnitTest assembleDebug
  ./gradlew recordRoborazziDebug
  ```
  Instrumentation-/Gerätetests, wenn ausführbar, durchführen. Wenn nicht, als offen dokumentieren und **keine realen Gerätetests vortäuschen**.
- GitHub Actions müssen durchlaufen, ein Debug-APK-Artefakt bereitstellen. Release-APK-Prozess vorbereiten, keine geheimen Schlüssel ins Repo committen. `versionName=1.0.0` und `versionCode=10` nur nach erfüllten Pflichtkriterien; andernfalls ehrlich als RC/Beta bezeichnen.
- Für mögliche öffentliche Veröffentlichung die urheberrechtliche Situation des Nikita-Fanprojekts deutlich dokumentieren.

## H. OBLIGATORISCHER ZWEITER UND DRITTER QUALITÄTSDURCHGANG DURCH CLAUDE SELBST

**Das ist ein eigenständiger Pflichtauftrag, nicht optional.**

1. **Erster Durchgang – Entwicklung:** Die bestehenden Systeme und das gesamte UI verbessern und neue Inhalte integrieren.
2. **Zweiter Durchgang – kompromissloser Selbst-Audit:** Lies und prüfe den *geänderten* Code erneut, als wärst du ein unabhängiger Senior-Reviewer. Suche nach schlechter Architektur, redundanten Klassen, fehlenden Interaktionen, fragwürdiger UI, schwer lesbaren Texten, fehlerhaften Icons, gebrochenen Spielständen, kaputtem Balancing und narrativen Widersprüchen. Überarbeite die gefundenen Schwächen **selbst im Code**, statt sie nur aufzulisten.
3. **Dritter Durchgang – Qualitätssicherung und UI-Politur:** Screenshots der echten App vergleichen, Navigation auf kleinen Displays prüfen, UI weiter veredeln, Gameplay-Smoke-Tests fahren, Tests erneut ausführen, Probleme reparieren und abschließend den Build nachweisen.

**Anspruch:** Hinterlasse die App **deutlich besser** als v0.8 in **Gameplay, Atmosphäre, UI, Stabilität, Qualität und Langzeitmotivation**, nicht nur in der Anzahl der Features.

## I. GITHUB-WORKFLOW UND HANDOFF

- Arbeite in `feature/v1.0-full-release` auf Basis des aktuellen main; klare thematische Commits. Änderungen nach Möglichkeit in überprüfbaren Pull Request gegen main einreichen, nicht blind selbst mergen.
- Erstelle bzw. aktualisiere:
  - `docs/V1_AUDIT.md` – umfassende Bestandsaufnahme, echte Probleme, Fixstatus.
  - `docs/V1_UI_REVIEW.md` – vollständige UI-Analyse und tatsächliche Änderungen, Screenshot-Beweise.
  - `docs/V1_RELEASE_CHECKLIST.md` – Prüfungen, offene Punkte, Abnahme.
  - `docs/CODEX_HANDOFF_V1.md` – Architektur, UI-Komponenten, wichtige Änderungen, offene Schwachstellen für die spätere Codex-Nachprüfung.
  - `docs/V1_GAME_REVIEW.md` – objektive Feature-/Gameplay-/Technikübersicht und offene Einschränkungen für spätere externe Bewertung durch Grok.
  - `README.md` – tatsächlicher Versionsstand, Installationsanleitung und changelog.
- Am Ende: Branch/PR, implementierte Features, Testergebnisse, CI-Link/APK-Artefakt, bekannte Einschränkungen und ehrlich dokumentierte offene Aufgaben angeben.

### Finale Definition of Done

Ein echter neuer Spieler kann das Spiel installieren, Agenten erstellen, die gesamte Story bis zu mehreren Enden durchspielen, Missionen inkl. Taktik absolvieren, Karriere machen, NPCs kennenlernen und Beziehungen entwickeln, einen eigenen Gefährten anlegen, sein Privatleben führen, Team/Division leiten und auch nach Rang 12 sinnvoll weiterspielen. Screens wirken zusammengehörig, modern, schnell und klar; alle Kernbuttons funktionieren. Savegames gehen nicht verloren. Bestehende Funktionen bleiben intakt. Test- und Build-Beweise sind vorhanden.

**Beginne JETZT mit einem gründlichen Repo-Audit, implementiere danach konsequent die Verbesserungen, führe die verpflichtenden Selbst-Reviews durch und bereite ein überprüfbares v1.0-Release vor. Keine reine Konzeptantwort.**