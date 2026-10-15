package com.client;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import com.shared.Const;
import com.shared.Wall;

/**
 * Còpia local de l'estat que envia el servidor. El client NO decideix res:
 * només guarda l'últim estat rebut i el suavitza per dibuixar-lo.
 *
 * - Tancs: el servidor envia ~30 estats/s; aquí s'interpolen cap a la posició
 *   i els angles rebuts perquè el moviment i les rotacions es vegin fluids.
 * - Projectils: es dibuixen avançats segons el temps passat des de l'últim estat.
 */
class GameModel {

    static final class TankView {
        double x, y, angle, turret;      // valors que es dibuixen
        double tx, ty, tAngle, tTurret;  // últim valor rebut del servidor
        int lives = Const.START_LIVES;
        int bullets;
        boolean snapped;                 // false fins a rebre el primer estat
    }

    static final class BulletView {
        int id, owner;
        double x, y, angle;
    }

    final String[] names = { "?", "?" };
    final String[] colors = { "#1E90FF", "#DAA520" };
    int youIndex;
    String scenarioName = "";
    List<Wall> walls = new ArrayList<>();

    final TankView[] tanks = { new TankView(), new TankView() };
    List<BulletView> bullets = new ArrayList<>();
    final List<Explosion> explosions = new ArrayList<>();

    long lastStateNanos;
    boolean ready;    // ja tenim escenari i almenys un estat
    boolean playing;  // la partida està en marxa (després del compte enrere)

    // Dades de l'escenari i dels jugadors (missatge "matchFound")
    void onMatchFound(JSONObject m) {
        youIndex = m.getInt("youIndex");
        scenarioName = m.getString("scenarioName");

        JSONArray ps = m.getJSONArray("players");
        for (int i = 0; i < 2; i++) {
            names[i] = ps.getJSONObject(i).getString("name");
            colors[i] = ps.getJSONObject(i).getString("color");
        }

        List<Wall> list = new ArrayList<>();
        JSONArray ws = m.getJSONArray("walls");
        for (int i = 0; i < ws.length(); i++) {
            JSONArray w = ws.getJSONArray(i);
            list.add(new Wall(w.getDouble(0), w.getDouble(1), w.getDouble(2), w.getDouble(3)));
        }
        walls = list;

        for (int i = 0; i < 2; i++) tanks[i] = new TankView();
        bullets = new ArrayList<>();
        explosions.clear();
        ready = false;
        playing = false;
    }

    // Estat de la partida (missatge "state")
    void onState(JSONObject s) {
        JSONArray ts = s.getJSONArray("tanks");
        for (int i = 0; i < 2; i++) {
            JSONObject t = ts.getJSONObject(i);
            TankView v = tanks[i];
            v.tx = t.getDouble("x");
            v.ty = t.getDouble("y");
            v.tAngle = t.getDouble("angle");
            v.tTurret = t.getDouble("turret");
            v.lives = t.getInt("lives");
            v.bullets = t.getInt("bullets");
            if (!v.snapped) {
                v.x = v.tx;
                v.y = v.ty;
                v.angle = v.tAngle;
                v.turret = v.tTurret;
                v.snapped = true;
            }
        }

        List<BulletView> list = new ArrayList<>();
        JSONArray bs = s.getJSONArray("bullets");
        for (int i = 0; i < bs.length(); i++) {
            JSONObject b = bs.getJSONObject(i);
            BulletView v = new BulletView();
            v.id = b.getInt("id");
            v.owner = b.getInt("owner");
            v.x = b.getDouble("x");
            v.y = b.getDouble("y");
            v.angle = b.getDouble("angle");
            list.add(v);
        }
        bullets = list;

        JSONArray es = s.getJSONArray("events");
        for (int i = 0; i < es.length(); i++) {
            JSONObject e = es.getJSONObject(i);
            boolean hit = e.getString("kind").equals("hit");
            double radius = !hit ? 14 : (e.getBoolean("dead") ? 60 : 30);
            explosions.add(new Explosion(e.getDouble("x"), e.getDouble("y"), radius, hit ? 0.6 : 0.3));
        }

        lastStateNanos = System.nanoTime();
        ready = true;
    }

    // Suavitzat dels tancs i animació dels impactes (cada frame)
    void update(double dt) {
        double f = 1 - Math.exp(-dt * 20);
        for (TankView t : tanks) {
            if (!t.snapped) continue;
            t.x += (t.tx - t.x) * f;
            t.y += (t.ty - t.y) * f;
            t.angle += shortestDiff(t.angle, t.tAngle) * f;
            t.turret += shortestDiff(t.turret, t.tTurret) * f;
        }
        for (Explosion e : explosions) e.update(dt);
        explosions.removeIf(Explosion::isFinished);
    }

    // Posició d'una bala ara mateix (avança des de l'últim estat rebut)
    double[] bulletPosition(BulletView b, long nowNanos) {
        double age = Math.min((nowNanos - lastStateNanos) / 1_000_000_000.0, 0.1);
        double rad = Math.toRadians(b.angle);
        return new double[] {
            b.x + Math.cos(rad) * Const.BULLET_SPEED * age,
            b.y + Math.sin(rad) * Const.BULLET_SPEED * age
        };
    }

    // Diferència d'angles pel camí més curt (evita girar 350° en lloc de -10°)
    private static double shortestDiff(double from, double to) {
        return ((to - from + 540) % 360 + 360) % 360 - 180;
    }
}
