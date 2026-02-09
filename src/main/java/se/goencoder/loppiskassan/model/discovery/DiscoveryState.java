package se.goencoder.loppiskassan.model.discovery;

import se.goencoder.iloppis.model.V1Event;
import se.goencoder.iloppis.model.V1RevenueSplit;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Presentation state for the Discovery tab.
 * <p>
 * This model holds all UI-relevant data for the discovery screen:
 * available events, selected event details, revenue split configuration,
 * cashier code, and mode flags.
 * <p>
 * Observability: Uses {@link PropertyChangeSupport} to notify listeners
 * when properties change. Views can register listeners to auto-update UI.
 */
public class DiscoveryState {

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    // Core data
    private List<V1Event> events = new ArrayList<>();
    private V1Event selectedEvent = null;
    private V1RevenueSplit revenueSplit = null;

    // UI state
    private boolean registerOpened = false;
    private String cashierCode = "";
    private boolean localMode = false;
    private String dateFrom = "";

    // Button/field states
    private boolean cashierButtonEnabled = true;
    private boolean detailFormVisible = false;

    // Property name constants
    public static final String PROP_EVENTS = "events";
    public static final String PROP_SELECTED_EVENT = "selectedEvent";
    public static final String PROP_REVENUE_SPLIT = "revenueSplit";
    public static final String PROP_REGISTER_OPENED = "registerOpened";
    public static final String PROP_CASHIER_CODE = "cashierCode";
    public static final String PROP_LOCAL_MODE = "localMode";
    public static final String PROP_DATE_FROM = "dateFrom";
    public static final String PROP_CASHIER_BUTTON_ENABLED = "cashierButtonEnabled";
    public static final String PROP_DETAIL_FORM_VISIBLE = "detailFormVisible";

    // PropertyChangeSupport methods
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    // Getters
    public List<V1Event> getEvents() {
        return Collections.unmodifiableList(events);
    }

    public V1Event getSelectedEvent() {
        return selectedEvent;
    }

    public V1RevenueSplit getRevenueSplit() {
        return revenueSplit;
    }

    public boolean isRegisterOpened() {
        return registerOpened;
    }

    public String getCashierCode() {
        return cashierCode;
    }

    public boolean isLocalMode() {
        return localMode;
    }

    public String getDateFrom() {
        return dateFrom;
    }

    public boolean isCashierButtonEnabled() {
        return cashierButtonEnabled;
    }

    public boolean isDetailFormVisible() {
        return detailFormVisible;
    }

    // Setters with PropertyChangeSupport notification
    public void setEvents(List<V1Event> events) {
        List<V1Event> oldValue = this.events;
        this.events = new ArrayList<>(events);
        pcs.firePropertyChange(PROP_EVENTS, oldValue, this.events);
    }

    public void setSelectedEvent(V1Event selectedEvent) {
        V1Event oldValue = this.selectedEvent;
        this.selectedEvent = selectedEvent;
        pcs.firePropertyChange(PROP_SELECTED_EVENT, oldValue, selectedEvent);
    }

    public void setRevenueSplit(V1RevenueSplit revenueSplit) {
        V1RevenueSplit oldValue = this.revenueSplit;
        this.revenueSplit = revenueSplit;
        pcs.firePropertyChange(PROP_REVENUE_SPLIT, oldValue, revenueSplit);
    }

    public void setRegisterOpened(boolean registerOpened) {
        boolean oldValue = this.registerOpened;
        this.registerOpened = registerOpened;
        pcs.firePropertyChange(PROP_REGISTER_OPENED, oldValue, registerOpened);
    }

    public void setCashierCode(String cashierCode) {
        String oldValue = this.cashierCode;
        this.cashierCode = cashierCode;
        pcs.firePropertyChange(PROP_CASHIER_CODE, oldValue, cashierCode);
    }

    public void setLocalMode(boolean localMode) {
        boolean oldValue = this.localMode;
        this.localMode = localMode;
        pcs.firePropertyChange(PROP_LOCAL_MODE, oldValue, localMode);
    }

    public void setDateFrom(String dateFrom) {
        String oldValue = this.dateFrom;
        this.dateFrom = dateFrom;
        pcs.firePropertyChange(PROP_DATE_FROM, oldValue, dateFrom);
    }

    public void setCashierButtonEnabled(boolean cashierButtonEnabled) {
        boolean oldValue = this.cashierButtonEnabled;
        this.cashierButtonEnabled = cashierButtonEnabled;
        pcs.firePropertyChange(PROP_CASHIER_BUTTON_ENABLED, oldValue, cashierButtonEnabled);
    }

    public void setDetailFormVisible(boolean detailFormVisible) {
        boolean oldValue = this.detailFormVisible;
        this.detailFormVisible = detailFormVisible;
        pcs.firePropertyChange(PROP_DETAIL_FORM_VISIBLE, oldValue, detailFormVisible);
    }

    /**
     * Resets the discovery state to initial values (no events, no selection).
     */
    public void reset() {
        setEvents(new ArrayList<>());
        setSelectedEvent(null);
        setRevenueSplit(null);
        setRegisterOpened(false);
        setCashierCode("");
        setLocalMode(false);
        setDateFrom("");
        setCashierButtonEnabled(true);
        setDetailFormVisible(false);
    }
}
