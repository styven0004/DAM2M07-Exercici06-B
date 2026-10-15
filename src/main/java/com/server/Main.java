package com.server;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONArray;
import org.json.JSONObject;

import com.shared.Const;
import com.shared.Wall;

/**
 * Servidor de Tanks. Gestiona jugadors, llista de disponibles, aparellament,
 * compte enrere i partides (la lògica de cada partida és a Game).
 *
 * Tot l'estat es toca des d'UN sol fil (l'executor "exec"): els events de
 * WebSocket només encuen tasques, així no calen locks.
 *
 * Client -> Servidor:
 *   join {name} · MOVE_FORWARD · MOVE_BACKWARD · TURN_LEFT · TURN_RIGHT
 *   STOP_MOVE {axis: "move"|"turn"|absent=tots dos} · AIM {x,y} · FIRE · BACK_TO_LOBBY
 *
 * Servidor -> Client:
 *   hello · lobby {you, players[]} · matchFound {...} · countdown {value}
 *   state {tanks[], bullets[], events[]} · gameOver {winner, winnerName, loserName, reason}
 */
public class Main extends WebSocketServer {

    public static final int DEFAULT_PORT = 3000;

    private static final int TICKS_PER_SECOND = 60;
    private static final int SEND_EVERY_TICKS = 2;   // estat a 30 vegades/segon
    private static final int COUNTDOWN_START = 3;
    private static final String[] COLORS = { "#1E90FF", "#DAA520" };

    private enum PlayerState { NEW, LOBBY, MATCH, RESULT }

    private static final class Player {
        final WebSocket conn;
        String name = "";
        PlayerState state = PlayerState.NEW;
        Match match;
        int index;

        Player(WebSocket conn) {
            this.conn = conn;
        }
    }

    private static final class Match {
        final Player[] players = new Player[2];
        final Game game;
        boolean playing = false;
        int countdown = COUNTDOWN_START;
        long nextCountdownNanos;
        int tick = 0;

        Match(Game game) {
            this.game = game;
        }
    }

    private final Map<WebSocket, Player> players = new LinkedHashMap<>();
    private final List<Match> matches = new ArrayList<>();
    private final ScheduledExecutorService exec;
    private long lastTickNanos = System.nanoTime();

