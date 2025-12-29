package com.prarambh.act.one.ticketing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuditoriumControllerIntegrationTest {

    @LocalServerPort
    int port;

    WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void upsertAndGetAndAvailableSeatsRequiresAdmin() {
        // forbidden without admin
        client.post().uri("/api/auditoriums")
                .bodyValue(Map.of(
                        "showId", "SHOW-TEST-1",
                        "auditoriumName", "Main Auditorium",
                        "showDate", LocalDate.now().toString(),
                        "showTime", LocalTime.of(19,0).toString(),
                        "totalSeats", 100,
                        "reservedSeats", 10
                ))
                .exchange().expectStatus().isForbidden();

        Map created = client.post().uri("/api/auditoriums")
                .header("X-Admin-Password", "prarambh-admin-delhi")
                .bodyValue(Map.of(
                        "showId", "SHOW-TEST-1",
                        "auditoriumName", "Main Auditorium",
                        "showDate", LocalDate.now().toString(),
                        "showTime", LocalTime.of(19,0).toString(),
                        "totalSeats", 100,
                        "reservedSeats", 10
                ))
                .exchange().expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();

        assertThat(created).isNotNull();
        String auditoriumId = created.get("auditoriumId").toString();

        client.get().uri("/api/auditoriums/{id}", auditoriumId)
                .header("X-Admin-Password", "prarambh-admin-delhi")
                .exchange().expectStatus().isOk()
                .expectBody(Map.class)
                .value(body -> {
                    assertThat(body.get("auditoriumId")).isEqualTo(auditoriumId);
                    assertThat(body.get("totalSeats")).isEqualTo(100);
                });

        client.get().uri("/api/auditoriums/{id}/available-seats", auditoriumId)
                .header("X-Admin-Password", "prarambh-admin-delhi")
                .exchange().expectStatus().isOk()
                .expectBody(Map.class)
                .value(body -> assertThat(body.get("availableSeats")).isNotNull());
    }

    @Test
    void deleteAuditoriumRequiresAdmin() {
        // Create one first
        Map created = client.post().uri("/api/auditoriums")
                .header("X-Admin-Password", "prarambh-admin-delhi")
                .bodyValue(Map.of(
                        "showId", "SHOW-DEL-1",
                        "auditoriumName", "Delete Me",
                        "showDate", LocalDate.now().toString(),
                        "showTime", LocalTime.of(10,0).toString(),
                        "totalSeats", 50,
                        "reservedSeats", 0
                ))
                .exchange().expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
        String id = created.get("auditoriumId").toString();

        // Delete without admin -> 403
        client.delete().uri("/api/auditoriums/{id}", id)
                .exchange().expectStatus().isForbidden();

        // Delete with admin -> 200
        client.delete().uri("/api/auditoriums/{id}", id)
                .header("X-Admin-Password", "prarambh-admin-delhi")
                .exchange().expectStatus().isOk();

        // Get -> 404
        client.get().uri("/api/auditoriums/{id}", id)
                .header("X-Admin-Password", "prarambh-admin-delhi")
                .exchange().expectStatus().isNotFound();
    }
}
