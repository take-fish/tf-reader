package com.fishmatrix.reader;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        properties = {
            "spring.cloud.nacos.discovery.enabled=false",
            "spring.sql.init.mode=never"
        })
class ReaderApplicationTests {

    @Test
    void contextLoads() {
    }
}
