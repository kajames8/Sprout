package com.jellyai;

/**
 * The kinds of plant you can grow. Each seed bag plants one of these.
 *
 * rows: the top 13 rows of the plant (the pot is shared). Only the left half
 * of each row is written; the right half is mirrored.
 *   o outline  g leaf  l leaf highlight  d leaf shade  s stem
 *   u petal/cap outline  f petal/cap  e petal/cap shade
 *   y flower center  z center shade  n seed-head brown
 *   b trunk  B trunk shade  w mushroom stem  W stem shade
 *
 * icon: the 8x8 picture printed on the seed bag label.
 */
enum PlantType {
    SPROUT("Sprout", "#5DBB63", 1.0, "leaf", "#8ED081",
            new String[]{
                    "........",
                    "gg....gg",
                    "gGg..gGg",
                    ".gGggGg.",
                    "..gGGg..",
                    "...GG...",
                    "...GG...",
                    ".rrrrrr."
            },
            "..........oo",
            ".........ogg",
            ".........olg",
            ".........olg",
            "..oooo...ogg",
            ".olggggo.ogd",
            ".olgggdo.ogd",
            ".ogggggdo.og",
            "..ogggddo.os",
            "....oddddoos",
            ".....oooooos",
            "..........os",
            "..........os"),

    CACTUS("Cactus", "#58B27A", 0.0, "leaf", "#6FC2B0",
            new String[]{
                    "...gg...",
                    "...gg...",
                    ".g.gg.g.",
                    ".g.gg.g.",
                    ".gggggg.",
                    "...gg...",
                    "...gg...",
                    ".rrrrrr."
            },
            "..........oo",
            ".........ogg",
            ".........olg",
            "..oo.....olg",
            ".oggo....ogg",
            ".olgo....olg",
            ".olgo....ogd",
            ".olgoooooogd",
            ".olggggggggd",
            ".oggggggggdd",
            ".ooooooooogd",
            ".........ogd",
            ".........ogd"),

    FLOWER("Flower", "#F27BA7", 1.2, "petal", "#F5A3C0",
            new String[]{
                    "..pppp..",
                    ".pppppp.",
                    ".ppYYpp.",
                    ".pppppp.",
                    "..pppp..",
                    "...GG...",
                    "..gGGg..",
                    ".rrrrrr."
            },
            "........uuuu",
            ".......uffff",
            "......ufffff",
            "......ufffyy",
            "......ufffyy",
            "......ufffzz",
            "......ufffff",
            ".......uffee",
            "........uuuu",
            "...oooo...os",
            "..olgggggoos",
            "...oddddo.os",
            "....oooo..os"),

    BONSAI("Bonsai", "#3F9A55", 0.5, "leaf", "#C9A06B",
            new String[]{
                    "..gggg..",
                    ".gggggg.",
                    "gggggggg",
                    ".gggggg.",
                    "...bb...",
                    "...bb...",
                    "..bbbb..",
                    ".rrrrrr."
            },
            ".......ooooo",
            "......olgggg",
            "..oooolggggg",
            ".olgggldgggg",
            ".ogglggggddd",
            "..oggggggddd",
            "...ooooooooo",
            "..........Bb",
            "..........Bb",
            "..........Bb",
            ".........Bbb",
            "........Bbbb",
            ".......BBBBB"),

    SUNFLOWER("Sunflower", "#F7C531", 0.8, "petal", "#F5C542",
            new String[]{
                    "..PPPP..",
                    ".PPPPPP.",
                    "PPPnnPPP",
                    "PPPnnPPP",
                    ".PPPPPP.",
                    "...GG...",
                    "..gGGg..",
                    ".rrrrrr."
            },
            "....uuuuuuuu",
            "..uuffffffff",
            ".uffffffffff",
            ".uffffnnnnnn",
            ".uffffnBnBnB",
            ".uffffnnnnnn",
            ".uffffffffff",
            "..uuffffffff",
            "....uuuuuuuu",
            "....oooo..os",
            "..olgggoo.os",
            "...oddddo.os",
            "....oooo..os"),

