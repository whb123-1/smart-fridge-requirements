package com.xianzhi.fridge.shared.security;

import com.xianzhi.fridge.shared.config.AppProperties;
import com.xianzhi.fridge.shared.web.ApiEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SameOriginFilter extends OncePerRequestFilter {
    private static final Set<String> SAFE=Set.of("GET","HEAD","OPTIONS");
    private final AppProperties properties;private final ObjectMapper mapper;private final Environment environment;
    public SameOriginFilter(AppProperties properties,ObjectMapper mapper,Environment environment){this.properties=properties;this.mapper=mapper;this.environment=environment;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        if(environment.matchesProfiles("prod")&&!SAFE.contains(request.getMethod())){
            String origin=request.getHeader("Origin");String fetchSite=request.getHeader("Sec-Fetch-Site");
            boolean crossSite="cross-site".equalsIgnoreCase(fetchSite)||origin!=null&&!origin.isBlank()&&!origin.equals(properties.getPublicUrl());
            if(crossSite){response.setStatus(403);response.setContentType(MediaType.APPLICATION_JSON_VALUE);mapper.writeValue(response.getOutputStream(),ApiEnvelope.error("CROSS_ORIGIN_REQUEST_REJECTED","Cross-origin state changes are not allowed",Map.of()));return;}
        }
        chain.doFilter(request,response);
    }
}
