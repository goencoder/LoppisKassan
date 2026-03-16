package se.goencoder.loppiskassan.ui;

import se.goencoder.iloppis.model.*;
import se.goencoder.loppiskassan.config.AppModeManager;
import se.goencoder.loppiskassan.localization.LocalizationAware;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.rest.ApiHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Live statistics panel for iLoppis mode.
 * Polls GetEventLiveOpsStats every 10 seconds and displays KPI cards + cashier list.
 */
public class LiveStatsPanel extends JPanel implements SelectabableTab, LocalizationAware {

    private static final Logger log = Logger.getLogger(LiveStatsPanel.class.getName());
    private static final long POLL_INTERVAL_MS = 10_000;
    private static final NumberFormat SEK_FORMAT = NumberFormat.getIntegerInstance(new Locale("sv", "SE"));

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "live-stats-poller");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> pollFuture;

    // KPI value labels
    private JLabel purchasesValue;
    private JLabel itemsValue;
    private JLabel revenueValue;
    private JLabel cashiersValue;

    // KPI subtitle labels
    private JLabel purchasesLabel;
    private JLabel itemsLabel;
    private JLabel revenueLabel;
    private JLabel cashiersLabel;

    // Cashier list
    private JPanel cashierListPanel;
    private JLabel cashierSectionTitle;

    // Status
    private JLabel statusLabel;

    public LiveStatsPanel() {
        setLayout(new BorderLayout());
        setBackground(AppColors.WHITE);
        buildUI();
    }

    private void buildUI() {
        removeAll();

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(AppColors.WHITE);
        content.setBorder(new EmptyBorder(24, 32, 24, 32));

        // KPI grid — 4 cards in a row
        JPanel kpiGrid = new JPanel(new GridLayout(1, 4, 16, 0));
        kpiGrid.setBackground(AppColors.WHITE);
        kpiGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));

        purchasesValue = new JLabel("—", SwingConstants.CENTER);
        purchasesLabel = new JLabel(LocalizationManager.tr("livestats.purchases"), SwingConstants.CENTER);
        kpiGrid.add(createKpiCard(purchasesValue, purchasesLabel));

        itemsValue = new JLabel("—", SwingConstants.CENTER);
        itemsLabel = new JLabel(LocalizationManager.tr("livestats.items"), SwingConstants.CENTER);
        kpiGrid.add(createKpiCard(itemsValue, itemsLabel));

        revenueValue = new JLabel("—", SwingConstants.CENTER);
        revenueLabel = new JLabel(LocalizationManager.tr("livestats.revenue"), SwingConstants.CENTER);
        kpiGrid.add(createKpiCard(revenueValue, revenueLabel));

        cashiersValue = new JLabel("—", SwingConstants.CENTER);
        cashiersLabel = new JLabel(LocalizationManager.tr("livestats.cashiers"), SwingConstants.CENTER);
        kpiGrid.add(createKpiCard(cashiersValue, cashiersLabel));

        content.add(kpiGrid);
        content.add(Box.createVerticalStrut(24));

        // Cashier section
        cashierSectionTitle = new JLabel(LocalizationManager.tr("livestats.cashier_section"));
        cashierSectionTitle.setFont(cashierSectionTitle.getFont().deriveFont(Font.BOLD, 16f));
        cashierSectionTitle.setForeground(AppColors.TEXT_PRIMARY);
        cashierSectionTitle.setAlignmentX(LEFT_ALIGNMENT);
        content.add(cashierSectionTitle);
        content.add(Box.createVerticalStrut(8));

        cashierListPanel = new JPanel();
        cashierListPanel.setLayout(new BoxLayout(cashierListPanel, BoxLayout.Y_AXIS));
        cashierListPanel.setBackground(AppColors.WHITE);
        cashierListPanel.setAlignmentX(LEFT_ALIGNMENT);
        content.add(cashierListPanel);

        content.add(Box.createVerticalGlue());

        // Status line at bottom
        statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));
        statusLabel.setForeground(AppColors.TEXT_MUTED);
        statusLabel.setAlignmentX(LEFT_ALIGNMENT);
        content.add(Box.createVerticalStrut(16));
        content.add(statusLabel);

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel createKpiCard(JLabel valueLabel, JLabel subtitleLabel) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(AppColors.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColors.BORDER, 1),
                new EmptyBorder(20, 16, 20, 16)
        ));

        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 32f));
        valueLabel.setForeground(AppColors.TEXT_PRIMARY);
        valueLabel.setAlignmentX(CENTER_ALIGNMENT);

        subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(Font.BOLD, 11f));
        subtitleLabel.setForeground(AppColors.TEXT_MUTED);
        subtitleLabel.setAlignmentX(CENTER_ALIGNMENT);

        card.add(Box.createVerticalGlue());
        card.add(valueLabel);
        card.add(Box.createVerticalStrut(4));
        card.add(subtitleLabel);
        card.add(Box.createVerticalGlue());

        return card;
    }

    // ── Polling ──

    @Override
    public void selected() {
        startPolling();
    }

    public void deselected() {
        stopPolling();
    }

    private void startPolling() {
        stopPolling();
        // Fetch immediately, then every 10s
        pollFuture = scheduler.scheduleAtFixedRate(this::fetchAndUpdate, 0, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void stopPolling() {
        if (pollFuture != null && !pollFuture.isCancelled()) {
            pollFuture.cancel(false);
            pollFuture = null;
        }
    }

    private void fetchAndUpdate() {
        try {
            String eventId = AppModeManager.getEventId();
            String apiKey = ApiHelper.INSTANCE.getCurrentApiKey();
            if (eventId == null || eventId.isBlank() || apiKey == null || apiKey.isBlank()) {
                return;
            }

            V1GetEventLiveOpsStatsResponse resp = ApiHelper.INSTANCE.getStatsServiceApi()
                    .statsServiceGetEventLiveOpsStats(eventId);

            SwingUtilities.invokeLater(() -> updateUI(resp));
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to fetch live stats", e);
            SwingUtilities.invokeLater(() ->
                    statusLabel.setText(LocalizationManager.tr("livestats.error")));
        }
    }

    // ── UI Update ──

    private void updateUI(V1GetEventLiveOpsStatsResponse resp) {
        // Sales KPIs
        V1LiveSalesStats sales = resp.getSales();
        if (sales != null) {
            purchasesValue.setText(formatInt(sales.getPurchasesTotal()));
            itemsValue.setText(formatInt(sales.getItemsTotal()));
            String rev = sales.getRevenueTotalSek();
            if (rev != null) {
                try {
                    revenueValue.setText(SEK_FORMAT.format(Long.parseLong(rev)) + " kr");
                } catch (NumberFormatException e) {
                    revenueValue.setText(rev + " kr");
                }
            } else {
                revenueValue.setText("0 kr");
            }
        }

        // Cashier count
        V1LiveCashierStats cashierStats = resp.getCashiers();
        if (cashierStats != null) {
            int total = safeInt(cashierStats.getOpenCount())
                    + safeInt(cashierStats.getProcessingCount())
                    + safeInt(cashierStats.getStalledCount());
            cashiersValue.setText(String.valueOf(total));
        }

        // Cashier list
        updateCashierList(resp.getCashierStatuses());

        // Status line
        if (resp.getGeneratedAt() != null) {
            statusLabel.setText(LocalizationManager.tr("livestats.updated",
                    resp.getGeneratedAt().toString().substring(11, 19)));
        }
    }

    private void updateCashierList(List<V1LiveCashierStatus> statuses) {
        cashierListPanel.removeAll();

        if (statuses == null || statuses.isEmpty()) {
            JLabel empty = new JLabel(LocalizationManager.tr("livestats.no_cashiers"));
            empty.setForeground(AppColors.TEXT_MUTED);
            empty.setFont(empty.getFont().deriveFont(13f));
            cashierListPanel.add(empty);
        } else {
            for (V1LiveCashierStatus cs : statuses) {
                cashierListPanel.add(createCashierRow(cs));
                cashierListPanel.add(Box.createVerticalStrut(4));
            }
        }

        cashierListPanel.revalidate();
        cashierListPanel.repaint();
    }

    private JPanel createCashierRow(V1LiveCashierStatus cs) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setBackground(AppColors.SURFACE);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColors.BORDER, 1),
                new EmptyBorder(10, 16, 10, 16)
        ));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));

        // Left: name
        String name = cs.getDisplayName() != null ? cs.getDisplayName() : "—";
        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 14f));
        nameLabel.setForeground(AppColors.TEXT_PRIMARY);
        row.add(nameLabel, BorderLayout.WEST);

        // Right: state badge
        JLabel stateLabel = new JLabel(formatState(cs.getState()));
        stateLabel.setFont(stateLabel.getFont().deriveFont(Font.BOLD, 12f));
        stateLabel.setForeground(stateColor(cs.getState()));
        stateLabel.setOpaque(true);
        stateLabel.setBackground(stateBackgroundColor(cs.getState()));
        stateLabel.setBorder(new EmptyBorder(2, 8, 2, 8));
        row.add(stateLabel, BorderLayout.EAST);

        return row;
    }

    private String formatState(LiveCashierStatusState state) {
        if (state == null) return "—";
        return switch (state) {
            case OPEN -> LocalizationManager.tr("livestats.state.open");
            case PROCESSING -> LocalizationManager.tr("livestats.state.processing");
            case STALLED -> LocalizationManager.tr("livestats.state.stalled");
            case OFFLINE -> LocalizationManager.tr("livestats.state.offline");
            default -> state.toString();
        };
    }

    private Color stateColor(LiveCashierStatusState state) {
        if (state == null) return AppColors.TEXT_MUTED;
        return switch (state) {
            case OPEN -> AppColors.STATE_OPEN;
            case PROCESSING -> AppColors.STATE_PROCESSING;
            case STALLED -> AppColors.STATE_STALLED;
            case OFFLINE -> AppColors.TEXT_MUTED;
            default -> AppColors.TEXT_MUTED;
        };
    }

    private Color stateBackgroundColor(LiveCashierStatusState state) {
        if (state == null) return AppColors.SURFACE;
        return switch (state) {
            case OPEN -> AppColors.STATE_OPEN_BG;
            case PROCESSING -> AppColors.STATE_PROCESSING_BG;
            case STALLED -> AppColors.STATE_STALLED_BG;
            case OFFLINE -> AppColors.SURFACE;
            default -> AppColors.SURFACE;
        };
    }

    // ── Helpers ──

    private static String formatInt(Integer val) {
        return val != null ? SEK_FORMAT.format(val) : "0";
    }

    private static int safeInt(Integer val) {
        return val != null ? val : 0;
    }

    @Override
    public void reloadTexts() {
        purchasesLabel.setText(LocalizationManager.tr("livestats.purchases"));
        itemsLabel.setText(LocalizationManager.tr("livestats.items"));
        revenueLabel.setText(LocalizationManager.tr("livestats.revenue"));
        cashiersLabel.setText(LocalizationManager.tr("livestats.cashiers"));
        cashierSectionTitle.setText(LocalizationManager.tr("livestats.cashier_section"));
    }

    @Override
    public void removeNotify() {
        stopPolling();
        super.removeNotify();
    }
}
