package com.innerstyle.notification.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Lets notification listeners send email off the request thread. */
@Configuration
@EnableAsync
public class AsyncConfig {
}
