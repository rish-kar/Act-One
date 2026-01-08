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
class UserControllerIntegrationTest {

    @LocalServerPort
    int port;

    WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void userEndpoints_requireAdmin() {
        client.get().uri("/api/users")
                .exchange().expectStatus().isForbidden();
        client.post().uri("/api/users").bodyValue(Map.of())
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void createAndManageUser() {
        // Create
        Map created = client.post().uri("/api/users")
                .header("X-Admin-Password", "{{admin-password}}")
                .bodyValue(Map.of(
                        "fullName", "Test User One",
                        "phoneNumber", "9876543210",
                        "email", "user1@example.com"
                ))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody();

        assertThat(created).isNotNull();
        String userId = (String) created.get("userId");
        Integer id = (Integer) created.get("id");
        assertThat(userId).isNotNull();

        // Get by userId
        client.get().uri("/api/users/{userId}", userId)
                .header("X-Admin-Password", "{{admin-password}}")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .value(body -> assertThat(body.get("fullName")).isEqualTo("Test User One"));

        // Update
        client.put().uri("/api/users/{id}", id)
                .header("X-Admin-Password", "{{admin-password}}")
                .bodyValue(Map.of("fullName", "Updated Name"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .value(body -> assertThat(body.get("fullName")).isEqualTo("Updated Name"));

        // Search by phone
        client.get().uri(uriBuilder -> uriBuilder.path("/api/users/by-phone").queryParam("phoneNumber", "9876543210").build())
                .header("X-Admin-Password", "{{admin-password}}")
                .exchange()
                .expectStatus().isOk()
                .expectBody(List.class)
                .value(list -> assertThat(list).isNotEmpty());

        // Delete
        client.delete().uri("/api/users/{id}", id)
                .header("X-Admin-Password", "{{admin-password}}")
                .exchange()
                .expectStatus().isOk();

        // Verify deleted
        client.get().uri("/api/users/{userId}", userId)
                .header("X-Admin-Password", "{{admin-password}}")
                .exchange()
                .expectStatus().isNotFound();
    }
}

