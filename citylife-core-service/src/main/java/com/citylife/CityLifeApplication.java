package com.citylife;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * @description: 服务启动
 **/
@EnableAspectJAutoProxy(exposeProxy = true)
@MapperScan("com.citylife.mapper")
@SpringBootApplication
public class CityLifeApplication {

    public static void main(String[] args) {
        SpringApplication.run(CityLifeApplication.class, args);
    }

}
