package com.bankflow.payment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
@ConfigurationProperties(prefix = "bankflow.payment")
@Getter
@Setter
public class PaymentProperties {

    private Limits limits = new Limits();

    @Getter
    @Setter
    public static class Limits {
        private BigDecimal upiMax = new BigDecimal("100000.00");
        private BigDecimal impsMax = new BigDecimal("500000.00");
    }
}
