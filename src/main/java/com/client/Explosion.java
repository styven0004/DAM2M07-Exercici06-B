package com.client;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

// Animació d'impacte: un cercle que creix i es va esvaint.
class Explosion {

    private final double x, y, radius, duration;
    private double remaining;

    Explosion(double x, double y, double radius, double duration) {
        this.x = x;
        this.y = y;
        this.radius = radius;
        this.duration = duration;
        this.remaining = duration;
    }

    void update(double dt) {
        remaining -= dt;
    }

    boolean isFinished() {
        return remaining <= 0;
    }

    void draw(GraphicsContext gc) {
        if (isFinished()) return;

        double progress = 1 - remaining / duration;
        double r = radius * (0.3 + 0.7 * progress);

        gc.save();
        gc.setGlobalAlpha(1 - progress);
        gc.setFill(Color.ORANGE);
        gc.fillOval(x - r, y - r, r * 2, r * 2);
        gc.setStroke(Color.ORANGERED);
        gc.setLineWidth(2);
        gc.strokeOval(x - r, y - r, r * 2, r * 2);
        gc.restore();
    }
}
