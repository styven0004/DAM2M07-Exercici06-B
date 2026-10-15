package com.server;

import com.shared.Const;
import com.shared.Wall;

/** Comprovacions de la lògica de la partida, sense finestra ni WebSockets. */
public class GameChecks {

    public static void main(String[] args) {

        // Cap escenari no ha de tenir parets sobre els tancs inicials
        for (int s = 0; s < Scenarios.count(); s++) {
            Game g = new Game(s);
            for (Game.TankState t : g.tanks) {
                for (Wall w : g.walls) {
                    check(!w.touchesCircle(t.x, t.y, Const.TANK_RADIUS), "tanc sobre paret a l'escenari " + s);
                }
            }
        }

        // Un tanc que avança sense parar no surt de l'escenari ni traspassa l'altre
        Game g = new Game(0);
        g.setMove(0, 1);
        for (int i = 0; i < 600; i++) g.update(1 / 60.0);
        check(g.tanks[0].x < g.tanks[1].x - 2 * Const.TANK_RADIUS + 0.5, "no ha de travessar l'altre tanc");

        // Girar i avançar contra la paret: es queda dins
        g = new Game(0);
        g.setTurn(0, -1);
        for (int i = 0; i < 30; i++) g.update(1 / 60.0);
        g.setTurn(0, 0);
        g.setMove(0, 1);
        for (int i = 0; i < 600; i++) g.update(1 / 60.0);
        check(g.tanks[0].y >= 40 - 0.01 && g.tanks[0].x >= 40 - 0.01, "no ha de sortir de l'escenari");

        // Màxim 3 projectils actius
        g = new Game(0);
        check(g.fire(0) && g.fire(0) && g.fire(0), "3 trets permesos");
        check(!g.fire(0), "el 4t tret ha de ser rebutjat");
        check(g.activeBullets(0) == 3, "3 projectils actius");

        // Una bala cap a l'altre tanc l'impacta i li treu una vida
        g = new Game(0);
        g.aim(0, 740, 250);
        g.fire(0);
        for (int i = 0; i < 600 && g.tanks[1].lives == Const.START_LIVES; i++) g.update(1 / 60.0);
        check(g.tanks[1].lives == Const.START_LIVES - 1, "impacte: -1 vida");
        check(g.bullets.isEmpty(), "la bala desapareix en impactar");
        check(g.events.stream().anyMatch(e -> e.kind.equals("hit")), "event d'impacte");

        // Una bala contra una paret rebota un cop i desapareix al segon
        g = new Game(0);
        g.aim(0, 60, 40); // cap amunt, a la paret superior
        g.fire(0);
        boolean bounced = false;
        for (int i = 0; i < 600 && !g.bullets.isEmpty(); i++) {
            g.update(1 / 60.0);
            if (!g.bullets.isEmpty() && g.bullets.get(0).bounced) bounced = true;
        }
        check(bounced, "la bala ha de rebotar una vegada");

        // Final de partida: 3 impactes -> guanyador
        g = new Game(0);
        for (int k = 0; k < Const.START_LIVES && !g.isOver(); k++) {
            g.aim(0, 740, 250);
            g.fire(0);
            for (int i = 0; i < 600 && g.activeBullets(0) > 0 && !g.isOver(); i++) g.update(1 / 60.0);
        }
        check(g.isOver() && g.getWinner() == 0, "ha de guanyar el jugador 0");
        check(g.tanks[1].lives == 0, "el perdedor té 0 vides");

        System.out.println("GameChecks: totes les comprovacions són correctes.");
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
