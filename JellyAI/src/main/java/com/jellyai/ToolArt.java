package com.jellyai;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/** Pixel art for the toolbox: one 12-cell-wide picture per tool. */
final class ToolArt {

    private ToolArt() {
    }

    static final String[] CAN = {
            "nn..........",
            "nn..........",
            ".nn.........",
            "..nnnnnnnnnn",
            "...nnhcccn.n",
            "....ncccdn.n",
            "....nccddn.n",
            "....nnnnnnnn"
    };

    static final String[] SUN = {
            ".....RR.....",
            "..R..RR..R..",
            "...R....R...",
            "....OOOO....",
            ".R.OYYYYO.R.",
            "RR.OYYYYO.RR",
            "RR.OYYYYO.RR",
            ".R.OYYYYO.R.",
            "....OOOO....",
            "...R....R...",
            "..R..RR..R..",
            ".....RR....."
    };

    static final String[] FOOD = {
            "....xxxx....",
            "....xxxx....",
            "....nvvn....",
            "...nvvvvn...",
            "..nvvvvvvn..",
            "..nvwwwwvn..",
            "..nvwVVwvn..",
            "..nvwwwwvn..",
            "..nvvvvvvn..",
            "..nvvvvVvn..",
            "..nVVVVVVn..",
            "...nnnnnn..."
    };

    static final String[] CLIPPERS_CLOSED = {
            "......ss....",
            ".....sSs....",
            ".....sSs....",
            ".....sSs....",
            "......Ss....",
            "......xx....",
            ".....x..x...",
            "....xr..rx..",
            "...xrr..rrx.",
            "...xrr..rrx.",
            "...xqr..rqx.",
            "....xx..xx.."
    };

    static final String[] CLIPPERS_OPEN = {
            "...ss....ss.",
            "...sS....Ss.",
            "....sS..Ss..",
            ".....sSSs...",
            "......Ss....",
            "......xx....",
            ".....x..x...",
            "....xr..rx..",
            "...xrr..rrx.",
            "...xrr..rrx.",
            "...xqr..rqx.",
            "....xx..xx.."
    };

    static final String[] HEART = {
            ".qqq....qqq.",
            "qrrrq..qrrrq",
            "qrprrqqrrrrq",
            "qrrrrrrrrrrq",
            "qrrrrrrrrrrq",
            ".qrrrrrrrrq.",
            "..qrrrrrrq..",
            "...qrrrrq...",
            "....qrrq....",
            ".....qq....."
    };

    static Color color(char c) {

        switch (c) {
            case 'n': return Color.web("#2D4F73");   // can outline
            case 'c': return Color.web("#5FA3D6");   // can body
            case 'h': return Color.web("#B5DAF5");   // can highlight
            case 'd': return Color.web("#3F7FB3");   // can shade
            case 'Y': return Color.web("#FFD43B");   // sun
            case 'O': return Color.web("#F2A516");   // sun rim
            case 'R': return Color.web("#FFC94D");   // sun rays
            case 'x': return Color.web("#3A3F4B");   // dark outline
            case 'v': return Color.web("#5DBB63");   // bottle
            case 'V': return Color.web("#3E8E46");   // bottle shade
            case 'w': return Color.web("#FFF8E6");   // label
            case 'S': return Color.web("#E7EDF5");   // steel
            case 's': return Color.web("#8E9BB0");   // steel shade
            case 'r': return Color.web("#E5484D");   // red
            case 'q': return Color.web("#A42B30");   // dark red
            case 'p': return Color.web("#FFB3B8");   // pink highlight
            default: return null;
        }
    }

    /** Draws a sprite with its top-left corner at (x, y). */
    static void draw(GraphicsContext g, String[] rows, double x, double y, double px) {

        for (int r = 0; r < rows.length; r++) {

            for (int c = 0; c < rows[r].length(); c++) {

                Color color = color(rows[r].charAt(c));

                if (color != null) {
                    g.setFill(color);
                    g.fillRect(x + c * px, y + r * px, px, px);
                }
            }
        }
    }

    /** Draws a sprite around (cx, cy), rotated by angle degrees. */
    static void drawCentered(GraphicsContext g, String[] rows, double cx, double cy,
                             double px, double angle, double alpha) {

        double halfW = rows[0].length() / 2.0;
        double halfH = rows.length / 2.0;

        g.save();

        g.setGlobalAlpha(Math.max(0.0, Math.min(1.0, alpha)));
        g.translate(cx, cy);
        g.rotate(angle);

        for (int r = 0; r < rows.length; r++) {

            for (int c = 0; c < rows[r].length(); c++) {

                Color color = color(rows[r].charAt(c));

                if (color == null) {
                    continue;
                }

                g.setFill(color);

                // Slightly oversized so rotated pixels don't leave seams.
                g.fillRect((c - halfW) * px - 0.25, (r - halfH) * px - 0.25,
                        px + 0.5, px + 0.5);
            }
        }

        g.restore();
    }
}