package se.goencoder.loppiskassan.ui;

import se.goencoder.iloppis.model.V1RevenueSplit;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.storage.LocalEvent;
import se.goencoder.loppiskassan.storage.LocalEventRepository;
import se.goencoder.loppiskassan.storage.LocalEventType;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

public class CreateLocalEventDialog extends JDialog {
    private final JTextField nameField;
    private final JTextArea descriptionField;
    private final JTextField streetField;
    private final JTextField cityField;
    private final JSpinner marketOwnerSpin;
    private final JSpinner vendorSpin;
    private final JSpinner platformSpin;

    private LocalEvent createdEvent;

    public CreateLocalEventDialog(Window owner) {
        super(owner, ModalityType.APPLICATION_MODAL);
        setTitle(LocalizationManager.tr("local_event.create.title"));

        nameField = new JTextField(24);
        descriptionField = new JTextArea(4, 24);
        descriptionField.setLineWrap(true);
        descriptionField.setWrapStyleWord(true);
        streetField = new JTextField(24);
        cityField = new JTextField(24);

        marketOwnerSpin = new JSpinner(new SpinnerNumberModel(10, 0, 100, 1));
        vendorSpin = new JSpinner(new SpinnerNumberModel(85, 0, 100, 1));
        platformSpin = new JSpinner(new SpinnerNumberModel(5, 0, 100, 1));

        JPanel content = new JPanel(new BorderLayout());
        content.add(buildForm(), BorderLayout.CENTER);
        content.add(buildButtons(), BorderLayout.SOUTH);
        setContentPane(content);

        pack();
        setLocationRelativeTo(owner);
    }

    public LocalEvent showDialog() {
        setVisible(true);
        return createdEvent;
    }

    private JPanel buildForm() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 10, 6, 10);
        gbc.anchor = GridBagConstraints.LINE_START;

        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel(LocalizationManager.tr("local_event.name")), gbc);
        gbc.gridx = 1;
        panel.add(nameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(new JLabel(LocalizationManager.tr("local_event.description")), gbc);
        gbc.gridx = 1;
        panel.add(new JScrollPane(descriptionField), gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(new JLabel(LocalizationManager.tr("local_event.address_street")), gbc);
        gbc.gridx = 1;
        panel.add(streetField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        panel.add(new JLabel(LocalizationManager.tr("local_event.address_city")), gbc);
        gbc.gridx = 1;
        panel.add(cityField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        panel.add(new JLabel(LocalizationManager.tr("local_event.market_owner")), gbc);
        gbc.gridx = 1;
        panel.add(marketOwnerSpin, gbc);

        gbc.gridx = 0;
        gbc.gridy = 5;
        panel.add(new JLabel(LocalizationManager.tr("local_event.vendor")), gbc);
        gbc.gridx = 1;
        panel.add(vendorSpin, gbc);

        gbc.gridx = 0;
        gbc.gridy = 6;
        panel.add(new JLabel(LocalizationManager.tr("local_event.platform")), gbc);
        gbc.gridx = 1;
        panel.add(platformSpin, gbc);

        return panel;
    }

    private JPanel buildButtons() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton(LocalizationManager.tr("popup.cancel"));
        JButton create = new JButton(LocalizationManager.tr("local_event.create.button"));
        panel.add(cancel);
        panel.add(create);

        cancel.addActionListener(e -> {
            createdEvent = null;
            setVisible(false);
            dispose();
        });

        create.addActionListener(e -> onCreate());
        return panel;
    }

    private void onCreate() {
        String name = nameField.getText().trim();
        String description = descriptionField.getText().trim();
        String street = streetField.getText().trim();
        String city = cityField.getText().trim();
        int marketOwner = ((Number) marketOwnerSpin.getValue()).intValue();
        int vendor = ((Number) vendorSpin.getValue()).intValue();
        int platform = ((Number) platformSpin.getValue()).intValue();

        if (name.isBlank()) {
            Popup.ERROR.showAndWait(
                    LocalizationManager.tr("local_event.error.title"),
                    LocalizationManager.tr("local_event.error.name_required"));
            return;
        }

        if (marketOwner + vendor + platform != 100) {
            Popup.ERROR.showAndWait(
                    LocalizationManager.tr("local_event.error.title"),
                    LocalizationManager.tr("local_event.error.invalid_split"));
            return;
        }

        V1RevenueSplit split = new V1RevenueSplit()
                .marketOwnerPercentage((float) marketOwner)
                .vendorPercentage((float) vendor)
                .platformProviderPercentage((float) platform)
                .charityPercentage(0.0f);

        String eventId = "local-" + UUID.randomUUID();
        LocalEvent event = new LocalEvent(
                eventId,
                LocalEventType.LOCAL,
                name,
                description,
                street,
                city,
                OffsetDateTime.now(ZoneOffset.UTC),
                split
        );

        try {
            LocalEventRepository.create(event);
            createdEvent = event;
            setVisible(false);
            dispose();
        } catch (IOException ex) {
            Popup.ERROR.showAndWait(
                    LocalizationManager.tr("local_event.error.title"),
                    LocalizationManager.tr("local_event.error.create_failed", ex.getMessage()));
        }
    }
}
