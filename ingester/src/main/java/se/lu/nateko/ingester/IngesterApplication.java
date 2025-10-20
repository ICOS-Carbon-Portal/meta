package se.lu.nateko.ingester;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "se.lu.nateko.ingester")
@EnableJpaRepositories(basePackages = "se.lu.nateko.ingester.repository")
public class IngesterApplication {
	public static void main(String[] args) {
		SpringApplication.run(IngesterApplication.class, args);
	}
}
