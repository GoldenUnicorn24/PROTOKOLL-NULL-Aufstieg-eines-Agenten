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

## Altersregel für Romanzen (ab v0.5)
- Romantische Optionen (Interesse, Paar, Zusammenziehen, Heirat, Familie) gibt es **nur, wenn beide Figuren volljährig sind**.
- Vor dem 18. Geburtstag beider Figuren sind Freundschaft, Vertrauen, Respekt, Loyalität und Mentoring möglich; Zuneigung steigt in dieser Zeit nicht.
- Die frühere 18/16-Regel aus v0.4 wurde in v0.5 bewusst nicht übernommen. Bestehende v0.4-Spielstände mit einer früheren Beziehungsstufe bleiben lesbar; die Beziehung wird als „pausiert“ angezeigt, bis beide volljährig sind.
- Alex-Route und Gefährtenakte bleiben erhalten: Die Alex-Dialoge (`dlg_alex_choice`, `dlg_alex_evening`) sind ab 18 verfügbar (Alex ist dann 19). Die 16-jährige zugeteilte Gefährtin bzw. der Gefährte ist bis zur Volljährigkeit Kollegin/Kollege und Freund/in.

## Beziehungen in v0.6 „Living Division“
- Die Altersregel gilt unverändert: Romantik nur, wenn beide Figuren volljährig sind. Die Lebenssimulation verändert die Romanzenstufe zwischen Spieler und Figur nie. NPC-Paare entstehen nur zwischen volljährigen Figuren, die keine Spielerromanze sein können.
- Neue Alex-Szenen (`dlg_alexv6_*`): Vor dem 18. Geburtstag gibt es ausschließlich Freundschaftsszenen. Der Freundschaftsweg (`alex_friendship_chosen`) ist vollständig spielbar, auch über `dlg_alexv6_friend_oath` und `dlg_alexv6_reconsider` (ab 19, mit bewusster zweiter Entscheidung). Die Romanzenszene `dlg_alexv6_rooftop` ist erst ab 18 und nur mit `alex_romance_unlocked` verfügbar.
- Eine feste Beziehung entsteht nur durch eine bewusste Entscheidung mit Bestätigung. Sie beendet andere Annäherungen respektvoll. „Nur Freunde bleiben“ und Trennung sind eigene Entscheidungen.

## Entwicklungs- und Qualitätsschutz
- `main` bleibt stabil; Code über Featurebranch & Pull Request mit CI.
- Die bestehenden 0.4-Spielstände müssen weiter lesbar sein. Die Änderungen erweitern Daten und Dialoge ohne Save-Schema-Änderung.
- GitHub Actions `Android CI` bei PRs: `testDebugUnitTest` und `assembleDebug`.
- Insbesondere die Tests `RulesTest` und `ContentValidationTest` müssen grün sein.
- Die privaten Fotos des Spielers verbleiben lokal und dürfen nicht im öffentlichen GitHub-Repository auftauchen.
