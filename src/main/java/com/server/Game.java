package com.server;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.shared.Const;
import com.shared.Wall;

/**
 * Lògica d'una partida (2 jugadors). No sap res de WebSockets ni de JSON:
 * Main li passa les accions dels jugadors i li demana avançar el temps.
 *
 * Aquí es decideix TOT: moviment, col·lisions, impactes, vides i guanyador.
 * Els angles són en graus: 0 = dreta, 90 = avall (l'eix Y creix cap avall).
 */
public class Game {

    public static final class TankState {
        public double x, y;
        public double angle;   // cap on mira el cos del tanc
        public double turret;  // cap on apunta el canó
        public int lives = Const.START_LIVES;
        public int move;       // -1 enrere, 0 aturat, 1 endavant
        public int turn;       // -1 esquerra, 0 recte, 1 dreta
        public double aimX, aimY;

        public boolean isAlive() {
            return lives > 0;
        }
    }

    public static final class BulletState {
        public int id;
        public int owner;
        public double x, y;
        public double angle;
        public double life;
        public boolean bounced;
    }

    // Esdeveniment puntual perquè els clients mostrin una animació
    public static final class Event {
        public final String kind; // "hit" (impacte en un tanc) o "puff" (paret / fi de vida)
        public final double x, y;
        public final int tank;    // tanc impactat (només "hit"), si no -1
        public final boolean dead;

