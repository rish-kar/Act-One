package com.prarambh.act.one;

import com.prarambh.act.one.ticketing.service.card.TicketCardProperties;
import com.prarambh.act.one.ticketing.service.email.EmailProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Entry point for the Act-One Spring Boot application.
 */
@SpringBootApplication(scanBasePackages = "com.prarambh.act.one")
@EnableConfigurationProperties({
        EmailProperties.class,
        TicketCardProperties.class
})
public class ActOneApplication {

	/**
	 * Boots the Spring application context.
	 */
	public static void main(String[] args) {
		SpringApplication.run(ActOneApplication.class, args);
	}
}
