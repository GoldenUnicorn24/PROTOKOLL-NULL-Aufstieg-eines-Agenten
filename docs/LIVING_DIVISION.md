# v0.6 „Living Division“ – technische Beschreibung

Stand: 0.6.0-beta. Alle Systeme laufen offline und deterministisch (Seed + Tag + Figur). Sie brauchen keine externen Dienste oder API-Schlüssel.

## 1. Lebenssimulation (`engine/Life.kt`)

Jede Figur hat ein persistentes `NpcLife` in `GameState.living.lives`:

| Feld | Bedeutung |
|---|---|
| `status` | `duty`, `training`, `free`, `mission`, `injured`, `vacation`, `absent`, `transferred` |
| `statusUntil` / `statusNote` | Dauer und Beschreibung mehrtägiger Zustände (Einsatz, Verletzung, Urlaub, Versetzung) |
| `rank`, `careerXp`, `examsPassed/Failed`, `examCooldownUntil` | eigene Laufbahn (Rang 1–9, je nach Profil gedeckelt, Minderjährige max. Rang 3) |
| `focus`, `skillGain`, `skillProgress`, `trainingDays` | eigenes Training in fünf Bereichen (Nahkampf, Technik, Analyse, Gesprächsführung, Sanität) |
| `missions`, `missionsFailed`, `injuries` | Einsatzbilanz |
| `grades` | Monatsnoten A–E (die letzten 6) |
| `mood`, `stress`, `goal`, `lastDecision` | Verfassung, Lebensziel, letzte Entscheidung |

**Tägliche Entscheidung.** Die Wahl hängt vom Profil der Figur ab (`recruit`, `agent`, `staff`, `leader`, `rogue` in `agents.json`). Dazu kommen Wochenende, Charakterzüge (ehrgeizig → mehr Training, ruhig → weniger Einsätze, humorvoll/loyal → mehr Freizeit), Stimmung, Stress, Volljährigkeit (Einsätze erst ab 18) und die Mitgliedschaft im Stammteam.
- Ein Einsatz dauert 1–5 Tage. Der Ausgang ergibt sich am letzten Tag aus Rang, Kompetenz, Stimmung und Wurf. Ein Fehlschlag führt eher zu Verletzungen (3–12 Tage Krankenstation).
- Sobald die Karrierepunkte reichen, tritt die Figur zur Prüfung an. Bestanden heißt Beförderung. Durchgefallen heißt 30 Tage Wartezeit, ein Drittel der Punkte geht verloren, und die Stimmung sinkt.
- Urlaub (5–12 Tage, nur Erwachsene, nicht Michael) und Versetzung sind selten. Versetzt werden können nur Thom, Jaden, Ryan, Helena und Sonya, bei anhaltend schlechter Stimmung, für 120–300 Tage. Danach kehren sie zurück, Inhalte gehen also nicht dauerhaft verloren.
- Nikita (`rogue`) ist meist untergetaucht. Percy, Amanda und Michael (`leader`) folgen außerhalb von Sonderzuständen ihrem Dienstplan.

**Sozialleben.** Einmal pro Woche begegnen sich anwesende Figuren. Ihre Bindung (−100..100, Schlüssel `a|b`) verändert sich nach gemeinsamen Interessen, Werten, Charakterzügen und Rangnähe. Gegensätze wie Kontrolle gegen Freiheit oder zwei Ehrgeizige ziehen ab. Ausgangswerte gibt es für Serienbeziehungen, zum Beispiel Michael–Nikita oder Alex–Jaden. Paare bilden sich nur zwischen **volljährigen** Figuren ohne Spielerromanze (Birkhoff, Sonya, Owen, Ryan, Helena, Thom, Jaden). Sinkt die Bindung unter 40, trennen sie sich.

**Fortschreibung.** `Life.advance` verarbeitet alle Tage seit `living.lastDay` bis heute. Die Funktion ist idempotent und läuft sowohl beim Tageswechsel (`World.newDay`) als auch in der Zeitplan-Simulation. Ein alter Spielstand startet das Leben am ersten neuen Tag und wird nicht rückwirkend über Jahre simuliert.

**Anwesenheit.** `World.npcLocation` richtet sich zuerst nach dem Lebensstatus. Einsatz, Urlaub, Abwesenheit und Versetzung bedeuten „außer Haus“. Verletzte liegen auf der Krankenstation. Wer trainiert, ist in der Trainingshalle, am Schießstand oder im Labor. In der Freizeit sind Figuren im Aufenthaltsbereich oder (Erwachsene) in der Stadt. Sonst gilt der bisherige Dienstplan.

