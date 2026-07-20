package com.youzhi.zhixun;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ZhixunApplication {
    public static void main(String[] args) {
        SpringApplication.run(ZhixunApplication.class, args);
    }
}
