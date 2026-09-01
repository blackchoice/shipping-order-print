package com.xinglong.print;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ShippingOrderPrintApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShippingOrderPrintApplication.class, args);
    }
}
