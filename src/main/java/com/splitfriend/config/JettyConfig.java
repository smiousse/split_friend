package com.splitfriend.config;

import org.eclipse.jetty.server.Server;
import org.springframework.boot.web.embedded.jetty.JettyServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JettyConfig {

    @Bean
    public WebServerFactoryCustomizer<JettyServletWebServerFactory> jettyCustomizer() {
        return factory -> {
            factory.addServerCustomizers(this::configureJetty);
        };
    }

    private void configureJetty(Server server) {
        // Additional Jetty configuration if needed
        server.setStopAtShutdown(true);
    }
}
