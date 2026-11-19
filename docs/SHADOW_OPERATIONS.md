# v0.7 „Shadow Operations“ – technische Beschreibung

Stand: 0.7.0-beta. Alles läuft offline und deterministisch: Seed + Spieltag + Spielzustand bestimmen jedes Ergebnis. Es gibt keine externen Dienste, keine KI-Würfe ohne Seed und keine Onlinepflicht.

## 1. Missionsdirektor 2.0 (`engine/Ops.kt`)

**Bausteine eines Auftrags**

| Baustein | Inhalt |
|---|---|
| Ort | Industriehafen, Flughafen, Krankenhaus, Diplomatenviertel, konspirative Wohnung, Innenstadt (je eigene Suchorte und Taktikkarten) |
| Einstieg | 10 Situationen (vorverlegtes Treffen, Regen, vorgewarnte Gegenseite, Quelle vor Ort, Großveranstaltung, Nacht, kalte Spur, Beschattung, Übergabe, Budgetkürzung); verändern Intel, Alarm, Tarnung oder Belohnung |
| Auftraggeber | Einsatzleitung (Michael), Technik (Birkhoff), Psychologie (Amanda), Direktorat (Percy), Regierungsaufsicht, Informantennetz, Abtrünnige (Nikita, nur mit `nikita_contact`), persönliche Bitte (Alex, Freundschaft ≥ 40). NPC-Auftraggeber nur, wenn sie bekannt und laut Lebenssimulation im HQ verfügbar sind. |
| Ziel | 10 Archetypen × 3 Ziele mit eigenen Briefingsätzen und je zwei Abschlussentscheidungen |
| Nebenbedingung | keine Zeugen (Alarm doppelt), Zeitdruck, Zivilisten, gewarnte Gegner, nur mit Team, Funkstille (Teambonus halbiert), Doppelagent |
| Konsequenz | Einfluss und Ressourcen der Gegenseite, Haltung des Auftraggebers, Vertrauen der Auftraggeber-Figur, Weltlage-Eintrag, Hinweise, Kettenfortschritt, Folgeaufträge |

**Archetypen**
- Observation
- Undercover
- Aufklärung
- Verhandlung
- Datenauswertung
- Rettung
- Informationsbeschaffung
- Eskorte
- Schutz
- Koordinierter Einsatz

Jeder Archetyp hat 3 Vorgehensweisen mit eigenem Attribut, eigener Fertigkeit und eigenem Risiko. Bei einigen Vorgehensweisen kommt das Taktikbrett hinzu.

**Ablauf eines Auftrags**
- **Einsatz:** Lage → Vorgehen (Probe) → leise Phase (Suche, Beobachtung oder Entscheidung) oder Taktikbrett → Abschlussentscheidung.
- **Mehrtägige Aufträge** (2 Einsatztage) laufen in Phasen. Gesammelte Intel wird mitgenommen. Die nächste Phase ist frühestens am Folgetag möglich. Wer sie verstreichen lässt, scheitert, und der Grund steht in der Historie.
- **Vertagen:** einmal möglich, +3 Tage, Belohnung −20 %. Kritische Aufträge lassen sich nicht vertagen.
- **Ablehnen:** ohne Folgen; nur bei kritischen Aufträgen sinkt die Haltung um 1.
- **Fristablauf:** Die Gegenseite gewinnt Einfluss, und der Auftraggeber ist enttäuscht.

**Pool und Verlauf**
- Der Pool ist Teil des Spielstands (`GameState.ops`).
- `Ops.refresh` schreibt den Pool pro Tag genau einmal fort: Abläufe, höchstens 7 freie Aufträge, Kettenangebote.
- Erneutes Öffnen oder Laden dupliziert nichts.
- Jeder Auftrag endet in der Historie (60 Einträge) mit einem Status:
  - erfüllt
  - gescheitert
  - abgelaufen
  - abgebrochen
  - abgelehnt
- Kein Auftrag verschwindet kommentarlos.
- Jede Nachbesprechung wird über `ops.settled` genau einmal verbucht, doppelte Belohnungen sind dadurch ausgeschlossen.

