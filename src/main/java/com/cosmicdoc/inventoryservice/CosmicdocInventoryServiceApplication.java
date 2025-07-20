package com.cosmicdoc.inventoryservice;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication


@SecurityScheme(
		name = "bearerAuth",
		type = SecuritySchemeType.HTTP,
		bearerFormat = "JWT",
		scheme = "bearer"
)
@OpenAPIDefinition(info = @Info(title = "Inventory API", version = "v1"))
public class CosmicdocInventoryServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(CosmicdocInventoryServiceApplication.class, args);
		System.out.println("Welcome to Inv Service");
	}

}
