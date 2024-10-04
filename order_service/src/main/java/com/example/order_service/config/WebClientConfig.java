package com.example.order_service.config;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;


@Configuration

public class WebClientConfig {
    @Bean
    @LoadBalanced
     public WebClient.Builder webClientBuilder() {
        return  WebClient.builder();
    }
}
