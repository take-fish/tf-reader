package com.fishmatrix.reader;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DependencyPresenceTest {

    @Test
    @DisplayName("MyBatis-Plus / Lombok / MapStruct 依赖已就位")
    void requiredLibrariesOnClasspath() {
        assertThat(loadable("com.baomidou.mybatisplus.core.mapper.BaseMapper")).isTrue();
        assertThat(loadable("com.baomidou.mybatisplus.annotation.TableName")).isTrue();
        assertThat(loadable("com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor"))
                .isTrue();
        assertThat(loadable("lombok.Data")).isTrue();
        assertThat(loadable("org.mapstruct.Mapper")).isTrue();
    }

    private static boolean loadable(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
