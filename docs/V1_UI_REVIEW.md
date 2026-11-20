# PROTOKOLL NULL – v1.0 UI-Review

Alle Screenshots werden automatisch von Robolectric/Roborazzi aus echtem Spielzustand gerendert (`ScreenshotTest`, Workflow „Screenshots“, 400 × 860 dp, xxhdpi) und liegen in [`docs/screenshots/`](screenshots/). Die Vorher-Bilder (v0.8) sind im Git-Verlauf von `main` (Commit `5d495f4`) erhalten.

## 1. Designsystem 2.0 (`ui/theme/Theme.kt`, `ui/theme/Components.kt`)

| Baustein | Zweck |
|---|---|
| Tokens `Pn`, `Sp`, `PnShapes` | Farben (Graphit/Navy, zurückhaltendes Türkis, Statusfarben), 4-dp-Raster, Formen. Keine Einzel-Screen-Farben. |
| Typografie | Anton (Titel), Inter (Text, jetzt auch Sekundärtexte), Anonymous Pro nur für Überschriften und Messwerte (`PnType.overline/data/metric`); deutsche Silbentrennung. |
| `GlassPanel`, `SectionLabel`, `Overline` | Karten mit Eckmarken, Abschnittsüberschriften (für TalkBack als Überschrift markiert). |
| `ActionButton`, `ChoiceButton` | ≥ 48 dp, Rollen-Semantik, Varianten primär/sekundär/gefährlich, Auswahlzustand mit Haken, Erfolgschance-Ring. |
| `PnTabs` | Einzeilige, scrollbare Reiterleiste mit automatischem Scrollen zur Auswahl und Plaketten. |
| `PnChip`, `CompactChips`, `ChipRow`, `PillRow` | Auswahl (interaktiv) und Kennzeichnung (nicht interaktiv) klar getrennt. |
| `NavRow`, `StatTile`, `ProgressRing`, `AttributeRadar` | Navigationszeilen, Kennzahlen-Kacheln, Fortschrittsringe, Attribut-Radar. |
| `Banner`, `InfoNote`, `EmptyState` | Priorisierte Hinweise, erklärende Notizen, leere Zustände mit Aktion. |
| `PnDialog` | Gestaltete Bestätigung (u. a. Spielstand überschreiben/löschen, Einsatz abbrechen, Agenda). |
| `CelebrationHost`, `BusyOverlay` | Feier-Einblendung (Rang, Kapitel, jedes 5. Level) und Ladeanzeige für den Zeitraffer. |
| `KeyValue` | Inhaltsabhängige Spaltenaufteilung statt fester Gewichte. |

**Animationen:** Reiterwechsel (Gleiten und Einblenden), Overlay-Einblendung von rechts, Druck-Skalierung, animierte Balken und Ringe, Feier-Einblendung, pulsierende Ziele auf dem Taktikbrett. Alle Animationen lassen sich über „Animationen“ abschalten (reduzierte Bewegung, dann Schnitte statt Übergänge).

## 2. Screen-Inventar und Änderungen

