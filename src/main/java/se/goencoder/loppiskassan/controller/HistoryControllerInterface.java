package se.goencoder.loppiskassan.controller;

import se.goencoder.loppiskassan.ui.HistoryPanelInterface;


public interface HistoryControllerInterface {
    void registerView(HistoryPanelInterface view);
    void loadHistory();
    void filterUpdated();
    // New method for direct button action handling
    void buttonAction(String actionCommand);
}
