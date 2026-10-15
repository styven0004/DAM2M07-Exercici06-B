package com.client;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

public class Main extends Application {

    public static UtilsWS wsClient;

    public static String playerName = "";
    public static final GameModel model = new GameModel();

    public static CtrlConfig ctrlConfig;
    public static CtrlWait ctrlWait;
    public static CtrlPlay ctrlPlay;
    public static CtrlResult ctrlResult;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {

        UtilsViews.parentContainer.setStyle("-fx-font: 14 arial;");
        UtilsViews.addView(getClass(), "ViewConfig", "/assets/viewConfig.fxml");
        UtilsViews.addView(getClass(), "ViewWait", "/assets/viewWait.fxml");
        UtilsViews.addView(getClass(), "ViewPlay", "/assets/viewPlay.fxml");
        UtilsViews.addView(getClass(), "ViewResult", "/assets/viewResult.fxml");

        ctrlConfig = (CtrlConfig) UtilsViews.getController("ViewConfig");
        ctrlWait = (CtrlWait) UtilsViews.getController("ViewWait");
        ctrlPlay = (CtrlPlay) UtilsViews.getController("ViewPlay");
        ctrlResult = (CtrlResult) UtilsViews.getController("ViewResult");

        Scene scene = new Scene(UtilsViews.parentContainer);

        // Teclat: només té efecte a la vista de joc
        scene.setOnKeyPressed(event -> {
            if (!"ViewPlay".equals(UtilsViews.getActiveView())) return;
            ctrlPlay.keyPressed(event.getCode());
            if (isGameKey(event.getCode())) event.consume();
        });
        scene.setOnKeyReleased(event -> ctrlPlay.keyReleased(event.getCode()));
        stage.focusedProperty().addListener((obs, old, focused) -> {
            if (!focused) ctrlPlay.resetInput();
        });

        stage.setScene(scene);
        stage.setTitle("Tanks");
        stage.setResizable(false);
        stage.show();

        // Add icon only if not Mac
        if (!System.getProperty("os.name").contains("Mac")) {
            var iconStream = getClass().getResourceAsStream("/icons/icon.png");
            if (iconStream != null) stage.getIcons().add(new Image(iconStream));
        }
    }

    private static boolean isGameKey(KeyCode c) {
        return c == KeyCode.UP || c == KeyCode.DOWN || c == KeyCode.LEFT || c == KeyCode.RIGHT
            || c == KeyCode.W || c == KeyCode.A || c == KeyCode.S || c == KeyCode.D;
    }

    @Override
    public void stop() {
        if (wsClient != null) {
            wsClient.forceExit();
        }
        System.exit(1); // Kill all executor services
    }

    public static void pauseDuring(long milliseconds, Runnable action) {
        PauseTransition pause = new PauseTransition(Duration.millis(milliseconds));
        pause.setOnFinished(event -> Platform.runLater(action));
        pause.play();
    }

    // ---------------- Connexió ----------------

    public static void connectToServer() {

        ctrlConfig.txtMessage.setTextFill(Color.BLACK);
        ctrlConfig.txtMessage.setText("Connecting ...");

        pauseDuring(1500, () -> { // Give time to show connecting message ...

            playerName = ctrlConfig.txtName.getText().trim();
            String protocol = ctrlConfig.txtProtocol.getText();
            String host = ctrlConfig.txtHost.getText();
            String port = ctrlConfig.txtPort.getText();
            wsClient = UtilsWS.getSharedInstance(protocol + "://" + host + ":" + port);

            wsClient.onMessage((response) -> { Platform.runLater(() -> { wsMessage(response); }); });
            wsClient.onError((response) -> { Platform.runLater(() -> { wsError(response); }); });
        });
    }

    public static void backToLobby() {
        wsClient.safeSend(new JSONObject().put("type", "BACK_TO_LOBBY").toString());
        UtilsViews.setViewAnimating("ViewWait");
    }

    // ---------------- Missatges del servidor ----------------

    private static void wsMessage(String response) {

        JSONObject msg = new JSONObject(response);
        switch (msg.getString("type")) {

            case "hello" -> {
                // El servidor ens saluda: ens presentem amb el nostre nom
                wsClient.safeSend(new JSONObject().put("type", "join").put("name", playerName).toString());
                String view = UtilsViews.getActiveView();
                if ("ViewPlay".equals(view) || "ViewResult".equals(view)) {
                    model.playing = false; // reconnexió: tornem a la llista
                    UtilsViews.setViewAnimating("ViewWait");
                }
            }

            case "lobby" -> {
                JSONArray arr = msg.getJSONArray("players");
                List<String> names = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) names.add(arr.getString(i));
                ctrlWait.setLobby(names, msg.getString("you"));

                if ("ViewConfig".equals(UtilsViews.getActiveView())) {
                    UtilsViews.setViewAnimating("ViewWait");
                }
            }

            case "matchFound" -> {
                model.onMatchFound(msg);
                ctrlWait.showMatch(model.names[0], model.names[1], model.scenarioName);
                ctrlPlay.resetInput();
            }

            case "countdown" -> {
                int value = msg.getInt("value");
                ctrlWait.setCountdown(value);
                if (value == 0) {
                    model.playing = true;
                    UtilsViews.setViewAnimating("ViewPlay");
                }
            }

            case "state" -> model.onState(msg);

            case "gameOver" -> {
                model.playing = false;
                ctrlPlay.resetInput();
                boolean won = msg.getInt("winner") == model.youIndex;
                String reason = msg.optString("reason", "lives");
                // Petita pausa perquè es vegi l'últim impacte
                pauseDuring(reason.equals("lives") ? 1200 : 0, () -> {
                    ctrlResult.show(won, msg.getString("winnerName"), msg.getString("loserName"), reason);
                    UtilsViews.setViewAnimating("ViewResult");
                });
            }

            default -> { }
        }
    }

    private static void wsError(String response) {
        String connectionRefused = "Connection refused";
        if (response.indexOf(connectionRefused) != -1) {
            ctrlConfig.txtMessage.setTextFill(Color.RED);
            ctrlConfig.txtMessage.setText(connectionRefused);
            pauseDuring(1500, () -> {
                ctrlConfig.txtMessage.setText("");
            });
        }
    }
}
