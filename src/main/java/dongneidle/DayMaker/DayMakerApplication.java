package dongneidle.DayMaker;

import dongneidle.DayMaker.service.FestivalService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication(exclude = {SecurityAutoConfiguration.class})
public class DayMakerApplication {

	public static void main(String[] args) {
		SpringApplication.run(DayMakerApplication.class, args);
	}
	
	@Bean
	public RestTemplate restTemplate() {
		return new RestTemplate();
	}
	
	@Bean
	public CommandLineRunner initData(FestivalService festivalService) {
		return args -> {
			festivalService.createInitialFestivals();
		};
	}
}
