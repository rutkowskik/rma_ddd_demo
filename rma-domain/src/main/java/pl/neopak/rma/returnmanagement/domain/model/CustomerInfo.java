package pl.neopak.rma.returnmanagement.domain.model;

import java.util.Objects;

public final class CustomerInfo {

    private final String email;
    private final String name;

    private CustomerInfo(String email, String name) {
        this.email = email;
        this.name = name;
    }

    public static CustomerInfo of(String email, String name) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Nieprawidlowy email: " + email);
        }
        int atIndex = email.indexOf('@');
        if (atIndex < 0) {
            throw new IllegalArgumentException("Nieprawidlowy email: " + email);
        }
        String afterAt = email.substring(atIndex + 1);
        if (!afterAt.contains(".")) {
            throw new IllegalArgumentException("Nieprawidlowy email: " + email);
        }
        if (atIndex == 0) {
            throw new IllegalArgumentException("Nieprawidlowy email: " + email);
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nieprawidlowe imie: " + name);
        }
        return new CustomerInfo(email, name);
    }

    public CustomerInfo pseudonymize() {
        int atIndex = email.indexOf('@');
        String maskedEmail = email.charAt(0) + "***@" + email.substring(atIndex + 1);

        int spaceIndex = name.indexOf(' ');
        String firstWord = spaceIndex >= 0 ? name.substring(0, spaceIndex) : name;
        String maskedName = firstWord + " ***";

        return new CustomerInfo(maskedEmail, maskedName);
    }

    public String email() {
        return email;
    }

    public String name() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomerInfo)) return false;
        CustomerInfo that = (CustomerInfo) o;
        return Objects.equals(email, that.email) && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(email, name);
    }
}
