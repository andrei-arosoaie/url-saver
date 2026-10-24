package com.know;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class KnowApplication {
  public static void main(String[] args) {
    SpringApplication.run(KnowApplication.class, args);
  }
}
