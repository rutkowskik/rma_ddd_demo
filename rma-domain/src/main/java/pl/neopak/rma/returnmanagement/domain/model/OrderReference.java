package pl.neopak.rma.returnmanagement.domain.model;

public record OrderReference(String orderId, SourceSystem sourceSystem) {

    public OrderReference {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId nie moze byc null ani pusty");
        }
        if (sourceSystem == null) {
            throw new IllegalArgumentException("sourceSystem nie moze byc null");
        }
    }
}