**Divisionsleben-Feed.** Die letzten 160 Ereignisse (Beförderung, Prüfung, Einsatz, Verletzung, Rückkehr, Freundschaft, Spannung, Paar, HQ-Ereignis) erscheinen in den Kontakten. Wichtige Meldungen erscheinen außerdem als Tagesmeldung.

## 2. Strukturierte Erinnerungen (`engine/Bonds.kt` → `Memories`)

- Jede neue Erinnerung, die über `Relations.update` entsteht, wird automatisch als `MemoryEntry(day, type, text, weight)` abgelegt. Typ und Gewicht werden aus dem Text abgeleitet (Geschenk, Einsatz, Training, Konflikt, Vertrauen, Beziehung, Laufbahn, Meilenstein, HQ).
- Ab 24 Einträgen werden die ältesten 12 zu einer `MemorySummary` verdichtet: Zeitraum, Anzahl, Typverteilung und die prägendste Erinnerung. Höchstens 10 Zusammenfassungen bleiben erhalten, ältere werden zusammengeführt.
- Die klassische Textliste `Relation.memories` (12 Einträge) bleibt für ältere Teile des Spiels bestehen.
- **Migration:** Beim Laden eines Schema-2-Spielstands (v0.4/v0.5) werden vorhandene Textlisten in strukturierte Einträge mit Tag `-1` übernommen.

## 3. Gefährtin/Gefährte als zweite Hauptfigur

- Der Editor erfasst zusätzlich 10 Charakterzüge (bis zu 3), 12 Interessen (bis zu 3), Werte (bis zu 2), ein Lebensziel und einen Berufswunsch (Außendienst, Analyse, Technik, Sanität, Führung).
- Die Laufbahn wird durch Training, Monatsnoten, Prüfungen und Einsätze (ab 18) bestimmt, nicht durch die vergangene Zeit. `Social.customRank` liest den Rang aus `Life`.
- Der Spieler kann zur Laufbahn raten (alle 14 Tage). Weicht der Rat vom Berufswunsch ab, folgt die Figur ihm erst ab Vertrauen ≥ 55.
- Freundschaften mit anderen Figuren entstehen über das wöchentliche Sozialleben.
- Eine Beziehung zum Spieler beginnt nie automatisch. Die Lebenssimulation verändert `romanceStage` an keiner Stelle (per Test geprüft).

## 4. HQ-Ereignisse (`assets/game/hq_events.json`, `HqEvents`)

Es gibt 27 Ereignisse rund um Michael, Nikita, Birkhoff, Sonya, Amanda, Percy, Alex, Jaden, Thom, Ryan, Owen, Maya, Ethan, Helena und die Gefährtin bzw. den Gefährten. Die Bedingungen nutzen Lebensstatus (`requireStatus`), Anwesenheit (`requirePresent`), Bindungen (`minBond`/`maxBond`), Flags der Simulation (`lf_<id>_promoted`, `_exam_failed`, `_injured`, `_mission_failed`), erzählerische Bögen (`percy_pressure`, `amanda_watch`), Spielerstress und offene Konflikte.

Mögliche Folgen einer Wahl:
- Beziehungswerte mehrerer Figuren
- Änderungen am Dienstplan, etwa eine Beurlaubung durch Amanda oder eine Versetzung durch Percy
- Stimmung und Karrierepunkte
- NPC-NPC-Bindungen
- neue oder gelöste Konflikte
- Erinnerungen und Bögen
- Proben mit eigenem Ausgang bei Misserfolg

Wer nicht innerhalb von 4 Tagen reagiert, löst die `ignore`-Option aus (falls vorhanden). Ereignisse entstehen nur im Tagesspiel, nicht während der Zeitplan-Simulation, damit keine Entscheidung ungefragt fällt.

## 5. Beziehungen 2.0

