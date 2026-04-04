package pl.neopak.rma.returnmanagement.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import pl.neopak.rma.config.SecurityConfig;
import pl.neopak.rma.returnmanagement.domain.model.ConditionAssessment;
import pl.neopak.rma.returnmanagement.domain.model.RefundDecision;
import pl.neopak.rma.returnmanagement.domain.model.ReturnRequest;
import pl.neopak.rma.returnmanagement.domain.model.ReturnStatus;
import pl.neopak.rma.returnmanagement.port.in.AssessConditionUseCase;
import pl.neopak.rma.returnmanagement.port.in.MakeRefundDecisionUseCase;
import pl.neopak.rma.returnmanagement.port.in.ReceiveShipmentUseCase;
import pl.neopak.rma.returnmanagement.port.out.ReturnRequestRepository;
import pl.neopak.rma.security.JwtAuthFilter;
import pl.neopak.rma.security.JwtTokenProvider;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WarehouseController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtTokenProvider.class})
@TestPropertySource(properties = {
        "jwt.secret=rma-super-secret-key-that-is-at-least-256-bits-long-for-hs256-algorithm",
        "jwt.expiration-ms=86400000"
})
class WarehouseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReceiveShipmentUseCase receiveShipmentUseCase;

    @MockBean
    private AssessConditionUseCase assessConditionUseCase;

    @MockBean
    private MakeRefundDecisionUseCase makeRefundDecisionUseCase;

    @MockBean
    private ReturnRequestRepository returnRequestRepository;

    @Test
    void receiveShipment_withWarehouseWorkerRole_returns200() throws Exception {
        String token = jwtTokenProvider.generateToken("worker@neopak.pl", "WAREHOUSE_WORKER");
        String body = objectMapper.writeValueAsString(new WarehouseController.ReceiveShipmentRequest("TRK-001"));

        mockMvc.perform(post("/api/v1/warehouse/shipments/receive")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void receiveShipment_withCustomerRole_returns403() throws Exception {
        String token = jwtTokenProvider.generateToken("customer@neopak.pl", "CUSTOMER");
        String body = objectMapper.writeValueAsString(new WarehouseController.ReceiveShipmentRequest("TRK-001"));

        mockMvc.perform(post("/api/v1/warehouse/shipments/receive")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void receiveShipment_withoutToken_returns401() throws Exception {
        String body = objectMapper.writeValueAsString(new WarehouseController.ReceiveShipmentRequest("TRK-001"));

        mockMvc.perform(post("/api/v1/warehouse/shipments/receive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void assessCondition_withWarehouseWorkerRole_returns200() throws Exception {
        String token = jwtTokenProvider.generateToken("worker@neopak.pl", "WAREHOUSE_WORKER");
        var request = new WarehouseController.AssessConditionRequest(List.of(ConditionAssessment.NEW));
        String body = objectMapper.writeValueAsString(request);

        mockMvc.perform(put("/api/v1/warehouse/returns/ZWR-00001/condition")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void makeDecision_withWarehouseManagerRole_returns200() throws Exception {
        String token = jwtTokenProvider.generateToken("manager@neopak.pl", "WAREHOUSE_MANAGER");
        var request = new WarehouseController.MakeDecisionRequest(RefundDecision.REFUND_AND_RETURN, 5000);
        String body = objectMapper.writeValueAsString(request);

        mockMvc.perform(put("/api/v1/warehouse/returns/ZWR-00001/decision")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void listReturns_withWarehouseWorkerRole_returns200WithEmptyList() throws Exception {
        String token = jwtTokenProvider.generateToken("worker@neopak.pl", "WAREHOUSE_WORKER");
        when(returnRequestRepository.findByStatuses(anyList())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/warehouse/returns")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
