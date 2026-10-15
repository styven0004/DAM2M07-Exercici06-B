package com.client;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;

// Vista 2: llista de jugadors disponibles, espera de contrincant i compte enrere
public class CtrlWait implements Initializable {

    @FXML
    public Label txtTitle;

    @FXML
    public Label txtSubtitle;

    @FXML
    public Label txtPlayer0;

    @FXML
    public Label txtPlayer1;

    @FXML
    public ListView<String> lstPlayers;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
    }

    // Llista de jugadors disponibles (missatge "lobby")
    public void setLobby(List<String> names, String you) {
        List<String> items = new ArrayList<>();
        for (String n : names) items.add(n.equals(you) ? n + " (tu)" : n);
        lstPlayers.setItems(FXCollections.observableArrayList(items));
        lstPlayers.setVisible(true);
        lstPlayers.setManaged(true);

        txtTitle.setText("Esperant contrincant ...");
        txtSubtitle.setText("Jugadors disponibles");
        txtPlayer0.setText("");
        txtPlayer1.setText("");
    }

    // Ja hi ha contrincant: mostra qui juga i amaga la llista
    public void showMatch(String player0, String player1, String scenarioName) {
        lstPlayers.setVisible(false);
        lstPlayers.setManaged(false);
        txtSubtitle.setText("Escenari: " + scenarioName);
        txtPlayer0.setText(player0);
        txtPlayer1.setText(player1);
    }

    public void setCountdown(int value) {
        txtTitle.setText(value == 0 ? "GO" : String.valueOf(value));
    }
}
