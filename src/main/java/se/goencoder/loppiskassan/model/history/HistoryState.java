package se.goencoder.loppiskassan.model.history;

import se.goencoder.loppiskassan.V1SoldItem;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.*;

/**
 * Presentation state for the History tab.
 * <p>
 * This model holds all UI-relevant data for the history screen:
 * sold items (all and filtered), filter criteria, summary counts,
 * and button states.
 * <p>
 * Observability: Uses {@link PropertyChangeSupport} to notify listeners
 * when properties change. Views can register listeners to auto-update UI.
 */
public class HistoryState {

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    // Core data
    private List<V1SoldItem> allItems = new ArrayList<>();
    private List<V1SoldItem> filteredItems = new ArrayList<>();
    private int itemCount = 0;
    private int totalSum = 0;

    // Filter criteria
    private String paidFilter = null;       // "true", "false", or null (all)
    private String sellerFilter = null;      // seller ID string or null (all)
    private String paymentMethodFilter = null; // payment method name or null (all)

    // Distinct sellers for dropdown population
    private Set<String> distinctSellers = new HashSet<>();

    // Button states
    private boolean payoutEnabled = false;
    private boolean archiveEnabled = false;

    // Property name constants
    public static final String PROP_ALL_ITEMS = "allItems";
    public static final String PROP_FILTERED_ITEMS = "filteredItems";
    public static final String PROP_ITEM_COUNT = "itemCount";
    public static final String PROP_TOTAL_SUM = "totalSum";
    public static final String PROP_PAID_FILTER = "paidFilter";
    public static final String PROP_SELLER_FILTER = "sellerFilter";
    public static final String PROP_PAYMENT_METHOD_FILTER = "paymentMethodFilter";
    public static final String PROP_DISTINCT_SELLERS = "distinctSellers";
    public static final String PROP_PAYOUT_ENABLED = "payoutEnabled";
    public static final String PROP_ARCHIVE_ENABLED = "archiveEnabled";

    // PropertyChangeSupport methods
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    // Getters
    public List<V1SoldItem> getAllItems() {
        return Collections.unmodifiableList(allItems);
    }

    public List<V1SoldItem> getFilteredItems() {
        return Collections.unmodifiableList(filteredItems);
    }

    public int getItemCount() {
        return itemCount;
    }

    public int getTotalSum() {
        return totalSum;
    }

    public String getPaidFilter() {
        return paidFilter;
    }

    public String getSellerFilter() {
        return sellerFilter;
    }

    public String getPaymentMethodFilter() {
        return paymentMethodFilter;
    }

    public Set<String> getDistinctSellers() {
        return Collections.unmodifiableSet(distinctSellers);
    }

    public boolean isPayoutEnabled() {
        return payoutEnabled;
    }

    public boolean isArchiveEnabled() {
        return archiveEnabled;
    }

    // Setters with PropertyChangeSupport notification
    public void setAllItems(List<V1SoldItem> allItems) {
        List<V1SoldItem> oldValue = this.allItems;
        this.allItems = new ArrayList<>(allItems);
        pcs.firePropertyChange(PROP_ALL_ITEMS, oldValue, this.allItems);
    }

    public void setFilteredItems(List<V1SoldItem> filteredItems) {
        List<V1SoldItem> oldValue = this.filteredItems;
        this.filteredItems = new ArrayList<>(filteredItems);
        pcs.firePropertyChange(PROP_FILTERED_ITEMS, oldValue, this.filteredItems);
    }

    public void setItemCount(int itemCount) {
        int oldValue = this.itemCount;
        this.itemCount = itemCount;
        pcs.firePropertyChange(PROP_ITEM_COUNT, oldValue, itemCount);
    }

    public void setTotalSum(int totalSum) {
        int oldValue = this.totalSum;
        this.totalSum = totalSum;
        pcs.firePropertyChange(PROP_TOTAL_SUM, oldValue, totalSum);
    }

    public void setPaidFilter(String paidFilter) {
        String oldValue = this.paidFilter;
        this.paidFilter = paidFilter;
        pcs.firePropertyChange(PROP_PAID_FILTER, oldValue, paidFilter);
    }

    public void setSellerFilter(String sellerFilter) {
        String oldValue = this.sellerFilter;
        this.sellerFilter = sellerFilter;
        pcs.firePropertyChange(PROP_SELLER_FILTER, oldValue, sellerFilter);
    }

    public void setPaymentMethodFilter(String paymentMethodFilter) {
        String oldValue = this.paymentMethodFilter;
        this.paymentMethodFilter = paymentMethodFilter;
        pcs.firePropertyChange(PROP_PAYMENT_METHOD_FILTER, oldValue, paymentMethodFilter);
    }

    public void setDistinctSellers(Set<String> distinctSellers) {
        Set<String> oldValue = this.distinctSellers;
        this.distinctSellers = new HashSet<>(distinctSellers);
        pcs.firePropertyChange(PROP_DISTINCT_SELLERS, oldValue, this.distinctSellers);
    }

    public void setPayoutEnabled(boolean payoutEnabled) {
        boolean oldValue = this.payoutEnabled;
        this.payoutEnabled = payoutEnabled;
        pcs.firePropertyChange(PROP_PAYOUT_ENABLED, oldValue, payoutEnabled);
    }

    public void setArchiveEnabled(boolean archiveEnabled) {
        boolean oldValue = this.archiveEnabled;
        this.archiveEnabled = archiveEnabled;
        pcs.firePropertyChange(PROP_ARCHIVE_ENABLED, oldValue, archiveEnabled);
    }

    /**
     * Resets all items and filters to initial state.
     */
    public void reset() {
        setAllItems(new ArrayList<>());
        setFilteredItems(new ArrayList<>());
        setItemCount(0);
        setTotalSum(0);
        setPaidFilter(null);
        setSellerFilter(null);
        setPaymentMethodFilter(null);
        setDistinctSellers(new HashSet<>());
        setPayoutEnabled(false);
        setArchiveEnabled(false);
    }
}
