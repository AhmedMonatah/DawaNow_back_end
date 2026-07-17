package com.example.dawanow.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        String reason = (String) request.getAttribute("expired_or_invalid_reason");

        // Default back to a readable message if nothing was set by the filter
        if (reason == null) {
            reason = "Authentication is required to access this resource.";
        }

        //Output the raw text blocks cleanly using formatted string templates
        response.getWriter().write("""
        {
          "success": false,
          "message": "%s",
          "data": null
        }
        """.formatted(reason));
    }
}