| Screen | v0.8-Probleme | v1.0 | Screenshot |
|---|---|---|---|
| Start | Viel leerer Raum, flache Optik | Kinoartiger Kopfbereich, Fortsetzen-Karte, Untertitel | `01_start` |
| Charaktereditor | Material-Chips, Monospace-Hinweise | `PnChip`, lesbare Hinweise | `02_charaktereditor` |
| Prolog / Dialog | Monospace-Labels | Überschriften, Silbentrennung | `03_prolog`, `04_dialog_michael` |
| HQ / Lageplan | Drei Statuskarten vor dem Handeln, abgeschnittene Ebenen | Lagezentrum: Begrüßung, Banner, Auftrag mit Ring, 8 Schnellzugriffe, Kennzahlen, einzeilige Ebenenwahl mit Hinweisplaketten | `05_hq_lageplan`, `34_hq_division_heute` |
| Mission Control | Zwei Zeilen Reiter, doppelter Titel | `PnTabs` mit Plaketten, Kennzahlen, Filterkarte, leere Zustände, Kapitel-Fortschritt | `06_einsatzboard`, `36`–`38` |
| Briefing | Viele Schlüsselzeilen, „✓“ im Text | Kennzahlen (Gefahr/XP/Credits), ausgewählte Vorgehensweise mit Haken, Team als ankreuzbare Liste, klare Startsperre | `07_briefing`, `40_auftrag_briefing`, `52_kapitel4_briefing` |
| Einsatz | Abbruch ohne Rückfrage | Abbruch-Dialog, Überschriften in der Lage | `08_mission_entscheidung`, `53_wendung` |
| Taktikbrett | Kacheln, Einzeltipp, wenig Kontrast | Canvas-Brett, Pfad, Gefahrenpunkte, Trefferchance je Gegner, Einheitenleiste, Gegnerliste, „Team handelt“, Legende | `09_taktikbrett`, `41_taktik_botschaft`, `50_taktik3_labor` |
| Nachbesprechung | Nur Text und Pill | Ergebnis-Icon, Belohnungsvorschau als Kacheln | `10_nachbesprechung` |
| Untersuchung | – | Überschriften, lesbare Hinweise | `11_untersuchung` |
| Inventar | Leere Slots als „—“, gedrängte Profile | Slot-Raster mit Symbolen und Namen, einklappbare Profile, kurze Aktionen | `12_inventar`, `29_ausruestungsprofile` |
| Gegenstand / Kammer / Spind | Monospace-Notizen, leere Zustände | Lesbare Hinweise, `EmptyState`, scrollende Kategorien | `13`, `14` |
| Kontakte / Personalakte | 3 Zeilen Filter-Chips, senkrechte Buchstaben | `PnTabs` (Interaktion mit Plakette), korrektes `KeyValue`, Charakterzüge als Plaketten | `15`, `16`, `30`–`33`, `49` |
| Profil | Gitter aus 11 Buttons | Personalakte mit Kennzahlen, Radar, Navigationszeilen | `17_profil` |
| Skilltree | Knotentitel brachen mitten im Wort | Breitere Knoten, Silbentrennung | `18_skilltree` |
| Karriere / Prüfung / Training | Chip-Reiter abgeschnitten | `PnTabs`, Auszeichnungen als Plaketten | `19`, `26`–`28` |
| Zeitplan | Bericht-Reiter deaktiviert sichtbar | Reiter erscheint nur mit Bericht; Zeitraffer im Hintergrund mit Ladeanzeige | `20_kalender`, `25_zeitplan_bericht` |
| Rekrutierungseditor | – | Gemeinsame Komponenten | `21` |
| Direktorat | Abgeschnittene Reiter, „!“ im Text | `PnTabs` mit Plakette, neuer Reiter **Agenda** | `22`, `45`, `51_direktion_agenda` |
| Führungscockpit / Standorte / Berichte | Abgeschnittene Reiter, Wortbrüche | `PnTabs`, adaptives `KeyValue` | `42`–`44`, `46` |
| Stadt & Privatleben | „Ereignis !“ im Reitertext | `PnTabs` mit Plakette | `47`, `48` |
| Spielstände | Überschreiben und Löschen ohne Rückfrage | Slot-Karten mit Symbol, Bestätigungsdialoge | `23_spielstaende` |
| Einstellungen | – | Gruppiert, Schriftvorschau, Hinweis zur reduzierten Bewegung | (nicht im Screenshot-Set) |
| Epilog | – | Zwei neue Enden | `24_epilog`, `55_epilog_akademie` |
| Feier-Einblendung | – | Neu | `54_feier_befoerderung` |
| HUD / Navigation | Groß, ohne XP, ohne Hinweise | Kompakt, XP-Balken, Plaketten an den Reitern | alle Spiel-Screens |

## 3. Visuelle Reviewdurchläufe

1. **Durchlauf 1** (nach dem ersten Umbau, Screenshots aus Commit `6d5f3f2`): Reiter einzeilig ✔, HQ ✔. Gefunden: zu dunkles Taktikbrett, harte Wortbrüche in `KeyValue` und im Skilltree, abgeschnittene Kachel-Überschrift „TEAM BERE…“. → behoben in `UI-Review 1` (helleres Brett mit plastischen Wänden, adaptives `KeyValue`, Silbentrennung, breitere Skill-Knoten, kürzere Labels).
2. **Durchlauf 2** (Screenshots aus Commit `11d55d1` inkl. neuer Szenen 50–55): Brett mit klar erkennbaren Wänden, Deckung, Trefferchance (z. B. „54 %“) und Gefahrenpunkten ✔, Agenda ✔, Wendung ✔, Feier-Einblendung ✔. Gefunden: „Ausrüstungskamm-er“ in einem halbbreiten Button → behoben in `UI-Review 2`.

## 4. Bekannte visuelle Restprobleme

- Horizontal scrollende Reiter- und Chip-Reihen zeigen am Rand angeschnittene Einträge (signalisiert Scrollbarkeit, aber ohne Verlaufskante).
- Die Zeitplan-Wochentage nutzen pro Tag eine scrollende Chip-Reihe; ein kompakteres Auswahlmenü wäre übersichtlicher.
- Robolectric rendert ohne echte Gerätefonts-Metriken; Silbentrennung und Schriftgröße XL sollten auf einem Gerät geprüft werden.
- Die Szenenbilder des Startbildschirms haben abgerundete Ecken auch am Bildschirmrand.

## 5. Qualitätsbewertung (Selbsteinschätzung, 1–5)

| Kriterium | v0.8 | v1.0 | Begründung |
|---|---|---|---|
| Visuelle Konsistenz | 3 | 4,5 | Einheitliche Tokens und Komponenten auf allen Screens |
| Lesbarkeit | 2,5 | 4 | Inter statt Monospace, Silbentrennung, adaptive Zeilen |
| Informationshierarchie | 3 | 4 | Banner, Kennzahlen, Navigationszeilen, Plaketten |
| Taktik-Darstellung | 2 | 4 | Canvas, Vorschauen, zweistufige Steuerung |
| Zugänglichkeit | 2 | 3,5 | Semantik und Touch-Ziele verbessert, ohne Geräteverifikation |
| Bewegung / Feedback | 3 | 4 | Übergänge, Feier, Ladeanzeige, abschaltbar |
