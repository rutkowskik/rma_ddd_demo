package pl.neopak.rma.returnmanagement.adapter.out.persistence;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "return_line_items", schema = "return_management")
public class ReturnLineItemJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_request_id", nullable = false)
    private ReturnRequestJpaEntity returnRequest;

    @Column(name = "product_id", nullable = false, length = 100)
    private String productId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "return_reason", nullable = false, length = 30)
    private String returnReason;

    @Column(name = "condition_assessment", length = 30)
    private String conditionAssessment;

    protected ReturnLineItemJpaEntity() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public ReturnRequestJpaEntity getReturnRequest() { return returnRequest; }
    public void setReturnRequest(ReturnRequestJpaEntity returnRequest) { this.returnRequest = returnRequest; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public String getReturnReason() { return returnReason; }
    public void setReturnReason(String returnReason) { this.returnReason = returnReason; }

    public String getConditionAssessment() { return conditionAssessment; }
    public void setConditionAssessment(String conditionAssessment) { this.conditionAssessment = conditionAssessment; }
}
