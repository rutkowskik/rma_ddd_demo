package pl.neopak.rma.returnmanagement.domain.model;

import java.util.Objects;

public final class ShippingCostSplit {

    private final int totalGrosze;
    private final int customerSharePercent;

    private ShippingCostSplit(int totalGrosze, int customerSharePercent) {
        this.totalGrosze = totalGrosze;
        this.customerSharePercent = customerSharePercent;
    }

    public static ShippingCostSplit of(int totalGrosze, int customerSharePercent) {
        if (totalGrosze < 0) {
            throw new IllegalArgumentException("totalGrosze must be >= 0, was: " + totalGrosze);
        }
        if (customerSharePercent < 0 || customerSharePercent > 100) {
            throw new IllegalArgumentException(
                    "customerSharePercent must be between 0 and 100, was: " + customerSharePercent);
        }
        return new ShippingCostSplit(totalGrosze, customerSharePercent);
    }

    public int getTotalGrosze() {
        return totalGrosze;
    }

    public int getCustomerSharePercent() {
        return customerSharePercent;
    }

    public int customerShare() {
        return (int) Math.ceil(totalGrosze * customerSharePercent / 100.0);
    }

    public int storeShare() {
        return totalGrosze - customerShare();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ShippingCostSplit)) return false;
        ShippingCostSplit that = (ShippingCostSplit) o;
        return totalGrosze == that.totalGrosze && customerSharePercent == that.customerSharePercent;
    }

    @Override
    public int hashCode() {
        return Objects.hash(totalGrosze, customerSharePercent);
    }

    @Override
    public String toString() {
        return "ShippingCostSplit{totalGrosze=" + totalGrosze
                + ", customerSharePercent=" + customerSharePercent
                + ", customerShare=" + customerShare()
                + ", storeShare=" + storeShare()
                + '}';
    }
}
