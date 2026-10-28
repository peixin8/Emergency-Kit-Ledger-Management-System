package com.emergency.ledger;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/geocoding")
public class GeocodingController {
    private final GeocodingService service;

    public GeocodingController(GeocodingService service) { this.service = service; }

    @GetMapping("/search")
    public ApiResponse<GeocodingService.SearchResults> search(@RequestParam(required = false) String q) {
        return ApiResponse.ok(service.search(q));
    }
}
