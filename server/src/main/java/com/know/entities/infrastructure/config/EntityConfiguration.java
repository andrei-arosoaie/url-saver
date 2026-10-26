package com.know.entities.infrastructure.config;

import java.time.Clock;
import org.springframework.context.annotation.*;

@Configuration
public class EntityConfiguration {
  @Bean
  public Clock entityClock() {
    return Clock.systemUTC();
  }
}
