package pl.neopak.rma.returnmanagement.domain.model;

import java.util.UUID;

public record ReturnRequestId(UUID value) {

    public static ReturnRequestId generate() {
        return new ReturnRequestId(UUID.randomUUID());
    }

    public static ReturnRequestId of(UUID value) {
        return new ReturnRequestId(value);
    }
}
