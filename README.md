# PROTOKOLL NULL – Aufstieg eines Agenten

**Native Android-Portrait-Agenten-RPG – technische Grundlage / frühe Demo.**  
Projektstand: Foundation (nicht fertige Beta).

## Vorhanden
- Kotlin/Jetpack Compose-Appgerüst für Android 8.0+ (minSdk 26), dunkles Hochformat-UI
- HUD, Prolog-Dialogauswahl, Missionsliste, Inventar-SVGs, Charakterkontakte, Profil, erster Partnereditor
- 14 NPCs, 42 Gegenstände, 27 Skills, 12 Ränge, 15 Orte, 6 Fraktionen, 16 Missionsvorlagen, 14 Story-Knoten, 13 Events
- Spielerlevel, XP, Trainingsaktion, Zeitfortschritt, Beziehungspunkte, lokale JSON-Speicherung
- **32 originale CC-lizenzierte Gegenstandsicons** unter `app/src/main/assets/items/`
- **11 real vorhandene CC-lizenzierte Schauspielerfotos** unter `app/src/main/assets/cast/`
- Quellen/Credits: `assets/ITEM_IMAGE_CREDITS.json`, `assets/CAST_IMAGE_CREDITS.json`

## Sehr wichtig: Figuren wie in der Serie
Nikita-Figuren Michael, Nikita, Alex, Birkhoff, Amanda, Percy, Thom, Jaden, Owen, Sonya, Ryan sollen optisch **in ihrem tatsächlichen Serien-Look** dargestellt werden. `assets/SERIES_LOOK_REFERENCES.json` enthält 11 Figuren, 8 direkte Referenzen auf echte Szenenbilder sowie glaubwürdige Frisur-/Kleidungs-/Lookbeschreibungen. `assets/SERIES_STYLE_GUIDE.md` erklärt die Integration. Die rechteninhabergeschützten Szenenbilder sind **nur Referenzen**, nicht im Repository hinterlegte Game-Assets. Bis zur Rechteklärung werden die vorhandenen lizenzierten Fotos genutzt. Für öffentliche Releases Serienrechte klären.

## Claude: Vollständiger Folgeauftrag
Siehe **[CLAUDE_IMPLEMENTATION_PROMPT.md](./CLAUDE_IMPLEMENTATION_PROMPT.md)**. Diesen ganzen Prompt an Claude Code geben, das Zugriff auf dieses Repo hat. Architektur, Spiellogik, UX, 2D-Missionen, Inventar, Beziehungen und Director-Management sind dort verbindlich spezifiziert.

## Build
Dieses Repository enthält ein Android-Gradle-Projekt mit Kotlin/Compose. Es wurde in dieser Umgebung **noch kein erfolgreicher Gradle-Build nachgewiesen**. Gradle-Wrapper-JAR und CI-Tests müssen im ersten Claude-Schritt erstellt und anschließend `assembleDebug` ausgeführt werden.

## Bildrechte
Inventar-Icons: Game-icons.net CC BY 3.0, insbesondere Lorc/Delapouite, Zuschreibung in App-Credits und Lizenzdatei. Schauspielerfotos: individuelle CC BY bzw. CC BY-SA, Autor/Link/Version in `assets/CAST_IMAGE_CREDITS.json`. CC-Bedingungen, insbesondere ggf. ShareAlike, bei Modifikationen einhalten. Offizielle The-CW-Serienstills, Logos und die geschützten Figuren dürfen nicht ohne Prüfung für eine öffentliche kommerzielle Veröffentlichung verwendet werden.

## Einschränkungen der Foundation
Die Missionsliste besitzt bislang nur einen **Demo-Abschluss**, keine echten taktischen Missionen. Der Partnereditor besitzt nur Grundfelder und wird mit 18 freigeschaltet. Die komplexe spielbare Beta einschließlich Datenbankmigration, Skilltree, abwechslungsreicher Missionen und vollwertiger Romance-/Direktor-Simulation ist Aufgabenbereich von Claude.
