package de.protokollnull.agent.engine

/**
 * v0.7/v1.0: Bibliothek handgebauter 2D-Taktikkarten (9×11, Hochformat) – 22 Karten.
 *
 * Legende: `#` Wand (blockiert Bewegung und Sicht) · `.` Boden · `c` Deckung
 * (passierbar, schützt) · `~` Wasser/Gleis (unpassierbar, Sicht frei) · `w` Glas/Zaun
 * (unpassierbar, Sicht frei) · `d` Tür (passierbar, blockiert Sicht) · `X` Evakuierung
 * · `I` Infopunkt · `R` Aufklärungspunkt · `Z` Treffpunkt/Schutzzone · `P` Team
 * · `E` Gegner · `G` Wachposten · `O` Überwachungsposten · `V` Schutzperson.
 * Nicht genutzte Zielmarker werden beim Aufbau zu Boden.
 */
object TacticalMaps {
    data class MapLayout(val id: String, val name: String, val environment: String, val rows: List<String>)

    val environments = mapOf("urban" to "Stadt", "industrial" to "Industrie", "interior" to "Gebäude", "clinic" to "Klinik", "transit" to "Verkehr", "nature" to "Natur")

    val all: List<MapLayout> = listOf(
        MapLayout("market", "Marktplatz", "urban", listOf(
            "#X..I...#",
            "#.c...c.#",
            "#..G..R.#",
            "#cc.#.cc#",
            "#...#..E#",
            "#.R...Z.#",
            "#c.w.wc.#",
            "#.E...I.#",
            "#..c.c..#",
            "#..V....#",
            "#PP..PP.#"
        )),
        MapLayout("backyard", "Hinterhof", "urban", listOf(
            "##X.##E.#",
            "#..c#..I#",
            "#R..d..c#",
            "###.#.###",
            "#.G...c.#",
            "#c..Z..R#",
            "#.##.##.#",
            "#I.c..cE#",
            "#.......#",
            "#V.c.c..#",
            "#.PP.PP.#"
        )),
        MapLayout("garage", "Parkhaus", "urban", listOf(
            "#X.......",
            "#.cc.cc.O",
            "#.......#",
            "##R.##.I#",
            "#..c..c.#",
            "#E..Z...#",
            "#.##.##.#",
            "#I.c..cR#",
            "#..G....#",
            "#c..V..c#",
            "#PP...PP#"
        )),
        MapLayout("terminal", "Containerterminal", "industrial", listOf(
            "~~X.....~",
            "~.cc.cc.~",
            "#.......#",
            "#cc#.#ccE",
            "#I.#.#.R#",
            "#...Z..c#",
            "#.##.##.#",
            "#R.c.G.I#",
            "#c.....c#",
            "#..V....#",
            "#PP...PP#"
        )),
        MapLayout("warehouse", "Lagerhalle", "industrial", listOf(
            "#########",
            "#X..c..I#",
            "#.cc.c.E#",
            "#...#...#",
            "#R#.d.#R#",
            "#...Z...#",
            "#c#####c#",
            "#..G.O..#",
            "#I.c.c..#",
            "#...V...#",
            "#PP...PP#"
        )),
        MapLayout("canal", "Kanalufer", "industrial", listOf(
            "#X..I..E#",
            "#.c...c.#",
            "#..R....#",
            "~~~.~~.~~",
            "#c..Z..c#",
            "#.G...R.#",
            "~~.~~~.~~",
            "#I.c.c..#",
            "#..E....#",
            "#c..V..c#",
            "#PP...PP#"
        )),
        MapLayout("embassy", "Botschaftsflügel", "interior", listOf(
            "#########",
            "#X.I#R.E#",
            "#..cd.c.#",
            "##d##.###",
            "#...Z...#",
            "#c.G..c.#",
            "###d#d###",
            "#I.c#.cR#",
            "#...#...#",
            "#.V.d...#",
            "#PP...PP#"
        )),
        MapLayout("office", "Bürotrakt", "interior", listOf(
            "#X.c.c.I#",
            "#.......#",
            "#R##d##.#",
            "#.#.E.#.#",
            "#.d.Z.d.#",
            "#.#c.c#.#",
            "#.##d##R#",
            "#.G....I#",
            "#c.c.c..#",
            "#..V....#",
            "#PP...PP#"
        )),
        MapLayout("archive", "Archivkeller", "interior", listOf(
            "#########",
            "#X.#I..E#",
            "#..#.c..#",
            "#.c#.#c.#",
            "#R...#..#",
            "#.##Z##.#",
            "#..#...R#",
            "#I.d.G..#",
            "#c.#.c..#",
            "#..#V...#",
            "#PP...PP#"
        )),
        MapLayout("clinic", "Klinikflur", "clinic", listOf(
            "#X.#R#.I#",
            "#..d.d..#",
            "#c.....c#",
            "##d##.###",
            "#..E.Z..#",
            "#.c....c#",
            "###.#d###",
            "#I...#.R#",
            "#.c..G..#",
            "#...V..c#",
            "#PP...PP#"
        )),
        MapLayout("airport", "Flughafenterminal", "transit", listOf(
            "#X..w..I#",
            "#.c.w.c.#",
            "#...w..E#",
            "#R.......",
            "#cc.Z.cc#",
            "#.......#",
            "#w.ww.w.#",
            "#I.c.G.R#",
            "#..c.c..#",
            "#...V...#",
            "#PP...PP#"
        )),
        MapLayout("platform", "Bahnsteig", "transit", listOf(
            "#X..c..I#",
            "#.......#",
            "~~~~.~~~~",
            "#R.c.cE.#",
            "#...Z...#",
            "#.c.G.c.#",
            "~~~.~~~~~",
            "#I..c..R#",
            "#.c...c.#",
            "#..V....#",
            "#PP...PP#"
        )),
        MapLayout("park", "Stadtpark", "nature", listOf(
            ".X..c..I.",
            ".c.....c.",
            "..~~.~~R.",
            "..~...~..",
            "c.~.Z.~.E",
            "..~...~..",
            "..~~.~~..",
            "I...G..cR",
            ".c.c...c.",
            "...V.....",
            "PP.....PP"
        )),
        MapLayout("cabin", "Waldhütte", "nature", listOf(
            ".X.c..I..",
            "..cc.c..E",
            "....#d#..",
            "R...#Z#.c",
            "..c.#.#..",
            ".c......R",
            "...cc....",
            "I.....G..",
            ".c..c..c.",
            "....V....",
            "PP.....PP"
        )),
        // --- v1.0: acht zusätzliche Karten (Taktik 3.0)
        MapLayout("rooftop", "Dachterrasse", "urban", listOf(
            "#X..c..I#",
            "#.c...c.#",
            "#..wEw..#",
            "#R.....R#",
            "#cc.Z.cc#",
            "#...O...#",
            "###d#d###",
            "#I.c.c..#",
            "#..G....#",
            "#c..V..c#",
            "#PP...PP#"
        )),
        MapLayout("subway", "U-Bahn-Tunnel", "transit", listOf(
            "#X.#I#..#",
            "#..d.d.E#",
            "~~~.~~~.~",
            "#R.c..c.#",
            "#...Z...#",
            "~.~~~~~.~",
            "#c.G..cR#",
            "#I.....c#",
            "#.c.c...#",
            "#..V....#",
            "#PP...PP#"
        )),
        MapLayout("lab", "Forschungslabor", "clinic", listOf(
            "#########",
            "#X.w.wI.#",
            "#..w.w.E#",
            "#c.....c#",
            "##d#Z#d##",
            "#R.c.c.R#",
            "#...O...#",
            "###d.d###",
            "#I.c.G..#",
            "#...V..c#",
            "#PP...PP#"
        )),
        MapLayout("villa", "Villa am See", "nature", listOf(
            "~~X..I.~~",
            "~.c...c.~",
            "..#d#d#.E",
            "R.#...#..",
            "..#.Z.#.R",
            ".c##d##c.",
            "....G....",
            "I.c...c..",
            ".c..c..c.",
            "...V.....",
            "PP.....PP"
        )),
        MapLayout("serverfarm", "Serverfarm", "interior", listOf(
            "#########",
            "#X#I#.#E#",
            "#.#.#.#.#",
            "#.......#",
            "#R#c#c#R#",
            "#...Z...#",
            "#c#.#.#c#",
            "#I..G...#",
            "#.#c#.#.#",
            "#..V....#",
            "#PP...PP#"
        )),
        MapLayout("drydock", "Trockendock", "industrial", listOf(
            "#X..I..E#",
            "#.cc.cc.#",
            "#~~...~~#",
            "#R~.Z.~R#",
            "#.~...~.#",
            "#.~~.~~.#",
            "#c..O..c#",
            "#I.c.c..#",
            "#...G...#",
            "#.V.....#",
            "#PP...PP#"
        )),
        MapLayout("library", "Staatsbibliothek", "interior", listOf(
            "#X.c#cI.#",
            "#.......#",
            "#c#c#c#E#",
            "#R......#",
            "#.#cZc#.#",
            "#.......#",
            "#c#c#c#R#",
            "#I...G..#",
            "#..c.c..#",
            "#...V...#",
            "#PP...PP#"
        )),
        MapLayout("bridge", "Hängebrücke", "transit", listOf(
            "~~.X..I~~",
            "~~.c..E~~",
            "~~.....~~",
            "~~R.c.R~~",
            "~~..Z..~~",
            "~~c.O.c~~",
            "~~.....~~",
            "~~I.c..~~",
            "~~..G..~~",
            "~~.V...~~",
            "~~PP.PP~~"
        ))
    )

    val byId = all.associateBy { it.id }

    fun forRegion(regionId: String): List<MapLayout> = when (regionId) {
        "harbor" -> listOf("terminal", "warehouse", "canal", "drydock")
        "airport" -> listOf("airport", "platform", "garage", "subway")
        "hospital" -> listOf("clinic", "office", "garage", "lab")
        "embassy" -> listOf("embassy", "office", "archive", "library")
        "safehouse" -> listOf("backyard", "cabin", "archive", "villa")
        "city" -> listOf("market", "park", "backyard", "garage", "platform", "rooftop", "bridge", "serverfarm")
        else -> all.map { it.id }
    }.mapNotNull { byId[it] }
}
