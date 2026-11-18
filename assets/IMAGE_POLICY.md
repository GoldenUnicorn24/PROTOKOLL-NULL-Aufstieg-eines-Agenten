# Bilder & Rechte

## Inventargrafiken – bereits im Repository
32 SVG-Assets liegen unter app/src/main/assets/items/. Quellen und Urheber: assets/ITEM_IMAGE_CREDITS.json. CC BY 3.0 – game-icons.net. In den App-Credits Autoren (Lorc/Delapouite), Quelle und Lizenz-Link nennen.

## Personenfotos – Wikimedia Commons
assets/CAST_IMAGES.json enthält 11 Bildreferenzen zu Schauspielern aus Nikita. Nach Download liegen die Fotos unter app/src/main/assets/cast/; die individuelle Zuschreibung erstellt tools/fetch_cast_photos.py in assets/CAST_IMAGE_CREDITS.json. Der Import prüft CC-Lizenzen bei Commons. Ausführung: python3 tools/fetch_cast_photos.py oder GitHub-Actions-Workflow.

Die Fotos sind reale Schauspielerfotos (überwiegend von Events), keine Original-Serienstills. Foto-Lizenzen sind KEINE Rechte an den TV-Figuren, Marken oder Logos. Vor einer kommerziellen Veröffentlichung gesondert klären oder eigene fiktionale Charaktere nutzen.

Fiktive NPCs erhalten den neutralen Fallback cast/placeholder.svg. Keine nicht lizenzierten Promotional-Stills automatisch herunterladen.