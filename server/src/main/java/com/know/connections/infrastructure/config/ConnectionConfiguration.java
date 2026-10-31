package com.know.connections.infrastructure.config;

import com.know.connections.application.ConnectionPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ConnectionProperties.class)
public class ConnectionConfiguration {
  @Bean
  ConnectionPolicy connectionPolicy(ConnectionProperties properties) {
    return properties.policy();
  }
}
