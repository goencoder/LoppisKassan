package se.goencoder.loppiskassan.ui;

import se.goencoder.loppiskassan.config.ConfigurationStore;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class UserInterface extends JFrame {
    private final JTabbedPane tabPane;
    private final List<SelectabableTab> selectabableTabs = new ArrayList<>();

    private enum SELECTABLE_TABS {
        DISCOVERY(0),
        CASHIER(1),
        HISTORY(2);
        private final int index;
        SELECTABLE_TABS(int index){
            this.index = index;
        }
        int getIndex() {
            return index;
        }
        static SELECTABLE_TABS fromIndex(int index) {
            return switch (index) {
                case 0 -> DISCOVERY;
                case 1 -> CASHIER;
                case 2 -> HISTORY;
                default -> {
                    Popup.FATAL.showAndWait(
                            "Denna tabb finns inte",
                            "Det finns ingen tabb med index " + index);
                    yield CASHIER;
                }
            };
        }
    }

    public UserInterface() {
        tabPane = new JTabbedPane();
        initializeTabs();

        // Add a ChangeListener to the tabPane
        tabPane.addChangeListener(e -> {
            // This method is called whenever the selected tab changes
            SELECTABLE_TABS selectedTab = SELECTABLE_TABS.fromIndex(tabPane.getSelectedIndex());
            if (selectedTab != SELECTABLE_TABS.DISCOVERY) {
                // If we are attempting to select a tab other than
                // The one selecting which loppis to manage, we need to check if a loppis has been selected or not.
                if (ConfigurationStore.EVENT_ID_STR.get() == null) {
                    tabPane.setSelectedIndex(SELECTABLE_TABS.DISCOVERY.getIndex());
                    Popup.ERROR.showAndWait(
                            "Ingen loppis vald",
                            "Vänligen välj en loppis att öppna en kassa för först.");
                    return;
                }
            }
            selectabableTabs.get(selectedTab.getIndex()).selected();
        });

        add(tabPane);
        setTitle("Loppiskassan");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);
    }
    static JButton createButton(String text, int width, int height) {
        JButton button = new JButton(text);
        button.setPreferredSize(new Dimension(width, height)); // Set the preferred size
        // You could also set the font here if needed
        // button.setFont(new Font("Arial", Font.BOLD, 14));
        return button;
    }

    private void initializeTabs() {
        DiscoveryTabPanel discoveryTabPanel = new DiscoveryTabPanel();
        tabPane.addTab("Välj Loppis", null, discoveryTabPanel, "Välj vilken loppis du vill öppna en kassa för");
        selectabableTabs.add(discoveryTabPanel);

        CashierTabPanel cashierTabPanel = new CashierTabPanel();
        tabPane.addTab("Kassahantering", null, cashierTabPanel, "Hantera kassatransaktioner");
        selectabableTabs.add(cashierTabPanel);

        HistoryTabPanel historyTabPanel = new HistoryTabPanel();
        tabPane.addTab("Historik", null, historyTabPanel, "Visa tidigare transaktioner");
        selectabableTabs.add(historyTabPanel);
    }


}
