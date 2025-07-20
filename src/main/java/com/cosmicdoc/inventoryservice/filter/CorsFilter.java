package com.cosmicdoc.inventoryservice.filter;

import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CorsFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        // Update to handle the origin of the request dynamically
        String origin = request.getHeader("Origin");
        if (origin != null) {
            // Allow the actual origin that made the request
            response.setHeader("Access-Control-Allow-Origin", origin);
        } else {
            // Fallback to healthcare app URL if no origin is present
            response.setHeader("Access-Control-Allow-Origin", "https://healthcare-app-1078740886343.us-central1.run.app, https://healthcare-app-145837205370.asia-south1.run.app");
        }
        
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "authorization, content-type, xsrf-token, x-auth-token, origin, accept, x-skip-loader");
        response.setHeader("Access-Control-Expose-Headers", "xsrf-token");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Max-Age", "3600");
        
        if ("OPTIONS".equals(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
        } else {
            filterChain.doFilter(request, response);
        }
    }
}
