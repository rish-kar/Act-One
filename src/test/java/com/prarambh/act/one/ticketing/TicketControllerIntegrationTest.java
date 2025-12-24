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
        assertThat(body.get("showId").toString()).contains("SHOW-");

        assertThat(body.get("ticketCount")).isEqualTo(1);
        assertThat((java.util.List<?>) body.get("ticketIds")).hasSize(1);
        assertThat((java.util.List<?>) body.get("barcodeIds")).hasSize(1);
    }

    @Test
    void issuingWithTicketCountCreatesMultipleRows() {
        Map issueBody = webTestClient.post()
                .uri("/api/tickets/issue")
                .bodyValue(Map.of(
                        "showName", "Test Show",
                        "fullName", "Test User",
                        "email", "test@example.com",
                        "phoneNumber", "+10000000000",
                        "ticketCount", 3
                ))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        assertThat(issueBody).isNotNull();
        assertThat(issueBody.get("ticketCount")).isEqualTo(3);

        java.util.List<?> ticketIds = (java.util.List<?>) issueBody.get("ticketIds");
        java.util.List<?> barcodeIds = (java.util.List<?>) issueBody.get("barcodeIds");
        assertThat(ticketIds).hasSize(3);
        assertThat(barcodeIds).hasSize(3);

        // list endpoint should include at least those 3 rows (test profile starts empty)
        webTestClient.get()
                .uri("/api/tickets/all")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Map.class)
                .value(list -> {
                    assertThat(list.size()).isGreaterThanOrEqualTo(3);
                    long matching = list.stream()
                            .filter(m -> "Test User".equals(((Map) m).get("fullName")))
                            .count();
                    assertThat(matching).isGreaterThanOrEqualTo(3);
                });

        // each created ticket id should be fetchable
        for (Object tid : ticketIds) {
            webTestClient.get()
                    .uri("/api/tickets/by-ticket-id/{ticketId}", tid.toString())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Map.class)
                    .value(body -> {
                        assertThat(body.get("ticketId").toString()).isEqualTo(tid.toString());
                        assertThat(body.get("ticketCount")).isEqualTo(3);
                        assertThat(body.get("fullName")).isEqualTo("Test User");
                        assertThat(body.get("showName")).isEqualTo("Test Show");
                    });
        }
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
        String barcodeId = issueBody.get("barcodeId").toString();

        Map first = webTestClient.post()
                .uri("/api/tickets/barcode/{barcodeId}/checkin", barcodeId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        assertThat(first).isNotNull();
        assertThat(first.get("result")).isEqualTo("VALID");

        Map second = webTestClient.post()
                .uri("/api/tickets/barcode/{barcodeId}/checkin", barcodeId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        assertThat(second).isNotNull();
        assertThat(second.get("result")).isEqualTo("ALREADY_USED");
        assertThat(second.get("usedAtDate")).isNotNull();
        assertThat(second.get("usedAtTimeIst")).isNotNull();
    }

    @Test
    void adminPurgeRequiresPasswordAndDeletes() {
        // create a ticket so we know there is something to delete
        webTestClient.post()
                .uri("/api/tickets/issue")
                .bodyValue(issueRequestPayload())
                .exchange()
                .expectStatus().isOk();

        // wrong password -> forbidden
        webTestClient.delete()
                .uri("/api/admin/tickets")
                .header("X-Admin-Password", "wrong")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody(Map.class)
                .value(body -> assertThat(body.get("message")).isEqualTo("Invalid admin password"));

        // correct password -> ok
        webTestClient.delete()
                .uri("/api/admin/tickets")
                .header("X-Admin-Password", "prarambh-admin-delhi")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .value(body -> assertThat(body.get("deletedCount")).isNotNull());

        // listing should now be empty
        webTestClient.get()
                .uri("/api/tickets/all")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Map.class)
                .hasSize(0);
    }

    @Test
    void getTicketByIdReturnsTicket() {
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

        // Legacy endpoint
        webTestClient.get()
                .uri("/api/tickets/{ticketId}", ticketId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .value(body -> {
                    assertThat(body.get("ticketId").toString()).isEqualTo(ticketId);
                    assertThat(body.get("showName")).isEqualTo("Test Show");
                });

        // New explicit ticketId endpoint
        webTestClient.get()
                .uri("/api/tickets/by-ticket-id/{ticketId}", ticketId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .value(body -> {
                    assertThat(body.get("ticketId").toString()).isEqualTo(ticketId);
                    assertThat(body.get("showName")).isEqualTo("Test Show");
                });
    }

    @Test
    void issueWithoutShowNameRequiresDefaultOrReturnsBadRequest() {
        webTestClient.post()
                .uri("/api/tickets/issue")
                .bodyValue(Map.of(
                        "fullName", "Test User",
                        "email", "test@example.com",
                        "phoneNumber", "+10000000000"
                ))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(Map.class)
                .value(body -> assertThat(body.get("message").toString()).contains("showName is required"));
    }

    @Test
    void adminSetsDefaultShowNameAndItAutoPopulatesNewTickets() {
        // create an existing ticket with the old showName
        Map existing = webTestClient.post()
                .uri("/api/tickets/issue")
                .bodyValue(issueRequestPayload())
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        assertThat(existing).isNotNull();
        String existingTicketId = existing.get("ticketId").toString();

        // set default show name (should also update existing tickets)
        webTestClient.post()
                .uri("/api/admin/show-name")
                .header("X-Admin-Password", "prarambh-admin-delhi")
                .bodyValue(Map.of(
                        "showName", "Act One - Special Preview"
                ))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .value(body -> {
                    assertThat(body.get("defaultShowName")).isEqualTo("Act One - Special Preview");
                    assertThat(body.get("showId")).isNotNull();
                });

        // GET default show name should return the same value
        webTestClient.get()
                .uri("/api/admin/show-name")
                .header("X-Admin-Password", "prarambh-admin-delhi")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .value(body -> assertThat(body.get("defaultShowName")).isEqualTo("Act One - Special Preview"));

        // existing ticket should now reflect updated showName and showId
        webTestClient.get()
                .uri("/api/tickets/{ticketId}", existingTicketId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .value(body -> {
                    assertThat(body.get("showName")).isEqualTo("Act One - Special Preview");
                    assertThat(body.get("showId")).isNotNull();
                });

        // issue without showName (should use default)
        Map issueBody = webTestClient.post()
                .uri("/api/tickets/issue")
                .bodyValue(Map.of(
                        "fullName", "Test User",
                        "email", "test@example.com",
                        "phoneNumber", "+10000000000"
                ))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        assertThat(issueBody).isNotNull();
        assertThat(issueBody.get("showName")).isEqualTo("Act One - Special Preview");

        // clear default
        webTestClient.post()
                .uri("/api/admin/show-name")
                .header("X-Admin-Password", "prarambh-admin-delhi")
                .bodyValue(Map.of(
                        "clear", true
                ))
                .exchange()
                .expectStatus().isOk();

        // issue without showName now should fail again
        webTestClient.post()
                .uri("/api/tickets/issue")
                .bodyValue(Map.of(
                        "fullName", "Test User",
                        "email", "test@example.com",
                        "phoneNumber", "+10000000000"
                ))
                .exchange()
                .expectStatus().isBadRequest();
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
