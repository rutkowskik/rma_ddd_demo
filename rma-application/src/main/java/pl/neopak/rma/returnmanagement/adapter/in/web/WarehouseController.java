package pl.neopak.rma.returnmanagement.adapter.in.web;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pl.neopak.rma.returnmanagement.domain.model.ConditionAssessment;
import pl.neopak.rma.returnmanagement.domain.model.RefundDecision;
import pl.neopak.rma.returnmanagement.domain.model.ReturnRequest;
import pl.neopak.rma.returnmanagement.domain.model.ReturnStatus;
import pl.neopak.rma.returnmanagement.port.in.AssessConditionUseCase;
import pl.neopak.rma.returnmanagement.port.in.MakeRefundDecisionUseCase;
import pl.neopak.rma.returnmanagement.port.in.ReceiveShipmentUseCase;
import pl.neopak.rma.returnmanagement.port.out.ReturnRequestRepository;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/warehouse")
public class WarehouseController {

    private final ReceiveShipmentUseCase receiveShipmentUseCase;
    private final AssessConditionUseCase assessConditionUseCase;
    private final MakeRefundDecisionUseCase makeRefundDecisionUseCase;
    private final ReturnRequestRepository returnRequestRepository;

    public WarehouseController(ReceiveShipmentUseCase receiveShipmentUseCase,
                               AssessConditionUseCase assessConditionUseCase,
                               MakeRefundDecisionUseCase makeRefundDecisionUseCase,
                               ReturnRequestRepository returnRequestRepository) {
        this.receiveShipmentUseCase = receiveShipmentUseCase;
        this.assessConditionUseCase = assessConditionUseCase;
        this.makeRefundDecisionUseCase = makeRefundDecisionUseCase;
        this.returnRequestRepository = returnRequestRepository;
    }

    record ReceiveShipmentRequest(String trackingNumber) {}

    record ReceiveShipmentResponse(String trackingNumber, String receivedBy, Instant receivedAt) {}

    record AssessConditionRequest(List<ConditionAssessment> assessments) {}

    record MakeDecisionRequest(RefundDecision decision, int refundAmountGrosze) {}

    record ReturnSummary(String rmaNumber, String status, Instant createdAt) {}

    @PostMapping("/shipments/receive")
    @PreAuthorize("hasRole('WAREHOUSE_WORKER')")
    public ResponseEntity<ReceiveShipmentResponse> receiveShipment(
            @RequestBody ReceiveShipmentRequest request,
            @AuthenticationPrincipal String workerId) {
        receiveShipmentUseCase.receiveShipment(request.trackingNumber(), workerId);
        return ResponseEntity.ok(new ReceiveShipmentResponse(request.trackingNumber(), workerId, Instant.now()));
    }

    @PutMapping("/returns/{rma}/condition")
    @PreAuthorize("hasRole('WAREHOUSE_WORKER')")
    public ResponseEntity<Void> assessCondition(
            @PathVariable String rma,
            @RequestBody AssessConditionRequest request,
            @AuthenticationPrincipal String assessedBy) {
        assessConditionUseCase.assessCondition(rma, request.assessments(), assessedBy);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/returns/{rma}/decision")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public ResponseEntity<Void> makeDecision(
            @PathVariable String rma,
            @RequestBody MakeDecisionRequest request,
            @AuthenticationPrincipal String decidedBy) {
        makeRefundDecisionUseCase.makeDecision(rma, request.decision(), request.refundAmountGrosze(), decidedBy);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/returns")
    @PreAuthorize("hasRole('WAREHOUSE_WORKER')")
    public ResponseEntity<List<ReturnSummary>> listReturns(
            @RequestParam(required = false) String status) {
        List<ReturnStatus> statuses = (status != null)
                ? List.of(ReturnStatus.valueOf(status))
                : List.of(ReturnStatus.values());
        List<ReturnSummary> summaries = returnRequestRepository.findByStatuses(statuses).stream()
                .map(r -> new ReturnSummary(r.rmaNumber().value(), r.status().name(), r.createdAt()))
                .toList();
        return ResponseEntity.ok(summaries);
    }
}
