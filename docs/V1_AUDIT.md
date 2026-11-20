# PROTOKOLL NULL – v1.0 Audit

**Ausgangsbasis:** `main` mit v0.8.0-beta „Director's Legacy“ (Commit `15268ee`).
**Methode:** Lesen von Engine, Modell, Daten, UI und allen Tests; Auswertung der 49 automatisch gerenderten Screenshots von v0.8; lokale Ausführung der 148 reinen Engine-Tests in einer JVM-Testumgebung; Builds, Robolectric-Tests und Screenshots über GitHub Actions.
**Prioritäten:** P1 = Datenverlust, Absturz, Sackgasse oder Exploit · P2 = spürbare Qualitäts- oder Bedienschwäche · P3 = Feinschliff.
**Status:** ✅ behoben · 🟡 teilweise · ⏳ offen

---

## 1. Technik und Architektur

| # | Befund (v0.8, im Code nachvollzogen) | Prio | Status / Lösung |
|---|---|---|---|
| T1 | `GameViewModel.act()` führte jede Aktion **und** das Autosave (JSON-Serialisierung und `fsync`) synchron auf dem UI-Thread aus. Ein Zeitraffer über 365 Tage blockierte die Oberfläche. | P1 | ✅ Autosave läuft seriell im Hintergrund (konfliktfreier Kanal mit Sequenznummer, damit kein älterer Stand einen neueren überschreibt). Der Zeitraffer läuft über `actAsync()` auf `Dispatchers.Default` mit sperrender Ladeanzeige. Beim Verlassen der App schreibt `flushSave()` sofort. |
| T2 | `Ops.refresh` (neue Aufträge) wurde als `LaunchedEffect`-Seiteneffekt beim Öffnen von Mission Control ausgelöst. | P2 | ✅ Läuft beim Laden eines Spielstands und beim Tageswechsel; in der UI bleibt nur ein Sicherheitsnetz für sehr alte Stände (`lastGenDay < 0`). |
| T3 | Die System-Zurück-Geste schloss nur Overlays. Bei laufendem Einsatz, Dialog oder Prüfung beendete sie die App bzw. schloss unsichtbare Overlays darunter; in Reitern gab es keinen Rückweg zum HQ. | P1 | ✅ Neue Zurück-Logik: Vollbild-Ereignisse sind geschützt (mit Hinweis), Overlays schließen nacheinander, Reiter kehren zum HQ zurück, der Editor zum Start. |
| T4 | „Fehlende Daten“-Wächter (Briefing/Gegenstand/Akte) riefen `vm.back()` – während der neuen Ausblend-Animation hätte das ein zweites Overlay geschlossen. *(Beim Selbst-Review gefunden.)* | P2 | ✅ `backFrom(overlay)` schließt nur, wenn das Overlay noch oben liegt. |
| T5 | Schema-Migration: v0.8 = Schema 5. | – | ✅ Schema 6 (Agenda, Reaktionsfeuer-Status); getestet mit einem v0.8-Spielstand mit laufendem Direktorat (`schemaFiveSaveMigratesWithRunningDirectorateState`). |
| T6 | Spielstände: Überschreiben und Löschen ohne Rückfrage. | P1 | ✅ Bestätigungsdialoge (`PnDialog`). |
| T7 | Lokal ist kein Android-SDK verfügbar (Google-Maven gesperrt). | – | Engine-Tests lokal in einer JVM-Testumgebung, alles Weitere in GitHub Actions (siehe `V1_RELEASE_CHECKLIST.md`). |
| T8 | Release-Prozess: keine Signaturkonfiguration. | P3 | ✅ Signatur nur über Umgebungsvariablen/Secrets, eigener Workflow `release.yml`. Er ist erst nach dem Merge auf `main` manuell auslösbar (GitHub-Einschränkung). |

## 2. UI/UX

