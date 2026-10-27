package com.emergency.ledger;

import java.net.URI;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
@ConditionalOnProperty("app.frontend-url")
public class DevHomeController {
    private final URI frontendUrl;

    DevHomeController(@Value("${app.frontend-url}") String frontendUrl) {
        this.frontendUrl = URI.create(frontendUrl);
    }

    @GetMapping("/")
    ResponseEntity<Void> home(HttpServletRequest request) {
        URI destination = UriComponentsBuilder.fromUri(frontendUrl).host(request.getServerName()).build().toUri();
        return ResponseEntity.status(302).location(destination).build();
    }
}
