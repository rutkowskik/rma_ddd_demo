package pl.neopak.rma.returnmanagement.domain.model;

import java.util.UUID;

public class Shipment {

    private final String shipmentId;
    private final PackageDimensions dimensions;
    private String labelUrl;
    private String trackingNumber;
    private boolean received = false;

    Shipment(PackageDimensions dimensions) {
        if (dimensions == null) {
            throw new IllegalArgumentException("dimensions nie moze byc null");
        }
        this.shipmentId = UUID.randomUUID().toString();
        this.dimensions = dimensions;
    }

    private Shipment(String shipmentId, PackageDimensions dimensions,
                     String labelUrl, String trackingNumber, boolean received) {
        this.shipmentId = shipmentId;
        this.dimensions = dimensions;
        this.labelUrl = labelUrl;
        this.trackingNumber = trackingNumber;
        this.received = received;
    }

    public static Shipment reconstruct(String shipmentId, PackageDimensions dimensions,
                                       String labelUrl, String trackingNumber, boolean received) {
        return new Shipment(shipmentId, dimensions, labelUrl, trackingNumber, received);
    }

    public void assignLabel(String labelUrl, String trackingNumber) {
        this.labelUrl = labelUrl;
        this.trackingNumber = trackingNumber;
    }

    public void markReceived() {
        this.received = true;
    }

    public boolean isReceived() {
        return received;
    }

    public String getShipmentId() {
        return shipmentId;
    }

    public PackageDimensions getDimensions() {
        return dimensions;
    }

    public String getLabelUrl() {
        return labelUrl;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }
}
