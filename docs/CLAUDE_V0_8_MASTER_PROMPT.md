# CLAUDE CODE — PROTOKOLL NULL v0.8.0-beta „Director's Legacy“

## Auftrag

Du bist leitender Senior-Android-Entwickler, Gameplay-Engineer und QA-Verantwortlicher für **PROTOKOLL NULL – Aufstieg eines Agenten**.

Repository: https://github.com/GoldenUnicorn24/PROTOKOLL-NULL-Aufstieg-eines-Agenten

**Aufgabe:** Implementiere auf Basis der aktuellsten stabilen v0.7 die neue **v0.8.0-beta — Director's Legacy**: umfassende Teamführung, Personalmanagement, Direktorat, Standorte/Außenstellen und ein tiefes Privatleben. Es soll tatsächlich spielbar sein, kein Konzept oder UI mit leeren Schaltflächen.

**Arbeitsweise:** Lies zuerst das tatsächliche Repository, lege einen eigenen Branch `feature/v0.8-directors-legacy` an, arbeite in thematischen Commits, teste jede größere Spielmechanik und erstelle am Ende einen Pull Request mit funktionierender Debug-APK. Kein Rebuild der App, keine Änderungen direkt auf main, kein Abschalten fehlerhafter Tests.

## 0. Vorhandene Codebasis sorgfältig prüfen

Lies insbesondere:
- README.md
- docs/SHADOW_OPERATIONS.md
- docs/LIVING_DIVISION.md
- docs/V06_ROMANCE_UPDATE.md
- docs/AI_WORKFLOW_AND_ROMANCE.md
- docs/EXPANDED_EQUIPMENT_V07.md
- docs/CAST_BUNDLE_GUIDE.md
- app/src/main/java/de/protokollnull/agent/model/{GameState.kt,LivingModels.kt,OpsModels.kt,GameModels.kt}
- app/src/main/java/de/protokollnull/agent/engine/{Director.kt,Career.kt,World.kt,Life.kt,Bonds.kt,Social.kt,Ops.kt,Simulation.kt,Inventory.kt,Tactical.kt}
- app/src/main/java/de/protokollnull/agent/ui/screens/{ProfileScreens.kt,HqScreen.kt,LivingScreens.kt,CareerScreens.kt,OpsScreens.kt,CompanionScreens.kt,ContactsScreens.kt,InventoryScreens.kt}
- app/src/main/assets/game/{rooms.json,ranks.json,items.json,hq_events.json,ops_chains.json}
- app/src/test/java/de/protokollnull/agent und .github/workflows/

**Verifizierte Basis von v0.7:**
- Android Kotlin/Compose Material3, minSdk 26, Hochformat, offline, deterministisch.
- 12 Ränge, 100 Level, ab Rang 8 Teamleiter, 9 Einsatzkommandant, 10 Sektionsleiter, 11 stellvertretender Direktor, 12 Direktor.
- Die jetzige Director.kt kann fünf Abteilungen (training, tech, medical, intel, security), Level, Budget, Politikoptionen und Abteilungsleitungen verwalten.
- Die bestehende Delegation in Director.kt verwendet noch das alte MissionGenerator-Board; v0.7 hat dagegen OpsState.contracts. Keine zweite konkurrierende Missionsquelle schaffen.
- 13 HQ-Räume auf vier Ebenen; die Stadt ist bislang ein Raum.
- World.rentApartment verwendet player.apartment (Boolean), 450 Cr Kaution und 150 Cr wöchentliche Miete; coverJob ist ein einfaches Arbeitsflag.
- LivingState und Life.kt simulieren NPCs, Karrieren, Erinnerungen, Verletzungen, Verfügbarkeit und Tagesroutinen.
- OpsState bietet neue Auftragsarten, 14 Taktikkarten, Fraktionen, Fälle und Missionsketten.
- Inventar: aktuell 102 Gegenstände (darunter 24 neue Waffen und 36 neue andere Gegenstände in v0.7) und 60 neue eigene SVG-Illustrationen.
- Bilder der Serienfiguren: zentraler privater ZIP-Import in Einstellungen, keine geschützten Show-Stills im öffentlichen GitHub.
- Der Login-/Startbildschirm zeigt dynamisch BuildConfig.VERSION_NAME an, keine hartcodierte v0.4.

