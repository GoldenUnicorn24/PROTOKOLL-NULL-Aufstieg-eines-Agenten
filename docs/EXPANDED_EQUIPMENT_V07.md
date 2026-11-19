# Neue Ausrüstung – Arsenal und Inventar (v0.7-Ergänzung)

Katalog: **42 → 102 Gegenstände**; 24 neue Waffen und 36 weitere Items.
**60 eigenständige, eigens gezeichnete SVG-Icons**, vollständig lokal im Android-Build. Keine Drittanbieterbilder, keine externen CDNs.

## Anbindung an das vorhandene Spiel
- Neue Waffen belegen den Slot `weapon`, besitzen abstrakte `damage`/`precision`-Werte und Rangfreigaben; sie verändern bereits existierende Taktikwerte.
- Schusswaffen und nicht-tödliche Einsatzwaffen dürfen erst ab 18 ausgerüstet und neu in der Ausrüstungskammer angefordert werden. Der bestehende Trainingsmarkierer bleibt eine Ausnahme.
- Alle neuen Gegenstände erscheinen gemäß Kategorie in der bestehenden Ausrüstungskammer und in den Inventarfiltern. Keine Änderung am Savegame-Schema.
- Medizinische Verbrauchsgegenstände können vorhandene Gesundheits-, Energie- und Stresswerte beeinflussen; technische, Schutz- und Dokumentengegenstände nutzen die vorhandenen Slots, Gewichtslimits und Attributboni.
- Neue SVGs werden über das vorhandene `ItemIcon` mit Coil SVG Decoder geladen. Fehlende Assets sind durch `ContentValidationTest` abgesichert.
- Die Waffen sind fiktionale Spielobjekte; deren statistische Werte sind bewusst keine realweltlichen Leistungsdaten oder Bauanleitungen.

## Waffen
- Glock 17: Rang 4, Wirkung 2, Präzision +5, 900 g
- Glock 19: Rang 4, Wirkung 2, Präzision +6, 750 g
- Walther P99: Rang 5, Wirkung 2, Präzision +7, 650 g
- SIG Sauer P226: Rang 5, Wirkung 3, Präzision +5, 960 g
- Beretta M9: Rang 4, Wirkung 2, Präzision +4, 950 g
- HK USP Tactical: Rang 6, Wirkung 3, Präzision +6, 920 g
- FN Five-seveN: Rang 6, Wirkung 3, Präzision +5, 740 g
- HK MP5: Rang 5, Wirkung 3, Präzision +5, 2550 g
- HK MP5SD: Rang 6, Wirkung 3, Präzision +6, 2950 g
- HK MP7: Rang 7, Wirkung 3, Präzision +7, 2100 g
- HK UMP45: Rang 6, Wirkung 4, Präzision +3, 2500 g
- KRISS Vector: Rang 7, Wirkung 4, Präzision +4, 2800 g
- M4A1: Rang 5, Wirkung 4, Präzision +4, 3050 g
- HK416: Rang 7, Wirkung 5, Präzision +5, 3500 g
- HK G36C: Rang 6, Wirkung 4, Präzision +4, 3000 g
- FN SCAR-L: Rang 8, Wirkung 5, Präzision +4, 3350 g
- AK-105: Rang 6, Wirkung 4, Präzision +2, 3300 g
- SR-25: Rang 8, Wirkung 5, Präzision +8, 4200 g
- Mk 14 EBR: Rang 9, Wirkung 6, Präzision +5, 4900 g
- Remington 700: Rang 8, Wirkung 5, Präzision +9, 4200 g
- Benelli M4: Rang 7, Wirkung 5, Präzision +2, 3800 g
- Remington 870: Rang 6, Wirkung 4, Präzision +3, 3200 g
- Elektroimpulsgerät: Rang 4, Wirkung 1, Präzision +5, 360 g
- Einsatz-Schockstock: Rang 3, Wirkung 1, Präzision +3, 520 g

