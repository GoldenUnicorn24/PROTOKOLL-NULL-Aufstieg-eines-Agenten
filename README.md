# PROTOKOLL NULL – Aufstieg eines Agenten

Native Android-RPG im Hochformat (Kotlin + Jetpack Compose, offline, deutsch, minSdk 26).
**Stand: 1.0.0-rc1 „Full Release“ (Release Candidate)** – vollständig durchspielbar: Prolog, vier Kapitel mit 36 Storymissionen, sechs Enden und ein Nachspiel-Kapitel nach dem Finale. Neu in 1.0: komplett überarbeitete Oberfläche (Designsystem 2.0), Taktik 3.0 mit 22 Karten und Team-KI, Missionsdirektor 3.0 mit Wendungen, Direktionsagenda für das Endgame, zehn neue HQ-Szenen und 25 neue Gegenstände. Alle Funktionen aus v0.5–v0.8 bleiben erhalten.

> **Warum „rc1“ und nicht 1.0.0?** Laut Masterplan wird 1.0.0 erst nach erfüllten Pflichtkriterien vergeben. Tests auf echten Geräten (TalkBack, Schriftgröße XL, 360/430 dp) und die externe Codex-Prüfung stehen noch aus – siehe [`docs/V1_RELEASE_CHECKLIST.md`](docs/V1_RELEASE_CHECKLIST.md).

> Privater, nicht-kommerzieller Fan-Prototyp im Universum der Serie *Nikita* (The CW, 2010–2013). Story, Dialoge und Missionen sind eigenständig geschrieben, keine Episodenabschriften. Figurennamen und Setting gehören den Rechteinhabern. Für eine öffentliche oder kommerzielle Veröffentlichung sind Rechtefreigaben oder eigenständige Figuren nötig; urheberrechtlich geschützte Serienstills sind nicht im Repository (privater ZIP-Import siehe unten).

## Neues: Serienbild-Pakete

Das Spiel unterstützt einen **zentralen privaten ZIP-Import** für bis zu elf Serienfiguren unter Einstellungen. Beschreibung, zulässige Dateinamen und Bildquellen stehen in [CAST_BUNDLE_GUIDE.md](docs/CAST_BUNDLE_GUIDE.md). Das ZIP enthält keine urheberrechtlich geschützten Bilder im öffentlichen Repository.

## Installation (Debug-APK)

1. Im Repository auf **Actions → Android CI** gehen und den neuesten grünen Lauf öffnen (für diesen Release Candidate: Läufe auf dem Branch des v1.0-Pull-Requests).
2. Unten unter *Artifacts* **`ProtokollNull-debug-apk`** herunterladen (ZIP), entpacken.
3. `app-debug.apk` aufs Android-Gerät (Android 8.0+) kopieren und installieren („Unbekannte Quellen“ erlauben).
4. Spielstände aus v0.1–v0.8 bleiben erhalten (Autosave wird beim Start übernommen; Export/Import über *Spielstände*).

Die APK wird bei jedem Push auf `main` und bei Pull Requests neu gebaut; vorher laufen alle Unit-Tests, die Inhaltsvalidierung, End-to-End-Durchläufe und ein Render-Test aller Kernscreens. Eine signierte Release-APK entsteht über den manuellen Workflow **Release-APK** (`.github/workflows/release.yml`), sobald die Keystore-Secrets gesetzt sind – Schlüssel liegen nie im Repository.

## Neu in v1.0 – Full Release (rc1)

Ausführlich: [Audit](docs/V1_AUDIT.md) · [UI-Review](docs/V1_UI_REVIEW.md) · [Release-Checkliste](docs/V1_RELEASE_CHECKLIST.md) · [Codex-Handoff](docs/CODEX_HANDOFF_V1.md) · [Spielübersicht](docs/V1_GAME_REVIEW.md)

### Oberfläche (Designsystem 2.0)
- Einheitliche Tokens und Komponenten auf allen Screens: einzeilige Reiterleisten mit Hinweisplaketten, Navigationszeilen, Kennzahlen-Kacheln, Fortschrittsringe, Attribut-Radar, Banner, leere Zustände, gestaltete Bestätigungsdialoge.
- Lesbarkeit: Sekundärtexte in Inter statt Monospace, deutsche Silbentrennung, Schlüssel/Wert-Zeilen ohne senkrechte Buchstaben.
- **HQ als Lagezentrum** (priorisierte Ereignisse, Auftrag, Schnellzugriffe, Kennzahlen), **Profil als Personalakte**, kompakte Statusleiste mit XP, Navigation mit Plaketten, animierte Übergänge, Feier-Einblendung bei Beförderung/Kapitel/jedem 5. Level – alles über „Animationen“ abschaltbar.
- Sicherheit: Bestätigung vor Einsatzabbruch, Überschreiben und Löschen; geschützte Zurück-Geste bei Einsätzen, Dialogen und Prüfungen.

### Taktik 3.0
- Neues Taktikbrett: Pfadvorschau, Gefahrenpunkte (Gegner mit Schussfeld), Trefferchance direkt am Gegner, zweistufige Steuerung (vormerken → bestätigen), Einheitenleiste, Gegnerliste.
- Reaktionsfeuer von Überwachungsposten, Rückzug angeschlagener Gegner, Überwältigungsversuche des Stoßtrupps, **„Team handelt“** (Team-KI) und **22 Karten** (+ Dachterrasse, U-Bahn-Tunnel, Forschungslabor, Villa am See, Serverfarm, Trockendock, Staatsbibliothek, Hängebrücke).

### Story
- **14 neue Storymissionen** (36 insgesamt): Alex' Schatten, Geist im Netz, das Bündnis mit Nikita, die Akademie, Michaels Akte – und **Kapitel 4 „Nachbeben“** nach dem Epilog (Kandidat 8, Dr. Lenz, ein Gesetz gegen Programme wie PHOENIX).
- **Zwei neue Enden** („Das Bündnis“, „Die Akademie“), die an diese Stränge gebunden sind. Die vier bisherigen Enden bleiben unverändert wählbar. Neue Epilog-Abschnitte greifen Entscheidungen wieder auf.
- **Missionsdirektor 3.0:** acht Wendungen in dynamischen Aufträgen (Zeuge, neuer Preis, Streife, Späher, Funkausfall, Verletzter, erkannte Tarnidentität, präparierte Daten).

