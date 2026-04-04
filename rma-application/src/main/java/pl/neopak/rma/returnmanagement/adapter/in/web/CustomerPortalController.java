package pl.neopak.rma.returnmanagement.adapter.in.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pl.neopak.rma.returnmanagement.domain.exception.RmaNotFoundException;
import pl.neopak.rma.returnmanagement.domain.model.CustomerInfo;
import pl.neopak.rma.returnmanagement.domain.model.OrderReference;
import pl.neopak.rma.returnmanagement.domain.model.SourceSystem;
import pl.neopak.rma.returnmanagement.port.in.CreateReturnRequestCommand;
import pl.neopak.rma.returnmanagement.port.in.CreateReturnRequestUseCase;
import pl.neopak.rma.returnmanagement.port.in.QueryReturnRequestUseCase;
import pl.neopak.rma.returnmanagement.port.out.PaymentGateway;
import pl.neopak.rma.returnmanagement.port.out.PhotoStoragePort;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/returns")
public class CustomerPortalController {

    private final CreateReturnRequestUseCase createReturnRequestUseCase;
    private final QueryReturnRequestUseCase queryReturnRequestUseCase;
    private final PaymentGateway paymentGateway;
    private final PhotoStoragePort photoStoragePort;

    public CustomerPortalController(CreateReturnRequestUseCase createReturnRequestUseCase,
                                    QueryReturnRequestUseCase queryReturnRequestUseCase,
                                    PaymentGateway paymentGateway,
                                    PhotoStoragePort photoStoragePort) {
        this.createReturnRequestUseCase = createReturnRequestUseCase;
        this.queryReturnRequestUseCase = queryReturnRequestUseCase;
        this.paymentGateway = paymentGateway;
        this.photoStoragePort = photoStoragePort;
    }

    // ---- DTOs ---------------------------------------------------------------

    record CreateReturnRequest(String orderId, String sourceSystem, String customerEmail, String customerName) {}

    record CreateReturnResponse(String rmaNumber) {}

    record ReturnStatusResponse(String rmaNumber, Object details) {}

    record InitPaymentRequest(int amountGrosze, String customerEmail) {}

    record InitPaymentResponse(String redirectUrl) {}

    record PhotoUploadResponse(List<String> urls) {}

    // ---- Endpoints ----------------------------------------------------------

    @PostMapping
    public ResponseEntity<CreateReturnResponse> createReturn(@RequestBody CreateReturnRequest request) {
        var orderReference = new OrderReference(request.orderId(), SourceSystem.valueOf(request.sourceSystem()));
        var customerInfo = CustomerInfo.of(request.customerEmail(), request.customerName());
        var command = new CreateReturnRequestCommand(orderReference, customerInfo);
        var rmaNumber = createReturnRequestUseCase.create(command);
        return ResponseEntity.status(201).body(new CreateReturnResponse(rmaNumber.value()));
    }

    @GetMapping("/{rma}")
    public ResponseEntity<ReturnStatusResponse> getReturn(@PathVariable String rma) {
        return queryReturnRequestUseCase.findByRmaNumber(rma)
                .map(details -> ResponseEntity.ok(new ReturnStatusResponse(rma, details)))
                .orElseThrow(() -> new RmaNotFoundException(rma));
    }

    @PostMapping("/{rma}/payment")
    public ResponseEntity<InitPaymentResponse> initiatePayment(@PathVariable String rma,
                                                               @RequestBody InitPaymentRequest request) {
        String redirectUrl = paymentGateway.createPaymentSession(rma, request.amountGrosze(), request.customerEmail());
        return ResponseEntity.ok(new InitPaymentResponse(redirectUrl));
    }

    @PostMapping("/{rma}/photos")
    public ResponseEntity<PhotoUploadResponse> uploadPhotos(@PathVariable String rma,
                                                            @RequestParam("files") List<MultipartFile> files) throws IOException {
        List<String> urls = new ArrayList<>();
        for (MultipartFile file : files) {
            String url = photoStoragePort.store(file.getBytes(), rma, file.getOriginalFilename());
            urls.add(url);
        }
        return ResponseEntity.ok(new PhotoUploadResponse(urls));
    }

    // ---- Exception handler --------------------------------------------------

    @ExceptionHandler(RmaNotFoundException.class)
    public ResponseEntity<Void> handleRmaNotFound(RmaNotFoundException ex) {
        return ResponseEntity.notFound().build();
    }
}
