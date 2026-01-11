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

    private static final String ADMIN_PASSWORD = System.getenv().getOrDefault("ACTONE_ADMIN_PASSWORD", "test-admin-password");

    @LocalServerPort
    int port;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        String baseUrl = System.getenv().getOrDefault("ACTONE_API_BASE", "http://localhost:" + port);
        this.webTestClient = WebTestClient.bindToServer()
                .baseUrl(baseUrl)
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
                        "transactionId", "UPI_TXN_123",
                        "ticketAmount", "500.00"
                ))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        assertThat(record).isNotNull();
        assertThat(record.get("userId")).isNotNull();
        assertThat(record.get("ticketCount")).isEqualTo(2);
        assertThat(record.get("status")).isEqualTo("TRANSACTION_MADE");

        String userId = record.get("userId").toString();

        // 2) list successful transactions includes this userId
        List<Map> list = webTestClient.get()
                .uri("/api/transactions/successful")
                .header("X-Admin-Password", ADMIN_PASSWORD)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Map.class)
                .returnResult()
                .getResponseBody();

        assertThat(list).isNotNull();
        assertThat(list.stream().anyMatch(m -> userId.equals(String.valueOf(m.get("userId"))))).isTrue();

        // 3) validate -> issued
        Map validate1 = webTestClient.post()
                .uri("/api/transactions/{userId}/validate", userId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        assertThat(validate1).isNotNull();
        assertThat(((Number) validate1.get("ticketCount")).intValue()).isEqualTo(2);
        assertThat(validate1.get("ticketStatus")).isEqualTo("ISSUED");

        Map validate2 = webTestClient.post()
                .uri("/api/transactions/{userId}/validate", userId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        assertThat(validate2).isNotNull();
        assertThat(((Number) validate2.get("ticketCount")).intValue()).isEqualTo(2);
        assertThat(validate2.get("ticketStatus")).isEqualTo("ISSUED");

        // sanity: tickets listing shows those tickets now ISSUED and has userId/transactionId
        webTestClient.get()
                .uri("/api/tickets/all")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Map.class)
                .value(all -> {
                    long mine = all.stream()
                            .filter(m -> m.get("userId") != null && userId.equals(String.valueOf(m.get("userId"))))
                            .count();
                    assertThat(mine).isEqualTo(2);

                    all.stream()
                            .filter(m -> m.get("userId") != null && userId.equals(String.valueOf(m.get("userId"))))
                            .forEach(m -> {
                                assertThat(m.get("transactionId")).isEqualTo("UPI_TXN_123");
                                assertThat(m.get("status")).isEqualTo("ISSUED");
                            });
                });
    }
}