### Division, Direktion, Inventar
- Zehn neue HQ-Szenen, in denen frühere Entscheidungen nachwirken; eine neue Szene, in der die Gefährtin bzw. der Gefährte selbst über ein Angebot entscheidet.
- **Direktionsagenda** (ab Rang 10): fünf Agenden über je 26 Wochen mit Sofortkosten, messbaren Zielen und einer einmaligen Bewertung.
- 25 neue Gegenstände (Ausrüstung, Verbrauchsgüter, Missionswerkzeug, Geschenke) mit eigenen Icons, Missionsboni und Geschenkinteressen.
- Fairness: Trost-XP nur beim ersten Scheitern an einem Story-Einsatz.

### Technik
- Autosave seriell im Hintergrund (Reihenfolgeschutz), Zeitraffer auf einem Hintergrund-Thread mit Ladeanzeige, Spielstand-Schema 6 mit Migration.
- Tests: End-to-End-Durchläufe bis zu allen sechs Enden und durch Kapitel 4, Zehn-Jahres-Simulation, Migration von v0.8-Spielständen, Kartenvalidierung, Agenda, Wendungen, Exploit-Schutz.

### Bekannte Einschränkungen
- Keine Tests auf echten Geräten; UI-Belege stammen aus Robolectric-Screenshots.
- 36 statt der im Masterplan angestrebten 45–60 Storymissionen (Qualität vor Menge).
- Barrierefreiheit verbessert, aber nicht mit TalkBack verifiziert.

## Neu in v0.8 – Director's Legacy

### Führungscockpit (ab Rang 8)
- Teamrollen (Aufklärung, Technik, Sanität, Kommunikation, Unterstützung) nach echter Kompetenz, Bereitschaft und Kohäsion; Status aus der Lebenssimulation (verletzt, im Einsatz, freigestellt, versetzt, minderjährig …).
- Aufgaben mit Zeit-, Energie- und Etatkosten: Training, Vorbereitung, Aufklärung, Mentoring, Dienstplanung, Nachbereitung, Erholung, Weiterbildung – genau einmal ausgewertet.
- Spannungen im Team, Aussprache, Erfolgsbilanz; Führung wirkt auf Einsatzproben (−8 bis +12), macht nichts unmöglich.

### Delegation echter Aufträge (ab Rang 9)
- Delegation von v0.7-Aufträgen aus dem Missionsboard: vorbereitet → läuft → abgeschlossen/gescheitert/abgebrochen, Rückkehrtag mit möglicher Verzögerung, einmalige Abrechnung über Fraktionen, Auftraggeber, Verletzungen, Erinnerungen und Einsatzbericht. Keine Doppelbelegung, kein Sofortabschluss.

### Direktorat (Rang 10–12)
- Sektionsleitung (eine Abteilung), stellvertretende Direktion (alle Abteilungen, Leitlinien mit Teilfreigabe, Haushaltsanträge) und Direktion (alles, inklusive Geheimhaltung) auf Basis des bestehenden DirectorState.
- Transparente Wochenrechnung (Einnahmen, Fixkosten, Personal, Unterhalt, Projekte, Fraktionslage, Aufsicht) und Monatsbericht; Organisationsbudget strikt getrennt von persönlichen Credits; genau einmal pro Woche, auch im Zeitraffer.
- 10 Abteilungsprojekte, 8 handgeschriebene Führungsdilemmata mit Proben und Spätfolgen.

### Standorte
- Sechs HQ-Module (Stufe 1–3) und vier Außenstellen (Stadtsafehouse, Analysezentrum, Sanitäts-/Ausbildungsstützpunkt, Hafenposten) mit Personalbedarf, Unterhalt und Wirkung auf Einsätze, Akten, Erholung und Weltlage; Compose-Stadtkarte. Alle 13 HQ-Räume bleiben.

### Personalakte 2.0 und Privatleben
- Reiter „Dienst“: Kompetenzen, Beurteilung (A–E), Weiterbildung, Beförderungsempfehlung, Versetzung. Figuren können Posten ablehnen; die eigene Begleitung entscheidet selbst.
- Vier Wohnstufen mit Miete und Räumung, acht Möbel mit Wirkung, 13 Stadtaktivitäten mit Tageszeit und Tageslimit, freundschaftliche Treffen, Tarnjobs mit Schichten, 15 Alltagsereignisse.
- **Romanzenregeln unverändert**: 16/17- und 18/16-Romanze freiwillig, unter 18 höchstens Stufe 2 und nicht sexuell, Alex optional. Neue Inhalte sind ausschließlich freundschaftlich.

## Neu in v0.7 – Shadow Operations

### Missionsdirektor 2.0
- **Aufträge aus Bausteinen** statt Vorlagen: Ort, Einstiegssituation, Auftraggeber, Ziel, Auflage, Konsequenzen – deterministisch aus Seed und Spielzustand, ohne Duplikate beim Öffnen oder Laden.
- **Zehn Auftragsarten**: Observation, Undercover, Aufklärung, Verhandlung, Datenauswertung, Rettung, Informationsbeschaffung, Eskorte, Schutz, koordinierter Einsatz – je drei Ziele, drei Vorgehensweisen mit eigenen Risiken, eigene Belohnungen und eine Abschlussentscheidung mit Folgen.
- **Mehrtägige Aufträge**, Vertagen, Ablehnen, Fristablauf und Abbruch – jeweils mit Begründung im Verlauf. Folgeaufträge entstehen aus Erfolgen und harten Entscheidungen.
- **Missionsboard mit Filtern** nach Ort, Art, Dringlichkeit, Frist, Dauer, Teamauftrag und Gefahrenstufe.
- Unter 18 unverändert nur ungefährliche Aufträge ohne Taktikbrett (Gefahr ≤ 2).

### 2D-Taktik 2.0
- **14 Karten in 6 Umgebungen** (Stadt, Industrie, Gebäude, Klinik, Verkehr, Natur) mit Wänden, Türen, Wasser, Glas und Deckung.
- **Sichtlinien** – keine Angriffe durch Wände, auch nicht für Gegner – und **Deckung nach Schussrichtung**.
- **Neue Ziele**: Evakuierung, Eskorte, Infopunkte sichern, Aufklärungspunkte, Treffpunkt, Schutzposition halten.
- **Gegnerrollen** (Wachposten, Flankierer, Überwachung, Stoßtrupp), Patt-Erkennung, Zusatzgegner bei hoher Gefahr.
- **Teammitglieder** kämpfen mit ihren Werten aus der Lebenssimulation; wer ausfällt, liegt danach auf der Krankenstation.

