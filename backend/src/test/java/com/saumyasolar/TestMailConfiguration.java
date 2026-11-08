package com.saumyasolar;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.mockito.Mockito;

@TestConfiguration
public class TestMailConfiguration {
    @Bean
    @Primary
    JavaMailSender testMailSender() {
        return Mockito.mock(JavaMailSender.class);
    }
}
