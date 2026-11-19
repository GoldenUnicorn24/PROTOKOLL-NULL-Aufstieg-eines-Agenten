# PROTOKOLL NULL v0.8.0-beta „Director's Legacy“

Führung, Direktorat, eigene Standorte und ein echtes Privatleben – aufgebaut auf den bestehenden Systemen (Lebenssimulation `Life`, Aufträge `OpsState`, `DirectorState`, Fraktionen, Akten und Ketten). Es gibt keine zweite Engine. Alles läuft offline und deterministisch.

## Rangstufen

| Rang | Freigabe |
|---|---|
| bis 7 | Training für das eigene Team, Berichte |
| 8 Teamleiter | Führungscockpit: Rollen, Aufgaben, Kohäsion, Aussprache, Beurteilungen, Teametat, erste HQ-Module, Stadtsafehouse |
| 9 Einsatzkommandant | Delegation echter v0.7-Aufträge, Technikwerkstatt und Sicherheitszentrale, Analysezentrum |
| 10 Sektionsleitung | eigene Abteilung (bis Stufe 3), Abteilungsleitung, 2 Projekte, Haushaltsanträge, Beförderungsempfehlung, Versetzung, Modulstufen 2–3, Stützpunkt |
| 11 Stellv. Direktion | alle Abteilungen (bis Stufe 4), 3 Projekte, Leitlinien „ausgewogen/transparent/Fürsorge“, Hafenposten, Außenstellen Stufe 3 |
| 12 Direktion | volle Verwaltung (bis Stufe 5), 4 Projekte, alle vier Leitlinien inklusive „Strikte Geheimhaltung“ |

Das Direktorat (`DirectorState`) wird ab Rang 10 angelegt, auch für ältere Spielstände. Der bisherige Teametat geht dabei in das Organisationsbudget über.

## Teamführung (Rang 8)

- **Rollen** Aufklärung, Technik, Sanität, Kommunikation und operative Unterstützung. Die Passung ist die jeweilige `Life`-Kompetenz (0–10).
- **Status** aus der Lebenssimulation: verletzt/krank, im Einsatz, in Weiterbildung, freigestellt, im Urlaub, abwesend, versetzt, eingesetzt (Posten), beschäftigt (Aufgabe), minderjährig, rangbedingt ungeeignet, unbekannt.
- **Aufgaben**: Training, Einsatzvorbereitung, Vorab-Aufklärung, Mentoring, Dienstplanung, Nachbereitung, Erholung und Weiterbildung (800 Cr). Jede Aufgabe kostet Energie und einen Zeitslot, setzt den `Life`-Status und wird am Folgetag des Endes genau einmal ausgewertet. Mentor und Mentee sind beide gebunden.
- **Kohäsion** steigt durch Dienstplanung, Nachbereitung, Erfolge und Aussprachen. Sie sinkt durch Streit (Bindung ≤ −20), Abbrüche und Fehlschläge und driftet wöchentlich zur Mitte.
- **Einsatzbonus** aus besetzten Rollen und Kohäsion: −8 bis +12 auf Proben eigener Einsätze. Schlechte Führung erschwert Einsätze, macht sie aber nicht unmöglich.
- **Unter 18** sind nur Training, Mentoring als Mentee, Dienstplanung, Nachbereitung, Erholung und Weiterbildung möglich. Es gibt keine Einsätze, keine Posten und keine Leitung.

## Delegation (Rang 9)

- Delegierbar sind offene oder vertagte Aufträge aus `OpsState.contracts`. Ketten- und Aktenaufträge bleiben Chefsache.
- Ablauf: **vorbereitet → läuft → abgeschlossen / gescheitert / abgebrochen**. Gespeichert werden Start- und Rückkehrtag, Team-IDs, Leitung, Chance und Bericht.
- Einschränkungen: Teamgröße, Volljährigkeit, Rang ≥ 2 (≥ 3 ab Gefahr 4), Rang der Leitung, Verfügbarkeit und keine Doppelbelegung.
- Die Rückkehr wird ausschließlich im Tageswechsel ausgewertet. Mit 20 % Wahrscheinlichkeit verzögert sie sich einmalig um 1–2 Tage. Die Abrechnung über `Ops.settleContract` erfolgt genau einmal (`settled`). Sie wirkt auf Fraktionen, Auftraggeber, Akten und Ketten, Verletzungen, Karrierepunkte, Erinnerungen, Loyalität und einen Einsatzbericht. Belohnungen fließen ins Organisationsbudget.
- **Abbruch**: Das Team kehrt zurück. Kohäsion −3, Vertrauen −2, der Auftraggeber ist verärgert.