| # | Befund | Prio | Status |
|---|---|---|---|
| U1 | `KeyValue` presste bei langen Werten den Schlüssel auf Buchstabenbreite („L e b e n s z i e l“ senkrecht, Screenshot 16 v0.8). | P1 | ✅ Inhaltsabhängiges Layout (Wert ≤ 60 %), Silbentrennung. |
| U2 | Reiterleisten aus `FilterChip`-Gittern brachen in 2–3 Zeilen um (Mission Control, Kontakte), andere wurden ohne Hinweis abgeschnitten (Karriere, Direktorat, Lageplan). Mission Control zeigte im Overlay zwei Titel übereinander. | P2 | ✅ `PnTabs`: einzeilig, scrollbar, die Auswahl scrollt automatisch in Sicht, Plaketten für offene Punkte; doppelter Titel entfernt. |
| U3 | Fließtexte und Hinweise in 12-sp-Monospace mit großer Laufweite (`labelSmall`, `SmallNote`) – schwer lesbar. | P2 | ✅ Sekundärtexte in Inter; Monospace nur noch für kurze Überschriften und Messwerte (`PnType.overline/data`). |
| U4 | Lange Komposita brachen hart mitten im Wort („Prüfungsvorbereitu-ng“, „Organisationsbudg-et“). *(Beim visuellen Review gefunden.)* | P2 | ✅ Deutsche Silbentrennung (`Hyphens.Auto`, `de-DE`) in allen Fließtextstilen. |
| U5 | Dekorative Chips ohne Wirkung (Charakterzüge in der Personalakte, Auszeichnungen in der Karriere) waren als Buttons klickbar. | P2 | ✅ Als nicht interaktive Plaketten (`PillRow`). |
| U6 | HQ: drei Statuskarten vor dem eigentlichen Handeln, Raumaktionen weit unten, Ebenen-Chips abgeschnitten. | P2 | ✅ Lagezentrum: priorisierte Banner (Nachricht, HQ-Ereignis, Dilemma, Alltag), Auftrag mit Fortschrittsring, Schnellzugriffe, Kennzahlen, einzeilige Ebenenwahl. |
| U7 | Profil als Gitter aus elf gleichförmigen Buttons. | P2 | ✅ Personalakte: Kennzahlen, Attribut-Radar, gruppierte Navigationszeilen mit Plaketten. |
| U8 | Statusleiste belegte ca. 15 % der Höhe, ohne XP-Anzeige. | P3 | ✅ Kompakt, mit XP-Balken und Bedienhilfe-Beschreibung. |
| U9 | Einsatzabbruch mit einem Tipp ohne Rückfrage. | P1 | ✅ Bestätigungsdialog. |
| U10 | Taktikbrett: ca. 100 einzelne Kachel-Composables, schwacher Kontrast; Bewegen/Angreifen mit einem Tipp (Fehltipps auf kleinen Displays); keine Pfad-, Gefahren- oder Trefferanzeige auf dem Brett. | P1 | ✅ Canvas-Brett, zweistufige Steuerung (vormerken → bestätigen), Pfad, Gefahrenpunkte, Trefferchance je Gegner, Einheitenleiste, Gegnerliste als zugängliche Alternative. |
| U11 | Touch-Ziele < 48 dp (Zurück 42 dp, Chips, Navigation). | P2 | 🟡 Buttons, Zurück, Reiter und Navigation ≥ 48 dp; Chips 40 dp hoch (bewusst kompakter). |
| U12 | TalkBack: keine Überschriften, kaum Rollen, Balken ohne Beschreibung. | P2 | 🟡 Überschriften, Rollen (Button/Tab/Checkbox), Beschreibungen für Balken, Ringe, Statusleiste und Taktikbrett. Ein Test mit echtem TalkBack auf einem Gerät steht aus. |
| U13 | Keine leeren Zustände (nur graue Notizen). | P3 | ✅ `EmptyState` mit Erklärung und ggf. Aktion. |
| U14 | Spielerfolge ohne visuelles Feedback. | P3 | ✅ Feier-Einblendung für Beförderung, Kapitelabschluss und jedes 5. Level (abschaltbar über „Animationen“). |

Details und Screen-Inventar: [`V1_UI_REVIEW.md`](V1_UI_REVIEW.md).

## 3. Story und Missionen