Die vorhandene Basis ist zu ERWEITERN, nicht umzubenennen oder zu duplizieren. Falls Quellen nach dem Lesen abweichen, gelten die tatsächlich gelesenen Klassen und die konkreten Nutzerwünsche.

## 1. UNVERHANDELBAR: Romanzenregeln erhalten

Claude hat diese Regel in älteren Versionen mehrfach zurückgesetzt; v0.8 darf das **nicht** wieder tun:

- Spieler 16 / Alex Udinov 17: freiwillige, ausschließlich altersgerechte, nicht-sexuelle Romanze möglich, aber nur wenn der Spieler ausdrücklich Alex wählt (Flag alex_romance_unlocked).
- Spieler 18 / selbst erstellte Gefährtin 16: freiwillige, nicht-sexuelle Beziehung möglich, maximal zwei Jahre Altersunterschied, solange eine Figur unter 18 ist.
- Spieler 19 / Gefährtin 16: entsprechend der freiwilligen Spielregel gesperrt.
- Solange eine Figur minderjährig ist, maximal Beziehungsstufe 2 („fest zusammen“); Zusammenziehen, Heirat und Familienplanung erst nach Volljährigkeit beider. Familienplanung erst, wenn beide mindestens 21 sind.
- Wenn beide volljährig sind, gilt die bestehende Erwachsenenlogik.
- Keine automatische Partnerschaft durch Rekrutierung, Personalzuweisung, Wohnen, Zeitraffer oder Führungsrolle.
- Kein Druck oder Ausnutzen von Autoritäts-/Ausbildungsgefällen, keine sexuellen Darstellungen Minderjähriger.
- Die bestehenden NPC-NPC-Romanzen aus Life.kt bleiben auf volljährige NPCs begrenzt.

**Engine-Funktionen Romance.allowed(), Romance.maxStage(), Romance.nextStep() und die Alex-Dialogflags nur bei zwingendem Grund anfassen und Regressionstests für alle Fälle verpflichtend behalten.** Besonders keine pauschale „beide >=18“-Änderung.

## 2. P0 — Teamführung 2.0 ab Rang 8

Ein Spieler auf Rang 8 erhält ein richtiges Teammanagement:
- Teamzusammenstellung nach Rolle: Aufklärung, Technik, Medizin, Kommunikation, operative Unterstützung.
- Berücksichtige realen NPC-Status: krank, verletzt, im Einsatz, freigestellt, auf Urlaub, versetzt, unbekannt, minderjährig, rangbedingt ungeeignet.
- Delegierbare Arbeit: Training, Vorbereitung, Aufklärung, Mentoring, Dienstplanung, Nachbereitung. Zeit/Energie/Moral und NPC-Kompetenzen verändern sich nachvollziehbar.
- Teamentwicklung: Zusammenarbeit, Teamkohäsion, individuelle Ziele, Stress, Konflikte, Erfolgsbilanz, Urlaubs- und Erholungsplanung.
- Bei schlechten Entscheidungen sind Missionen schwieriger, aber nicht unmöglich.
- Bestehende Life-/Career-Mechanik verwenden. Keine rein dekorativen Prozentbalken.
- Bei Rekruten unter 18 nur Training und altersgerechte ungefährliche Tätigkeiten.

