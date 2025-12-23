package com.prarambh.act.one.ticketing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TicketControllerIntegrationTest {

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
    void issuingReturnsTicketId() {
        Map body = webTestClient.post()
                .uri("/api/tickets/issue")
                .bodyValue(issueRequestPayload())
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.get("ticketId")).isNotNull();
        assertThat(body.get("barcodeId")).isNotNull();
        assertThat(body.get("status")).isEqualTo("ISSUED");
        assertThat(body.get("showId")).isNotNull();
        assertThat(body.get("showName")).isEqualTo("Test Show");
    }

    @Test
    void checkInValidThenAlreadyUsed() {
        Map issueBody = webTestClient.post()
                .uri("/api/tickets/issue")
                .bodyValue(issueRequestPayload())
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        assertThat(issueBody).isNotNull();
        String ticketId = issueBody.get("ticketId").toString();

        Map first = webTestClient.post()
                .uri("/api/tickets/{ticketId}/checkin", ticketId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        assertThat(first).isNotNull();
        assertThat(first.get("result")).isEqualTo("VALID");

        Map second = webTestClient.post()
                .uri("/api/tickets/{ticketId}/checkin", ticketId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        assertThat(second).isNotNull();
        assertThat(second.get("result")).isEqualTo("ALREADY_USED");
        assertThat(second.get("usedAt")).isNotNull();
    }

    private Map<String, String> issueRequestPayload() {
        return Map.of(
                "showName", "Test Show",
                "fullName", "Test User",
                "email", "test@example.com",
                "phoneNumber", "+10000000000"
        );
    }
}
