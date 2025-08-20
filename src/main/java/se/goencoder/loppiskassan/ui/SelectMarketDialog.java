package se.goencoder.loppiskassan.ui;

import se.goencoder.loppiskassan.config.ConfigurationStore;
import se.goencoder.loppiskassan.localization.LocalizationManager;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * Dialog for selecting a market when multiple markets are available.
 * The main controls are centered vertically for a balanced appearance.
 */
public class SelectMarketDialog extends JDialog {
    private final JComboBox<String> marketComboBox;

    public SelectMarketDialog(Frame owner) {
        super(owner, LocalizationManager.tr("select_market.title"), true);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        getContentPane().setLayout(new BorderLayout());

        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        centerPanel.add(Box.createVerticalGlue());

        JLabel promptLabel = new JLabel(LocalizationManager.tr("select_market.label"));
        promptLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(promptLabel);
        centerPanel.add(Box.createVerticalStrut(8));

        marketComboBox = new JComboBox<>();
        marketComboBox.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(marketComboBox);
        centerPanel.add(Box.createVerticalStrut(12));

        JPanel buttonPanel = new JPanel();
        JButton okButton = new JButton(LocalizationManager.tr("popup.confirm"));
        JButton cancelButton = new JButton(LocalizationManager.tr("popup.cancel"));
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        centerPanel.add(buttonPanel);
        centerPanel.add(Box.createVerticalGlue());

        getContentPane().add(centerPanel, BorderLayout.CENTER);

        JLabel versionLabel = new JLabel(ConfigurationStore.APP_VERSION_STR.get());
        JPanel footerPanel = new JPanel(new BorderLayout());
        footerPanel.setBorder(new EmptyBorder(0, 0, 5, 5));
        footerPanel.add(versionLabel, BorderLayout.EAST);
        getContentPane().add(footerPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(owner);

        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
        okButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
    }

    public void setMarkets(String[] markets) {
        marketComboBox.removeAllItems();
        if (markets != null) {
            for (String market : markets) {
                marketComboBox.addItem(market);
            }
        }
    }

    public String getSelectedMarket() {
        return (String) marketComboBox.getSelectedItem();
    }
}
