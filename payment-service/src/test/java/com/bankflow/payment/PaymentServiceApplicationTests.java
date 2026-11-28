package com.bankflow.payment;

import com.bankflow.common.enums.PaymentMode;
import com.bankflow.common.enums.TransactionStatus;
import com.bankflow.payment.dto.PaymentRequest;
import com.bankflow.payment.dto.PaymentResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreatePaymentAndReturnCachedResponseOnReplay() throws Exception {
        String key = "idem-" + UUID.randomUUID();
        PaymentRequest req = new PaymentRequest(
                "acc-101",
                "acc-102",
                new BigDecimal("12500.00"),
                PaymentMode.UPI,
                "Rent transfer"
        );

        MvcResult firstResult = mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value(TransactionStatus.PENDING_FRAUD_CHECK.name()))
                .andExpect(jsonPath("$.sagaState").value("STARTED"))
                .andReturn();

        PaymentResponse firstResponse = objectMapper.readValue(
                firstResult.getResponse().getContentAsString(),
                PaymentResponse.class
        );

        MvcResult replayResult = mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted())
                .andReturn();

        PaymentResponse replayResponse = objectMapper.readValue(
                replayResult.getResponse().getContentAsString(),
                PaymentResponse.class
        );

        assertThat(replayResponse.transactionId()).isEqualTo(firstResponse.transactionId());

        mockMvc.perform(get("/payments/{id}", firstResponse.transactionId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotencyKey").value(key));
    }

    @Test
    void shouldRejectSelfTransferAndExceedingModeLimits() throws Exception {
        PaymentRequest selfTransfer = new PaymentRequest(
                "acc-101",
                "acc-101",
                new BigDecimal("500.00"),
                PaymentMode.UPI,
                "Self"
        );

        mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", "idem-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(selfTransfer)))
                .andExpect(status().isBadRequest());

        PaymentRequest overUpiLimit = new PaymentRequest(
                "acc-101",
                "acc-102",
                new BigDecimal("150000.00"),
                PaymentMode.UPI,
                "Over UPI cap"
        );

        mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", "idem-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overUpiLimit)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectMissingIdempotencyKey() throws Exception {
        PaymentRequest req = new PaymentRequest(
                "acc-101",
                "acc-102",
                new BigDecimal("1000.00"),
                PaymentMode.NEFT,
                "No key"
        );

        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldHandleConcurrentDuplicateIdempotencyKeysSafely() throws Exception {
        String sharedKey = "concurrent-" + UUID.randomUUID();
        PaymentRequest req = new PaymentRequest(
                "acc-201",
                "acc-202",
                new BigDecimal("2500.00"),
                PaymentMode.IMPS,
                "Concurrent test"
        );
        String payload = objectMapper.writeValueAsString(req);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> mockMvc.perform(post("/payments")
                            .header("Idempotency-Key", sharedKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andReturn()
                    .getResponse()
                    .getStatus());
        }

        List<Future<Integer>> futures = pool.invokeAll(tasks);
        pool.shutdown();

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get());
        }

        assertThat(statuses).contains(202);
        assertThat(statuses).allMatch(code -> code == 202 || code == 409);
    }
}