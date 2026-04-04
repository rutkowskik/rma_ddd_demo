package pl.neopak.rma.returnmanagement.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import pl.neopak.rma.config.SecurityConfig;
import pl.neopak.rma.returnmanagement.domain.exception.RmaNotFoundException;
import pl.neopak.rma.returnmanagement.domain.model.RmaNumber;
import pl.neopak.rma.returnmanagement.port.in.CreateReturnRequestCommand;
import pl.neopak.rma.returnmanagement.port.in.CreateReturnRequestUseCase;
import pl.neopak.rma.returnmanagement.port.in.QueryReturnRequestUseCase;
import pl.neopak.rma.returnmanagement.port.out.PaymentGateway;
import pl.neopak.rma.returnmanagement.port.out.PhotoStoragePort;
import pl.neopak.rma.security.JwtAuthFilter;
import pl.neopak.rma.security.JwtTokenProvider;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CustomerPortalController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtTokenProvider.class})
class CustomerPortalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CreateReturnRequestUseCase createReturnRequestUseCase;

    @MockBean
    private QueryReturnRequestUseCase queryReturnRequestUseCase;

    @MockBean
    private PaymentGateway paymentGateway;

    @MockBean
    private PhotoStoragePort photoStoragePort;

    @Test
    @WithMockUser
    void createReturn_returnsStatus201WithRmaNumber() throws Exception {
        when(createReturnRequestUseCase.create(any(CreateReturnRequestCommand.class)))
                .thenReturn(RmaNumber.of("ZWR-00001"));

        var body = Map.of(
                "orderId", "ORD-123",
                "sourceSystem", "NEOPAK",
                "customerEmail", "jan@neopak.pl",
                "customerName", "Jan Kowalski"
        );

        mockMvc.perform(post("/api/v1/returns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rmaNumber").value("ZWR-00001"));
    }

    @Test
    @WithMockUser
    void getReturn_existingRma_returnsStatus200WithDetails() throws Exception {
        var details = Map.of("status", "PENDING_SHIPMENT");
        when(queryReturnRequestUseCase.findByRmaNumber("ZWR-00002"))
                .thenReturn(Optional.of(details));

        mockMvc.perform(get("/api/v1/returns/ZWR-00002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rmaNumber").value("ZWR-00002"));
    }

    @Test
    @WithMockUser
    void getReturn_unknownRma_returnsStatus404() throws Exception {
        when(queryReturnRequestUseCase.findByRmaNumber("ZWR-99999"))
                .thenThrow(new RmaNotFoundException("ZWR-99999"));

        mockMvc.perform(get("/api/v1/returns/ZWR-99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void initiatePayment_returnsStatus200WithRedirectUrl() throws Exception {
        when(paymentGateway.createPaymentSession(anyString(), anyInt(), anyString()))
                .thenReturn("https://secure.payu.com/pay/abc123");

        var body = Map.of("amountGrosze", 1999, "customerEmail", "jan@neopak.pl");

        mockMvc.perform(post("/api/v1/returns/ZWR-00003/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirectUrl").value("https://secure.payu.com/pay/abc123"));
    }

    @Test
    @WithMockUser
    void uploadPhotos_returnsStatus200WithUrls() throws Exception {
        when(photoStoragePort.store(any(), anyString(), anyString()))
                .thenReturn("https://storage.neopak.pl/ZWR-00004/photo1.jpg");

        MockMultipartFile file = new MockMultipartFile(
                "files", "photo1.jpg", MediaType.IMAGE_JPEG_VALUE, "fake-image-bytes".getBytes());

        mockMvc.perform(multipart("/api/v1/returns/ZWR-00004/photos").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urls[0]").value("https://storage.neopak.pl/ZWR-00004/photo1.jpg"));
    }
}
