# Serienbilder: Ein ZIP für alle Figuren

Öffne in der Android-App Startmenü → Einstellungen → Serienbilder – privat & offline → Komplettes Bildpaket (.zip) importieren.
Danach erscheinen die privat ausgewählten Fotos automatisch in sämtlichen Charakterakten und Dialogen.
Die ZIP-Vorlage kann auch nur einige Figuren enthalten. Fehlende Bilder bleiben unverändert.

## Unterstützte Figuren und Dateinamen

| Figur | Dateiname | Schauspieler/in |
|---|---|---|
| Michael | show_michael.jpg | Shane West |
| Nikita | show_nikita.jpg | Maggie Q |
| Alex Udinov | show_alex.jpg | Lyndsy Fonseca |
| Birkhoff | show_birkhoff.jpg | Aaron Stanford |
| Amanda | show_amanda.jpg | Melinda Clarke |
| Percy | show_percy.jpg | Xander Berkeley |
| Thom | show_thom.jpg | Ashton Holmes |
| Jaden | show_jaden.jpg | Tiffany Hines |
| Owen | show_owen.jpg | Devon Sawa |
| Sonya | show_sonya.jpg | Lyndie Greenwood |
| Ryan | show_ryan.jpg | Noah Bean |

Unterstützt: JPG, JPEG, PNG und WebP. Dateinamen beginnen immer mit show_. Die Dateien dürfen im ZIP-Stamm oder einem Ordner cast/ liegen.
Die App erkennt IDs automatisch, normalisiert die Fotos zu lokalen JPEGs und speichert die Zuordnungen erst, wenn alle erkannt und gültig sind.
Maximal 12 MiB pro Bild, 96 MiB insgesamt und 60 ZIP-Einträge. Der Import erfolgt offline und benutzt keine Webservices.

## Bildverwaltung

- Unter Einstellungen → Einzelne Bilder verwalten lassen sich Fotos einzeln ersetzen oder zurücksetzen.
- Unter Alle Serienbilder zurücksetzen werden die privaten Zuordnungen entfernt, ohne Spielstände zu löschen.
- Die alte importieren-Schaltfläche in den Personalakten wird entfernt.
- Bilder bleiben im privaten App-Speicher und werden nicht automatisch in JSON-Spielstände, GitHub oder eine öffentliche APK hochgeladen.

## Recherche – Bildquellen statt urheberrechtlich problematischer Verteilung

Diese Quellen zeigen teils echte CW-/Warner-Bros.-Serienbilder; verlinken heißt nicht, dass eine Weiterverbreitung erlaubt wäre.

- Alex: https://www.filmstarts.de/serien/8183/bilder/detail/?cmediafile=19698051
- Nikita: https://www.ksitetv.com/la-femme-nikita/nikita-images-from-the-season-4-premiere-wanted/26123/
- Michael: https://www.tvline.com/gallery/nikita-hot-shots-spoiler-returns-with-a-secret/
- Birkhoff und Sonya: https://www.allocine.fr/series/ficheserie-8183/photos/detail/?cmediafile=20466712
- Amanda: https://www.filmstarts.de/serien/8183/bilder/detail/?cmediafile=19726481
- Percy: https://www.tvline.com/gallery/nikita-hot-shots-percy-finally-breaks-free/
- Thom und Jaden: https://www.comingsoon.it/serietv/nikita/88/foto/
- Owen: https://www.tvline.com/news/nikita-season-2-preview-birkhoff-percy-mikita-249197/
- Ryan: https://www.themoviedb.org/tv/32868-nikita/images/backdrops
- Ensemble: https://www.fernsehserien.de/nikita-2010/cast-crew

Die Bildquellen sind Such- und Auswahlreferenzen, keine bestätigten Einzelrechte. Es werden keine geschützten Serienbilder im öffentlichen Repository oder in der Vorlage verteilt.

## Dateien im Code

- data/CastPortraitBundle.kt: ZIP-Parsing, Grenzen, Sicherheitsprüfung
- data/PrivateCastPortraits.kt: Batch- und Einzelzuordnung
- data/ImportedPortraits.kt: sichere Normalisierung
- ui/CastPortraitSettings.kt: zentrale Settings-Oberfläche
- ui/screens/MetaScreens.kt: Menüeinbindung
- ui/screens/ContactsScreens.kt: Einzelimport entfernt
- CastPortraitBundleTest.kt: ZIP-Import, Atomizität, Dubletten und Pfadtraversal