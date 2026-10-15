package com.client;

import java.net.URL;
import java.util.EnumSet;
import java.util.ResourceBundle;
import java.util.Set;

import org.json.JSONObject;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import com.shared.Const;

/**
 * Vista 3: partida. El client només captura teclat i ratolí, ho envia al
 * servidor i dibuixa l'estat que en rep (veure GameModel i Renderer).
 */
public class CtrlPlay implements Initializable {

    private static final long AIM_INTERVAL_NANOS = 33_000_000L; // màx. ~30 AIM/s

    @FXML
    private Canvas canvas;
    private GraphicsContext gc;

    private PlayTimer animationTimer;
    private long lastRunNanos;

    private final Set<KeyCode> pressedKeys = EnumSet.noneOf(KeyCode.class);
    private int sentMove = 0; // últim estat enviat al servidor (-1, 0, 1)
    private int sentTurn = 0;

    // Posició del ratolí en coordenades de l'escenari (sense el HUD)
    private double mouseX = Const.WORLD_W / 2.0;
    private double mouseY = Const.WORLD_H / 2.0;
    private boolean aimDirty = false;
    private long lastAimNanos = 0;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        gc = canvas.getGraphicsContext2D();

        canvas.setOnMouseMoved(this::onMouseMoved);
        canvas.setOnMouseDragged(this::onMouseMoved);
        canvas.setOnMousePressed(this::onMousePressed);

        animationTimer = new PlayTimer(this::run, this::draw, 60);
        animationTimer.start();
    }

    // ---------------- Teclat ----------------

    public void keyPressed(KeyCode code) {
        pressedKeys.add(code);
        syncKeys();
    }

    public void keyReleased(KeyCode code) {
        pressedKeys.remove(code);
        syncKeys();
    }

    // Allibera totes les tecles (canvi de vista o finestra sense focus)
    public void resetInput() {
        pressedKeys.clear();
        syncKeys();
    }

    // Només s'envia al servidor quan canvia l'estat del moviment o del gir
    private void syncKeys() {
        if (!Main.model.playing) {
            sentMove = 0;
            sentTurn = 0;
            return;
        }

        boolean forward = pressedKeys.contains(KeyCode.UP) || pressedKeys.contains(KeyCode.W);
        boolean backward = pressedKeys.contains(KeyCode.DOWN) || pressedKeys.contains(KeyCode.S);
        boolean left = pressedKeys.contains(KeyCode.LEFT) || pressedKeys.contains(KeyCode.A);
        boolean right = pressedKeys.contains(KeyCode.RIGHT) || pressedKeys.contains(KeyCode.D);

        int move = (forward ? 1 : 0) - (backward ? 1 : 0);
        int turn = (right ? 1 : 0) - (left ? 1 : 0);

        if (move != sentMove) {
            sentMove = move;
            if (move == 1) send("MOVE_FORWARD");
            else if (move == -1) send("MOVE_BACKWARD");
            else send(new JSONObject().put("type", "STOP_MOVE").put("axis", "move"));
        }
        if (turn != sentTurn) {
            sentTurn = turn;
            if (turn == 1) send("TURN_RIGHT");
            else if (turn == -1) send("TURN_LEFT");
            else send(new JSONObject().put("type", "STOP_MOVE").put("axis", "turn"));
        }
    }

    // ---------------- Ratolí ----------------

    private void updateMouse(MouseEvent event) {
        mouseX = Math.max(0, Math.min(event.getX(), Const.WORLD_W));
        mouseY = Math.max(0, Math.min(event.getY() - Renderer.HUD_HEIGHT, Const.WORLD_H));
        aimDirty = true;
    }

    private void onMouseMoved(MouseEvent event) {
        updateMouse(event);
    }

    private void onMousePressed(MouseEvent event) {
        updateMouse(event);
        if (event.getButton() != MouseButton.PRIMARY || !Main.model.playing) return;
        sendAim(); // el servidor ha de saber cap on apunteu abans de disparar
        send("FIRE");
    }

    private void sendAim() {
        send(new JSONObject().put("type", "AIM").put("x", (int) mouseX).put("y", (int) mouseY));
        aimDirty = false;
        lastAimNanos = System.nanoTime();
    }

    // ---------------- Enviament ----------------

    private void send(String type) {
        send(new JSONObject().put("type", type));
    }

    private void send(JSONObject msg) {
        if (Main.wsClient != null) Main.wsClient.safeSend(msg.toString());
    }

    // ---------------- Bucle ----------------

    // Actualitza (run)
    private void run(double fps) {
        long now = System.nanoTime();
        double dt = lastRunNanos == 0 ? 0 : Math.min((now - lastRunNanos) / 1_000_000_000.0, 0.05);
        lastRunNanos = now;

        Main.model.update(dt);

        if (Main.model.playing && aimDirty && now - lastAimNanos >= AIM_INTERVAL_NANOS) {
            sendAim();
        }
    }

    // Dibuixa (draw)
    private void draw() {
        if (!"ViewPlay".equals(UtilsViews.getActiveView()) || !Main.model.ready) return;

        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        Renderer.draw(gc, Main.model, mouseX, mouseY);

        // El cursor del sistema es substitueix pel punt de mira; amb el màxim
        // de projectils actius el punt de mira desapareix i torna el cursor.
        GameModel.TankView me = Main.model.tanks[Main.model.youIndex];
        boolean aiming = me.lives > 0 && me.bullets < Const.MAX_BULLETS;
        Cursor wanted = aiming ? Cursor.NONE : Cursor.DEFAULT;
        if (canvas.getCursor() != wanted) canvas.setCursor(wanted);
    }
}
