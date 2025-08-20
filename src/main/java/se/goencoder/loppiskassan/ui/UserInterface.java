package se.goencoder.loppiskassan.ui;

import se.goencoder.loppiskassan.config.ConfigurationStore;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.localization.LocalizationAware;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class UserInterface extends JFrame implements LocalizationAware {
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
                            LocalizationManager.tr("error.no_tab"),
                            LocalizationManager.tr("error.no_tab_index", index));
                    yield CASHIER;
                }
            };
        }
    }

    public UserInterface() {
        setLayout(new BorderLayout());
        tabPane = new JTabbedPane();
        initializeTabs();
        LocalizationManager.addListener(this::reloadTexts);

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
                            LocalizationManager.tr("error.no_event_selected.title"),
                            LocalizationManager.tr("error.no_event_selected.message"));
                    return;
                }
            }
            selectabableTabs.get(selectedTab.getIndex()).selected();
        });

        add(createLanguagePanel(), BorderLayout.NORTH);
        add(tabPane, BorderLayout.CENTER);
        reloadTexts();
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

    private JPanel createLanguagePanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        panel.setOpaque(false);

        LanguageSelector selector = new LanguageSelector();
        panel.add(selector);

        return panel;
    }

    @Override
    public void reloadTexts() {
        setTitle(LocalizationManager.tr("frame.title"));
        tabPane.setTitleAt(0, LocalizationManager.tr("tab.discovery"));
        tabPane.setToolTipTextAt(0, LocalizationManager.tr("tab.discovery.tooltip"));
        tabPane.setTitleAt(1, LocalizationManager.tr("tab.cashier"));
        tabPane.setToolTipTextAt(1, LocalizationManager.tr("tab.cashier.tooltip"));
        tabPane.setTitleAt(2, LocalizationManager.tr("tab.history"));
        tabPane.setToolTipTextAt(2, LocalizationManager.tr("tab.history.tooltip"));

        for (int i = 0; i < tabPane.getTabCount(); i++) {
            Component c = tabPane.getComponentAt(i);
            if (c instanceof LocalizationAware la) {
                la.reloadTexts();
            }
        }
    }

    @Override
    public void removeNotify() {
        LocalizationManager.removeListener(this::reloadTexts);
        super.removeNotify();
    }

    private void initializeTabs() {
        DiscoveryTabPanel discoveryTabPanel = new DiscoveryTabPanel();
        tabPane.addTab("", null, discoveryTabPanel, "");
        selectabableTabs.add(discoveryTabPanel);

        CashierTabPanel cashierTabPanel = new CashierTabPanel();
        tabPane.addTab("", null, cashierTabPanel, "");
        selectabableTabs.add(cashierTabPanel);

        HistoryTabPanel historyTabPanel = new HistoryTabPanel();
        tabPane.addTab("", null, historyTabPanel, "");
        selectabableTabs.add(historyTabPanel);
    }

}
