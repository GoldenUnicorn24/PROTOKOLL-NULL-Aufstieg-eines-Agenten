# Private Serienporträts: offline und nicht im öffentlichen GitHub

## Direkt in der Android-App
1. In PROTOKOLL NULL Kontakte öffnen.
2. Michael, Alex, Nikita, Birkhoff, Amanda, Percy oder eine andere Serienfigur auswählen.
3. In der Personalakte auf "Eigenes Bild ... auswählen" tippen.
4. Über den Android-Fotopicker ein Bild wählen, zu dessen Nutzung du berechtigt bist.
5. Das Porträt wird verkleinert und im privaten App-Speicher abgelegt. Über "Originalfoto wiederherstellen" wird die lokale Zuordnung entfernt.

Die Fotoauswahl funktioniert offline ohne Upload und gilt für alle NpcPortrait-Ansichten (Kontakte, Dialoge usw.). Reihenfolge: lokales Gerätebild > privates Build-Asset > freigegebenes/lizenziertes Serienstill > CC-Darstellerfoto > Original-Avatar.

Der lokale private Cast-Bildspeicher ist NICHT Teil der normalen JSON-Savegame-Exporte. Beim Gerätewechsel oder Löschen der App-Daten müssen diese Bilder erneut ausgewählt werden. Android-OS-Backups können abhängig von den Systemeinstellungen App-Daten sichern; eine automatische Cloud-Synchronisation durch PROTOKOLL NULL ist nicht vorgesehen.

## Optional: Private Bilder in einer selbst gebauten APK
Für Android-Studio-Nutzer, die auf ihrem eigenen Rechner eine APK erstellen, können optionale Bilder lokal liegen:

    app/src/private/assets/cast/show_michael.jpg
    app/src/private/assets/cast/show_nikita.jpg
    app/src/private/assets/cast/show_alex.jpg
    app/src/private/assets/cast/show_birkhoff.jpg
    app/src/private/assets/cast/show_amanda.jpg
    app/src/private/assets/cast/show_percy.jpg
    app/src/private/assets/cast/show_thom.jpg
    app/src/private/assets/cast/show_jaden.jpg
    app/src/private/assets/cast/show_owen.jpg
    app/src/private/assets/cast/show_sonya.jpg
    app/src/private/assets/cast/show_ryan.jpg

Gradle bindet src/private/assets als zusätzliche lokale Assetquelle ein; app/src/private/ wird in .gitignore ignoriert. Die öffentlichen GitHub-Actions-Builds enthalten diesen privaten lokalen Ordner nicht. Eine privat erzeugte APK enthält dagegen die eingebetteten Bilder. Diese APK darf daher nicht ohne passende Rechte verteilt werden.

## Bildrechte und Qualität
- Diese Funktion lädt KEINE geschützten CW-, IMDb-, TVLine- oder TMDb-Bilder automatisch herunter.
- Bilder aus privaten Quellen dürfen nicht ohne Nutzungsrecht öffentlich verteilt werden; .gitignore ersetzt keine Lizenz.
- CC-Lizenznachweise der öffentlich enthaltenen Darstellerfotos und Game-icons bleiben unverändert.
- Der Android-Fotopicker verwendet die schon vorhandene Import-Pipeline mit Größenlimit, JPEG-Normalisierung, EXIF-Rotation und lokalem Dateispeicher.
- Keine Bilddateien oder privaten Bilder im öffentlichen Repository anlegen. Claude soll nur Coden und vorhandene Bilder anzeigen, NICHT KI-Bilder generieren oder urheberrechtlich problematische Fotos automatisch beschaffen.
- Bei nicht verfügbarem Override automatisch auf vorhandene Fotos zurückfallen.

## Technischer Einstieg
- Daten: app/src/main/java/de/protokollnull/agent/data/PrivateCastPortraits.kt
- UI-Picker: app/src/main/java/de/protokollnull/agent/ui/CastPortraitUpload.kt
- Alle NPC-Porträts: app/src/main/java/de/protokollnull/agent/ui/Portraits.kt
- Anzeigen in Akten: app/src/main/java/de/protokollnull/agent/ui/screens/ContactsScreens.kt
- Tests: app/src/test/java/de/protokollnull/agent/PrivateCastPortraitsTest.kt
