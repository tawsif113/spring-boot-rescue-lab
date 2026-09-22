package com.tawsif.rescuelab.config;

import static com.tawsif.rescuelab.security.DemoIdentities.ALICE_CUSTOMER_ID;
import static com.tawsif.rescuelab.security.DemoIdentities.BOB_CUSTOMER_ID;

import com.tawsif.rescuelab.security.RescueUserPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/error", "/actuator/health", "/actuator/health/**", "/actuator/info")
                        .permitAll()
                        .requestMatchers("/actuator/prometheus").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/orders").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/orders/**").authenticated()
                        .anyRequest().denyAll()
                )
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(
            PasswordEncoder passwordEncoder,
            @Value("${rescue-lab.security.alice-password:alice-change-me}") String alicePassword,
            @Value("${rescue-lab.security.bob-password:bob-change-me}") String bobPassword,
            @Value("${rescue-lab.security.admin-password:admin-change-me}") String adminPassword
    ) {
        return new InMemoryUserDetailsManager(
                RescueUserPrincipal.customer(ALICE_CUSTOMER_ID, "alice", passwordEncoder.encode(alicePassword)),
                RescueUserPrincipal.customer(BOB_CUSTOMER_ID, "bob", passwordEncoder.encode(bobPassword)),
                RescueUserPrincipal.administrator("admin", passwordEncoder.encode(adminPassword))
        );
    }
}
