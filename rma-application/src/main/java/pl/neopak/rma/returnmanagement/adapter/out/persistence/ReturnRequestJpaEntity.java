package pl.neopak.rma.returnmanagement.adapter.out.persistence;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "return_requests", schema = "return_management")
public class ReturnRequestJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "rma_number", nullable = false, unique = true, length = 20)
    private String rmaNumber;

    @Column(name = "order_id", nullable = false, length = 100)
    private String orderId;

    @Column(name = "source_system", nullable = false, length = 30)
    private String sourceSystem;

    @Column(name = "customer_email", nullable = false, length = 255)
    private String customerEmail;

    @Column(name = "customer_name", nullable = false, length = 255)
    private String customerName;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "payment_confirmed", nullable = false)
    private boolean paymentConfirmed;

    @Column(name = "payment_session_id", length = 100)
    private String paymentSessionId;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "sla_deadline")
    private LocalDate slaDeadline;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "returnRequest", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ReturnLineItemJpaEntity> lineItems = new ArrayList<>();

    @OneToMany(mappedBy = "returnRequest", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ShipmentJpaEntity> shipments = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    protected ReturnRequestJpaEntity() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    public String getRmaNumber() { return rmaNumber; }
    public void setRmaNumber(String rmaNumber) { this.rmaNumber = rmaNumber; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isPaymentConfirmed() { return paymentConfirmed; }
    public void setPaymentConfirmed(boolean paymentConfirmed) { this.paymentConfirmed = paymentConfirmed; }

    public String getPaymentSessionId() { return paymentSessionId; }
    public void setPaymentSessionId(String paymentSessionId) { this.paymentSessionId = paymentSessionId; }

    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }

    public LocalDate getSlaDeadline() { return slaDeadline; }
    public void setSlaDeadline(LocalDate slaDeadline) { this.slaDeadline = slaDeadline; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public List<ReturnLineItemJpaEntity> getLineItems() { return lineItems; }
    public void setLineItems(List<ReturnLineItemJpaEntity> lineItems) { this.lineItems = lineItems; }

    public List<ShipmentJpaEntity> getShipments() { return shipments; }
    public void setShipments(List<ShipmentJpaEntity> shipments) { this.shipments = shipments; }
}
