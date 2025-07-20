package com.cosmicdoc.inventoryservice.config;

import com.cosmicdoc.inventoryservice.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TestTenantFilter extends OncePerRequestFilter {

    private static final String TEST_ORG_ID = "test-org-123";
    private static final String TEST_BRANCH_ID = "test-branch-456";
    private static final String TEST_USER_ID = "test-user-789";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            // Set default test values for TenantContext
            TenantContext.setContext(TEST_ORG_ID, TEST_BRANCH_ID, TEST_USER_ID);
            
            // Continue with the request
            filterChain.doFilter(request, response);
        } finally {
            // Clear the context after the request is processed
            TenantContext.clear();
        }
    }
}