### Fraktionen, Akten und Ketten
- **Weltlage**: Haltung, Einfluss und Ressourcen von Black Meridian, PHOENIX, Abtrünnigen, Regierungsaufsicht und Zivilbevölkerung; Konfliktbögen wie Meridians Gegenoffensive oder Budgetkürzungen der Aufsicht.
- **Ermittlungsjournal** mit vier Fällen, Quellenqualität, Widersprüchen und drei Lösungswegen (Beweislage, Konfrontation, Falle).
- **Zehn optionale Einsatzketten** – PHOENIX, Divisionspolitik mit Percy und Amanda, Michaels Mentoring, Black Meridian, Nikitas Signal, Alex' Vergangenheit, Birkhoffs Geist im Netz – mit schwierigen Entscheidungen.

### Lebendige Division im Einsatz
- Teamwahl nach Anwesenheit, Verletzung, Urlaub, Alter und Rang – mit Begründung für Nicht-Verfügbare und Passung der Kompetenzen.
- Einsätze verändern Missionsbilanz, Verletzungen, Vertrauen und Erinnerungen genau einmal.
- Michael, Birkhoff, Amanda, Percy, Alex und Nikita erteilen Aufträge nur, wenn sie laut Lebenssimulation und Story verfügbar sind.

## Neu in v0.6 – Living Division

### Lebendige Division
- **Jede Figur entscheidet täglich selbst** – deterministisch nach Profil, Charakterzügen, Stimmung und Zielen: Dienst, eigenes Training, Freizeit, Einsätze (ab 18), Urlaub; dazu Verletzungen, seltene Versetzungen mit späterer Rückkehr.
- **Eigene Laufbahnen**: Karrierepunkte, Monatsnoten A–E, Prüfungen mit Beförderung oder Durchfallen (30 Tage Wartezeit), Kompetenzen in fünf Bereichen, Einsatzbilanz.
- **Sozialleben untereinander**: Freundschaften und Spannungen nach Interessen und Werten; volljährige NPC-Paare (nie Figuren, die eine Spielerromanze sein können).
- **Anwesenheit nach Status**: Wer im Einsatz, im Urlaub oder versetzt ist, ist nicht im HQ; Verletzte liegen auf der Krankenstation.
- **Divisionsleben-Feed** und Tagesmeldungen zu Beförderungen, Verletzungen, Rückkehr und Paaren.

### Gefährtin/Gefährte als zweite Hauptfigur
- Editor mit Charakterzügen, Interessen, **Werten, Lebensziel und Berufswunsch**.
- **Eigenständige Laufbahn** – Rang aus Training, Noten, Prüfungen und Einsätzen statt aus vergangener Zeit; Laufbahn-Rat durch dich (Vertrauen entscheidet, ob sie/er folgt); eigene Freundschaften in der Division. Eine Beziehung beginnt nie automatisch.

### HQ-Ereignisse
- 27 Ereignisse rund um Michael, Nikita, Birkhoff, Sonya, Amanda, Percy, Alex, Jaden, Thom, Ryan, Owen, Maya, Ethan, Helena und deine Gefährtin/deinen Gefährten – mit echten Folgen: Beurlaubung, Versetzung, Konflikte, Bindungen zwischen Figuren, Percys Druck, Amandas Beobachtung. Wer nicht reagiert, entscheidet auch.

### Beziehungen 2.0
- **Passung** mit Begründung, **Meilensteine**, **strukturierte Erinnerungen** mit Zusammenfassung älterer Ereignisse.
- **Geschenke nach Interessen** mit vorhergesagter Reaktion; **Konflikte** mit Aussprache.
- **Mehrere mögliche Partner** in der Annäherungsphase, eine **feste Beziehung nur durch bewusste Entscheidung** (Bestätigung, andere Annäherungen enden respektvoll); „Nur Freunde bleiben“ und Trennung als eigene Entscheidungen.
- **Altersgerechte Spielerromanze:** freiwillige, nicht-sexuelle Romanzen ab 16 bei maximal zwei Jahren Abstand, solange jemand unter 18 ist; Stufen zum Zusammenziehen und Heiraten erst, wenn beide erwachsen sind.

