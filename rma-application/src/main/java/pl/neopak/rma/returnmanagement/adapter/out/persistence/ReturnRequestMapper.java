package pl.neopak.rma.returnmanagement.adapter.out.persistence;

import org.springframework.stereotype.Component;
import pl.neopak.rma.returnmanagement.domain.model.*;

import java.util.UUID;

@Component
class ReturnRequestMapper {

    ReturnRequestJpaEntity toJpa(ReturnRequest domain) {
        var entity = new ReturnRequestJpaEntity();
        entity.setId(domain.id().value());
        entity.setRmaNumber(domain.rmaNumber().value());
        entity.setOrderId(domain.orderReference().orderId());
        entity.setSourceSystem(domain.orderReference().sourceSystem().name());
        entity.setCustomerEmail(domain.customerInfo().email());
        entity.setCustomerName(domain.customerInfo().name());
        entity.setStatus(domain.status().name());
        entity.setPaymentConfirmed(domain.isPaymentConfirmed());
        entity.setReceivedAt(domain.receivedAt());
        entity.setSlaDeadline(domain.slaDeadline().map(SlaDeadline::asLocalDate).orElse(null));
        entity.setCreatedAt(domain.createdAt());

        var lineItems = domain.lineItems().stream()
                .map(item -> toLineItemJpa(item, entity))
                .toList();
        entity.getLineItems().clear();
        entity.getLineItems().addAll(lineItems);

        var shipments = domain.shipments().stream()
                .map(s -> toShipmentJpa(s, entity))
                .toList();
        entity.getShipments().clear();
        entity.getShipments().addAll(shipments);

        return entity;
    }

    ReturnRequest toDomain(ReturnRequestJpaEntity entity) {
        var lineItems = entity.getLineItems().stream()
                .map(this::toLineItemDomain)
                .toList();

        var shipments = entity.getShipments().stream()
                .map(this::toShipmentDomain)
                .toList();

        var slaDeadline = entity.getSlaDeadline() != null
                ? SlaDeadline.of(entity.getSlaDeadline())
                : null;

        return ReturnRequest.reconstruct(
                ReturnRequestId.of(entity.getId()),
                RmaNumber.of(entity.getRmaNumber()),
                new OrderReference(entity.getOrderId(), SourceSystem.valueOf(entity.getSourceSystem())),
                CustomerInfo.of(entity.getCustomerEmail(), entity.getCustomerName()),
                ReturnStatus.valueOf(entity.getStatus()),
                entity.isPaymentConfirmed(),
                entity.getReceivedAt(),
                slaDeadline,
                entity.getCreatedAt(),
                lineItems,
                shipments
        );
    }

    private ReturnLineItemJpaEntity toLineItemJpa(ReturnLineItem item, ReturnRequestJpaEntity parent) {
        var entity = new ReturnLineItemJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setReturnRequest(parent);
        entity.setProductId(item.getProductId());
        entity.setQuantity(item.getQuantity());
        entity.setReturnReason(item.getReason().name());
        entity.setConditionAssessment(
                item.getConditionAssessment() != null ? item.getConditionAssessment().name() : null);
        return entity;
    }

    private ReturnLineItem toLineItemDomain(ReturnLineItemJpaEntity entity) {
        return ReturnLineItem.reconstruct(
                entity.getProductId(),
                entity.getQuantity(),
                ReturnReason.valueOf(entity.getReturnReason()),
                entity.getConditionAssessment() != null
                        ? ConditionAssessment.valueOf(entity.getConditionAssessment())
                        : null
        );
    }

    private ShipmentJpaEntity toShipmentJpa(Shipment shipment, ReturnRequestJpaEntity parent) {
        var entity = new ShipmentJpaEntity();
        entity.setId(UUID.fromString(shipment.getShipmentId()));
        entity.setReturnRequest(parent);
        entity.setWeightKg(shipment.getDimensions().getWeightKg());
        entity.setLengthCm(shipment.getDimensions().getLengthCm());
        entity.setWidthCm(shipment.getDimensions().getWidthCm());
        entity.setHeightCm(shipment.getDimensions().getHeightCm());
        entity.setLabelUrl(shipment.getLabelUrl());
        entity.setTrackingNumber(shipment.getTrackingNumber());
        entity.setReceived(shipment.isReceived());
        return entity;
    }

    private Shipment toShipmentDomain(ShipmentJpaEntity entity) {
        return Shipment.reconstruct(
                entity.getId().toString(),
                PackageDimensions.of(entity.getWeightKg(), entity.getLengthCm(),
                        entity.getWidthCm(), entity.getHeightCm()),
                entity.getLabelUrl(),
                entity.getTrackingNumber(),
                entity.isReceived()
        );
    }
}
