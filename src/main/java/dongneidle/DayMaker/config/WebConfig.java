package dongneidle.DayMaker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                // API 경로에 대한 CORS 설정
                registry.addMapping("/api/**")
                        .allowedOriginPatterns("*") // 모든 origin 허용 (개발 환경용)
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true);
                
                // 정적 리소스 경로에 대한 CORS 설정 (지도 API 포함)
                registry.addMapping("/static/**")
                        .allowedOriginPatterns("*")
                        .allowedMethods("GET", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true);
                
                // 루트 경로에 대한 CORS 설정
                registry.addMapping("/**")
                        .allowedOriginPatterns("*")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }
}