        Event(String kind, double x, double y, int tank, boolean dead) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.tank = tank;
            this.dead = dead;
        }
    }

    // Passos petits dins de cada actualització perquè els objectes ràpids
    // no "saltin" una paret sense detectar el xoc.
    private static final int PHYSICS_STEPS = 4;

    public final int scenario;
    public final List<Wall> walls;
    public final TankState[] tanks = new TankState[2];
    public final List<BulletState> bullets = new ArrayList<>();
    public final List<Event> events = new ArrayList<>();

    private int nextBulletId = 1;
    private int winner = -1;

    public Game(int scenario) {
        this.scenario = scenario;
        this.walls = Scenarios.walls(scenario);

        tanks[0] = new TankState();
        tanks[0].x = 60;
        tanks[0].y = Const.WORLD_H / 2.0;
        tanks[0].angle = 0;
        tanks[0].turret = 0;
        tanks[0].aimX = 740;
        tanks[0].aimY = tanks[0].y;

        tanks[1] = new TankState();
        tanks[1].x = 740;
        tanks[1].y = Const.WORLD_H / 2.0;
        tanks[1].angle = 180;
        tanks[1].turret = 180;
        tanks[1].aimX = 60;
        tanks[1].aimY = tanks[1].y;
    }

    public boolean isOver() {
        return winner >= 0;
    }

    public int getWinner() {
        return winner;
    }

    // ---------------- Accions dels jugadors ----------------

    public void setMove(int player, int value) {
        tanks[player].move = Integer.signum(value);
    }

    public void setTurn(int player, int value) {
        tanks[player].turn = Integer.signum(value);
    }

    public void aim(int player, double x, double y) {
        tanks[player].aimX = x;
        tanks[player].aimY = y;
        updateTurret(tanks[player]);
    }

    public int activeBullets(int player) {
        int n = 0;
        for (BulletState b : bullets) {
            if (b.owner == player) n++;
        }
        return n;
    }

    // Dispara cap on apunta el canó. Retorna false si no es pot disparar.
    public boolean fire(int player) {
        TankState t = tanks[player];
        if (isOver() || !t.isAlive() || activeBullets(player) >= Const.MAX_BULLETS) return false;

        updateTurret(t);
        double rad = Math.toRadians(t.turret);
        double dist = Const.TANK_RADIUS + Const.BULLET_RADIUS + 2;
        double bx = t.x + Math.cos(rad) * dist;
        double by = t.y + Math.sin(rad) * dist;
        if (touchesWall(bx, by, Const.BULLET_RADIUS)) { // canó enganxat a una paret
            bx = t.x;
            by = t.y;
        }

        BulletState b = new BulletState();
        b.id = nextBulletId++;
        b.owner = player;
        b.x = bx;
        b.y = by;
        b.angle = t.turret;
        bullets.add(b);
        return true;
    }

    // ---------------- Simulació ----------------

    public void update(double dt) {
        if (isOver()) return;

        double step = dt / PHYSICS_STEPS;
        for (int i = 0; i < PHYSICS_STEPS && !isOver(); i++) {
            updateTanks(step);
            updateBullets(step);
        }
        for (TankState t : tanks) updateTurret(t);
    }

    private void updateTurret(TankState t) {
        t.turret = Math.toDegrees(Math.atan2(t.aimY - t.y, t.aimX - t.x));
    }

    private void updateTanks(double dt) {
        for (int i = 0; i < 2; i++) {
            TankState t = tanks[i];
            if (!t.isAlive()) continue;

            // Rotació del cos
            t.angle = normalize(t.angle + t.turn * Const.TANK_TURN_SPEED * dt);

            // Moviment endavant / enrere segons l'angle
            if (t.move == 0) continue;
            double rad = Math.toRadians(t.angle);
            double d = t.move * Const.TANK_SPEED * dt;
            double nx = t.x + Math.cos(rad) * d;
            double ny = t.y + Math.sin(rad) * d;

            // Si el moviment complet xoca, provem només en X o només en Y:
            // així el tanc llisca per la paret en lloc de quedar-s'hi enganxat.
            if (!isTankBlocked(i, nx, ny)) {
                t.x = nx;
                t.y = ny;
            } else if (!isTankBlocked(i, nx, t.y)) {
                t.x = nx;
            } else if (!isTankBlocked(i, t.x, ny)) {
                t.y = ny;
            }
        }
    }

    // El tanc no pot sortir de l'escenari, entrar a una paret ni travessar l'altre tanc.
    private boolean isTankBlocked(int index, double nx, double ny) {
        double r = Const.TANK_RADIUS;
        if (nx < r || ny < r || nx > Const.WORLD_W - r || ny > Const.WORLD_H - r) return true;
        if (touchesWall(nx, ny, r)) return true;

        TankState me = tanks[index];
        TankState other = tanks[1 - index];
        if (other.isAlive()) {
            double newDist = Math.hypot(nx - other.x, ny - other.y);
            double oldDist = Math.hypot(me.x - other.x, me.y - other.y);
            // Bloquegem si s'hi acosta massa (així mai queden encallats si ja se solapen)
            if (newDist < 2 * r && newDist < oldDist) return true;
        }
        return false;
    }

    private void updateBullets(double dt) {
        Iterator<BulletState> it = bullets.iterator();
        while (it.hasNext()) {
            BulletState b = it.next();
            double rad = Math.toRadians(b.angle);
            double nx = b.x + Math.cos(rad) * Const.BULLET_SPEED * dt;
            double ny = b.y + Math.sin(rad) * Const.BULLET_SPEED * dt;

            // 1. Paret: el primer cop rebota, el segon desapareix
            if (touchesWall(nx, ny, Const.BULLET_RADIUS)) {
                if (b.bounced) {
                    events.add(new Event("puff", b.x, b.y, -1, false));
                    it.remove();
                } else {
                    boolean invertX = touchesWall(nx, b.y, Const.BULLET_RADIUS);
                    boolean invertY = touchesWall(b.x, ny, Const.BULLET_RADIUS);
                    if (!invertX && !invertY) { // cantonada: torna enrere
                        invertX = true;
                        invertY = true;
                    }
                    double dx = invertX ? -Math.cos(rad) : Math.cos(rad);
                    double dy = invertY ? -Math.sin(rad) : Math.sin(rad);
                    b.angle = Math.toDegrees(Math.atan2(dy, dx));
                    b.bounced = true;
                }
                continue; // no avança aquest pas
            }
            b.x = nx;
            b.y = ny;

            // 2. Temps de vida màxim o fora de l'escenari
            b.life += dt;
            if (b.life > Const.BULLET_MAX_LIFE || nx < 0 || ny < 0
                    || nx > Const.WORLD_W || ny > Const.WORLD_H) {
                events.add(new Event("puff", b.x, b.y, -1, false));
                it.remove();
                continue;
            }

            // 3. Tancs: l'altre sempre; el propi només si la bala ja ha rebotat
            for (int t = 0; t < 2; t++) {
                TankState tank = tanks[t];
                if (!tank.isAlive()) continue;
                if (t == b.owner && !b.bounced) continue;
                if (Math.hypot(b.x - tank.x, b.y - tank.y) < Const.TANK_RADIUS + Const.BULLET_RADIUS) {
                    tank.lives--;
                    events.add(new Event("hit", b.x, b.y, t, tank.lives == 0));
                    it.remove();
                    if (tank.lives == 0) {
                        winner = 1 - t;
                        return;
                    }
                    break;
                }
            }
        }
    }

    private boolean touchesWall(double x, double y, double radius) {
        for (Wall w : walls) {
            if (w.touchesCircle(x, y, radius)) return true;
        }
        return false;
    }

    // Deixa l'angle a l'interval [-180, 180)
    private static double normalize(double angle) {
        return ((angle + 180) % 360 + 360) % 360 - 180;
    }
}
