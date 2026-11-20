# PROTOKOLL NULL – Objektive Spielübersicht (Stand 1.0.0-rc1)

Diese Übersicht ist für eine externe Bewertung gedacht. Sie beschreibt, was im Spiel tatsächlich implementiert und getestet ist, und benennt Einschränkungen ohne Beschönigung.

## Genre und Rahmen

Natives Android-Spionage-RPG im Hochformat (Kotlin, Jetpack Compose), deutsch, vollständig offline, deterministische Simulation. Privater, nicht-kommerzieller Fan-Prototyp im Universum der Serie *Nikita* mit eigenständig geschriebener Story.

## Umfang

| Bereich | Umfang |
|---|---|
| Story | Prolog (13–16 Jahre), 4 Kapitel, **36 Storymissionen**, **6 Enden**, 48 Dialoge, 24 Quests; Kapitel 4 spielt nach dem Epilog |
| Dynamische Aufträge | 10 Auftragsarten aus Bausteinen (Ort, Einstieg, Auftraggeber, Ziel, Auflage, Folgen), mehrtägige Aufträge, Fristen, 8 Wendungen, 10 Einsatzketten, 4 Ermittlungsfälle |
| Taktik | Rundenbasiertes 2D-Brett, **22 Karten** in 6 Umgebungen, Sichtlinien, Deckung nach Schussrichtung, 8 Zielarten, 4 Gegnerrollen inkl. Reaktionsfeuer, Team-KI, Patt-Erkennung; abstrakte Darstellung („kampfunfähig“) |
| Progression | Level 1–100, 12 Dienstgrade mit mehrphasigen Prüfungen, 9 Skillbäume / 36 Fähigkeiten, Leistungsbeurteilungen, Mentoring, Trainingsplaner, Zeitplan mit Zeitraffer |
| Division | 16 Figuren mit eigenem Tagesablauf, Laufbahn, Verletzungen, Versetzungen, Freundschaften und Rivalitäten; 37 HQ-Ereignisse; strukturierte Erinnerungen |
| Beziehungen | Passung, Meilensteine, Geschenke nach Interessen, Konflikte, altersgerechte Romanzenregeln (16/17 und 18/16 nur bis Stufe 2, nicht sexuell; Zusammenziehen/Heirat nur unter Volljährigen) |
| Gefährtin/Gefährte | Selbst erstellte zweite Hauptfigur mit Werten, Lebensziel, Berufswunsch und eigener Laufbahn; trifft eigene Entscheidungen |
| Führung | Ab Rang 8 Teamrollen und Aufgaben, ab 9 Delegation, ab 10–12 Direktorat (5 Abteilungen, 13 HQ-Räume, 6 Module, 4 Außenstellen, Projekte, Dilemmata, Wochen-/Monatsabrechnung) und **Direktionsagenda** |
| Privatleben | 4 Wohnstufen, Möbel mit Wirkung, Stadtkalender, Treffen, Tarnjobs, Alltagsereignisse |
| Inventar | 127 Gegenstände mit Bildern, 6 Slots, Ausrüstungsprofile, Vergleich, Verschleiß, Rang- und Altersfreigaben |
| Technik | 3 Speicherslots + Autosave (atomar, im Hintergrund), Import/Export, Migration ab v0.1 (Schema 6), prozedurale Musik, einstellbare Schrift, hoher Kontrast, reduzierte Bewegung |

## Was sich in v1.0 spürbar verbessert

- Oberfläche: einheitliches Designsystem, lesbarere Typografie, einzeilige Reiter, Lagezentrum im HQ, Personalakte im Profil, deutlich klareres Taktikbrett mit Vorschauen.
- Spielgefühl: Wendungen in Aufträgen, ein reaktiverer Gegner, Team-KI, Story-Entscheidungen, die in späteren Szenen und im Epilog wiederkehren.
- Langzeitmotivation: Kapitel 4 nach dem Finale, Direktionsagenda alle 26 Wochen, zwei zusätzliche Enden.
- Stabilität: keine blockierende Simulation auf dem UI-Thread, geschützte Zurück-Geste, Bestätigungen vor Datenverlust, Exploit-Schutz bei Story-Fehlschlägen.

## Testabdeckung (automatisiert)

Über 170 reine Engine-Tests (lokal und in CI) plus Robolectric-UI-Tests und 55 gerenderte Screens. Darunter End-to-End-Durchläufe der Kampagne bis zu allen sechs Enden und durch Kapitel 4, eine Zehn-Jahres-Simulation und die Migration von v0.8-Spielständen.

## Einschränkungen

- Keine Tests auf echten Geräten (kein Emulator in der Entwicklungsumgebung).
- Storyumfang unter dem Masterplan-Ziel (36 statt 45–60 Missionen).
- Barrierefreiheit verbessert, aber nicht mit TalkBack verifiziert.
- Grafiken: Porträts sind CC-lizenzierte Darstellerfotos oder generierte Bilder, Szenenbilder generiert, Gegenstands-Icons von game-icons.net (CC BY 3.0) bzw. eigene SVGs. Es gibt keine Animationen von Figuren.
- Rechtliche Lage: Fan-Prototyp; für eine öffentliche oder kommerzielle Veröffentlichung sind Rechtefreigaben oder eigenständige Figuren nötig.