**Ab Rang 9: Delegation echter v0.7-Operationen**
- Binde Delegationen an OpsState.contracts an, statt nur das alte board zu verwenden.
- Status (vorbereitet, läuft, abgeschlossen, gescheitert, abgebrochen), Start- und Rückkehrtag, eingesetzte NPC-IDs, Bericht und einmalige Abschlussverbuchung.
- Echte Einschränkungen für Teamgröße, Rang, Missionsgefahr, Verfügbarkeit und Fähigkeiten.
- Keine doppelte Belohnung, keine Missionsduplikate nach Save/Load und kein zeitgleiches Arbeiten eines NPC an zwei Missionen.
- Delegierte Missionen verändern Fraktionen, Loyalität, Erinnerungen, Verletzungen und Missionseinträge.
- Die Rückkehr kann sich ereignisabhängig verschieben; kein sofortiger Missionsabschluss gegen wiederholtes Tippen.
- Aktive Missionen können mit nachvollziehbaren Konsequenzen abgebrochen werden.

## 3. P0 — Sektionsleitung und Direktorat ab Rang 10

Nutze unbedingt den **existierenden DirectorState** und die fünf bestehenden Abteilungen.

- Rang 10: eigene Sektion mit Personal, Leistungszielen, Projekten und begrenztem Budget.
- Rang 11: stellvertretende Direktionsfunktion, Krisenvertretung, Vorschläge, Haushaltsanträge und Personalentscheidungen mit Teilfreigaben.
- Rang 12: volle Direktoratsverwaltung mit Personal, Budget, Abteilungsprojekten und strategischer Politik.
- Fünf Abteilungen mit spürbaren Boni für konkretes Gameplay: Ausbildung, Technik, Sanität, Aufklärung und Sicherheit.
- Abteilungsleiter dürfen nicht gleichzeitig an mehreren Orten oder in mehreren Leitungsstellen eingesetzt werden; müssen volljährig und verfügbar sein, mit zu ihrer Kompetenz passenden Aufgaben.
- Transparente Wochen- und Monatsrechnung: Einnahmen, fixe Kosten, Projektkosten, HQ-Unterhalt, Personal, Fraktionsereignisse, Aufsicht. Zentrale persönliche Credits und Organisationsbudget strikt trennen.
- Die bestehende Director.weekly()-Berechnung überarbeiten und bei Zeitraffer genau einmal pro relevanter Woche anwenden. Keine unendlichen Geld-/Moral-Exploits.
- Vier vorhandene Leitlinien (balanced, transparent, secrecy, people) sinnvoll ausbauen statt ersetzen.
- Ressourcenknappheit und Aufsicht haben reale, aber lösbare Folgen; keine unentrinnbaren Dead-Ends.
- Mindestens sechs ausgearbeitete Führungsdilemmata: Etatkürzung, Sicherheitsleck, Personalstreit, fehlerhafte Mission, Aufsichtskontrolle, medizinische Krise.
- Monatsbericht mit nachprüfbaren Zustandsänderungen. Nicht nur generischer Text.

## 4. P0 — HQ-Ausbau und eigene Außenstellen

- Alle 13 HQ-Räume und Raum-IDs erhalten, sodass existierende NPC-Routinen, Dialoge und Storymissionen weiterhin funktionieren.
- Erweiterungen für Ausbildung, Analyse, Technik, Medizin, Sicherheit und Aufenthaltsbereiche mit Kosten, Ausbaustufen, Unterhalt und spürbarem Nutzen.
- **Mindestens drei spielbare Außenstellen** mit unterschiedlicher Funktion, etwa Stadtsafehouse, Analysezentrum und Sanitäts-/Ausbildungsstützpunkt.
- Außenstellen besitzen Freigaben, Geld- und Personalbedarf sowie 1–3 reale Upgradestufen; ggf. später auf fünf ausbauen.
- Keine doppelt vergebenen Mitarbeiter oder budgets. Schließen und Wiedereröffnen einer Außenstelle muss sauber funktionieren.
- Die Standorte wirken auf v0.7-Missionsverfügbarkeit, Erholung, Aufklärungsqualität oder Personalentwicklung.
- 2D-/Compose-Karten und Manageroberflächen statt neuer 3D-Engine. Keine Karten-API oder Onlinepflicht.

