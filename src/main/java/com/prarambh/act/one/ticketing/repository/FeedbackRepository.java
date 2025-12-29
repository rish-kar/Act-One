package com.prarambh.act.one.ticketing.repository;

import com.prarambh.act.one.ticketing.model.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
}

