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
class FeedbackControllerIntegrationTest {

    @LocalServerPort
    int port;

    WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void submitFeedbackIsPublic_andAdminCanList() {
        client.post()
                .uri("/api/feedback")
                .bodyValue(Map.of(
                        "fullName", "Feedback User",
                        "phoneNumber", "+919916604905",
                        "email", "fb@example.com",
                        "message", "Loved it"
                ))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .value(body -> {
                    assertThat(body.get("id")).isNotNull();
                    assertThat(body.get("fullName")).isEqualTo("Feedback User");
                });

        client.get()
                .uri("/api/feedback")
                .exchange()
                .expectStatus().isForbidden();

        client.get()
                .uri("/api/feedback")
                .header("X-Admin-Password", "{{admin-password}}")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Map.class)
                .value(list -> assertThat(list.size()).isGreaterThanOrEqualTo(1));
    }
}

