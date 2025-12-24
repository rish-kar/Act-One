package com.prarambh.act.one.ticketing.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Enables async processing (used for non-blocking email notifications). */
@Configuration
@EnableAsync
public class AsyncConfig {
}