| # | Befund | Prio | Status |
|---|---|---|---|
| S1 | 22 Storymissionen, 4 Enden. Nach dem Epilog gab es keine weitere Story. | P2 | ✅ 36 Storymissionen: +7 in Kapitel 2/3 (Alex' Schatten, Geist im Netz, zwei Bündnis-Einsätze mit Nikita, zwei Akademie-Einsätze, Michaels Akte) und das neue **Kapitel 4 „Nachbeben“** (7 Einsätze nach dem Epilog). Der Zielkorridor 45–60 wird bewusst **nicht** erreicht – Qualität und vollständige Integration hatten Vorrang. |
| S2 | Nur 4 Enden. | P2 | ✅ 6 Enden: die vier ursprünglichen bleiben bedingungslos wählbar; „Das Bündnis“ und „Die Akademie“ hängen an den neuen Strängen. Neue Epilog-Abschnitte greifen Entscheidungen auf (Alex' Notizbuch, Birkhoffs Praktikantin, Michaels Akte, Akademie). |
| S3 | Kapitelabschluss nur für Kapitel 1 als Flag (`ch1_done`). | P2 | ✅ `ch2_done`/`ch3_done`/`ch4_done` aus erledigten Hauptaufträgen (`Quests.chapterRules`); alte Spielstände holen sie beim nächsten Quest-Update nach. |
| S4 | Wiederholtes Scheitern an Story-Einsätzen brachte jedes Mal 1/3 XP (XP-Farming möglich). | P1 | ✅ Trost-XP nur beim ersten Scheitern; Vorschau und Abrechnung nutzen dieselbe Funktion (`rewardPreview`). |
| S5 | Dynamische Aufträge liefen stets Vorgehen → Durchführung → Abschluss (repetitiv). | P2 | ✅ Missionsdirektor 3.0: acht deterministische Wendungen (Zeuge, neuer Preis, Streife, Späher, Funkausfall, Verletzter, erkannte Tarnidentität, präparierte Daten). Routineaufträge bleiben kurz. |

## 4. Taktik

| # | Befund | Prio | Status |
|---|---|---|---|
| K1 | 14 Karten. | P2 | ✅ 22 Karten (+ Dachterrasse, U-Bahn-Tunnel, Forschungslabor, Villa am See, Serverfarm, Trockendock, Staatsbibliothek, Hängebrücke); per Test sind alle Marker vom Start aus erreichbar. |
| K2 | Verbündete hatten keine KI – jede Einheit musste einzeln gesteuert werden. | P2 | ✅ „Team handelt“: nachvollziehbare Team-KI, die nur reguläre Aktionen nutzt. |
| K3 | Gegnerrollen wirkten kaum unterschiedlich; Überwachungsposten reagierten nicht auf Bewegung. | P2 | ✅ Reaktionsfeuer (einmal pro Runde), angeschlagene Gegner suchen Deckung, der Stoßtrupp überwältigt Einheiten in Deckung. |
| K4 | Keine Wegfindungsvorschau. | P3 | ✅ `Tactical.path` (BFS) und `dangerAt` für die Oberfläche. |

## 5. Lebendige Division, Karriere, Beziehungen

| # | Befund | Prio | Status |
|---|---|---|---|
| L1 | Story-Entscheidungen hallten im Alltag kaum nach. | P2 | ✅ Zehn neue HQ-Szenen, die an neue Story-Flags gebunden sind (z. B. Alex' Vater, Michaels Friedhof, Nikita in der Kantine, Weras Foto, Leonies Besuch) und Erinnerungen ablegen. |
| L2 | Gefährtin/Gefährte: Autonomie vorhanden, wenige späte Szenen. | P3 | 🟡 Neue Szene mit eigenem Angebot (Lissabon), bei der die Figur selbst entscheidet. Weitere Laufbahnszenen wären sinnvoll. |
| L3 | Alters- und Romanzenregeln. | P1 | ✅ Unverändert; Regressionstests laufen weiter, dazu prüft der Zehn-Jahres-Test, dass keine Romanze mit Minderjährigen über Stufe 2 hinausgeht. |
| L4 | Geschenke: nur 9 Gegenstände sprachen Interessen an. | P3 | ✅ +6 Geschenkartikel (Musik, Kunst/Bücher, Schach, Kochen, Sprachen, Zeichnen) und 2 Alltagsgegenstände. |

## 6. Direktorat, Führung, Privatleben

| # | Befund | Prio | Status |
|---|---|---|---|
| D1 | Rang 12 nach dem Finale: 8 Dilemmata, danach wenig strategische Entscheidungen. | P2 | ✅ **Direktionsagenda**: 5 Agenden (Transparenz, Meridian, Menschen, Ausbau, Akademie) über je 26 Wochen, mit Sofortkosten, messbaren Zielen und genau einer Bewertung (auch im Zeitraffer, getestet). Dazu Kapitel 4. |
| D2 | Wochen- und Monatsabrechnung. | – | Bereits in v0.8 idempotent; Agenda wird innerhalb dieser Abrechnung ausgewertet, Doppelbewertung getestet. |
| D3 | Privatleben. | P3 | 🟡 Inhaltlich unverändert (Wohnstufen, Möbel, Stadtkalender, Tarnjobs, Alltagsereignisse); Oberfläche modernisiert. |

## 7. Inventar und Balance

| # | Befund | Prio | Status |
|---|---|---|---|
| I1 | 102 Gegenstände. | P3 | ✅ 127: 19 Ausrüstungs- und Verbrauchsgegenstände (`gear_`) mit Boni, Wirkung oder Missionsboni sowie 6 Geschenke (`gift_`), alle mit eigenen SVG-Icons; per Test hat jeder neue Gegenstand einen Nutzen. |
| I2 | Langzeitspiel. | P2 | ✅ Zehn Jahre Simulation: Werte bleiben im zulässigen Bereich, Feed und Log bleiben kompakt, der Spielstand übersteht den Rundlauf (`tenYearsOfSimulationStayConsistent`). |

## 8. Offene Punkte (ehrlich)

- ⏳ **Keine Tests auf echten Geräten und keine Instrumentation-Tests** – in dieser Umgebung gibt es weder Emulator noch Gerät. Alle UI-Belege stammen aus Robolectric/Roborazzi.
- ⏳ Storyumfang 36 statt 45–60 Missionen.
- 🟡 Barrierefreiheit: Semantik verbessert, aber nicht mit TalkBack verifiziert; die Schriftstufe XL ist nicht als eigene Screenshot-Serie geprüft.
- 🟡 Einige horizontal scrollende Chip-Reihen (Zeitplan-Tätigkeiten, Rollen im Führungscockpit) schneiden Einträge am Rand ab – sie scrollen, zeigen aber keine Verlaufskante.
- 🟡 Privatleben und Gefährten-Laufbahn wurden inhaltlich kaum erweitert.
- ⏳ Die urheberrechtliche Lage des Nikita-Fanprojekts bleibt unverändert (siehe `README.md`).
