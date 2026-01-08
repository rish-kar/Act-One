package com.prarambh.act.one.ticketing;

import static org.assertj.core.api.Assertions.assertThat;

import com.prarambh.act.one.ticketing.support.TestAdminPasswordProvider;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SuffixCheckInControllerIntegrationTest {

    @LocalServerPort
    int port;

    WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void checkInBySuffix_success() {
        // Issue ticket
        Map issueResp = client.post().uri("/api/tickets/issue")
                .bodyValue(Map.of(
                        "showName", "Suffix Show",
                        "fullName", "Suffix User",
                        "email", "suffix@example.com",
                        "phoneNumber", "5555555555",
                        "transactionId", "SUFFIX-1",
                        "ticketAmount", 100
                ))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult().getResponseBody();

        String ticketId = (String) issueResp.get("ticketId");
        String userId = (String) issueResp.get("userId");
        String suffix = ticketId.substring(ticketId.length() - 5);

        // Validate
        client.post().uri("/api/transactions/{userId}/validate", userId)
                .header("X-Admin-Password", TestAdminPasswordProvider.adminPassword())
                .exchange()
                .expectStatus().isOk();

        // Check-in by suffix
        client.post().uri("/api/checkin/ticket-suffix/{suffix}", suffix)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .value(body -> {
                    assertThat(body.get("result")).isEqualTo("OK");
                    assertThat(body.get("ticketId")).isEqualTo(ticketId);
                });
    }

    @Test
    void checkInBySuffix_notFound() {
        client.post().uri("/api/checkin/ticket-suffix/zzzzz")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .value(body -> assertThat(body.get("result")).isEqualTo("NOT_FOUND"));
    }

    @Test
    void checkInBySuffix_invalidFormat() {
        client.post().uri("/api/checkin/ticket-suffix/123")
                .exchange()
                .expectStatus().isBadRequest();
    }
}
