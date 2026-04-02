package pl.neopak.rma.returnmanagement.domain.model;

import pl.neopak.rma.returnmanagement.domain.event.BlindReturnRegistered;
import pl.neopak.rma.returnmanagement.domain.event.DomainEvent;
import pl.neopak.rma.returnmanagement.domain.event.RefundDecisionMade;
import pl.neopak.rma.returnmanagement.domain.event.ReturnCompleted;
import pl.neopak.rma.returnmanagement.domain.event.ReturnConditionAssessed;
import pl.neopak.rma.returnmanagement.domain.event.ReturnLabelGenerated;
import pl.neopak.rma.returnmanagement.domain.event.ReturnLabelPaymentRequested;
import pl.neopak.rma.returnmanagement.domain.event.ReturnRejected;
import pl.neopak.rma.returnmanagement.domain.event.ReturnRequestCreated;
import pl.neopak.rma.returnmanagement.domain.event.ReturnShipmentReceived;
import pl.neopak.rma.returnmanagement.domain.exception.InvalidStatusTransitionException;
import pl.neopak.rma.returnmanagement.domain.exception.LabelGenerationBeforePaymentException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ReturnRequest {

    private final ReturnRequestId id;
    private final RmaNumber rmaNumber;
    private final OrderReference orderReference;
    private final CustomerInfo customerInfo;
    private ReturnStatus status;
    private final List<ReturnLineItem> lineItems = new ArrayList<>();
    private final List<Shipment> shipments = new ArrayList<>();
    private boolean paymentConfirmed = false;
    private Instant receivedAt;
    private SlaDeadline slaDeadline;
    private final List<DomainEvent> domainEvents = new ArrayList<>();
    private final Instant createdAt;

    private ReturnRequest(ReturnRequestId id, RmaNumber rmaNumber,
                          OrderReference orderReference, CustomerInfo customerInfo,
                          ReturnStatus status, Instant createdAt) {
        this.id = id;
        this.rmaNumber = rmaNumber;
        this.orderReference = orderReference;
        this.customerInfo = customerInfo;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static ReturnRequest create(RmaNumber rmaNumber, OrderReference orderReference, CustomerInfo customerInfo) {
        var rma = new ReturnRequest(ReturnRequestId.generate(), rmaNumber, orderReference, customerInfo,
                ReturnStatus.PENDING_SHIPMENT, Instant.now());
        rma.domainEvents.add(new ReturnRequestCreated(
                rmaNumber.value(), orderReference.orderId(),
                orderReference.sourceSystem().name(), customerInfo.email(), Instant.now()));
        return rma;
    }

    public static ReturnRequest reconstruct(
            ReturnRequestId id,
            RmaNumber rmaNumber,
            OrderReference orderReference,
            CustomerInfo customerInfo,
            ReturnStatus status,
            boolean paymentConfirmed,
            Instant receivedAt,
            SlaDeadline slaDeadline,
            Instant createdAt,
            List<ReturnLineItem> lineItems,
            List<Shipment> shipments) {
        var rma = new ReturnRequest(id, rmaNumber, orderReference, customerInfo, status, createdAt);
        rma.paymentConfirmed = paymentConfirmed;
        rma.receivedAt = receivedAt;
        rma.slaDeadline = slaDeadline;
        rma.lineItems.addAll(lineItems);
        rma.shipments.addAll(shipments);
        return rma;
    }

    public static ReturnRequest registerBlind(RmaNumber rmaNumber, String parcelDescription) {
        var rma = new ReturnRequest(ReturnRequestId.generate(), rmaNumber,
                new OrderReference("UNKNOWN", SourceSystem.MANUAL),
                CustomerInfo.of("unknown@unknown.com", "Unknown"),
                ReturnStatus.BLIND_RECEIVED, Instant.now());
        rma.domainEvents.add(new BlindReturnRegistered(rmaNumber.value(), parcelDescription, Instant.now()));
        return rma;
    }

    public void addLineItem(String productId, int quantity, ReturnReason reason) {
        lineItems.add(new ReturnLineItem(productId, quantity, reason));
    }

    public void addShipment(PackageDimensions dimensions) {
        shipments.add(new Shipment(dimensions));
    }

    public void confirmPayment(String paymentSessionId) {
        this.paymentConfirmed = true;
        domainEvents.add(new ReturnLabelPaymentRequested(rmaNumber.value(), 0, Instant.now()));
    }

    public void assignLabels(List<String> labelUrls) {
        if (!paymentConfirmed) {
            throw new LabelGenerationBeforePaymentException(rmaNumber.value());
        }
        for (int i = 0; i < labelUrls.size() && i < shipments.size(); i++) {
            shipments.get(i).assignLabel(labelUrls.get(i), "TRK-" + i);
        }
        this.status = status.transitionTo(ReturnStatus.IN_TRANSIT);
        domainEvents.add(new ReturnLabelGenerated(rmaNumber.value(), labelUrls, Instant.now()));
    }

    public void receiveShipment(String trackingNumber, String warehouseWorkerId, Instant receivedAt) {
        shipments.stream()
                .filter(s -> !s.isReceived())
                .findFirst()
                .ifPresent(Shipment::markReceived);
        domainEvents.add(new ReturnShipmentReceived(rmaNumber.value(), trackingNumber, warehouseWorkerId, Instant.now()));
        boolean allReceived = shipments.stream().allMatch(Shipment::isReceived);
        if (allReceived && !shipments.isEmpty()) {
            this.status = status.transitionTo(ReturnStatus.RECEIVED);
            this.receivedAt = receivedAt;
            this.slaDeadline = SlaDeadline.fromReceivedAt(receivedAt);
        }
    }

    public void startVerification() {
        this.status = status.transitionTo(ReturnStatus.VERIFICATION);
    }

    public void assessCondition(List<ConditionAssessment> assessments) {
        if (status != ReturnStatus.VERIFICATION) {
            throw new InvalidStatusTransitionException(status, ReturnStatus.DECISION);
        }
        for (int i = 0; i < assessments.size() && i < lineItems.size(); i++) {
            lineItems.get(i).assess(assessments.get(i));
        }
        this.status = status.transitionTo(ReturnStatus.DECISION);
        domainEvents.add(new ReturnConditionAssessed(rmaNumber.value(),
                assessments.isEmpty() ? "NONE" : assessments.get(0).name(), Instant.now()));
    }

    public void makeRefundDecision(RefundDecision decision, int refundAmountGrosze, String decidedByUserId) {
        if (status != ReturnStatus.DECISION) {
            throw new InvalidStatusTransitionException(status, ReturnStatus.AWAITING_REFUND);
        }
        switch (decision) {
            case REFUND_AND_RETURN -> {
                this.status = status.transitionTo(ReturnStatus.AWAITING_REFUND);
                domainEvents.add(new RefundDecisionMade(rmaNumber.value(), decision.name(), refundAmountGrosze, Instant.now()));
            }
            case REFUND_AND_DISPOSE -> {
                this.status = status.transitionTo(ReturnStatus.REFUND_AND_DISPOSE);
                domainEvents.add(new RefundDecisionMade(rmaNumber.value(), decision.name(), refundAmountGrosze, Instant.now()));
            }
            case REJECTION -> {
                this.status = status.transitionTo(ReturnStatus.REJECTED);
                domainEvents.add(new ReturnRejected(rmaNumber.value(), "Odrzucenie przez " + decidedByUserId, Instant.now()));
            }
        }
    }

    public void complete() {
        this.status = status.transitionTo(ReturnStatus.COMPLETED);
        domainEvents.add(new ReturnCompleted(rmaNumber.value(), Instant.now()));
    }

    public List<DomainEvent> pullEvents() {
        var events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    public ReturnRequestId id() {
        return id;
    }

    public RmaNumber rmaNumber() {
        return rmaNumber;
    }

    public OrderReference orderReference() {
        return orderReference;
    }

    public CustomerInfo customerInfo() {
        return customerInfo;
    }

    public Instant receivedAt() {
        return receivedAt;
    }

    public ReturnStatus status() {
        return status;
    }

    public boolean isPaymentConfirmed() {
        return paymentConfirmed;
    }

    public Optional<SlaDeadline> slaDeadline() {
        return Optional.ofNullable(slaDeadline);
    }

    public List<ReturnLineItem> lineItems() {
        return Collections.unmodifiableList(lineItems);
    }

    public List<Shipment> shipments() {
        return Collections.unmodifiableList(shipments);
    }

    public Instant createdAt() {
        return createdAt;
    }
}
