package pl.neopak.rma.returnmanagement.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

public final class RmaNumber {

    private static final Pattern PATTERN = Pattern.compile("ZWR-\\d{5}");

    private final String value;

    private RmaNumber(String value) {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Nieprawidlowy format numeru RMA: " + value);
        }
        this.value = value;
    }

    public static RmaNumber of(String value) {
        return new RmaNumber(value);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RmaNumber)) return false;
        RmaNumber other = (RmaNumber) o;
        return Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
