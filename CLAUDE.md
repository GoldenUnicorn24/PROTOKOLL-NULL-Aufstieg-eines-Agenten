# Claude Code – Repository-Anweisungen für PROTOKOLL NULL

## Aktiver Auftrag: v1.0 Full Release

**LIES ALS ERSTES UND VOLLSTÄNDIG:** [docs/V1_FULL_RELEASE_MASTERPLAN.md](docs/V1_FULL_RELEASE_MASTERPLAN.md)

Dieser Masterplan ist der **verbindliche Arbeitsauftrag** für die Entwicklung der v1.0. Frühere Versions-Prompts in `docs/` sind Hintergrunddokumentation, kein Ersatz oder höher priorisierter Auftrag. Prüfe stets die tatsächliche aktuelle Codebasis und die bestehenden Tests.

### Unverhandelbare Grundsätze

- **Gesamtes Spiel prüfen, weiterentwickeln und überarbeiten:** Gameplay, Story, sämtliche Missionen, KI-/NPC-Simulation, Karriere, Beziehungen, Gefährten, Inventar, Taktik, Privatleben, Teamführung, Direktorat, Savegames, technische Architektur.
- **Gesamtes UI/UX umfassend selbst modernisieren:** **alle** wichtigen Screens, Navigation, Layouts, Typografie, Interaktion, Responsivität, Zugänglichkeit, Animationen und visuelle Qualität. Das spätere Codex-Review ersetzt die eigene UI-Arbeit nicht.
- **Bestehende v0.8-Funktionen erhalten:** Nicht neu anfangen, keine Inhalte blind entfernen, keine zwei konkurrierenden Engine-Systeme, keine leeren Buttons oder Scheinimplementierungen.
- **Eigenständige dreistufige Qualitätsarbeit:** erst implementieren, dann den vollständigen geänderten Code kritisch selbst reviewen und Fehler direkt reparieren, anschließend echte UI-Screenshots und Tests prüfen und nochmals optimieren.
- **Ein echtes Spiel, keine bloße Verwaltung:** Entscheidungen und Aktionen müssen spürbar in echten Game-State und Geschichte wirken, ohne wiederholte Klick-Exploits.
- **Bestehende Alters- und Romanzenregeln, Serienbildrechte und Offlinefähigkeit beachten:** Siehe Masterplan und aktuelle Regressionstests.
- **Ehrlichkeit bei Abschluss:** Keine Behauptungen über Tests, Builds, Releases oder Features ohne Nachweis. Fehlende Gerätetests oder unvollständige Funktionen offen dokumentieren.
- **GitHub-Arbeitsweise:** Auf Basis aktueller `main` den Branch `feature/v1.0-full-release` verwenden, thematische Commits, erfolgreiche CI, Pull Request, Debug-APK-Artefakt und die im Masterplan geforderten Dokumentationen. Nicht eigenmächtig unfertige Arbeit als 1.0.0 releasen.

### Arbeitsbeginn und Abschluss

Starte mit einer umfassenden Prüfung aller Hauptsysteme und UI-Screens. Arbeite danach die Anforderungen in `docs/V1_FULL_RELEASE_MASTERPLAN.md` implementierend ab. Stelle nur Fragen bei echten notwendigen Blockern. Beende den Arbeitslauf mit einem überprüfbaren Branch/PR, Build-/Testnachweisen, APK-Artefakt, Vorher-/Nachher-UI-Review und ehrlichem Status.

**Kernziel: Das ganze Spiel und seine gesamte Oberfläche qualitativ deutlich besser machen – nicht lediglich die Versionsnummer oder die Contentmenge erhöhen.**