## 5. P1 — Personalwesen und eigene Rekruten

- Abteilungsbesetzung und Mentoren, Weiterbildungen, Kompetenzprofile, Beförderungen, Leistungsbeurteilungen, Konflikte und Versetzungen.
- Berücksichtige Ziele, Moral und Eigenständigkeit der NPCs. Nicht jeder NPC nimmt jeden angebotenen Posten an.
- Die vom Spieler individuell erstellte Gefährtin / der Gefährte bleibt eine unabhängige Person und kein Bonusgegenstand. Karriere, Training und private Entscheidungen bleiben erhalten.
- Niemand unter 18 darf eine leitende Stelle oder einen gefährlichen operativen Dienst erhalten.
- Ein Personalentscheid muss mindestens auf einen späteren Bericht, eine Mission oder ein NPC-Ereignis Einfluss haben.

## 6. P1 — Eigenes Privatleben und Wohnungen

Die bisherige Wohnung ist nur ein Boolean. Mache daraus eine tiefe, kompatible Lebenssimulation:
- Unterkünfte: Rekrutenquartier, erste kleine Wohnung, größere Wohnung, optional gehobene Wohnung; Kosten und Freigaben klar zeigen.
- Vorhandene v0.7-Saves mit player.apartment=true müssen die passende bestehende Wohnung ohne Verlust übernehmen.
- Möbel-/Raummodule mit tatsächlicher Wirkung auf Erholung, Schlaf, Stress, Freizeit oder Kosten, keine 50 Platzhalter.
- Stadtkalender mit Café, Hobbys, Freizeit, Treffen, privaten Gesprächen, Besuchen, Erholung und Tarnjobs.
- Privatleben konkurriert glaubwürdig mit Missionsdienst, Training, Führungsaufgaben, Geld und Energie.
- Mindestens zwölf handgeschriebene Alltagsereignisse mit Wahl und Konsequenzen; persönliche Erinnerungen, Freundschafts- und Konfliktfolgen nutzen.
- Alex, Michael, Nikita, Birkhoff und die selbst erstellte Gefährtin erhalten passende, freiwillige persönliche Aktivitäten, wenn Story, Alter, eigene Ziele und Verfügbarkeit es erlauben.
- Eine Wohnung schafft keine neue Romanze oder Altersausnahme. Zusammenziehen und Erwachsene-Funktionen nur gemäß unveränderten Romance-Regeln.
- Keine automatischen Einnahmen durch wiederholten Freizeitbutton oder Zeitsprung-Exploits.

## 7. P1 — Führung, Fraktionen, Story und Endgame verbinden

- V0.7-Fraktionen, Ermittlungsjournal und zehn Missionsketten müssen mit strategischen Entscheidungen verknüpft werden.
- Neue Führungsevents und Privatleben können Fallout über Tage/Wochen verursachen und Missionen oder NPCs verändern.
- Der Nutzer will langfristig auch als Direktor noch abwechslungsreiche Entscheidungen treffen können, nicht nur Budget +5000 anklicken.
- Bestehende Hauptstory und vier Enden bleiben spielbar. Keine Retcons der bekannten Charaktere oder kopierten Episodendialoge.
- Keine Anleitungen zu realen Waffen, Infiltration oder Gewalt; das Missionssystem ist eine fiktionale und abstrahierte Simulation.

## 8. P1 — Mobile Oberfläche

Baue mit echten aktiven Funktionen und gutem Dark-Spy-Design:
1. Führungscockpit: Personal, Teamrollen, aktive Delegationen, Bereitschaft.
2. Personalakte 2.0: Kompetenzen, Beförderungsbedarf, Versetzung, Ausbildung, Konflikte.
3. Direktorat: Abteilungen, Politik, Haushalt, Aufsicht, Projektstatus und Entscheidung.
4. Standorte und HQ: Räume, Ausbaustufen, Wartungs-/Projektkosten.
5. Stadt und Privatleben: Wohnung, Tages-/Wochenplan, Treffen, Freizeit.
6. Wochen-/Monatsbericht: Finanzen, Einsätze, Personal, Privatleben, Krisen.

