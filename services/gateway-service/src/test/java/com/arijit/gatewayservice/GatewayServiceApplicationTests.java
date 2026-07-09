package com.arijit.gatewayservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class GatewayServiceApplicationTests {

    @Test
    void contextLoads() {
        // If this passes, every @Bean/@Configuration wired up correctly -
        // including the four gRPC client stubs and the JWT/rate-limit
        // filter chain - without any of the four downstream services
        // actually needing to be up (the static gRPC channels connect
        // lazily, only failing once a call is actually made).
    }
}