## Direktorat (Rang 10–12)

- Es gibt fünf Abteilungen: Ausbildung, Technik, Sanität, Aufklärung und Sicherheit. Die **wirksame Stärke** ist die Ausbaustufe, plus 1, wenn die Leitung verfügbar ist und eine passende Kompetenz ≥ 6 hat. Sie wirkt auf Training, Behandlung, Start-Intel, Delegationschancen, den Alarm in Einsätzen und die Meridian-Offensive.
- **Abteilungsleitungen** müssen volljährig und verfügbar sein, Rang ≥ 3 haben und dürfen keinen anderen Posten besetzen. Sie können ablehnen. Danach gilt eine Sperrfrist von 14 Tagen.
- **Wochenabrechnung** einmal pro Kalenderwoche, auch im Zeitraffer (`lastFinanceWeek`):
  - Etat je Stufe, Abteilungsbetrieb, Leitungsgehälter, Fürsorgeprogramm, Meridian-Offensive, Prüfauflagen, Personal der Außenstellen, Modul- und Standortunterhalt sowie Projekte.
  - Reicht das Budget nicht, werden zuerst die Fixkosten bezahlt. Die Projekte pausieren und die Moral sinkt. Daraus entsteht keine Sackgasse.
  - Persönliche Credits bleiben unberührt.
- **Monatsbericht** alle vier Wochen mit:
  - Finanzen
  - eigenen Aufträgen und Delegationen
  - Personal (Kohäsion, Verletzungen, Beurteilungen)
  - Moral, Aufsicht und Leitlinie
  - Black-Meridian-Einfluss
  - Projekten und Außenstellen
  - Privatleben
  - getroffenen Entscheidungen
- **Leitlinien** (die vier bestehenden, ausgebaut):

  | Leitlinie | Wirkung |
  |---|---|
  | ausgewogen | Moral +1 |
  | transparent | Aufsicht +3 |
  | Geheimhaltung | Aufsicht −3, Alarm −1 |
  | Fürsorge | Moral +3, Stress −3, 600 Cr Kosten |

- **10 Projekte**, z. B. Simulationsparcours, Mentorenprogramm, Abgesichertes Netz, Trauma-Einheit, Spionageabwehr und Perimeter. Sie haben eine Laufzeit und Wochenkosten und wirken nach Abschluss dauerhaft (`proj_<id>`).
- **Haushaltsantrag** für Rang 10–11: 2.000–20.000 Cr, Entscheidung zum Wochenwechsel, dann 28 Tage Sperrfrist.
- **8 Führungsdilemmata**:
  - Etatkürzung
  - Sicherheitsleck
  - Personalstreit
  - fehlgeschlagene Mission
  - Aufsichtskontrolle
  - medizinische Krise
  - junger Freiwilliger (nur Ablehnungsoptionen)
  - Angebot von Black Meridian

  Optionen können Proben, Fraktions- und Haltungsänderungen sowie **Spätfolgen** nach 5–14 Tagen haben. Eine starke Sicherheitsabteilung kann einen geglätteten Bericht schützen.

## Standorte

- Alle 13 HQ-Räume und Raum-IDs bleiben erhalten.
- **HQ-Module** (Stufe 0–3), jeweils mit Kosten, Unterhalt und Wirkung:
  - Trainingshalle
  - Analyseraum
  - Technikwerkstatt
  - Krankenstation
  - Sicherheitszentrale
  - Aufenthaltsbereich
- **Außenstellen** (Stufe 1–3 mit Personalbedarf):

  | Außenstelle | ab Rang | Wirkung |
  |---|---|---|
  | Stadtsafehouse | 8 | Probenbonus in der Innenstadt, Stress des Teams, sicherer Treffpunkt |
  | Analysezentrum | 9 | Intel, Delegationschance, Zusatzhinweise in Akten, leichtere Aufträge |
  | Sanitäts- und Ausbildungsstützpunkt | 10 | Karrierepunkte, Heilung, weniger Verletzungsrisiko bei Delegationen |
  | Beobachtungsposten Hafen | 11 | Black Meridian −Einfluss, Hafenbonus |