- **Passung (0–100)** mit Begründung: gemeinsame Interessen (der Spieler wählt sie im Charaktereditor, dazu kommen Interessen aus dem Hintergrund), Charakterzüge, gemeinsame Geschichte (Erinnerungsgewicht), Rivalität und Konflikte.
- **Meilensteine:** erstes Gespräch, Freundschaft, enge Freundschaft, volles Vertrauen, Respekt, Rivalität, erstes und perfektes Geschenk, Mentoring, Laufbahngespräch, Konflikt beigelegt, Interesse, feste Beziehung, bewusst Freunde geblieben, Trennung.
- **Geschenke** wählt der Spieler gezielt aus. Jedes Geschenk zeigt eine vorhergesagte Reaktion: +10, wenn es ein Interesse trifft, +5 bei neugierigen oder kreativen Figuren, +3 sonst und +1 bei Wiederholung innerhalb von 60 Tagen.
- **Konflikte** kosten jede Woche Freundschaft und blockieren romantische Schritte. Lösen lassen sie sich durch eine Aussprache (Empathie-Probe) oder, bei leichten Konflikten, durch ein perfektes Geschenk. Ungelöste leichte Konflikte verblassen nach 75 Tagen, schwere werden schwächer. Wer in einer Partnerschaft 21 Tage lang keine Zeit füreinander hat, bekommt einen Konflikt.
- **Mehrere mögliche Partner:** Interesse (Stufe 1) kann bei mehreren Figuren gleichzeitig bestehen. Eine **feste Beziehung** ist eine bewusste Entscheidung mit Bestätigungsdialog und beendet andere Annäherungen respektvoll. „Nur Freunde bleiben“ pausiert Romantik für 90 Tage. Eine Trennung hinterlässt einen Konflikt.
- **Altersregel (ergänzender v0.6-Patch):** Spielerromanzen sind als freiwillige, nicht-sexuelle Beziehungen ab 16 möglich, wenn beide mindestens 16 sind und höchstens zwei Jahre auseinanderliegen (16/17, 18/16). Ab Stufe 2 bleibt die Entwicklung bis zur Volljährigkeit beider Figuren stehen; erst danach sind Zusammenziehen und Heirat möglich. Unabhängige NPC-NPC-Paare entstehen weiterhin nur zwischen Erwachsenen.

## 6. Alex-Route

Zehn neue handgeschriebene Szenen (`dlg_alexv6_*`) ergänzen die bestehenden:
- **Freundschaftsszenen ab 16:** Rangliste, geteilter Kopfhörer, drei Uhr morgens, Narben (nach einer Verletzung in der Simulation), eine Zeile höher (nach Alex' Beförderung), Achtzehn (Alex' Geburtstag).
- **Freundschaftsweg:** „Familie, die man sich aussucht“ und „Noch einmal ehrlich“. Die zweite Szene ist ab 19 eine bewusste zweite Entscheidung, bei der man Freunde bleiben oder sich öffnen kann.
- **Romantische Annäherung ab 16 (altersgerecht, nicht-sexuell):** „Ein Abend ohne Division“. Die späteren Erwachsenen-Beziehungsschritte bleiben gesperrt, solange jemand minderjährig ist.
- **Gemeinsames Kapitel:** „Danach“, mit Antworten je nach Weg.

Der reine Freundschaftsweg bleibt vollständig spielbar.

## 7. Oberfläche

- **Kontakte:**
  - Anwesenheitsleiste (im HQ, im Einsatz, verletzt, abwesend)
  - Statusplakette je Figur
  - Filter: Im HQ, Abwesend, Freunde, Spannungen, Team
  - Divisionsleben-Feed
  - Markierungen für Konflikte und NPC-Partner
- **Personalakte mit fünf Reitern:**
  - Akte: Interessen, Werte, Lebensziel, Berufswunsch
  - Laufbahn: Rang, Prüfungsreife, Einsätze, Prüfungen, Monatsnoten, Kompetenzen, Chronik
  - Beziehung: Werte, Passung, Konflikt, Meilensteine, Erinnerungen mit Zusammenfassungen
  - Umfeld: Freunde und Spannungen der Figur
  - Interaktion: Geschenkauswahl, Aussprache, Laufbahn-Rat, Bestätigungsdialoge
- **HQ:** Karte für das offene HQ-Ereignis (mit Ablaufzeit) und „Heute in der Division“. Dazu kommt ein eigener Ereignis-Screen mit Folgenhinweisen.

## 8. Spielstände

`CURRENT_SCHEMA = 3`. Alle neuen Felder haben Standardwerte. Spielstände aus v0.4 und v0.5 laden (getestet), und die Erinnerungen werden dabei migriert. Der 10-Jahres-Roundtrip ist bitgenau.

## 9. Bilder

Es wurden **keine neuen Bilder** erzeugt oder heruntergeladen. Alle neuen Ansichten nutzen vorhandene Porträts, Avatare und Icons. Optional wünschenswerte Assets sind in `docs/MISSING_ITEM_ART.md` aufgeführt.
