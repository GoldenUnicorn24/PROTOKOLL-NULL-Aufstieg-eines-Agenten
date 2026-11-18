# Nikita – verbindliche Referenzen für den Serien-Look

**Nutzerwunsch:** Die Figuren müssen aussehen wie in der Serie *Nikita* (2010–2013), keine beliebigen aktuellen Headshots. Die Bildlinks und Rollen-Look-Beschreibungen liegen in `assets/SERIES_LOOK_REFERENCES.json`.

Das öffentliche Repository darf keine ungeklärten ©-The-CW-Stills enthalten. Die Quellen dienen als **visuelle Referenz für die Art Direction**, nicht als verteilbare Bilder. In der App stehen vorläufig 11 lizensierte Castfotos aus Wikimedia Commons zur Verfügung. Jede `seriesImage` muss erst nach einer passenden Nutzungserlaubnis lokal importiert werden. Nicht vorgeben, sie sei bereits importiert.

## Implementationshinweis für Claude
- `agents.json` bleibt bei `portraitAsset: cast/<id>.jpg`, solange keine lizenzierte Show-Aufnahme vorliegt.
- **Umgesetzt:** Die App zeigt `cast/show_<id>.jpg` *nur*, wenn die Datei existiert **und** `app/src/main/assets/licenses/series_image_licenses.json` einen Eintrag mit `verified: true`, `license` und `proof` enthält (Vorlage/Spiegel: `assets/SERIES_IMAGE_LICENSES.json`). Ein Unit-Test lässt den Build scheitern, wenn ein Still ohne Nachweis eingecheckt wird. Derzeit ist kein Still lizenziert.
- Keine Hotlinks aus IMDb, Acsta, TMDb oder TVLine zur Laufzeit: das widerspricht dem Offline-Konzept und Bilder-URLs können ausfallen.
- Bei selbst erstellten fiktiven NPCs hochwertige eigenständige Portraits einsetzen; damit sind diese von TV-Look-Referenzen sauber getrennt.
- Kein `photoCredit` erfinden; konkrete Serien-Fotocredits vom Rechteinhaber klären.

## Verwendung
So schnell wie möglich serienspezifischen Look für Michael, Nikita, Alex, Birkhoff, Amanda, Percy, Thom, Jaden, Owen, Sonya und Ryan planen. 2010/2011-Ästhetik insbesondere bei Rekruten. Charakterdesign ist unabhängig vom Zeit- und Karriereprogress des Spielercharakters.