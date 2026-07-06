package com.arijit.authservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AuthServiceApplicationTests {

    @Test
    void contextLoads() {
        // If this passes, it means every @Bean/@Configuration in the app
        // wired up correctly - it's the cheapest possible test to catch
        // "I broke something in a config class" mistakes early.
    }
}
