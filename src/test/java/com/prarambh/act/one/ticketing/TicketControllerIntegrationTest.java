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
                .header("X-Admin-Password", "purged-by-prarambh-admin")
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

        webTestClient.get()
                .uri("/api/tickets/{ticketId}", ticketId)
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
                .header("X-Admin-Password", "purged-by-prarambh-admin")
                .bodyValue(Map.of(
                        "showName", "Act One - Special Preview"
                ))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .value(body -> assertThat(body.get("defaultShowName")).isEqualTo("Act One - Special Preview"));

        // GET default show name should return the same value
        webTestClient.get()
                .uri("/api/admin/show-name")
                .header("X-Admin-Password", "purged-by-prarambh-admin")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .value(body -> assertThat(body.get("defaultShowName")).isEqualTo("Act One - Special Preview"));

        // existing ticket should now reflect updated showName
        webTestClient.get()
                .uri("/api/tickets/{ticketId}", existingTicketId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .value(body -> assertThat(body.get("showName")).isEqualTo("Act One - Special Preview"));

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
                .header("X-Admin-Password", "purged-by-prarambh-admin")
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

    @Test
    void checkInWithInvalidUuidReturns400() {
        String invalidUuid = "2acc227e-716c-42fc"; // incomplete UUID
        
        webTestClient.post()
                .uri("/api/tickets/{ticketId}/checkin", invalidUuid)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(Map.class)
                .value(body -> {
                    assertThat(body.get("message")).isNotNull();
                    assertThat(body.get("message").toString()).contains("Invalid UUID format for parameter: ticketId");
                });
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
