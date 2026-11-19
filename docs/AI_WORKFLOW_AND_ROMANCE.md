# PROTOKOLL NULL – KI-Workflow und Romanzen (v0.4 → v0.9)

## Entwicklungsteam: klare Verantwortlichkeiten
- **Claude Code: einzige reguläre Integrationsinstanz.** Kotlin/Compose, Engine, Tests, Assets und reproduzierbare Android-APK auf GitHub. Erst Branch, PR, CI, Review; dann Merge. Keine zweite KI darf parallel dieselben Dateien ohne abgesprochenen Branch editieren.
- **ChatGPT: Game Director / Spezifikation / Code Review.** Prüft auf GitHub die aktuelle Engine, führt Balancing- und Systemreviews durch, entwirft Story- und Datenregeln und spezifiziert kleine, isolierte Patches.
- **Gemini Pro: UI / UX / Visual QA.** Arbeitet mit Screenshots und isolierten Design-Tasks; bewertet Portraitmodus, Skalierung, visuelle Hierarchie und Gegenstandsabbildungen. Änderungen gehen über Claude in die App.
- **Grok Pro: optionales Red-Team der Spielideen.** Alternative Missionsketten, Widersprüche, Endgame-Fragen und narrative Optionen. Keine eigenständigen Core-Engine-Commits.

## Geplante Roadmap
1. **v0.5** – Balancing/Zeitfortschritt/Inventar/Karriere, Savegame-Migrationen, Langzeit-Tests.
2. **v0.6** – NPC-Simulation, Erinnerungen, Alex-Route und Gefährtenautonomie.
3. **v0.7** – Missionsgenerator, persistente Fraktionen, neue 2D-Karten und Untersuchungen.
4. **v0.8** – Teamführung, Standorte, Direktorat und Privatleben.
5. **v0.9** – content-complete Beta, interagierende Systeme, Performance und Geräte-Tests.

## Optionale Romanze mit Alex Udinov
- Alex ist in dieser alternativen Fankontinuität eine **freiwillige, optionale** Romanzenroute; die Seriengeschichte darf dafür variieren.
- Die Romanze ist zunächst **nicht** aktiv. Erst der neue Charakterdialog `dlg_alex_choice` kann durch die Antwort `interest` das Storyflag `alex_romance_unlocked` setzen.
- Wer `friends` oder `friendship` wählt, bleibt Freund/in; nicht automatisch Zuneigung durch Smalltalk.
- Zweite persönliche Szene `dlg_alex_evening` nur bei offenem Interesse und ausreichendem Vertrauen.
- Alex behält eigene Ziele und Freiwilligkeit, keine automatische Partnerschaft durch Entscheidung allein.

## Altersgerechte Romanze 18/16
- Romantische **nicht-sexuelle** Beziehung zwischen 18- und 16-jährigen Figuren ist im Spiel nun möglich, auch bei der individuell erstellten Gefährtin/dem Gefährten.
- Regel: beide >=16 und Altersabstand höchstens 2 Jahre; oder beide volljährig.
- Solange eine Person minderjährig ist, maximal Stufe 2 (Interesse, Paar). Nur Gespräche, Freizeit, Dates und emotionale Nähe; kein expliziter Inhalt.
- **Zusammenziehen, Heirat und Familienplanung erst, wenn beide erwachsen sind**, weitere Voraussetzungen gelten unverändert.
- Keine Abhängigkeit oder Zwang durch den Ausbildungsstatus. Auswahl ist für beide Charaktere freiwillig und kann durch Konflikte unterbrochen werden.
- Testfälle: 16/17 erlaubt; 18/16 erlaubt bis Stufe 2; 19/16 gesperrt; bei 20/18 Erwachsene-Stufen freigeschaltet.

## Entwicklungs- und Qualitätsschutz
- `main` bleibt stabil; Code über Featurebranch & Pull Request mit CI.
- Die bestehenden 0.4-Spielstände müssen weiter lesbar sein. Die Änderungen erweitern Daten und Dialoge ohne Save-Schema-Änderung.
- GitHub Actions `Android CI` bei PRs: `testDebugUnitTest` und `assembleDebug`.
- Insbesondere die Tests `RulesTest` und `ContentValidationTest` müssen grün sein.
- Die privaten Fotos des Spielers verbleiben lokal und dürfen nicht im öffentlichen GitHub-Repository auftauchen.
