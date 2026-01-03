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
class DonationControllerIntegrationTest {

    @LocalServerPort
    int port;

    WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void createDonation_success() {
        Map<String, Object> payload = Map.of(
                "fullName", "Donor One",
                "phoneNumber", "9999999999",
                "email", "donor@example.com",
                "message", "Keep it up",
                "transactionId", "DN-TXN-TEST-1",
                "amount", 100.50
        );

        Map created = client.post().uri("/api/donations")
                .bodyValue(payload)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.get("id")).isNotNull();
        assertThat(created.get("serialNumber")).isNotNull();
        assertThat(created.get("fullName")).isEqualTo("Donor One");
        assertThat(created.get("amount")).isEqualTo(100.50);
    }

    @Test
    void createDonation_failsWhenMissingRequiredFields() {
        Map<String, Object> payload = Map.of(
                "phoneNumber", "9999999999"
                // missing fullName, transactionId and amount
        );

        client.post().uri("/api/donations")
                .bodyValue(payload)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void listAndGetDonations() {
        // Create one
        Map created = client.post().uri("/api/donations")
                .bodyValue(Map.of("fullName", "Donor Two", "transactionId", "DN-TXN-TEST-2", "amount", 50.0))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        String serial = (String) created.get("serialNumber");

        // List
        client.get().uri("/api/donations")
                .exchange()
                .expectStatus().isOk()
                .expectBody(List.class)
                .value(list -> assertThat(list).isNotEmpty());

        // Get by serial
        client.get().uri("/api/donations/{serial}", serial)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .value(body -> assertThat(body.get("serialNumber")).isEqualTo(serial));

        // Get unknown
        client.get().uri("/api/donations/UNKNOWN123")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void deleteDonation_requiresAdmin() {
        // Create one
        Map created = client.post().uri("/api/donations")
                .bodyValue(Map.of("fullName", "Donor Del", "transactionId", "DN-TXN-TEST-3", "amount", 10.0))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        Integer id = (Integer) created.get("id");

        // Delete without admin
        client.delete().uri("/api/donations/{id}", id)
                .exchange()
                .expectStatus().isForbidden();

        // Delete with admin
        client.delete().uri("/api/donations/{id}", id)
                .header("X-Admin-Password", "prarambh-admin-delhi")
                .exchange()
                .expectStatus().isOk();

        // Verify deleted (by serial lookup failing)
        String serial = (String) created.get("serialNumber");
        client.get().uri("/api/donations/{serial}", serial)
                .exchange()
                .expectStatus().isNotFound();
    }
}

