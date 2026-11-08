package com.saumyasolar.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AuditWebConfig implements WebMvcConfigurer {
    private final AuditLogInterceptor interceptor;
    public AuditWebConfig(AuditLogInterceptor interceptor) { this.interceptor=interceptor; }
    @Override public void addInterceptors(InterceptorRegistry registry) { registry.addInterceptor(interceptor); }
}
