# Codex-Handoff v1.0 (Release Candidate 1)

Kurzer Leitfaden für die externe Nachprüfung. Die Codebasis bleibt bei Kotlin 2.0, Jetpack Compose, einer einzigen Activity, Offline-Betrieb und `minSdk 26`.

## Architektur in einem Absatz

`engine/` enthält reine, deterministische Kotlin-Objekte (`Result(state, messages, ok)`), die einen unveränderlichen `GameState` (`model/`) in einen neuen überführen. Zufall stammt immer aus `Rng(Rng.mix(seed, …))`. `data/GameData` lädt die JSON-Kataloge (`assets/game/*.json`), `data/SaveStore` speichert und migriert (Schema 6). `ui/GameViewModel` ist die einzige Brücke: `act { data, state -> … }` für schnelle Aktionen, `actAsync(label) { … }` für den Zeitraffer, `update { }` nur für reine UI-Zustände. `ui/AgentApp` hält die Shell (HUD, Reiter, Overlay-Stapel, Vollbild-Ereignisse, Feier- und Ladeanzeige).

## Wichtige Änderungen in v1.0

| Bereich | Dateien | Stichpunkte |
|---|---|---|
| Designsystem | `ui/theme/Theme.kt`, `ui/theme/Components.kt`, `ui/theme/PnIcons.kt` | Tokens, Typografie mit Silbentrennung, `PnTabs`, `PnChip`, `NavRow`, `StatTile`, `ProgressRing`, `AttributeRadar`, `Banner`, `EmptyState`, `PnDialog`, `CelebrationHost`, `BusyOverlay`, adaptives `KeyValue` |
| Shell und Navigation | `ui/AgentApp.kt`, `ui/GameViewModel.kt` | Hintergrund-Autosave (CONFLATED-Kanal + Sequenznummer), `flushSave()` in `onStop`, `actAsync`, `backFrom`, geschützte Zurück-Geste, Navigationsplaketten (`engine/Alerts.kt`), Feier-Erkennung (`engine/Milestones.kt`) |
| Taktik 3.0 | `engine/Tactical.kt`, `engine/TacticalMaps.kt`, `ui/screens/TacticalScreen.kt` | `reactionFire`, `teamAuto`, `path`, `dangerAt`, Rückzug, Überwältigen durch den Stoßtrupp, 22 Karten; Canvas-Brett mit zweistufiger Eingabe (`rememberUpdatedState` für den Tap-Handler) |
| Story | `assets/game/missions.json`, `quests.json`, `dialogues.json`, `engine/Narrative.kt` | M023–M036, q20–q24, `dlg_ch4_intro`/`dlg_ch4_end`, zwei bedingte Enden, `Quests.chapterRules`, `Endings.all` |
| Missionsdirektor 3.0 | `engine/Twists.kt`, `engine/Ops.kt` | Deterministische Wendung zwischen Vorgehen und Durchführung |
| Direktion | `engine/Agenda.kt`, `model/LeadershipModels.kt`, `engine/Directorate.kt`, `ui/screens/LeadershipScreens.kt` | Agenda (26 Wochen), Bewertung innerhalb von `Directorate.weekly` |
| Division | `assets/game/hq_events.json` | Zehn neue HQ-Szenen, an Story-Flags gebunden |
| Inventar | `assets/game/items.json`, `assets/items/gear_*.svg`, `gift_*.svg`, `engine/Bonds.kt` | 25 Gegenstände mit Nutzen |
| Exploit | `engine/Missions.kt` | `rewardPreview(repeatedFail)` – kein XP-Farming durch Scheitern |
| Release | `app/build.gradle.kts`, `.github/workflows/release.yml` | Signatur nur über Umgebungsvariablen |

## Bitte besonders prüfen

1. **Nebenläufigkeit im ViewModel:** `actAsync` übernimmt das Ergebnis nur, wenn sich `state` nicht geändert hat (`===`). Die Eingabe ist währenddessen über `BusyOverlay` gesperrt. Gibt es Pfade (z. B. `update` aus einem `LaunchedEffect`), die den Zustand dennoch ändern und das Ergebnis verwerfen?
2. **Autosave:** `writeAuto(seq, state)` unter `saveLock`; `flushSave()` schreibt mit der zuletzt vergebenen Sequenz. Bitte auf Randfälle beim Prozess-Kill prüfen.
3. **AnimatedContent für Overlays:** Ausgehende Overlays werden während der Animation weiter komponiert. Wächter verwenden deshalb `backFrom`. Weitere Seiteneffekte in Overlay-Composables?
4. **Canvas-Taktikbrett:** Hit-Test über Zellgröße; Semantik als Beschreibung plus Gegnerliste als zugängliche Alternative. Reicht das für TalkBack?
5. **Team-KI (`teamAuto`):** nutzt nur `attack/move/takedown/useAbility/secure/guard`; Schleifenbegrenzung 4 pro Einheit.
6. **Silbentrennung:** `Hyphens.Auto` mit `LocaleList("de-DE")` in allen Body-/Label-/Title-Stilen – Auswirkungen auf Performance und Darstellung auf Geräten.
7. **Inhaltskonsistenz:** neue Flags (`alliance_1`, `alliance_forged`, `academy_charter`, `academy_founded`, `michael_reckoning`, `lenz_*`, `phoenix_law`) und ihre Nutzung in Epilog und HQ-Szenen.

## Bekannte Schwachstellen

- Keine Instrumentation- oder Gerätetests; die UI-Belege stammen ausschließlich aus Robolectric-Screenshots.
- `LeadershipScreens.kt` und `Tactical.kt` sind groß; eine Aufteilung (Cockpit, Direktorat, Stadt) wäre wartungsfreundlicher.
- Einige Screens rechnen Ableitungen in der Komposition (z. B. `Alerts.nav` mit `remember(s)`, Teamkandidaten im Briefing). Unkritisch, aber Kandidaten für `derivedStateOf`.
- Horizontal scrollende Chip-Reihen ohne Verlaufskante.
- Storyumfang 36 Missionen (Ziel laut Masterplan 45–60, ausdrücklich „nur soweit sinnvoll“).
- Lokal steht kein Android-SDK zur Verfügung: UI-Code wurde ausschließlich in GitHub Actions kompiliert und getestet.
