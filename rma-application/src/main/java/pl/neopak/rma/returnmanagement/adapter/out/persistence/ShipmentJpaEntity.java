package pl.neopak.rma.returnmanagement.adapter.out.persistence;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "shipments", schema = "return_management")
public class ShipmentJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_request_id", nullable = false)
    private ReturnRequestJpaEntity returnRequest;

    @Column(name = "weight_kg", nullable = false)
    private int weightKg;

    @Column(name = "length_cm", nullable = false)
    private int lengthCm;

    @Column(name = "width_cm", nullable = false)
    private int widthCm;

    @Column(name = "height_cm", nullable = false)
    private int heightCm;

    @Column(name = "label_url", length = 500)
    private String labelUrl;

    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;

    @Column(name = "received", nullable = false)
    private boolean received;

    protected ShipmentJpaEntity() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public ReturnRequestJpaEntity getReturnRequest() { return returnRequest; }
    public void setReturnRequest(ReturnRequestJpaEntity returnRequest) { this.returnRequest = returnRequest; }

    public int getWeightKg() { return weightKg; }
    public void setWeightKg(int weightKg) { this.weightKg = weightKg; }

    public int getLengthCm() { return lengthCm; }
    public void setLengthCm(int lengthCm) { this.lengthCm = lengthCm; }

    public int getWidthCm() { return widthCm; }
    public void setWidthCm(int widthCm) { this.widthCm = widthCm; }

    public int getHeightCm() { return heightCm; }
    public void setHeightCm(int heightCm) { this.heightCm = heightCm; }

    public String getLabelUrl() { return labelUrl; }
    public void setLabelUrl(String labelUrl) { this.labelUrl = labelUrl; }

    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }

    public boolean isReceived() { return received; }
    public void setReceived(boolean received) { this.received = received; }
}
