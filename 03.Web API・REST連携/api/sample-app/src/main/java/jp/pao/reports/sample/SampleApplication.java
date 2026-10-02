package jp.pao.reports.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import javax.sql.DataSource;
import java.nio.file.Path;

@SpringBootApplication
public class SampleApplication {
    public static void main(String[] args) { SpringApplication.run(SampleApplication.class, args); }
    @Bean SampleCatalog sampleCatalog(DataSource source, @Value("${reports.resources}") String resources) {
        return new SampleCatalog(source, Path.of(resources));
    }
}
