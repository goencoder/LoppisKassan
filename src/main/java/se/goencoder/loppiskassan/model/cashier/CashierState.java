package se.goencoder.loppiskassan.model.cashier;

import se.goencoder.loppiskassan.V1SoldItem;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Presentation state for the Cashier tab.
 * <p>
 * This model holds all UI-relevant data for the cashier screen:
 * current items in the cart, totals, formatted strings, and button states.
 * <p>
 * Observability: Uses {@link PropertyChangeSupport} to notify listeners
 * when properties change. Views can register listeners to auto-update UI.
 */
public class CashierState {

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    // Core data
    private List<V1SoldItem> items = new ArrayList<>();
    private int totalSum = 0;
    private int itemCount = 0;
    private int paidAmount = 0;
    private int change = 0;

    // Pre-formatted display strings
    private String formattedSum = "";
    private String formattedChange = "";

    // Button states
    private boolean checkoutEnabled = false;

    // Property name constants for listeners
    public static final String PROP_ITEMS = "items";
    public static final String PROP_TOTAL_SUM = "totalSum";
    public static final String PROP_ITEM_COUNT = "itemCount";
    public static final String PROP_PAID_AMOUNT = "paidAmount";
    public static final String PROP_CHANGE = "change";
    public static final String PROP_FORMATTED_SUM = "formattedSum";
    public static final String PROP_FORMATTED_CHANGE = "formattedChange";
    public static final String PROP_CHECKOUT_ENABLED = "checkoutEnabled";

    // PropertyChangeSupport methods
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    // Getters
    public List<V1SoldItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public int getTotalSum() {
        return totalSum;
    }

    public int getItemCount() {
        return itemCount;
    }

    public int getPaidAmount() {
        return paidAmount;
    }

    public int getChange() {
        return change;
    }

    public String getFormattedSum() {
        return formattedSum;
    }

    public String getFormattedChange() {
        return formattedChange;
    }

    public boolean isCheckoutEnabled() {
        return checkoutEnabled;
    }

    // Setters with PropertyChangeSupport notification
    public void setItems(List<V1SoldItem> items) {
        List<V1SoldItem> oldValue = this.items;
        this.items = new ArrayList<>(items);
        pcs.firePropertyChange(PROP_ITEMS, oldValue, this.items);
    }

    public void setTotalSum(int totalSum) {
        int oldValue = this.totalSum;
        this.totalSum = totalSum;
        pcs.firePropertyChange(PROP_TOTAL_SUM, oldValue, totalSum);
    }

    public void setItemCount(int itemCount) {
        int oldValue = this.itemCount;
        this.itemCount = itemCount;
        pcs.firePropertyChange(PROP_ITEM_COUNT, oldValue, itemCount);
    }

    public void setPaidAmount(int paidAmount) {
        int oldValue = this.paidAmount;
        this.paidAmount = paidAmount;
        pcs.firePropertyChange(PROP_PAID_AMOUNT, oldValue, paidAmount);
    }

    public void setChange(int change) {
        int oldValue = this.change;
        this.change = change;
        pcs.firePropertyChange(PROP_CHANGE, oldValue, change);
    }

    public void setFormattedSum(String formattedSum) {
        String oldValue = this.formattedSum;
        this.formattedSum = formattedSum;
        pcs.firePropertyChange(PROP_FORMATTED_SUM, oldValue, formattedSum);
    }

    public void setFormattedChange(String formattedChange) {
        String oldValue = this.formattedChange;
        this.formattedChange = formattedChange;
        pcs.firePropertyChange(PROP_FORMATTED_CHANGE, oldValue, formattedChange);
    }

    public void setCheckoutEnabled(boolean checkoutEnabled) {
        boolean oldValue = this.checkoutEnabled;
        this.checkoutEnabled = checkoutEnabled;
        pcs.firePropertyChange(PROP_CHECKOUT_ENABLED, oldValue, checkoutEnabled);
    }

    /**
     * Resets the state to initial values (empty cart, zero totals).
     * Useful for clearing the cashier UI after checkout or cancellation.
     */
    public void reset() {
        setItems(new ArrayList<>());
        setTotalSum(0);
        setItemCount(0);
        setPaidAmount(0);
        setChange(0);
        setFormattedSum("");
        setFormattedChange("");
        setCheckoutEnabled(false);
    }
}
