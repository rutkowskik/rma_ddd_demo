package pl.neopak.rma.returnmanagement.domain.model;

public class ReturnLineItem {

    private final String productId;
    private final int quantity;
    private final ReturnReason reason;
    private ConditionAssessment conditionAssessment;

    ReturnLineItem(String productId, int quantity, ReturnReason reason) {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId nie moze byc blank");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity musi byc wieksze od 0, bylo: " + quantity);
        }
        if (reason == null) {
            throw new IllegalArgumentException("reason nie moze byc null");
        }
        this.productId = productId;
        this.quantity = quantity;
        this.reason = reason;
    }

    public static ReturnLineItem reconstruct(String productId, int quantity,
                                               ReturnReason reason, ConditionAssessment conditionAssessment) {
        var item = new ReturnLineItem(productId, quantity, reason);
        item.conditionAssessment = conditionAssessment;
        return item;
    }

    public void assess(ConditionAssessment condition) {
        if (this.conditionAssessment != null) {
            throw new IllegalStateException("Ocena stanu juz zostala ustawiona dla produktu: " + productId);
        }
        this.conditionAssessment = condition;
    }

    public String getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public ReturnReason getReason() {
        return reason;
    }

    public ConditionAssessment getConditionAssessment() {
        return conditionAssessment;
    }
}
