package com.example.SpringTgBot;

import com.example.SpringTgBot.config.BotPropeties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@EnableConfigurationProperties
@EnableAutoConfiguration
public class StringBotApplication {

	public static void main(String[] args) {
		SpringApplication.run(StringBotApplication.class, args);
	}

}
