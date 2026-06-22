package com.document.private_cloud_back;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.document.private_cloud_back", "front", "back"})
@EntityScan(basePackages = {"com.document.private_cloud_back", "front"})
@EnableJpaRepositories(basePackages = {"com.document.private_cloud_back", "front"})
@EnableElasticsearchRepositories(basePackages = {"front.search.engine.es"})
@EnableAsync
@EnableScheduling
public class PrivateCloudBackApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrivateCloudBackApplication.class, args);
    }

}