### Alex-Route
- Zehn neue Szenen: Freundschaft ab 16 (Rangliste, Kopfhörer, drei Uhr morgens, Narben, Beförderung, Alex' 18. Geburtstag), ein vollständiger Freundschaftsweg („Familie, die man sich aussucht“, „Noch einmal ehrlich“), eine altersgerechte Romanzenszene ab 16 und ein gemeinsames drittes Kapitel.

## Neu in v0.5 – Karriere, Zeitplan, Ausrüstung

### Karriere & Ausbildung
- **Leistungsbeurteilung durch Michael** (Note A–E) aus tatsächlichen Leistungen der letzten 30 Tage: Trainingseinheiten, Einsätze, Entwicklung der Attribute, Prüfungsvorbereitung, Verfassung. Anforderbar alle 30 Tage, im Zeitplan automatisch monatlich.
- **Beförderungen zu Rang 2–7 erfordern eine aktuelle Beurteilung ≥ C.** Ränge 5–11 bleiben zusätzlich an Story-Missionen gebunden.
- **Mehrphasige Beförderungsprüfung** im Direktorat: Theorie → Praxis → Lageentscheidung (drei Antworten, eine fachlich beste) → Urteil (Michaels Vertrauen + Beurteilung). Bestanden ab 60 Punkten. Nicht bestanden: Auflage zur Prüfungsvorbereitung und 14 Tage Wartezeit – nie eine Sackgasse.
- **Michael als Mentor**: Einzeltraining an deiner Schwäche, Prüfungsvorbereitung (je einmal pro Tag, Vertrauen ≥ 45, nur wenn er anwesend ist).
- **Trainingsplaner**: Ausbilder (Michael, Owen, Birkhoff, Sonya, Amanda, Ryan – nur wenn anwesend, Bonus nach Vertrauen und Kompetenz), Intensität (Leicht/Normal/Intensiv mit Verletzungsrisiko), Tageslimit (danach Ermüdung), persönliche Trainingsziele.
- **Karrierejournal** (Beurteilungen, Prüfungen, Beförderungen, Ziele, Geburtstage, Auszeichnungen) und **Freischaltungen je Dienstgrad** (z. B. Rabatt ab Rang 6, Geheimarchiv ab Rang 7, Teambefehl ab Rang 8, Delegieren ab Rang 9).

### Zeitplan & Simulation
- Ersetzt den alten Zeitraffer. Du legst für jeden Wochentag eine Tätigkeit fest (Kampftraining, Technik, Aufklärung, Sozialleben, Erholung, Dienst, Prüfungsvorbereitung) und simulierst 1/7/30/90/365 Tage oder bis zum 18. Geburtstag.
- Tag für Tag: Energie, Stress, Erfolg der Einheit, Ausbilder vor Ort, Attributfortschritt mit abnehmendem Ertrag, XP sinkend mit Level, Wundheilung (7 Heiltage pro Wunde, Erholung schneller), Sold/Miete wöchentlich, Dienstzulagen, Ruf aus Dienst gedeckelt.
- Beziehungen ändern sich nur durch tatsächliche Begegnungen mit bekannten, anwesenden Figuren; lange ohne Kontakt schläft eine Freundschaft leicht ein. Zufallsbegegnungen höchstens dreimal je Ereignis.
- **Anhalten bei wichtigen Ereignissen**: 18. Geburtstag (inkl. Gefährtenankunft), neue Story-Ereignisse, kritische Gesundheit, erfüllte Prüfungsauflage. Story-Ereignisse gehen nie verloren.
- Ausführlicher **Bericht** (Training, Attribute, XP/Level, Gesundheit/Wunden/Stress, Einnahmen/Ausgaben, Begegnungen, Ereignisprotokoll). Deterministisch: gleicher Seed + gleicher Plan = gleicher Spielstand.

### Inventar & Ausrüstung
- **Vier speicherbare Ausrüstungsprofile** (Aufklärung, Analyse, Erste Hilfe, Taktische Unterstützung); Anlegen holt Teile aus dem Spind und prüft Rangfreigaben und Gewicht.
- **Direkter Vergleich** mit dem ausgerüsteten Gegenstand (Boni, Schutz, Wirkung, Tragkraft, Gewicht, Zustand).
- **Situative Gegenstandsboni in zehn Missionsoptionen** (z. B. Kamera beim Beweisfoto am Hafen, Erste-Hilfe-Set bei Versorgung, Laptop bei der Entschlüsselung); ausgerüstet voller, im Inventar halber Bonus.
- Kein Kauf-/Rückgabe-Gewinn möglich (getestet), Rangrabatt ab Rang 6. Alle 42 Gegenstände haben ein vorhandenes Bild; ein neutraler Platzhalter mit Asset-ID erscheint nur, falls eine Datei fehlt (siehe `docs/MISSING_ITEM_ART.md`).

### Beziehungen
- **Ergänzender v0.6-Patch:** Freiwillige, nicht-sexuelle 16/17- und 18/16-Romanzen sind wieder möglich, begrenzt auf Interesse und feste Partnerschaft bis beide 18 sind. Alex-Route ab 16, weitere Erwachsenenfunktionen nach Alter; alte Spielstände bleiben erhalten.

### Private Serienporträts
- Lokale Bildauswahl pro Serienfigur über den Android-Fotopicker (PR #2), ohne Upload; wird in Dialogen, Personalakten und allen NPC-Porträts verwendet. Details: [private Serienporträts](docs/PRIVATE_CAST_PORTRAITS.md).

## Screenshots

Automatisch von Robolectric/Roborazzi aus echtem Spielzustand gerendert (Workflow *Screenshots*, Ordner `docs/screenshots/`). Fotos und Item-Icons werden in der Testumgebung synchron geladen und sind daher sichtbar.

| Start | Charaktereditor | Prolog | Dialog |
|---|---|---|---|
| ![](docs/screenshots/01_start.png) | ![](docs/screenshots/02_charaktereditor.png) | ![](docs/screenshots/03_prolog.png) | ![](docs/screenshots/04_dialog_michael.png) |
| **HQ-Lageplan** | **Einsatzboard** | **Briefing** | **Mission** |
| ![](docs/screenshots/05_hq_lageplan.png) | ![](docs/screenshots/06_einsatzboard.png) | ![](docs/screenshots/07_briefing.png) | ![](docs/screenshots/08_mission_entscheidung.png) |
| **Taktikbrett** | **Nachbesprechung** | **Untersuchung** | **Inventar** |
| ![](docs/screenshots/09_taktikbrett.png) | ![](docs/screenshots/10_nachbesprechung.png) | ![](docs/screenshots/11_untersuchung.png) | ![](docs/screenshots/12_inventar.png) |
| **Gegenstand** | **Ausrüstungskammer** | **Kontakte** | **Personalakte** |
| ![](docs/screenshots/13_gegenstand.png) | ![](docs/screenshots/14_ausruestungskammer.png) | ![](docs/screenshots/15_kontakte.png) | ![](docs/screenshots/16_personalakte.png) |
| **Profil** | **Skilltree** | **Karriere/Ränge** | **Zeitplan** |
| ![](docs/screenshots/17_profil.png) | ![](docs/screenshots/18_skilltree.png) | ![](docs/screenshots/19_raenge.png) | ![](docs/screenshots/20_kalender.png) |
| **Rekrutierungseditor (ab 18)** | **Direktorat** | **Spielstände** | **Epilog** |
| ![](docs/screenshots/21_rekrutierungseditor.png) | ![](docs/screenshots/22_direktorat.png) | ![](docs/screenshots/23_spielstaende.png) | ![](docs/screenshots/24_epilog.png) |
| **Zeitplan-Bericht (v0.5)** | **Karriere (v0.5)** | **Trainingsplaner (v0.5)** | **Prüfung (v0.5)** |
| ![](docs/screenshots/25_zeitplan_bericht.png) | ![](docs/screenshots/26_karriere.png) | ![](docs/screenshots/27_trainingsplaner.png) | ![](docs/screenshots/28_pruefung.png) |
| **Ausrüstungsprofile (v0.5)** | **Kontakte mit Status (v0.6)** | **Personalakte · Laufbahn (v0.6)** | **Beziehung & Erinnerungen (v0.6)** |
| ![](docs/screenshots/29_ausruestungsprofile.png) | ![](docs/screenshots/30_kontakte_status.png) | ![](docs/screenshots/31_personalakte_laufbahn.png) | ![](docs/screenshots/32_beziehung_erinnerungen.png) |
| **Umfeld (v0.6)** | **Heute in der Division (v0.6)** | **HQ-Ereignis (v0.6)** | |
| ![](docs/screenshots/33_umfeld.png) | ![](docs/screenshots/34_hq_division_heute.png) | ![](docs/screenshots/35_hq_ereignis.png) | |
| **Taktik 3.0 (v1.0)** | **Direktionsagenda (v1.0)** | **Kapitel 4 (v1.0)** | **Wendung (v1.0)** |
| ![](docs/screenshots/50_taktik3_labor.png) | ![](docs/screenshots/51_direktion_agenda.png) | ![](docs/screenshots/52_kapitel4_briefing.png) | ![](docs/screenshots/53_wendung.png) |
| **Beförderung (v1.0)** | **Ende: Die Akademie (v1.0)** | | |
| ![](docs/screenshots/54_feier_befoerderung.png) | ![](docs/screenshots/55_epilog_akademie.png) | | |
| **Mission Control (v0.7)** | **Weltlage (v0.7)** | **Einsatzketten (v0.7)** | **Ermittlungsakte (v0.7)** |
| ![](docs/screenshots/36_mission_control.png) | ![](docs/screenshots/37_weltlage.png) | ![](docs/screenshots/38_einsatzketten.png) | ![](docs/screenshots/39_ermittlungsakte.png) |
| **Auftragsbriefing (v0.7)** | **Taktik 2.0 (v0.7)** | **Führungscockpit (v0.8)** | **Delegation (v0.8)** |
| ![](docs/screenshots/40_auftrag_briefing.png) | ![](docs/screenshots/41_taktik_botschaft.png) | ![](docs/screenshots/42_fuehrungscockpit.png) | ![](docs/screenshots/43_delegation.png) |
| **Standorte (v0.8)** | **Führungsentscheidung (v0.8)** | **Wochenbericht (v0.8)** | **Stadt & Privatleben (v0.8)** |
| ![](docs/screenshots/44_standorte.png) | ![](docs/screenshots/45_direktorat_dilemma.png) | ![](docs/screenshots/46_wochenbericht.png) | ![](docs/screenshots/47_stadt_privatleben.png) |
| **Alltagsereignis (v0.8)** | **Personalakte · Dienst (v0.8)** | | |
| ![](docs/screenshots/48_alltagsereignis.png) | ![](docs/screenshots/49_personalakte_dienst.png) | | |

## Was ist spielbar? (ehrlicher Stand)

### ✅ Spielbar
- **Start-/Fortsetzen-Menü**, 3 Speicherslots + Autosave nach jeder Aktion, atomare Speicherung, Import/Export als JSON, automatische Migration alter Foundation-Spielstände (Schema 1 → 2).
- **Charaktereditor** mit lokalem Fotoimport, Schnellstart über Vorgabewerte und optionaler Gefährtenakte: Name, Deckname, Geschlecht, Auftreten, prozeduraler Original-Avatar (Hautton, Frisur, Haarfarbe, Augen, Kleidung, Zubehör), PHOENIX-Hintergrund, Stärke/Schwäche, Schwierigkeitsgrad.
- **Prolog 13–16 Jahre** mit 21 Szenen, von denen du pro Durchlauf 13 erlebst; Entscheidungen setzen Attribute und Story-Flags (z. B. Gehorsam/Widerstand gegenüber Voss).
- **Kapitel 1 „Neues Blut“** komplett: 9 Quests, **10 handgeschriebene Missionen** (M001–M010) mit Briefing, Vorgehenswahl, Team, Ausrüstungsboni, Abbruch-/Fehlpfaden, Nachbesprechung und Konsequenzen; Kapitelabschluss im Direktorat mit mehreren Ausgängen.
- **Missionstypen**: Entscheidungen mit transparenten Erfolgschancen, Untersuchung (Suchraster mit Hinweisen), Observation (Muster erkennen), Undercover (Tarnungswert), Rettung und **rundenbasiertes abstraktes 2D-Taktikbrett** (Deckung, Bewegung, AP, Status, Teamfähigkeiten, Gegner-KI, deterministische Würfe).
- **Missionsgenerator**: wöchentliches Einsatzboard aus Seeds (Ort, Fraktion, Zeugen, Risiko, Zielperson). Unter 18 nur Gespräche, Untersuchungen und Observationen mit Gefahr ≤ 2.
- **Erkundbares 2D-HQ**: 4 Ebenen, 13 Räume als antippbarer Lageplan, Zutritt nach Rang/Alter/Sicherheitskarte, Raumaktionen, **NPC-Tagespläne** (wer ist wann wo), zufällige Begegnungen.
- **Kalender**: 6 Zeitslots pro Tag, Schlafen/Ausruhen, Erschöpfung ohne Schlaf, Wochensold, Geburtstage, **Zeitraffer** (Woche/Monat/3 Monate/bis 18) in geschlossener Form mit Routinen – keine 365-fach-Schleifen.
- **Werte**: Energie, Stress, Gesundheit, Wunden, Regeneration (Krankenstation, Psychologie, Café, Wohnung).
- **Progression**: Level 1–100, 6 Attribute mit Level-abhängigem Limit, Training mit Partnerbonus, **12 Ränge** mit Level/Alter/Ruf/Vorgesetzten-Vertrauen/Missionen/Story und Prüfung im Direktorat.
- **Skilltree**: 9 Bäume, 36 Fähigkeiten (aktiv/passiv, Levelgrenzen, Voraussetzungen, Meisterperks mit baumübergreifenden Anforderungen), scroll- und zoombar.
- **Inventar** mit Gegenstandsbildern (38 SVGs), 6 Ausrüstungsslots mit Attributboni, Stapeln, Seltenheit, Zustand/Verschleiß, Gewicht/Tragkraft, Spind-Lager, Filter, Detailansicht, Verbrauchsgegenstände, **Ausrüstungskammer** (Anfordern/Warten/Zurückgeben, Rangfreigaben).
- **Beziehungen**: Freundschaft, Vertrauen, Loyalität, Respekt, Zuneigung, Rivalität, Erinnerungen; 22 handgeschriebene, verzweigte Dialoge mit Bedingungen und Proben; Smalltalk, Zeit verbringen, Rat, Geschenke, Wettkämpfe gegen Rivalen.
- **Romanze (altersgerecht)**: mit Maya, Ethan, Kira, eigenen Rekruten sowie optional Alex Udinov. Ab 16 zwischen Figuren mit höchstens zwei Jahren Abstand sind freiwillige, nicht-sexuelle Beziehungen bis Stufe 2 möglich; Zusammenziehen/Heirat erst nach Volljährigkeit beider und Familie nach den zusätzlichen 21er-Bedingungen.
- **Rekrutierungseditor ab 18**: neue/r 16-jährige/r Agent/in (Standard: anderes Geschlecht) mit Name, Avatar, Charakterzügen, Hintergrund, Startberuf, Interessen und Fähigkeiten; eigenständiger NPC mit Tagesplan, Karriere und Mentoring – nicht sofort Partner/in.
- **Team**: Teamgröße nach Rang, Teammitglieder kämpfen im Taktikmodus mit eigenen Fähigkeiten; ab Rang 8 Teambefehl, ab Rang 9 Delegieren von Aufträgen.
- **Direktorat (Rang 12)**: Budget, 5 Abteilungen mit Ausbau und Leitung, Moral, Aufsicht, Leitlinien, wöchentliche Abrechnung; Abteilungsboni wirken auf Training, Heilung, Intel und Alarm.
- **Ziviles Doppelleben** ab 17/18: Tarnjob, eigene Wohnung, Innenstadt.
- **Einstellungen**: Schriftgröße (S–XL), Animationen, Effektlautstärke, hoher Kontrast, Haptik. **Credits** mit Bild-/Lizenz- und KI-Hinweisen.

### ✅ Neu in 0.3 (Vervollständigung)
- **Kapitel 2 „Protokoll Null“** und **Kapitel 3 „Schattenrat“**: insgesamt 22 Story-Missionen (M001–M022), 19 Quests, 34 Dialoge; neue Figuren Kira (ehemalige Kandidatin 4, später Teammitglied/Romanze ab 18) und Helena (Maulwurf, kann umgedreht werden).
- **Ränge 5–11 sind an Story-Missionen gekoppelt** (z. B. Teamleiter erst nach „Feuerprobe“).
- **Finale**: letzte Entscheidung als Direktor/in mit **vier Enden** (Im Licht, Die neue Ordnung, Phönix, Spurlos) und einem **dynamischen Epilog**, der Voss’ Schicksal, Michael, Partner/Familie, Kira, Nikita, Birkhoff, Percy und deine Bilanz berücksichtigt. Danach freies Spiel.
- **Design 2.0**: eigene Schriften (Anton, Inter, Anonymous Pro – OFL), eigene Vektor-Icons, Glas-Panels mit Eckmarken, Scanline-Hintergrund, Radar-Emblem, kinoartige Dialoge mit Schreibmaschinentext, Chancen-Ringe, Phasen-Stepper, Raum-Icons und Anwesenden-Miniaturen im Lageplan, überarbeitetes Taktikbrett.
- **Musik**: prozedural erzeugte, eigene Ambient-Musik (Lautstärke in den Einstellungen, pausiert im Hintergrund).

### 🟡 Bewusst offen
- **Serienstills**: Umschalter eingebaut, aber keine Stills im Repo, da keine Nutzungsrechte vorliegen (siehe unten).
- Kein Sprachausgabe/Voice-Over, keine aufgenommene Musik; Tablet-Layouts und weitere Sprachen geplant.

## Figuren-Porträts und Bildrechte

- **Serienfiguren** (Michael, Nikita, Alex, Birkhoff, Amanda, Percy, Thom, Jaden, Owen, Sonya, Ryan): CC-lizenzierte Fotos der Darsteller von Wikimedia Commons (`app/src/main/assets/cast/`, Credits in `assets/CAST_IMAGE_CREDITS.json`). Das sind keine Serienstills und keine Rechte an den Figuren.
- **Serienstills**: Die Szenenbilder in `assets/SERIES_LOOK_REFERENCES.json` sind © The CW bzw. Rechteinhaber und dienen nur als Stilreferenz. Sie werden **nicht** heruntergeladen oder verlinkt. Liegt eine Nutzungserlaubnis vor, kann `app/src/main/assets/cast/show_<id>.jpg` ergänzt und in `app/src/main/assets/licenses/series_image_licenses.json` mit `verified: true`, Lizenz und Nachweis eingetragen werden – erst dann zeigt die App das Still. Ein Test bricht den Build ab, wenn ein `show_*.jpg` ohne Lizenznachweis auftaucht.
- **Originalporträts und Schauplätze**: Die fünf Porträts von Voss, Maya, Ethan, Kira und Helena und die neun Szenenbilder wurden für das Spiel mit OpenAI ImageGen erstellt. Für selbst erstellte Figuren stehen ein prozeduraler Avatar und der lokale Fotoimport zur Wahl.
- **Gegenstände**: 32 Icons von game-icons.net (CC BY 3.0, Hintergrund entfernt), 6 eigene Original-Icons (CC0); siehe `assets/ITEM_IMAGE_CREDITS.json`.
- **Schriften**: Anton, Inter, Anonymous Pro (SIL OFL 1.1), Lizenztexte in `app/src/main/assets/licenses/fonts/`.
- **Musik/Icons der Oberfläche**: eigene Erstellung.

## Technik

- Kotlin 2.0, Jetpack Compose (Material 3, eigenes Designsystem 2.0 in `ui/theme/Theme.kt` und `ui/theme/Components.kt`), AGP 8.7, Gradle-Wrapper 8.9, compile/targetSdk 35, minSdk 26.
- Architektur: `model/` (Kataloge + versionierter `GameState`), `engine/` (reine, deterministische Regeln: Clock, Progression, Inventory, Social/Romance, Missions, Tactical, MissionGenerator, Director, Narrative), `data/` (JSON-Kataloge, `SaveRepository` mit atomarer Speicherung und Migration), `ui/` (`GameViewModel` + Compose-Screens).
- Abweichung vom ursprünglichen Plan: statt Room/DataStore/Navigation werden versionierte JSON-Spielstände (atomar per tmp+fsync+rename) und eine Overlay-Navigation im ViewModel genutzt – offlinefähig, testbar und ohne Annotation-Processing.
- Spielinhalte liegen in `app/src/main/assets/game/` (`agents`, `items`, `skills`, `ranks`, `rooms`, `missions`, `quests`, `dialogues`, `story`, `events`, `places`, `factions`).
- Deterministische Würfe (SplitMix64, Seed + Wurfzähler), persistierte NPC-Beziehungen.

### Build lokal
```bash
./gradlew testDebugUnitTest assembleDebug
./gradlew recordRoborazziDebug   # Screenshots nach docs/screenshots
```

### Tests
- `ContentValidationTest`: Kataloggrößen, eindeutige IDs, alle Asset-Pfade, Serienstill-Lizenzregel, Missions-/Dialog-/Quest-Referenzen, unerreichbare Dialogknoten, Prolog-Graph, Kapitel 1 = 10 Missionen.
- `RulesTest`: Level-100-Grenze, Editor ab 18, altersgerechte 16/17- und 18/16-Romanzen, Alex-Route ab 16, Vertrauens-Clamp, Itemstapel, Gewichtsgrenze, Ausrüstungsfreigaben, Rang-12-Voraussetzungen, Stress/Energie, Zeitraffer, Save-Roundtrip, Migration, Dialogentscheidungen, Missionsablauf, Taktik-Determinismus, Generator-Altersgrenzen, Prolog → Spiel.
- `V05SystemsTest`: Simulation 1/7/30/90/365 Tage deterministisch, Fortschritt nur aus Tätigkeiten, Beziehungen nur mit bekannten/anwesenden Figuren, Wundheilung, Stopp beim 18. Geburtstag mit einmaliger Gefährtenankunft, Story-Ereignisse bleiben erhalten, kein Rang 12 per Zeitraffer, **10-Jahres-Spielverlauf** mit Invarianten (Kalender, Geld, Beziehungen, Geburtstage genau einmal, Ereignisidentitäten, Save-Roundtrip), Beurteilung, Prüfungsphasen/Auflagen/Wartezeit, Tageslimit/Ausbilderbonus, Mentoring, Ausrüstungsprofile mit Rangsperre, Kauf/Rückgabe ohne Gewinn, Vergleich, Missionsboni, Archiv ab Rang 7, v0.4-Spielstand ohne neue Felder.
- `LivingDivisionTest` (v0.6): Lebenssimulation deterministisch, idempotent und persistent; **10 Jahre Divisionsleben** (Beförderungen, Durchfallen, Einsätze, Verletzungen, Urlaub/Versetzung, NPC-Freundschaften, Paare nur unter Erwachsenen, keine automatische Romanze, Gefährten-Laufbahn, verdichtete Erinnerungen, Save-Roundtrip); Laufbahn hängt vom Charakter ab; Laufbahn-Rat; Anwesenheit nach Status; Rückkehr nach Urlaub; Erinnerungs-Zusammenfassung und Migration aus Schema 2; v0.5-Spielstand ohne Lebensdaten; HQ-Ereignisse mit Folgen, Ablauf mit „ignore“, Wissens-/Ankunftsprüfung; Geschenke nach Interessen; bewusste feste Beziehung, Nur-Freunde, Trennung; Aussprache; Passung; Altersregel; Alex-Szenen; Katalogvalidierung.
- `ShadowOpsTest` (v0.7, 27 Tests): Kartenbibliothek (Validität, Erreichbarkeit, keine Duplikate), BFS, Sichtlinien, keine Angriffe durch Wände, Deckung nach Richtung, Ziele Infopunkte/Aufklärung/Treffpunkt/Evakuierung/Schutzposition, Patt-Erkennung, Reproduzierbarkeit, Teamwerte aus Life; deterministischer Pool ohne Duplikate, zehn Archetypen mit Alternativen und gültigen Stage-Verweisen, sichere Aufträge unter 18, Teamstatus/Alter/Rang, einmalige Verbuchung, mehrtägige Aufträge, Vertagen/Ablehnen/Ablauf, Teamauflage, Fraktionsbögen, Akten mit Widersprüchen, Falle, Ketten mit Entscheidungen, Katalogprüfung, v0.6-Spielstand ohne Ops-Daten, **10 Jahre Shadow Operations**.
- `DirectorsLegacyTest` (v0.8, 33 Tests): Ranggrenzen 7/8/9/10/11/12, Sektionsbefugnis, Minderjährigenschutz (nur Training, keine Posten/Delegation), Aufgaben und Mentoren genau einmal, Ablehnung und Eigenständigkeit der Begleitung, Beurteilung/Versetzung, Delegation ohne Sofortabschluss, einmalige Abrechnung, Abbruch, keine Doppelbelegung, Finanzen einmal pro Woche und im Zeitraffer, getrennte Credits, Projekte nur bei Deckung, Haushaltsantrag, Dilemmata mit Spätfolgen, Außenstellen mit Personal/Schließen/Wiedereröffnen, HQ-Module, Migration der v0.7-Wohnung, Miete/Räumung, Möbel, Freizeitgrenzen, Treffen ohne Romanzenänderung, Tarnjob-Schichten, Alltagsereignisse, vollständiger v0.7-Spielstand (Team, Aufträge, 102 Gegenstände), Neuladen mitten in Projekt und Delegation, Führungsbonus, **1/7/30/90/365/3650 Tage** mit Invarianten, Determinismus, Spielstände Schema 2–4, unveränderte Romanzenregeln.
- `V1ReleaseTest` (v1.0): 22 Karten mit Erreichbarkeit, Pfade, Reaktionsfeuer, Team-KI, Rückzug, Ziele auf neuen Karten; 36 Missionen/4 Kapitel; 6 Enden; Kapitel-Flags; Kapitel 4 nach dem Epilog; kein XP-Farming; Agenda; Feier-Erkennung; Plaketten; HQ-Szenen; Wendungen; neue Gegenstände; v0.8-Migration; 10 Jahre Simulation.
- `E2EPlaythroughTest` (v1.0): Kampagne über echte Engine-Aktionen bis zu allen sechs Enden und durch Kapitel 4.
- `ScreenshotTest`: rendert 55 Kernscreens mit echtem Spielzustand (Robolectric).

## Changelog

### 1.0.0-rc1 „Full Release“ (Release Candidate)
- Designsystem 2.0 und Überarbeitung aller Screens (Reiter, Typografie, HQ-Lagezentrum, Personalakte, Inventar, Briefing, Nachbesprechung, Spielstände, Einstellungen), Feier-Einblendungen, geschützte Zurück-Geste, Bestätigungsdialoge.
- Taktik 3.0: Canvas-Brett mit Vorschauen, zweistufige Steuerung, Reaktionsfeuer, Rückzug, Team-KI, 22 Karten.
- 14 neue Storymissionen inkl. Kapitel 4 „Nachbeben“, zwei neue Enden, Kapitelabschluss-Flags, 5 neue Quests, 2 neue Story-Dialoge.
- Missionsdirektor 3.0 mit acht Wendungen; zehn neue HQ-Szenen; Direktionsagenda; 25 neue Gegenstände.
- Hintergrund-Autosave, asynchroner Zeitraffer, Schema 6, Exploit-Schutz bei Story-Fehlschlägen, vorbereiteter Release-Workflow.
- Neue Testsuiten (V1ReleaseTest, E2EPlaythroughTest), 6 neue Screenshots, Dokumentation unter `docs/V1_*.md` und `docs/CODEX_HANDOFF_V1.md`.

### 0.8.0-beta „Director's Legacy“
- Führungscockpit ab Rang 8: Rollen, Aufgaben, Kohäsion, Aussprache, Bereitschaft, Führungsbonus.
- Delegation echter v0.7-Aufträge ab Rang 9 mit Status, Rückkehr, Verzögerung, Abbruch und einmaliger Abrechnung.
- Direktorat in drei Stufen (Rang 10/11/12) auf dem bestehenden DirectorState: Abteilungen mit wirksamer Stärke, Leitungen, ausgebaute Leitlinien, Wochen- und Monatsrechnung, 10 Projekte, Haushaltsanträge, 8 Führungsdilemmata mit Spätfolgen.
- Sechs HQ-Module und vier Außenstellen mit Personal, Unterhalt und Wirkung.
- Personalakte 2.0: Beurteilung, Weiterbildung, Beförderungsempfehlung, Versetzung; Annahme oder Ablehnung von Posten.
- Privatleben: Wohnstufen, Möbel, Stadtkalender, Hobbys, Treffen, Tarnjobs mit Schichten, 15 Alltagsereignisse.
- Schema 5 mit Migration der v0.7-Wohnung und des Tarnjobs; 33 neue Unit-Tests, 8 neue Screenshots. Romanzenregeln unverändert.

### 0.7.0-beta „Shadow Operations“
- Missionsdirektor 2.0 mit zehn Auftragsarten, modularen Bausteinen, mehrtägigen Aufträgen, Filtern und langfristigen Konsequenzen.
- Taktik 2.0: 14 Karten, 6 Umgebungen, Sichtlinien, Richtungsdeckung, neue Ziele, Gegnerrollen, Patt-Erkennung.
- Fraktionen mit Weltlage und Konfliktbögen, Ermittlungsjournal mit vier Fällen, zehn optionale Einsatzketten.
- Teamwahl und Einsatzfolgen verzahnt mit der Lebenssimulation.
- Mission Control, Auftragsbriefing, Weltlage, Akte, Ketten, Taktik-HUD; Schema 4.

### 0.6.0-beta „Living Division“
- Lebenssimulation aller Figuren mit eigenen Laufbahnen, Einsätzen, Verletzungen, Abwesenheiten, Versetzungen und Sozialleben.
- Strukturierte Erinnerungen mit Zusammenfassung; Schema 3 mit Migration.
- Gefährtin/Gefährte mit Werten, Lebensziel, Berufswunsch und eigenständiger Laufbahn.
- 27 HQ-Ereignisse mit Folgen; HQ-Übersicht „Heute in der Division“.
- Beziehungen 2.0 (Passung, Meilensteine, Geschenke nach Interessen, Konflikte, bewusste Partnerschaft).
- Zehn neue Alex-Szenen mit vollständigem Freundschaftsweg.
- Neue/überarbeitete Screens: Kontakte, Personalakte (Laufbahn, Beziehung, Umfeld), HQ-Ereignis, Gefährten-Editor, Spieler-Interessen.

### 0.5.0-beta
- Karriere 2.0 (Beurteilungen, Mentoring, dreiphasige Prüfungen mit Auflagen, Ziele, Journal, Auszeichnungen, Rangfreigaben).
- Zeitplan-Simulation statt pauschalem Zeitraffer, mit Bericht und Ereignis-Stopps.
- Ausrüstungsprofile, Gegenstandsvergleich, situative Missionsboni, Rangrabatt.
- Neue Screens: Zeitplan-Assistent, Karriereübersicht, Prüfung, Trainingsplaner.
- Historischer Stand von v0.5: Romanzen nur unter Volljährigen. Der ergänzende v0.6-Patch hat die altersgerechten Stufen 1–2 ab 16 wiederhergestellt; private Serienporträts (PR #2) sind integriert.


### 0.4.0-beta
- Ein Fundstück vom Bahnhof begleitet dich durch die PHOENIX-Jahre. Entscheidungen über Beweise, Erinnerung und Kira eröffnen spätere Gesprächsoptionen und prägen den Epilog.
- Eigene Fotos lassen sich für die Hauptfigur und die Gefährtenakte übernehmen. Die App verkleinert die Bilder lokal und nimmt sie beim JSON-Export mit.
- Eine vor dem Prolog erstellte Gefährtenakte wird an deinem 18. Geburtstag wirksam. Michael stellt euch persönlich vor. Die neue Figur tritt wie im ursprünglichen Entwurf mit 16 ein. Eine altersgerechte nicht-sexuelle Romanze kann nach dem Kennenlernen entstehen; Erwachsenenfunktionen erst, wenn beide volljährig sind.
- Neun eigens generierte Szenenbilder und fünf Originalporträts begleiten Prolog, Einsatzbriefings, Zuteilung und Personalakten. Observationen verwenden erkennbare Zeichenfolgen.

### 0.3.0-beta
- Kapitel 2/3, Finale mit vier Enden, Epilog, neue Figuren, Rang-Story-Kopplung.
- Design 2.0 mit eigenen Schriften/Icons, kinoartigen Dialogen, prozeduraler Musik.
- Screenshot-Tests laden Bilder synchron.

### 0.2.0-beta
- Gradle-Wrapper, GitHub-Actions-CI mit Tests, Render-Smoke-Test und Debug-APK-Artefakt, Screenshot-Workflow.
- Komplett neue Engine und Oberfläche (siehe „Spielbar“), Inhalte für Prolog, Kapitel 1 und Teile von Kapitel 2.
- Migration der Foundation-Spielstände.

### 0.1.0-foundation
- Von ChatGPT erstellte Grundlage: Kataloge, Icons, Darstellerfotos, erste Screens (ohne nachgewiesenen Build).
