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
class TicketControllerIntegrationTest {

    @LocalServerPort
    int port;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        String baseUrl = System.getenv().getOrDefault("ACTONE_API_BASE", "http://localhost:" + port);
        this.webTestClient = WebTestClient.bindToServer()
                .baseUrl(baseUrl)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(20 * 1024 * 1024))
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
        assertThat(body.get("qrCodeId")).isNotNull();
        assertThat(body.get("status")).isEqualTo("TRANSACTION_MADE");
        assertThat(body.get("showId")).isNotNull();
        assertThat(body.get("showName")).isEqualTo("Test Show");
        assertThat(body.get("showId").toString()).contains("SHOW-");
        assertThat(body.get("userId")).isNotNull();
        assertThat(body.get("transactionId")).isNotNull();
        assertThat(body.get("message")).isEqualTo("Transaction recorded. Tickets will be issued after manual approval.");
    }

    @Test
    void getShowsFromTickets_returnsDistinctShows() {
        // The H2 data loader seeds tickets with "Act One - Opening Night", etc.
        // So we expect some results.

        webTestClient.get().uri("/api/tickets/shows")
                .exchange()
                .expectStatus().isOk()
                .expectBody(java.util.List.class)
                .value(list -> {
                    assertThat(list).isNotEmpty();
                    Map first = (Map) list.get(0);
                    assertThat(first.get("showName")).isNotNull();
                    assertThat(first.get("showId")).isNotNull();
                });
    }

    private Map<String, Object> issueRequestPayload() {
        return Map.of(
                "showName", "Test Show",
                "fullName", "Test User",
                "email", "test@example.com",
                "phoneNumber", "1000000000",
                "transactionId", "DEMO-TXN",
                "ticketAmount", "750.00"
        );
    }
}
