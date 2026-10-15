package com.server;

import java.util.ArrayList;
import java.util.List;

import com.shared.Const;
import com.shared.Wall;

// Definició dels escenaris. Totes tenen les parets exteriors (gruix 20)
// i els tancs comencen a (60, 250) i (740, 250), sempre en zona lliure.
public final class Scenarios {

    private Scenarios() { }

    public static final String[] NAMES = { "Arena", "Passadissos", "Laberint" };

    private static final double T = 20; // gruix de les parets

    public static int count() {
        return NAMES.length;
    }

    public static List<Wall> walls(int scenario) {
        List<Wall> list = new ArrayList<>();

        // Parets exteriors
        list.add(new Wall(0, 0, Const.WORLD_W, T));
        list.add(new Wall(0, Const.WORLD_H - T, Const.WORLD_W, T));
        list.add(new Wall(0, 0, T, Const.WORLD_H));
        list.add(new Wall(Const.WORLD_W - T, 0, T, Const.WORLD_H));

        switch (scenario) {
            case 0 -> {
                // Arena: quatre obstacles al centre i espais amplis
                list.add(new Wall(300, 130, 60, 60));
                list.add(new Wall(440, 130, 60, 60));
                list.add(new Wall(300, 310, 60, 60));
                list.add(new Wall(440, 310, 60, 60));
            }
            case 1 -> {
                // Passadissos: parets verticals amb pas alternat (a baix / a dalt)
                list.add(new Wall(200, 20, T, 320));
                list.add(new Wall(330, 160, T, 320));
                list.add(new Wall(460, 20, T, 320));
                list.add(new Wall(590, 160, T, 320));
            }
            default -> {
                // Laberint: diversos camins possibles entre els dos extrems
                list.add(new Wall(140, 20, T, 200));
                list.add(new Wall(140, 300, T, 180));
                list.add(new Wall(260, 100, T, 380));
                list.add(new Wall(380, 20, T, 300));
                list.add(new Wall(380, 200, 60, T));
                list.add(new Wall(500, 140, T, 200));
                list.add(new Wall(620, 20, T, 220));
                list.add(new Wall(620, 320, 100, T));
            }
        }
        return list;
    }
}
