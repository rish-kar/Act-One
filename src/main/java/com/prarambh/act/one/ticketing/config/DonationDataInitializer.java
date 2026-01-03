package com.prarambh.act.one.ticketing.config;

import com.prarambh.act.one.ticketing.model.Donation;
import com.prarambh.act.one.ticketing.repository.DonationRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates sample donation data at application startup when the donations table is empty.
 */
@Configuration
public class DonationDataInitializer {

    /**
     * Seed sample donations if repository is empty.
     *
     * @param repo donation repository
     * @return command line runner
     */
    @Bean
    CommandLineRunner seedDonations(DonationRepository repo) {
        return args -> {
            if (repo.count() > 0) return;

            List<Donation> sample = List.of(
                make("Asha Nair", "9916604905", "asha@example.com", "All the best!", new BigDecimal("50.00")),
                make("Rohit Singh", "9876543210", "rohit@example.com", "Thank you!", new BigDecimal("100.00")),
                make("Leela Rao", "9123456780", "leela@example.com", "Great show", new BigDecimal("25.00")),
                make("Maya Patel", "9988776655", "maya@example.com", "Supporting art", new BigDecimal("200.00")),
                make("Arjun Mehta", "9001122334", "arjun@example.com", "Keep it up", new BigDecimal("75.00")),
                make("Sana Khan", "9112233445", "sana@example.com", "Love it!", new BigDecimal("30.00"))
            );

            sample.forEach(d -> {
                try { repo.save(d); } catch (Exception ex) { /* ignore duplicates */ }
            });
        };
    }

    private static Donation make(String fullName, String phone, String email, String msg, BigDecimal amt) {
        Donation d = new Donation();
        d.setFullName(fullName);
        d.setPhoneNumber(phone);
        d.setEmail(email);
        d.setMessage(msg);
        d.setTransactionId("DN-SEED-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
        d.setAmount(amt);
        return d;
    }
}
