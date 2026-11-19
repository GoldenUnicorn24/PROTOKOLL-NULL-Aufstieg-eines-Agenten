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

## Altersgerechte Romanzen ab dem ergänzenden v0.6-Patch
- Freiwillige, ausschließlich nicht-sexuelle Romanzen ab 16 sind möglich, wenn beide Figuren mindestens 16 und maximal zwei Jahre auseinander sind (etwa 16/17 oder 18/16).
- Für zwei volljährige Figuren gilt die reguläre Erwachsenenregel; der Abstand von zwei Jahren ist nur eine freiwillige Spielregel für Minderjährige.
- Bis zur Volljährigkeit beider Figuren gibt es nur Stufe 1 (Interesse) und Stufe 2 (feste Beziehung). Zusammenziehen, Heirat und Familienplanung sind gesperrt; Familienplanung setzt ohnehin 21 Jahre bei beiden voraus.
- Alex bleibt freiwillig: Erst die Dialogentscheidung `alex_romance_unlocked` eröffnet ihre Romanzenroute. `dlg_alex_choice`, `dlg_alex_evening` und `dlg_alexv6_rooftop` sind ab 16 verfügbar, sofern die weiteren Bedingungen erfüllt sind.
- Die 16-jährige neu zugeteilte Gefährtin bzw. der Gefährte kann mit dem 18-jährigen Spieler eine gleichberechtigte altersgerechte Beziehung entwickeln, aber nicht automatisch durch Rekrutierung. Keine Ausnutzung der dienstlichen Position.
- Alle Romanzen der NPC-Lebenssimulation, die nicht den Spieler betreffen, bleiben auf volljährige NPCs begrenzt. Alte Spielstände mit Stufe 1–2 bleiben gültig.

## Beziehungen in v0.6 „Living Division“
- Die v0.6-Spielerromanzenregel erlaubt auch altersgerechte 16/17- und 18/16-Beziehungen. Die Lebenssimulation verändert die Spielerromanzenstufe nie; eigenständig gebildete NPC-Paare bleiben auf Volljährige beschränkt.
- Neue Alex-Szenen (`dlg_alexv6_*`): Vor dem 16. Geburtstag gibt es keine Romanze; ab 16 sind auch altersgerechte romantische Szenen möglich. Der Freundschaftsweg (`alex_friendship_chosen`) ist vollständig spielbar, auch über `dlg_alexv6_friend_oath` und `dlg_alexv6_reconsider` (ab 19, mit bewusster zweiter Entscheidung). Die Romanzenszene `dlg_alexv6_rooftop` ist erst ab 18 und nur mit `alex_romance_unlocked` verfügbar.
- Eine feste Beziehung entsteht nur durch eine bewusste Entscheidung mit Bestätigung. Sie beendet andere Annäherungen respektvoll. „Nur Freunde bleiben“ und Trennung sind eigene Entscheidungen.

## Entwicklungs- und Qualitätsschutz
- `main` bleibt stabil; Code über Featurebranch & Pull Request mit CI.
- Die bestehenden 0.4-Spielstände müssen weiter lesbar sein. Die Änderungen erweitern Daten und Dialoge ohne Save-Schema-Änderung.
- GitHub Actions `Android CI` bei PRs: `testDebugUnitTest` und `assembleDebug`.
- Insbesondere die Tests `RulesTest` und `ContentValidationTest` müssen grün sein.
- Die privaten Fotos des Spielers verbleiben lokal und dürfen nicht im öffentlichen GitHub-Repository auftauchen.
