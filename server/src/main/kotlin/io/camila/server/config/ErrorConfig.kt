package io.camila.server.config

import com.github.manosbatsis.scrudbeans.error.RestExceptionHandler
import org.springframework.context.annotation.Bean
import org.springframework.web.servlet.HandlerExceptionResolver

/**
 * Automatically handle errors by creating a REST exception response,
 * courtesy of `scrudbeans-error`, see:
 * https://manosbatsis.github.io/scrudbeans/docs/restfulservices#error-responses
 */
class ErrorConfig {

    /**
     * Register our custom `HandlerExceptionResolver`
     */
    @Bean
    fun restExceptionHandler(): HandlerExceptionResolver {
        return RestExceptionHandler()
    }
}