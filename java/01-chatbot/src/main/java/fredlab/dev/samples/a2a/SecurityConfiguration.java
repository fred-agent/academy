package fredlab.dev.samples.a2a;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfiguration {

    @Bean
    @ConditionalOnProperty(name = "app.security.enabled", havingValue = "true", matchIfMissing = true)
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
                .authorizeExchange(exchanges -> exchanges
                        // The public agent card must be accessible without authentication
                        .pathMatchers("/.well-known/agent-card.json").permitAll()
                        // All other endpoints require an authenticated user
                        .anyExchange().authenticated()
                )
                // Stateless API: disable CSRF so POST/stream calls work without a token cookie
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                // Configure the server to accept and validate JWTs as bearer tokens
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));

        return http.build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.security.enabled", havingValue = "false")
    public SecurityWebFilterChain springSecurityRelaxedFilterChain(ServerHttpSecurity http) {
        http
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                .csrf(ServerHttpSecurity.CsrfSpec::disable);

        return http.build();
    }
}
