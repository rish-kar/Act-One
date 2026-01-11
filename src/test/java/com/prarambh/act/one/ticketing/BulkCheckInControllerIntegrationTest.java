package com.prarambh.act.one.ticketing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BulkCheckInControllerIntegrationTest {

    private static final String ADMIN_PASSWORD = System.getenv().getOrDefault("ACTONE_ADMIN_PASSWORD", "test-admin-password");

    @LocalServerPort
    int port;

    WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void checkInByPhone_success() {
        // Issue tickets
        client.post().uri("/api/tickets/issue")
                .bodyValue(Map.of(
                        "showName", "Bulk Show",
                        "fullName", "Bulk User",
                        "email", "bulk@example.com",
                        "phoneNumber", "9988776655",
                        "transactionId", "BULK-1",
                        "ticketAmount", 100
                ))
                .exchange()
                .expectStatus().isOk();

        // Validate (to make them ISSUED)
        // We need to find the userId first or just assume we can check-in TRANSACTION_MADE?
        // The controller uses ticketCheckInService.checkInByBarcode which handles status.
        // But wait, checkInByBarcode usually requires ISSUED status?
        // Let's check TicketCheckInService.
        // Actually, TicketController checkInByBarcode allows TRANSACTION_MADE but warns "PENDING_APPROVAL".
        // However, BulkCheckInController calls ticketCheckInService.checkInByBarcode directly.
        // Let's assume we need to validate first to be safe, or check if service allows it.
        // But for integration test, let's just try to check in.

        // Actually, let's validate first to be proper.
        // I need userId.
        // I can get it from the issue response.
        Map issueResp = client.post().uri("/api/tickets/issue")
                .bodyValue(Map.of(
                        "showName", "Bulk Show 2",
                        "fullName", "Bulk User 2",
                        "email", "bulk2@example.com",
                        "phoneNumber", "1122334455",
                        "transactionId", "BULK-2",
                        "ticketAmount", 100
                ))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        String userId = (String) issueResp.get("userId");

        // Validate
        client.post().uri("/api/transactions/{userId}/validate", userId)
                .header("X-Admin-Password", ADMIN_PASSWORD)
                .exchange()
                .expectStatus().isOk();

        // Bulk check-in
        client.post().uri("/api/checkin/phone/1122334455")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .value(body -> {
                    assertThat(body.get("result")).isEqualTo("OK");
                    assertThat(body.get("checkedIn")).isEqualTo(1);
                });
    }

    @Test
    void checkInByPhone_notFound() {
        client.post().uri("/api/checkin/phone/0000000000")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .value(body -> assertThat(body.get("result")).isEqualTo("NOT_FOUND"));
    }

    @Test
    void checkInByPhone_invalidFormat() {
        client.post().uri("/api/checkin/phone/123")
                .exchange()
                .expectStatus().isBadRequest();
    }
}

