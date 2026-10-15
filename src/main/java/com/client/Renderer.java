package com.client;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import com.shared.Const;
import com.shared.Wall;

/** Dibuixa l'estat del joc al Canvas: HUD a dalt i l'escenari a sota. */
final class Renderer {

    private Renderer() { }

    static final double HUD_HEIGHT = 60;

    static void draw(GraphicsContext gc, GameModel m, double mouseX, double mouseY) {
        long now = System.nanoTime();

        drawHud(gc, m);

        // L'escenari es dibuixa desplaçat per sota del HUD
        gc.save();
        gc.translate(0, HUD_HEIGHT);

        gc.setFill(Color.WHITESMOKE);
        gc.fillRect(0, 0, Const.WORLD_W, Const.WORLD_H);

        gc.setFill(Color.DIMGRAY);
        for (Wall w : m.walls) gc.fillRect(w.x, w.y, w.w, w.h);

        for (GameModel.BulletView b : m.bullets) {
            double[] p = m.bulletPosition(b, now);
            drawBullet(gc, p[0], p[1], b.angle);
        }

        for (int i = 0; i < 2; i++) {
            GameModel.TankView t = m.tanks[i];
            if (t.snapped && t.lives > 0) drawTank(gc, t, Color.web(m.colors[i]));
        }

        for (Explosion e : m.explosions) e.draw(gc);

        // Punt de mira del jugador local (desapareix quan té el màxim de projectils actius)
        GameModel.TankView me = m.tanks[m.youIndex];
        if (me.snapped && me.lives > 0 && me.bullets < Const.MAX_BULLETS) {
            Color c = Color.web(m.colors[m.youIndex]);
            drawGuide(gc, m, me, mouseX, mouseY, c);
            drawCrosshair(gc, mouseX, mouseY, c);
        }

        gc.restore();
    }

    private static void drawHud(GraphicsContext gc, GameModel m) {
        gc.setFill(Color.web("#222222"));
        gc.fillRect(0, 0, Const.WORLD_W, HUD_HEIGHT);

        Font big = Font.font("Arial", FontWeight.BOLD, 18);
        Font small = Font.font("Arial", 14);

        for (int i = 0; i < 2; i++) {
            GameModel.TankView t = m.tanks[i];
            boolean left = i == 0;
            double x = left ? 16 : Const.WORLD_W - 16;
            String name = m.names[i] + (i == m.youIndex ? " (tu)" : "");

            gc.setTextAlign(left ? TextAlignment.LEFT : TextAlignment.RIGHT);
            gc.setFill(Color.web(m.colors[i]));
            gc.setFont(big);
            gc.fillText(name + "  " + "♥".repeat(Math.max(0, t.lives)), x, 26);
            gc.setFill(Color.LIGHTGRAY);
            gc.setFont(small);
            gc.fillText("Projectils: " + t.bullets, x, 48);
        }

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.WHITE);
        gc.setFont(small);
        gc.fillText("Escenari", Const.WORLD_W / 2.0, 26);
        gc.setFont(big);
        gc.fillText(m.scenarioName, Const.WORLD_W / 2.0, 48);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    // Cos del tanc (segons l'angle) i torreta (segons el seu propi angle)
    private static void drawTank(GraphicsContext gc, GameModel.TankView t, Color color) {
        gc.save();
        gc.translate(t.x, t.y);
        gc.rotate(t.angle);
        gc.setFill(Color.DARKSLATEGRAY);
        gc.fillRect(-18, -17, 36, 6);   // erugues
        gc.fillRect(-18, 11, 36, 6);
        gc.setFill(color);
        gc.fillRect(-16, -11, 32, 22);  // cos
        gc.restore();

        gc.save();
        gc.translate(t.x, t.y);
        gc.rotate(t.turret);
        gc.setFill(color.darker());
        gc.fillRect(0, -3, 32, 6);      // canó
        gc.fillOval(-9, -9, 18, 18);    // torreta
        gc.restore();
    }

    private static void drawBullet(GraphicsContext gc, double x, double y, double angle) {
        gc.save();
        gc.translate(x, y);
        gc.rotate(angle);
        gc.setFill(Color.RED);
        gc.fillRect(-4, -2, 7, 4);
        gc.fillOval(1, -2, 4, 4);
        gc.restore();
    }

    // Punts des del tanc fins al ratolí; s'aturen a la primera paret
    private static void drawGuide(GraphicsContext gc, GameModel m, GameModel.TankView t,
                                  double mx, double my, Color color) {
        double dx = mx - t.x, dy = my - t.y;
        double dist = Math.hypot(dx, dy);
        if (dist < 1) return;
        double ux = dx / dist, uy = dy / dist;

        gc.setFill(color);
        for (double d = 44; d < dist; d += 18) {
            double px = t.x + ux * d, py = t.y + uy * d;
            for (Wall w : m.walls) {
                if (w.touchesCircle(px, py, 3)) return;
            }
            gc.fillOval(px - 3, py - 3, 6, 6);
        }
    }

    private static void drawCrosshair(GraphicsContext gc, double x, double y, Color color) {
        gc.setStroke(color);
        gc.setLineWidth(2);
        gc.strokeLine(x - 12, y, x - 4, y);
        gc.strokeLine(x + 4, y, x + 12, y);
        gc.strokeLine(x, y - 12, x, y - 4);
        gc.strokeLine(x, y + 4, x, y + 12);
    }
}
