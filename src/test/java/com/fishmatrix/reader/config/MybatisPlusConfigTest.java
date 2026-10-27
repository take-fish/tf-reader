package com.fishmatrix.reader.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MybatisPlusConfigTest {

    @Autowired
    private MybatisPlusInterceptor interceptor;

    @Autowired
    private MetaObjectHandler metaObjectHandler;

    @Test
    @DisplayName("分页拦截器与自动填充处理器已注册")
    void beansRegistered() {
        assertThat(interceptor).isNotNull();
        assertThat(interceptor.getInterceptors()).isNotEmpty();
        assertThat(metaObjectHandler).isNotNull();
    }
}
