package com.prarambh.act.one.ticketing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ManualTransactionFlowIntegrationTest {

    @LocalServerPort
    int port;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        this.webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void recordThenListThenValidateIsIdempotent() {
        // 1) record transaction
        Map record = webTestClient.post()
                .uri("/api/transactions/record")
                .bodyValue(Map.of(
                        "showName", "Test Show",
                        "fullName", "Txn User",
                        "email", "txn@example.com",
                        "phoneNumber", "+911234567890",
                        "ticketCount", 2,
                        "transactionId", "UPI_TXN_123"
                ))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        assertThat(record).isNotNull();
        assertThat(record.get("customerId")).isNotNull();
        assertThat(record.get("ticketCount")).isEqualTo(2);
        assertThat(record.get("status")).isEqualTo("TRANSACTION_MADE");

        int customerId = ((Number) record.get("customerId")).intValue();

        // 2) list successful transactions includes this customerId
        List<Map> list = webTestClient.get()
                .uri("/api/transactions/successful")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Map.class)
                .returnResult()
                .getResponseBody();

        assertThat(list).isNotNull();
        assertThat(list.stream().anyMatch(m -> ((Number) m.get("customerId")).intValue() == customerId)).isTrue();

        // 3) validate -> issued
        Map validate1 = webTestClient.post()
                .uri("/api/transactions/{customerId}/validate", customerId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        assertThat(validate1).isNotNull();
        assertThat(((Number) validate1.get("issuedCount")).intValue()).isEqualTo(2);
        assertThat(validate1.get("newStatus")).isEqualTo("ISSUED");

        // 4) validate again -> should be no-op and still return issuedCount=2
        Map validate2 = webTestClient.post()
                .uri("/api/transactions/{customerId}/validate", customerId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        assertThat(validate2).isNotNull();
        assertThat(((Number) validate2.get("issuedCount")).intValue()).isEqualTo(2);
        assertThat(validate2.get("newStatus")).isEqualTo("ISSUED");

        // sanity: tickets listing shows those tickets now ISSUED and has customerId/transactionId
        webTestClient.get()
                .uri("/api/tickets/all")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Map.class)
                .value(all -> {
                    long mine = all.stream()
                            .filter(m -> m.get("customerId") != null && ((Number) m.get("customerId")).intValue() == customerId)
                            .count();
                    assertThat(mine).isEqualTo(2);

                    all.stream()
                            .filter(m -> m.get("customerId") != null && ((Number) m.get("customerId")).intValue() == customerId)
                            .forEach(m -> {
                                assertThat(m.get("transactionId")).isEqualTo("UPI_TXN_123");
                                assertThat(m.get("status")).isEqualTo("ISSUED");
                            });
                });
    }
}