**Missionsboard-Filter:** Ort, Einsatzart, Dringlichkeit, Frist ≤ 2 Tage, mehrtägig, Teamauftrag, Gefahr ≤ 2, Gefahr ≥ 4, Kette. Das Board zeigt außerdem eine Lagezeile mit der Zahl der verfügbaren Teammitglieder und der Verletzten.

**Unter 18 unverändert eingeschränkt:**
- Erlaubt sind Observation, Aufklärung, Verhandlung, Datenauswertung und Informationsbeschaffung.
- Nur ohne Taktikbrett, Gefahrenstufe ≤ 2.
- Die Gefährtin bzw. der Gefährte nimmt mit 16 und 17 nur an solchen Aufträgen teil.

Die 22 statischen Storymissionen (M001–M022) bleiben unverändert. Der alte Wochen-Generator bleibt für ältere Spielstände erhalten; das Board zeigt jetzt die Aufträge des Missionsdirektors.

## 2. Taktik 2.0 (`engine/Tactical.kt`, `engine/TacticalMaps.kt`)

- **14 handgebaute Karten (9×11, Hochformat) in 6 Umgebungen:**
  - Stadt: Marktplatz, Hinterhof, Parkhaus
  - Industrie: Containerterminal, Lagerhalle, Kanalufer
  - Gebäude: Botschaftsflügel, Bürotrakt, Archivkeller
  - Klinik: Klinikflur
  - Verkehr: Flughafenterminal, Bahnsteig
  - Natur: Stadtpark, Waldhütte
- **Geländearten:**
  - Wand: blockiert Weg und Sicht
  - Tür: passierbar, blockiert die Sicht
  - Wasser/Gleis und Glas/Zaun: blockieren den Weg, nicht die Sicht
  - Deckung
- **Sichtlinien:** Bresenham, auch durch diagonale Wandecken blockiert. Angriffe brauchen Reichweite und Sicht – auch für Gegner.
- **Deckung:** auf Deckung −25, hinter Deckung oder Wand in Schussrichtung −20.
- **Ziele:**
  - neutralisieren
  - Zielpunkt erreichen
  - Schutzperson evakuieren oder eskortieren
  - Infopunkte sichern (1 AP)
  - Aufklärungspunkte erreichen
  - Treffpunkt erreichen
  - Schutzposition N Runden halten
- **Gegnerrollen:**
  - Wachposten bleiben nah am Posten und suchen Deckung.
  - Flankierer suchen Positionen ohne Deckung des Ziels.
  - Überwachung bewegt sich nicht und hat Reichweite 8.
  - Stoßtrupps rücken vor.
- **Schwierigkeit:** Ab Gefahrenstufe 3 kommen deterministisch 1–2 zusätzliche Gegner hinzu.
- **Patt:** Vier Runden ohne jede Veränderung beenden den Einsatz mit Rückzug.
- **Verbündete:** Trefferwert, Lebenspunkte und Fähigkeit kommen aus der Lebenssimulation (Kompetenz Nahkampf, Schwerpunkt, Stimmung, Stress) und aus der Loyalität.
- **Anzeige:**
  - Zielpanel mit Fortschritt, Patt-Warnung und Rundenanzeige
  - abgedunkelte Felder außerhalb der Sicht
  - verdeckte Gegner, Rollenkürzel
  - eigene Deckung gegenüber dem nächsten Gegner
  - Gegnerliste mit Begründung („kein Sichtkontakt“, „außer Reichweite“)
  - kein Text über dem Spielfeld

## 3. Fraktionen und Weltlage

- Black Meridian, PHOENIX (nach `phoenix_truth`), Abtrünnige, Regierungsaufsicht und Zivilbevölkerung haben jeweils:
  - Haltung (−100..100, `factionStanding`)
  - Einfluss und Ressourcen (`ops.factions`)
  - einen Konfliktbogen
- **Wöchentliche Entwicklung:** Black Meridian wächst bis zu einem Gleichgewicht, die übrigen Organisationen pendeln zu ihrem Grundniveau zurück.
- **Bögen:**
  - Gegenoffensive Black Meridians (Einfluss ≥ 70): mehr und schwerere Aufträge; Rückzug bei ≤ 25
  - Budgetkürzung der Regierungsaufsicht (Haltung ≤ −30): Belohnungen −20 %
  - Kooperationsangebot der Abtrünnigen (Haltung ≥ 30)
