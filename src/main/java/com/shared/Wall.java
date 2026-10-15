package com.shared;

// Paret o obstacle rectangular de l'escenari.
// (x, y) és la cantonada superior esquerra.
public final class Wall {

    public final double x;
    public final double y;
    public final double w;
    public final double h;

    public Wall(double x, double y, double w, double h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    // Un cercle toca el rectangle si el punt del rectangle més proper al centre
    // és a menys d'un radi de distància.
    public boolean touchesCircle(double cx, double cy, double radius) {
        double nearestX = Math.max(x, Math.min(cx, x + w));
        double nearestY = Math.max(y, Math.min(cy, y + h));
        double dx = cx - nearestX;
        double dy = cy - nearestY;
        return dx * dx + dy * dy < radius * radius;
    }
}