    TULIP("Tulip", "#E5485E", 0.8, "petal", "#E5707E",
            new String[]{
                    "..R..R..",
                    "..RRRR..",
                    ".RRRRRR.",
                    ".RRRRRR.",
                    "..RRRR..",
                    "...GG...",
                    "..gGGg..",
                    ".rrrrrr."
            },
            "......u....u",
            "......uu..uf",
            ".....uffuuff",
            ".....uffffff",
            ".....uffffff",
            "......ufffee",
            "......ufeeee",
            ".......ueeee",
            "........uuuu",
            "..oo......os",
            ".olgoo....os",
            ".ogggdoo..os",
            "..ooooooo.os"),

    FERN("Fern", "#4DAA5E", 1.4, "leaf", "#2E9E7E",
            new String[]{
                    "...gg...",
                    "g..gg..g",
                    "gg.gg.gg",
                    ".gggggg.",
                    "..gGGg..",
                    "...GG...",
                    "...GG...",
                    ".rrrrrr."
            },
            "..........oo",
            "..oo.....ogg",
            ".oggo....olg",
            ".ogggo...ogg",
            "olgggdo..ogg",
            "oggggdoo.ogd",
            ".ogggdddoogd",
            "..oodddooogd",
            "....ooooo.os",
            "..oo....o.os",
            ".ogdo..ogoos",
            "..oodoodoo.s",
            "....oooo..os"),

    SUCCULENT("Succulent", "#5DB5A4", 0.2, "leaf", "#8EC9E8",
            new String[]{
                    "...ee...",
                    "..eeee..",
                    ".eeEEee.",
                    "eeEeeEee",
                    "eeEeeEee",
                    ".eeEEee.",
                    "...EE...",
                    ".rrrrrr."
            },
            "............",
            "..........oo",
            ".........ogg",
            "...oo....olg",
            "..ogg...oggg",
            "..ogggo.ogdd",
            "ooogggdooddd",
            "ogggggdoogdd",
            ".ogggddoggdd",
            "..ooddddogdd",
            "....oooodgdd",
            "......ooooos",
            "..........os"),

    MUSHROOM("Mushroom", "#E0453A", 0.0, "cap", "#B08968",
            new String[]{
                    "..mmmm..",
                    ".mmWmmm.",
                    "mmmmmWmm",
                    "mWmmmmmm",
                    "mmmmmmmm",
                    "..TTTT..",
                    "..TTTT..",
                    ".rrrrrr."
            },
            "....uuuuuuuu",
            "..uuffffffff",
            ".ufffwwfffff",
            "ufffwwwfffww",
            "uffffwfffwww",
            "ufffffffffwf",
            "uffwwffffeee",
            "ueeeeeeeeeee",
            ".uuuuuuuuuuu",
            "......Wwwwww",
            "......Wwwwww",
            ".....WwwWwww",
            "....WWWWWWWW"),

    LAVENDER("Lavender", "#9B7FD1", 1.2, "petal", "#B9A2E8",
            new String[]{
                    ".L.LL.L.",
                    ".L.LL.L.",
                    ".L.LL.L.",
                    ".L.LL.L.",
                    ".G.GG.G.",
                    "..GGGG..",
                    "...GG...",
                    ".rrrrrr."
            },
            "...u......uu",
            "..uf.....uff",
            "..ufu...uffe",
            "..uff...ufff",
            "..ufe...uffe",
            "..uff...ufff",
            "...ufe..uffe",
            "...uf...ufee",
            "...uuu.uuuuu",
            ".....s....os",
            ".....s....os",
            "..olgsoo.gos",
            ".ogggdsoogds");

    final String label;
    final String defaultColor;
    final double sway;        // how much the plant sways (0 = stays rigid)
    final String colorPart;   // what the color picker changes: leaf, petal or cap
    final String bagColor;    // color of the seed bag
    final String[] icon;
    final String[] rows;

    PlantType(String label, String defaultColor, double sway, String colorPart,
              String bagColor, String[] icon, String... rows) {

        this.label = label;
        this.defaultColor = defaultColor;
        this.sway = sway;
        this.colorPart = colorPart;
        this.bagColor = bagColor;
        this.icon = icon;
        this.rows = rows;
    }

    // Flowers and mushrooms are colored by their petals or cap; leaves stay green.
    boolean usesPetals() {
        return !colorPart.equals("leaf");
    }
}