- Die Weltlage zeigt die letzten 30 Entwicklungen.

## 4. Ermittlungsjournal (`engine/Cases.kt`)

**Fälle**
- „Der Maulwurf im Hafen“
- „Das Geisterkonto“
- „Die PHOENIX-Spur“ (nach `phoenix_truth`)
- „Das Leck im Direktorat“

**Inhalt eines Falls**
- je 6–7 Hinweise mit Quellenqualität: Gerücht, Aussage, Beweis
- Widersprüche: Ein widerlegter Hinweis zählt nicht mehr
- Verbindungen zu Personen und Orten

**Herkunft der Hinweise:** erfüllte Aufträge der Arten Daten, Information, Observation, Verhandlung, Aufklärung und Undercover sowie Kettenentscheidungen. Sie bleiben im Spielstand erhalten.

**Lösungswege**
- Beweislage vorlegen (gewichtete Belastung ≥ 5)
- Konfrontieren (≥ 2 Hinweise, Probe)
- Falle stellen (≥ 3 Hinweise): erzeugt einen Einsatz, der die Wahrheit zeigt

**Folgen:** Eine falsche Beschuldigung bleibt bestehen (Flag `case_<id>_wrong`, Einfluss und Haltung). Die richtige setzt `case_<id>_solved`.

## 5. Optionale Einsatzketten (`assets/game/ops_chains.json`)

Es gibt 10 Ketten mit insgesamt 28 Schritten:
- Echo aus PHOENIX
- Station 7
- Percys Hauptbuch
- Amandas Profil
- Michaels Lektion
- Schatten über den Docks
- Meridians Geldwäsche
- Nikitas Signal
- Alex' Vergangenheit (Freundschaft, keine Romantik)
- Geist im Netz

**Regeln**
- Jede Kette hat Entscheidungen mit Flags, Beziehungs- und Fraktionsfolgen und teils Hinweisen.
- Höchstens 3 Ketten sind gleichzeitig aktiv. Eine gescheiterte oder abgelehnte Kette wird nach 3 Tagen erneut angeboten.
- Ketten ab 16 nutzen nur sichere Archetypen.
- Die Storymissionen und die vier Enden bleiben unberührt. Ketten setzen nur eigene Flags (`chain_*`, `ledger_*`, `station7_*` …).

## 6. Einbindung der lebendigen Division

**Teamwahl**
- Wer im Einsatz, verletzt, im Urlaub, abwesend oder versetzt ist, steht nicht zur Verfügung. Das Briefing nennt den Grund.
- Unter 18 nur sichere Aufträge.
- Ab Gefahrenstufe 4 ist Rang ≥ 2 nötig.
- Das Briefing zeigt die Passung der Kompetenzen zum Auftrag.

**Folgen für Teammitglieder**
- Während des Auftrags stehen sie im Status „Einsatz mit dir“.
- Danach verändern sich Missionsbilanz, Karrierepunkte, Stimmung, Vertrauen und Erinnerungen.
- Wer auf dem Taktikbrett ausfällt, liegt einige Tage auf der Krankenstation.

## 7. Spielstände

`CURRENT_SCHEMA = 4`. `ops` hat Standardwerte. Spielstände aus v0.4–v0.6 laden (getestet), und der Pool entsteht am ersten neuen Tag. Neue Felder in `TacticalUnit`/`TacticalState`/`TacticalSetup` haben Standardwerte, laufende Einsätze alter Spielstände bleiben spielbar.

## 8. Nicht umgesetzt / Grenzen

- Keine neuen Audiodateien; die prozedurale Ambient-Musik bleibt.
- Keine neuen Bilder. Die neuen Ansichten nutzen vorhandene Hintergründe, Icons und Compose-Grafik. Wünschenswerte Assets stehen in `docs/MISSING_ITEM_ART.md`.
- Verbündete werden manuell gesteuert, eine Verbündeten-KI gibt es nicht.
- Fraktionsbögen sind zweistufig. Weitere Eskalationsstufen sind vorbereitet (`FactionState.arc`), aber nicht ausgebaut.
- Die Romanzenregeln sind unverändert übernommen (`engine/Social.kt` nicht angefasst). v0.7 enthält keine neuen romantischen Inhalte.
