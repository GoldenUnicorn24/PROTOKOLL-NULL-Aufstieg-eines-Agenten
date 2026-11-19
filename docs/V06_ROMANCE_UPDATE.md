# Ergänzung v0.6 – freiwillige altersgerechte Spielerromanzen

Diese Ergänzung setzt auf dem bereits fertigen v0.6-Living-Division-Code auf, ohne die unabhängige NPC-Lebenssimulation zu verändern.

## Regeln
- Eine Spielerromanze ist ab 16 möglich, sofern die Gegenfigur ebenfalls mindestens 16 und höchstens zwei Jahre älter oder jünger ist. Beispiele: Spieler 16 / Alex 17; Spieler 18 / neu zugeteilte Gefährtin 16.
- Zwei volljährige Figuren dürfen miteinander eine Romanze entwickeln, auch wenn ihr Altersabstand größer als zwei Jahre ist.
- Nur Interesse (Stufe 1) und freiwillig eingegangene feste Beziehung (Stufe 2), solange mindestens eine beteiligte Figur minderjährig ist.
- Kein Zusammenziehen, keine Heirat oder Familienplanung solange eine Person minderjährig ist. Die bereits vorhandene Familienvoraussetzung (beide mindestens 21) bleibt bestehen.
- ausschließlich nicht-sexuelle, altersgerechte Szenen wie Gespräche, Dates, emotionale Nähe und Händchenhalten. Keine erotischen oder expliziten Inhalte mit Minderjährigen.
- Die Gefährtin wird nicht automatisch zur Partnerin. Das bestehende System aus Freundschaft, Zuneigung, Vertrauen, Passung, Konflikten und bewusster Bestätigung bleibt erhalten.
- Alex bleibt eine optionale Route. Die ausdrücklich ausgewählte Dialogantwort \`interest\` setzt \`alex_romance_unlocked\`; wer Freundschaft wählt, bleibt befreundet.
- Unabhängige Paare zwischen NPCs in der Lebenssimulation bleiben wie in v0.6 auf Volljährige beschränkt.
- Die freiwillige Spielregel "maximal zwei Jahre bei Minderjährigen" ist keine Aussage über eine starre gesetzliche Altersgrenze.

## Dateien
- engine/Social.kt: Romance.allowed / nextStep, abgesicherte Stufen.
- assets/game/dialogues.json: drei Alex-Szenen ab 16 statt erst ab 18.
- ui/screens/ContactsScreens.kt: Hinweise zum Altersbereich und sicheren Erwachsenenaktionen.
- RulesTest + LivingDivisionTest: Regressionen für 16/17, 18/16, 19/16, zwei Erwachsene und Restore früherer Stufen.
- Keine zusätzlichen Fotos/Assets und keine Save-Schema-Änderung.

## Verifizierung
Vor dem Merge: GitHub Actions testDebugUnitTest und assembleDebug müssen erfolgreich sein.