## Weitere Items
- Steriler Feldverband (medical) · Rang 1 · Bild kit_field_dressing.svg
- Trauma-Notfallset (medical) · Rang 4 · Bild kit_trauma_kit.svg
- Wunddesinfektion (medical) · Rang 1 · Bild kit_antiseptic.svg
- Kühlkompresse (medical) · Rang 1 · Bild kit_cool_pack.svg
- Elektrolytgetränk (medical) · Rang 1 · Bild kit_electrolytes.svg
- Energieriegel (medical) · Rang 1 · Bild kit_energy_bar.svg
- Regenerationsbeutel (medical) · Rang 3 · Bild kit_recovery_pouch.svg
- Nano-Aufklärungsdrohne (gadgets) · Rang 5 · Bild kit_mini_drone.svg
- Drohnen-Akkumodul (gadgets) · Rang 3 · Bild kit_drone_battery.svg
- Thermischer Umgebungsscanner (gadgets) · Rang 6 · Bild kit_thermal_sensor.svg
- Verschlüsseltes Einsatztablet (gadgets) · Rang 5 · Bild kit_encrypted_tablet.svg
- Kompakter Funkrepeater (gadgets) · Rang 3 · Bild kit_relay_unit.svg
- Einsatzkamera (gadgets) · Rang 3 · Bild kit_bodycam.svg
- Ortungsmarkierung (gadgets) · Rang 3 · Bild kit_tracker_beacon.svg
- Frequenzanalysator (gadgets) · Rang 6 · Bild kit_frequency_meter.svg
- Zugangstest-Modul (gadgets) · Rang 5 · Bild kit_security_probe.svg
- Simulierter Funkstörer (gadgets) · Rang 7 · Bild kit_jammer_unit.svg
- Minikamera (gadgets) · Rang 4 · Bild kit_micro_camera.svg
- Nachtsicht-Visier (gadgets) · Rang 6 · Bild kit_night_visor.svg
- Umgebungs-Audiosensor (gadgets) · Rang 4 · Bild kit_audio_sensor.svg
- Keramikschutzweste (armor) · Rang 7 · Bild kit_ceramic_vest.svg
- Verdeckte Schutzweste (armor) · Rang 5 · Bild kit_concealed_vest.svg
- Schutzhelm (armor) · Rang 6 · Bild kit_ballistic_helmet.svg
- Schnittschutzhandschuhe (armor) · Rang 2 · Bild kit_protective_gloves.svg
- Knieschoner (armor) · Rang 2 · Bild kit_knee_protection.svg
- Mobiler Spurenleser (tools) · Rang 4 · Bild kit_field_scanner.svg
- Präzisionswerkzeugtasche (tools) · Rang 2 · Bild kit_compact_toolkit.svg
- Sicherheits-Trennwerkzeug (tools) · Rang 4 · Bild kit_rescue_cutter.svg
- Sicherungshaken (tools) · Rang 4 · Bild kit_climbing_hook.svg
- Spurensicherungsset (tools) · Rang 3 · Bild kit_evidence_kit.svg
- Fallakten-Mappe (documents) · Rang 2 · Bild kit_casefile_pack.svg
- Chiffre-Datenträger (documents) · Rang 5 · Bild kit_cipher_disk.svg
- Personendossier (documents) · Rang 4 · Bild kit_suspect_dossier.svg
- Tatort-Fotodossier (documents) · Rang 3 · Bild kit_forensic_photos.svg
- Modularer Einsatzkoffer (equipment) · Rang 4 · Bild kit_tactical_case.svg
- Leichte Umhängetasche (equipment) · Rang 2 · Bild kit_sling_bag.svg

## Prüfung
- 102 eindeutige Item-IDs und mindestens 60 neue originale SVGs.
- Keine Fremdmaterialien, keine Bildlizenzverletzungen.
- Das Spiel bleibt nativ, offline und kompatibel zu v0.4 bis v0.7.
- CI: `./gradlew testDebugUnitTest assembleDebug`.