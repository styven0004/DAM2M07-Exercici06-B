package com.shared;

// Constants del joc compartides entre servidor i clients.
// El servidor les fa servir per simular; el client, per dibuixar i suavitzar.
public final class Const {

    private Const() { }

    // Mida de l'escenari (coordenades del "món", independents de la finestra)
    public static final int WORLD_W = 800;
    public static final int WORLD_H = 500;

    // Tancs
    public static final double TANK_RADIUS = 20;
    public static final double TANK_SPEED = 130;       // píxels/segon
    public static final double TANK_TURN_SPEED = 150;  // graus/segon
    public static final int START_LIVES = 3;

    // Projectils
    public static final double BULLET_RADIUS = 4;
    public static final double BULLET_SPEED = 320;     // píxels/segon
    public static final double BULLET_MAX_LIFE = 4.0;  // segons
    public static final int MAX_BULLETS = 3;           // projectils actius per jugador
}
