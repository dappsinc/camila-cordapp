package com.github.manosbatsis.corbeans.corda.webserver.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import springfox.documentation.builders.ApiInfoBuilder
import springfox.documentation.builders.PathSelectors
import springfox.documentation.builders.RequestHandlerSelectors
import springfox.documentation.service.ApiInfo
import springfox.documentation.spi.DocumentationType
import springfox.documentation.spring.web.plugins.Docket
import springfox.documentation.swagger.web.UiConfiguration
import springfox.documentation.swagger2.annotations.EnableSwagger2

/**
 * Swagger configuration
 */
@Configuration
@EnableSwagger2
class SwaggerConfig {

    @Bean
    fun uiConfig(): UiConfiguration {
        return UiConfiguration(null as String?)
    }

    @Bean
    fun api(): Docket {
        return Docket(DocumentationType.SWAGGER_2)
                .apiInfo(apiInfo())
                .select()
                .apis(RequestHandlerSelectors.any())
                .paths(PathSelectors.any())
                .build()
    }

    protected fun apiInfo(): ApiInfo {
        return ApiInfoBuilder().title("Camila CLM")
                .description("A rest API for Camila CLM nodes using Spring Boot 2").version("0.1").build()
    }

}