- Ohne Personal bleibt eine Außenstelle wirkungslos. Beim Schließen wird das Personal freigegeben. Die Wiedereröffnung kostet ¼ der Baukosten.

## Personalwesen

- **Leistungsbeurteilung** A–E alle 30 Tage. Sie wirkt auf Karrierepunkte, Stimmung und Respekt. D und E erzeugen einen Konflikt. Beurteilungen erscheinen im Monatsbericht.
- **Beförderungsempfehlung** ab Rang 10, ab 70 % der Karrierepunkte: Die Prüfung wird sofort zugelassen.
- **Versetzung** ab Rang 10 für 60 Tage (nur bestimmte Figuren). Folgen: Loyalität −10, Lebensereignis und Erinnerung.
- Die **eigene Begleitung** entscheidet selbst und wird nur bei Vertrauen ≥ 60 und eigener Zustimmung versetzt. Wer bei einem Posten nicht zum eigenen Karrierewunsch passt, lehnt eher ab.

## Privatleben

- **Unterkünfte**:

  | Unterkunft | Kaution | Miete/Woche | Voraussetzung | Möbelplätze |
  |---|---|---|---|---|
  | Rekrutenquartier | – | – | – | 0 |
  | Kleine Wohnung | 450 Cr | 150 Cr | ab 18 | 3 |
  | Größere Wohnung | 1.200 Cr | 320 Cr | Rang 5 | 5 |
  | Gehobene Wohnung | 3.000 Cr | 600 Cr | Rang 8 | 7 |

  Die Miete wird einmal pro Woche abgebucht. Nach zwei Mahnungen folgt die Räumung. `player.apartment` wird mitgeführt, damit die unveränderten Romanzenregeln weiter funktionieren.
- **Möbel** (8) mit echter Wirkung:
  - Bett: Schlaf
  - Musikanlage und Pflanzen: Stress
  - Küche: Kochabend
  - Schreibtisch: Lernabend
  - Trainingsecke: Heimtraining
  - Schloss: Sicherheitsereignisse
  - Esstisch: Gastgeberbonus
- **Stadtkalender**: 13 Aktivitäten mit Tageszeitfenstern, Kosten, Energie und Stress, höchstens drei Freizeitaktionen pro Tag, jede Aktivität einmal täglich. Hobbys werden ab Stufe 3 zu Interessen.
- **Treffen** (freundschaftlich) mit Alex, Michael, Birkhoff, Nikita (erst ab 18, mit Kontakt und Risiko) und der eigenen Begleitung sowie Kaffee mit weiteren Bekannten. Es gelten eine Abklingzeit, Verfügbarkeit, Konflikte und Freundschaftsschwellen.
- **Tarnjobs** ab 17 (Kurier, Café, Nachhilfe; Übersetzung ab 18). Der volle Lohn wird nur mit einer Schicht pro Woche gezahlt, ohne Schicht die Hälfte plus Verwarnung. Nach drei Verwarnungen folgt die Kündigung.
- **15 Alltagsereignisse** mit Auswahl, Erinnerungen, Freundschafts- und Geldfolgen. Sie verfallen nach drei Tagen.

## Romanzenregeln

Die Romanzenlogik (`Romance.allowed`, `maxStage`, `nextStep`, die Alex-Flags) wurde **nicht verändert**:

- Die freiwillige 16/17- und 18/16-Romanze bleibt erhalten.
- Bei Minderjährigen gilt höchstens Stufe 2, nicht sexuell.
- Alex bleibt optional.

Alle neuen Privatlebensinhalte sind freundschaftlich. Eine Wohnung schafft keine neue Romanze und keine Altersausnahme. `teenRomanceRulesRemainUnchanged` und die bestehenden `RulesTest`-Regressionen sichern das ab.

## Spielstände

Die Spielstände haben Schema 5. Alle neuen Felder (`lead`, `life`) besitzen Standardwerte. Bei der Migration werden v0.7-Wohnung (`apartment=true`) und Tarnjob übernommen. Tests prüfen JSON-Fixtures von Schema 2–4 und einen vollständigen v0.7-Stand mit Team, Aufträgen, Lebenssimulation und 102 Gegenständen.
