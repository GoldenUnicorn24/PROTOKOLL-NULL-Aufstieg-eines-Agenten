# PROTOKOLL NULL – v1.0 Release-Checkliste

**Stand dieses Branches:** `versionName = "1.0.0-rc1"`, `versionCode = 9`.
**Entscheidung:** Release Candidate statt 1.0.0. Laut Masterplan dürfen `1.0.0`/`10` erst gesetzt werden, wenn alle Pflichtkriterien erfüllt sind. Offen sind Gerätetests (keine Hardware/kein Emulator in der Entwicklungsumgebung) und die externe Codex-Prüfung.

## Build und Tests

| Prüfung | Ergebnis | Nachweis |
|---|---|---|
| `testDebugUnitTest` (alle Unit- und Robolectric-Tests) | ✅ grün | GitHub Actions „Android CI“ (workflow_dispatch auf diesem Branch, Läufe 52, 53 ff.) |
| `assembleDebug` | ✅ grün, Artefakt `ProtokollNull-debug-apk` | dieselben Läufe |
| `recordRoborazziDebug` (55 Screenshots) | ✅ grün, Bilder committet | Workflow „Screenshots“ |
| Reine Engine-Tests lokal (JVM-Testumgebung) | ✅ 173/173 | lokal, ohne Android-SDK |
| Instrumentation-/Gerätetests | ⏳ **nicht ausgeführt** | Kein Gerät und kein Emulator verfügbar – nicht vorgetäuscht |
| Release-APK | 🟡 vorbereitet | `release.yml` (manuell, Signatur nur über Secrets); erst nach dem Merge auf `main` auslösbar |

## Neue und erweiterte Tests (Auswahl)

- `E2EPlaythroughTest`: spielt die Kampagne über echte Engine-Aktionen bis zu den Enden „Im Licht“, „Neue Ordnung“, „Phönix“, „Spurlos“, „Bündnis“ und „Akademie“ und anschließend durch Kapitel 4.
- `V1ReleaseTest`: 22 gültige Karten (Erreichbarkeit aller Marker), Pfade, Reaktionsfeuer einmal pro Runde, deterministische Team-KI, Rückzug angeschlagener Gegner, alle Zielarten auf neuen Karten enden; 36 Missionen in 4 Kapiteln; 6 Enden mit Epilog; neue Enden nur mit ihrem Strang; Kapitel-Flags; Kapitel 4 erst nach dem Epilog; kein XP-Farming; Vorschau = Abrechnung; Agenda einmalig und mit Folgen; Feier-Erkennung; Navigationsplaketten; neue HQ-Szenen; Wendungen; neue Gegenstände mit Nutzen; Migration eines v0.8-Spielstands; zehn Jahre Simulation.
- Angepasste Alt-Tests (keine abgeschalteten Tests): Finale mit 6 statt 4 Wahlmöglichkeiten (die vier ursprünglichen bleiben bedingungslos), Gegenstandsanzahl ≥ 102 statt genau 102, Inhaltsvalidierung für Bibliothekskarten, Feier-Einblendung im Gefährten-UI-Test schließen.

## Abnahme gegen die Definition of Done

| Kriterium | Status |
|---|---|
| Installieren, Agent erstellen, Story bis zu mehreren Enden | ✅ (E2E-Test + Debug-APK; manuell auf einem Gerät ⏳) |
| Missionen inkl. Taktik | ✅ |
| Karriere, NPCs, Beziehungen, eigener Gefährte | ✅ (aus v0.8 erhalten, ergänzt) |
| Privatleben | ✅ (erhalten, UI modernisiert) |
| Team/Division leiten, nach Rang 12 weiterspielen | ✅ Agenda + Kapitel 4 |
| Screens zusammengehörig, modern, klar | ✅ siehe `V1_UI_REVIEW.md` |
| Savegames gehen nicht verloren | ✅ Migration getestet, Bestätigungsdialoge, Hintergrund-Autosave mit Reihenfolgeschutz |
| Bestehende Funktionen intakt | ✅ alle Alt-Tests grün |
| Test- und Build-Beweise | ✅ CI-Läufe und Artefakte |

## Vor einem echten 1.0.0

1. Debug-APK auf mindestens zwei Geräten (360 dp und 430 dp) mit Schriftgröße XL und TalkBack prüfen.
2. Spielstand aus v0.8 auf einem Gerät importieren und weiterspielen.
3. Codex-Prüfung (`CODEX_HANDOFF_V1.md`) einarbeiten.
4. `versionName = "1.0.0"`, `versionCode = 10`, Release über `release.yml` mit Keystore-Secrets.
5. Für eine öffentliche Veröffentlichung: Rechtefreigaben oder eigenständige Figuren (siehe README).