    public Main(InetSocketAddress address) {
        super(address);
        exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "GameLoop");
            t.setDaemon(true);
            return t;
        });
    }

    // ---------------- WebSocketServer ----------------

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        run(() -> {
            players.put(conn, new Player(conn));
            sendSafe(conn, msg("hello"));
        });
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        run(() -> {
            Player p = players.remove(conn);
            if (p != null) handleLeave(p);
        });
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        run(() -> handleMessage(conn, message));
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("WebSocket server started on port: " + getPort());
        setConnectionLostTimeout(100);
        lastTickNanos = System.nanoTime();
        exec.scheduleAtFixedRate(this::safeTick, 0, 1000 / TICKS_PER_SECOND, TimeUnit.MILLISECONDS);
    }

    private void run(Runnable r) {
        try {
            exec.execute(() -> {
                try {
                    r.run();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        } catch (Exception ignored) {
            // executor aturat
        }
    }

    // ---------------- Missatges dels clients ----------------

    private void handleMessage(WebSocket conn, String message) {
        Player p = players.get(conn);
        if (p == null) return;

        JSONObject obj;
        try {
            obj = new JSONObject(message);
        } catch (Exception ex) {
            return; // JSON invàlid
        }

        String type = obj.optString("type", "");
        switch (type) {
            case "join" -> handleJoin(p, obj.optString("name", ""));
            case "BACK_TO_LOBBY" -> handleBackToLobby(p);
            case "MOVE_FORWARD", "MOVE_BACKWARD", "TURN_LEFT", "TURN_RIGHT",
                 "STOP_MOVE", "AIM", "FIRE" -> handleInput(p, type, obj);
            default -> { }
        }
    }

    private void handleJoin(Player p, String requested) {
        if (p.state != PlayerState.NEW) return;
        p.name = uniqueName(requested);
        p.state = PlayerState.LOBBY;
        System.out.println("Jugador connectat: " + p.name);
        broadcastLobby();
        tryPair();
    }

    private void handleBackToLobby(Player p) {
        if (p.state != PlayerState.RESULT) return;
        p.state = PlayerState.LOBBY;
        broadcastLobby();
        tryPair();
    }

    private void handleInput(Player p, String type, JSONObject obj) {
        if (p.state != PlayerState.MATCH || p.match == null || !p.match.playing) return;
        Game g = p.match.game;
        int i = p.index;

        switch (type) {
            case "MOVE_FORWARD" -> g.setMove(i, 1);
            case "MOVE_BACKWARD" -> g.setMove(i, -1);
            case "TURN_LEFT" -> g.setTurn(i, -1);
            case "TURN_RIGHT" -> g.setTurn(i, 1);
            case "STOP_MOVE" -> {
                String axis = obj.optString("axis", "");
                if (!axis.equals("turn")) g.setMove(i, 0);
                if (!axis.equals("move")) g.setTurn(i, 0);
            }
            case "AIM" -> g.aim(i, obj.optDouble("x", 0), obj.optDouble("y", 0));
            case "FIRE" -> g.fire(i);
            default -> { }
        }
    }

    private String uniqueName(String requested) {
        String base = requested == null ? "" : requested.trim();
        if (base.isEmpty()) base = "Jugador";
        if (base.length() > 16) base = base.substring(0, 16);

        String name = base;
        int n = 2;
        while (nameTaken(name)) {
            name = base + " (" + n++ + ")";
        }
        return name;
    }

    private boolean nameTaken(String name) {
        for (Player p : players.values()) {
            if (name.equals(p.name)) return true;
        }
        return false;
    }

    // ---------------- Lobby i partides ----------------

    private List<Player> lobbyPlayers() {
        List<Player> list = new ArrayList<>();
        for (Player p : players.values()) {
            if (p.state == PlayerState.LOBBY) list.add(p);
        }
        return list;
    }

    // Envia als jugadors disponibles la llista de jugadors disponibles
    private void broadcastLobby() {
        List<Player> lobby = lobbyPlayers();
        JSONArray names = new JSONArray();
        for (Player p : lobby) names.put(p.name);

        for (Player p : lobby) {
            sendSafe(p.conn, msg("lobby").put("you", p.name).put("players", names));
        }
    }

    // Aparella de dos en dos els jugadors disponibles
    private void tryPair() {
        List<Player> lobby = lobbyPlayers();
        while (lobby.size() >= 2) {
            createMatch(lobby.remove(0), lobby.remove(0));
        }
    }

    private void createMatch(Player a, Player b) {
        int scenario = ThreadLocalRandom.current().nextInt(Scenarios.count());
        Match m = new Match(new Game(scenario));
        m.players[0] = a;
        m.players[1] = b;
        m.nextCountdownNanos = System.nanoTime();
        matches.add(m);

        for (int i = 0; i < 2; i++) {
            Player p = m.players[i];
            p.state = PlayerState.MATCH;
            p.match = m;
            p.index = i;
        }
        System.out.println("Partida: " + a.name + " vs " + b.name
            + " (" + Scenarios.NAMES[scenario] + ")");

        JSONArray ps = new JSONArray();
        for (int i = 0; i < 2; i++) {
            ps.put(new JSONObject().put("name", m.players[i].name).put("color", COLORS[i]));
        }
        JSONArray walls = new JSONArray();
        for (Wall w : m.game.walls) {
            walls.put(new JSONArray().put(w.x).put(w.y).put(w.w).put(w.h));
        }

        for (int i = 0; i < 2; i++) {
            sendSafe(m.players[i].conn, msg("matchFound")
                .put("youIndex", i)
                .put("players", ps)
                .put("scenario", scenario)
                .put("scenarioName", Scenarios.NAMES[scenario])
                .put("width", Const.WORLD_W)
                .put("height", Const.WORLD_H)
                .put("walls", walls));
        }
        sendState(m); // posicions inicials, per poder dibuixar durant el compte enrere
        broadcastLobby();
    }

    // Un jugador marxa (tanca la finestra o es perd la connexió)
    private void handleLeave(Player p) {
        Match m = p.match;
        if (p.state == PlayerState.MATCH && m != null) {
            Player other = m.players[1 - p.index];
            if (m.playing) {
                finishMatch(m, other.index, "disconnect");
            } else { // encara en compte enrere: l'altre torna a la llista
                matches.remove(m);
                other.state = PlayerState.LOBBY;
                other.match = null;
            }
        }
        broadcastLobby();
        tryPair();
    }

    private void finishMatch(Match m, int winnerIndex, String reason) {
        matches.remove(m);
        JSONObject over = msg("gameOver")
            .put("winner", winnerIndex)
            .put("winnerName", m.players[winnerIndex].name)
            .put("loserName", m.players[1 - winnerIndex].name)
            .put("reason", reason);

        for (Player p : m.players) {
            p.state = PlayerState.RESULT;
            p.match = null;
            sendSafe(p.conn, over);
        }
        System.out.println("Final: guanya " + m.players[winnerIndex].name + " (" + reason + ")");
    }

    // ---------------- Bucle de joc ----------------

    private void safeTick() {
        try {
            tick();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void tick() {
        long now = System.nanoTime();
        double dt = Math.min((now - lastTickNanos) / 1_000_000_000.0, 0.05);
        lastTickNanos = now;

        for (Match m : new ArrayList<>(matches)) {
            if (!m.playing) {
                if (now >= m.nextCountdownNanos) {
                    JSONObject c = msg("countdown").put("value", m.countdown);
                    sendSafe(m.players[0].conn, c);
                    sendSafe(m.players[1].conn, c);
                    if (m.countdown == 0) {
                        m.playing = true;
                    } else {
                        m.countdown--;
                        m.nextCountdownNanos = now + 1_000_000_000L;
                    }
                }
                continue;
            }

            m.game.update(dt);
            m.tick++;
            if (m.tick % SEND_EVERY_TICKS == 0 || m.game.isOver()) sendState(m);
            if (m.game.isOver()) finishMatch(m, m.game.getWinner(), "lives");
        }
    }

    private void sendState(Match m) {
        Game g = m.game;

        JSONArray tanks = new JSONArray();
        for (int i = 0; i < 2; i++) {
            Game.TankState t = g.tanks[i];
            tanks.put(new JSONObject()
                .put("x", r1(t.x)).put("y", r1(t.y))
                .put("angle", r1(t.angle)).put("turret", r1(t.turret))
                .put("lives", t.lives)
                .put("bullets", g.activeBullets(i)));
        }

        JSONArray bullets = new JSONArray();
        for (Game.BulletState b : g.bullets) {
            bullets.put(new JSONObject()
                .put("id", b.id).put("owner", b.owner)
                .put("x", r1(b.x)).put("y", r1(b.y)).put("angle", r1(b.angle)));
        }

        JSONArray events = new JSONArray();
        for (Game.Event e : g.events) {
            events.put(new JSONObject()
                .put("kind", e.kind).put("x", r1(e.x)).put("y", r1(e.y))
                .put("tank", e.tank).put("dead", e.dead));
        }
        g.events.clear();

        JSONObject state = msg("state").put("tanks", tanks).put("bullets", bullets).put("events", events);
        sendSafe(m.players[0].conn, state);
        sendSafe(m.players[1].conn, state);
    }

    // ---------------- Utilitats ----------------

    private static double r1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static JSONObject msg(String type) {
        return new JSONObject().put("type", type);
    }

    private void sendSafe(WebSocket to, JSONObject payload) {
        try {
            if (to != null && to.isOpen()) to.send(payload.toString());
        } catch (Exception e) {
            // client desconnectat: onClose ja ho gestionarà
        }
    }

    public static void main(String[] args) {
        Main server = new Main(new InetSocketAddress(DEFAULT_PORT));
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Aturant servidor ...");
            try {
                server.exec.shutdownNow();
                server.stop(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));

        System.out.println("Server running on port " + DEFAULT_PORT + ". Press Ctrl+C to stop it.");
        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
