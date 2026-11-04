package com.gmart.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Gives every incoming request an X-Request-Id (replacing any value sent by the client)
 * and returns it in the response, so a customer-reported problem can be traced in the logs.
 */
@Component
public class RequestIdGatewayFilter implements GlobalFilter, Ordered {

    static final String HEADER = "X-Request-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String id = UUID.randomUUID().toString();
        ServerHttpRequest request = exchange.getRequest().mutate().headers(h -> h.set(HEADER, id)).build();
        exchange.getResponse().getHeaders().set(HEADER, id);
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
