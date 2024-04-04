package se.goencoder.loppiskassan.ui;

import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import java.util.ArrayList;
import java.util.List;

public class UserInterface extends JFrame {
    private final JTabbedPane tabPane;
    private final List<SelectabableTab> selectabableTabs = new ArrayList<>();

    private enum SELECTABLE_TABS {
        CASHIER(0),
        HISTORY(1);
        private final int index;
        SELECTABLE_TABS(int index){
            this.index = index;
        }
        int getIndex() {
            return index;
        }
        static SELECTABLE_TABS fromIndex(int index) {
            switch (index) {
                case 0: return CASHIER;
                case 1: return HISTORY;
                default: Popup.FATAL.showAndWait(
                        "Selected tab out of range",
                        "The selected tab is out of range. Program will exit!");
                    return CASHIER; // Dummy, will not reach here after FATAL
            }
        }
    }

    public UserInterface() {
        tabPane = new JTabbedPane();
        initializeTabs();

        // Add a ChangeListener to the tabPane
        tabPane.addChangeListener(e -> {
            // This method is called whenever the selected tab changes
            SELECTABLE_TABS selectedTab = SELECTABLE_TABS.fromIndex(tabPane.getSelectedIndex());
            selectabableTabs.get(selectedTab.getIndex()).selected();
        });

        add(tabPane);
        setTitle("Loppiskassan");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);
    }

    private void initializeTabs() {
        CashierTabPanel cashierTabPanel = new CashierTabPanel();
        tabPane.addTab("Kassa", null, cashierTabPanel, "Hantera kassatransaktioner");
        selectabableTabs.add(cashierTabPanel);

        HistoryTabPanel historyTabPanel = new HistoryTabPanel();
        tabPane.addTab("Historik", null, historyTabPanel, "Visa tidigare transaktioner");
        selectabableTabs.add(historyTabPanel);
    }


}
