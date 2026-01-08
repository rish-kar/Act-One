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
class AdminControllerIntegrationTest {

    @LocalServerPort
    int port;

    WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void adminEndpoints_requirePassword() {
        client.get().uri("/api/admin/show-name")
                .exchange().expectStatus().isForbidden();
        client.post().uri("/api/admin/show-name").bodyValue(Map.of())
                .exchange().expectStatus().isForbidden();
        client.delete().uri("/api/admin/tickets")
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void manageShowName() {
        // Set show name
        client.post().uri("/api/admin/show-name")
                .header("X-Admin-Password", "{{admin-password}}")
                .bodyValue(Map.of("showName", "Admin Test Show", "showDate", "2025-12-31", "showTime", "18:00"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .value(body -> {
                    assertThat(body.get("defaultShowName")).isEqualTo("Admin Test Show");
                    assertThat(body.get("showId")).isNotNull();
                });

        // Get show name
        client.get().uri("/api/admin/show-name")
                .header("X-Admin-Password", "{{admin-password}}")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .value(body -> assertThat(body.get("defaultShowName")).isEqualTo("Admin Test Show"));

        // Clear show name
        client.post().uri("/api/admin/show-name")
                .header("X-Admin-Password", "{{admin-password}}")
                .bodyValue(Map.of("clear", true))
                .exchange()
                .expectStatus().isOk();

        // Verify cleared
        client.get().uri("/api/admin/show-name")
                .header("X-Admin-Password", "{{admin-password}}")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .value(body -> assertThat(body.get("defaultShowName")).isNull());
    }

    @Test
    void purgeTickets() {
        // Create a ticket first (using public API)
        client.post().uri("/api/tickets/issue")
                .bodyValue(Map.of(
                        "showName", "Purge Show",
                        "fullName", "Purge User",
                        "email", "purge@example.com",
                        "phoneNumber", "1111111111",
                        "transactionId", "PURGE-1",
                        "ticketAmount", 100
                ))
                .exchange()
                .expectStatus().isOk();

        // Purge
        client.delete().uri("/api/admin/tickets")
                .header("X-Admin-Password", "{{admin-password}}")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .value(body -> assertThat(body.get("message")).isEqualTo("All tickets deleted"));

        // Verify empty
        client.get().uri("/api/tickets/all")
                .exchange()
                .expectStatus().isOk()
                .expectBody(java.util.List.class)
                .value(list -> assertThat(list).isEmpty());
    }
}