360/393/430 dp testen, große Bedienelemente, brauchbare Navigation, keine Fake-Buttons, keine abgeschnittenen Zahlen. Nutze vorhandene SVG-Grafiken und das Cast-ZIP-System; keine unrechtmäßig verteilten Serienbilder generieren oder herunterladen. Start-/Loginbildschirm weiterhin BuildConfig.VERSION_NAME.

## 9. Savegames, Integrität, Performance

- Bewahre GameState, DirectorState, LivingState, OpsState, Missionen, Items und Relationstags. Neue @Serializable-Felder müssen Defaults besitzen; Schema nur bei Bedarf erhöhen.
- Alte Spielstände aus v0.4–v0.7 mit echten JSON-Fixtures migrieren und prüfen; speziell v0.7-Wohnung, Team, Partner, NPC-Historie, laufende Ops, aktiver Fall und 102 Gegenstände.
- Deterministisch: gleicher Seed und gleicher Plan → identisches Ergebnis.
- Keine doppelten Wochenzahlungen, Mietabbuchungen, Projektfortschritte oder Einsatzabschlüsse. Keine negativen Credits, Budgets, Bestände oder unendlichen Ereignislisten.
- Simuliere 1, 7, 30, 90, 365 und 3.650 Tage als Regression. Auch beim Neustart mitten in einem laufenden Projekt muss der Zustand korrekt bleiben.
- Keine zweite parallele Engine, keine Cloud, keine WebView, keine 3D-Umstellung.

## 10. Testpflicht und Abschluss

**Mindestens 25 neue Unit-Tests**, 6 neue Screenshot-Szenarien und alle vorhandenen Regressionen müssen grün sein. Prüfe die Freigabestufen von Rang 1/7/8/9/10/11/12, minderjährige Rekruten, Teamverfügbarkeit, doppelte Delegation, Haushaltszyklen, v0.7-Wohnung und Romanzen-Regressionen.

- Tests vor jedem Merge: ./gradlew testDebugUnitTest assembleDebug.
- Screenshot-Workflow der bestehenden CI ausführen. Fehler beheben, keine Tests auskommentieren.
- VersionCode auf 8 und versionName auf 0.8.0-beta **erst nach erfolgreicher Implementierung**, danach alle Tests erneut ausführen.
- DEBUG-APK als GitHub-Actions-Artifact `ProtokollNull-debug-apk` bereitstellen.
- PR mit verständlichem Changelog, geänderten Dateien, echten Testergebnissen, bekanntem Rest und GitHub-Action-Link.

## Definition of Done

- Rang 8/9: echte, persistente Teamleitung und Delegation in OpsState.
- Rang 10/11/12: abgestufte, funktionierende Abteilungs-, Politik-, Budget- und Personalverwaltung.
- Mindestens drei sinnvolle Außenstellen mit Personaleinsatz, Finanzierung und Konsequenzen.
- Privatleben mit Wohnung, Budget, Freizeit, sozialen Situationen und Zeitkonflikten wirklich spielbar.
- Bestehende 16er-Romanzenregel, optionale Alex-Route, Gefährtenkarriere und Erwachsenenfreigaben unverändert.
- 102 Items, 60 neue Icons, Cast-ZIP-Import und sämtliche v0.7-Missionen erhalten.
- Savegames v0.4 bis v0.7 ladefähig, CI erfolgreich und installierbare v0.8-APK vorhanden.
- Kein Dummy-Code, keine neue App von null, keine Fortschrittsbehauptung ohne Test.

**BEGINNE JETZT IM GITHUB-REPOSITORY MIT DER IMPLEMENTIERUNG.** Arbeite systematisch, schließe spielbare Teilstücke ab und teste vor dem nächsten Abschnitt. Kein reiner Projektplan.
