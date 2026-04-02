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
