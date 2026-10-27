package com.fishmatrix.reader.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class MapperLocationTest {

    @Test
    @DisplayName("application.yml 里配置的 classpath:mapper/*.xml 能解析到 XML")
    void configuredPatternResolvesXml() throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath:mapper/*.xml");

        assertThat(resources).isNotEmpty();
        assertThat(resources)
                .extracting(Resource::getFilename)
                .contains("ReaderBookMapper.xml");
    }
}
