package com.client;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;

// Vista 4: resultat de la partida
public class CtrlResult implements Initializable {

    @FXML
    public Label txtTitle;

    @FXML
    public Label txtWinner;

    @FXML
    public Label txtLoser;

    @FXML
    public Label txtReason;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
    }

    public void show(boolean youWon, String winner, String loser, String reason) {
        txtTitle.setText(youWon ? "Has guanyat!" : "Has perdut");
        txtTitle.setTextFill(youWon ? Color.FORESTGREEN : Color.FIREBRICK);
        txtWinner.setText("Guanyador: " + winner);
        txtLoser.setText("Perdedor: " + loser);
        txtReason.setText(reason.equals("disconnect") ? "El contrincant s'ha desconnectat." : "");
    }

    @FXML
    private void backToLobby() {
        Main.backToLobby();
    }
}
