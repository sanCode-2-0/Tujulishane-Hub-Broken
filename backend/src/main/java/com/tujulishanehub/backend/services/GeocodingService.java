package com.tujulishanehub.backend.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.cache.annotation.Cacheable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GeocodingService {
    
    private static final Logger logger = LoggerFactory.getLogger(GeocodingService.class);
    
    @Value("${geocoding.api.key:}")
    private String apiKey;
    
    @Value("${geocoding.enabled:true}")
    private boolean geocodingEnabled;
    
    private final RestTemplate restTemplate;
    
    // Patterns for extracting coordinates from text
    private static final Pattern COORDINATES_PATTERN = Pattern.compile(
        "(-?\\d+\\.\\d+)\\s*,\\s*(-?\\d+\\.\\d+)"
    );
    
    private static final Pattern LAT_LNG_PATTERN = Pattern.compile(
        "(?:lat|latitude)[:\\s]*(-?\\d+\\.\\d+).*?(?:lng|lon|longitude)[:\\s]*(-?\\d+\\.\\d+)",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    public GeocodingService() {
        this.restTemplate = new RestTemplate();
    }
    
    /**
     * Extract coordinates from maps_address field
     * This method tries multiple strategies:
     * 1. Parse direct coordinates (lat,lng format)
     * 2. Parse named coordinate fields (lat: x, lng: y)
     * 3. Use geocoding API if configured
     * 4. Return default Kenya center coordinates as fallback
     */
    @Cacheable(value = "geocoding", key = "#mapsAddress", unless = "#result == null || !#result.valid")
    public CoordinateResult extractCoordinates(String mapsAddress) {
        if (mapsAddress == null || mapsAddress.trim().isEmpty()) {
            return new CoordinateResult(null, null, "No address provided");
        }
        
        // Strategy 1: Try to parse direct coordinates (e.g., "-1.2921, 36.8219")
        CoordinateResult directResult = parseDirectCoordinates(mapsAddress);
        if (directResult.isValid()) {
            logger.info("Extracted direct coordinates from address: {}", mapsAddress);
            return directResult;
        }
        
        // Strategy 2: Try to parse named coordinate fields
        CoordinateResult namedResult = parseNamedCoordinates(mapsAddress);
        if (namedResult.isValid()) {
            logger.info("Extracted named coordinates from address: {}", mapsAddress);
            return namedResult;
        }
        
        // Strategy 3: Use geocoding API if enabled and configured
        if (geocodingEnabled && !apiKey.isEmpty()) {
            CoordinateResult geocodedResult = geocodeAddress(mapsAddress);
            if (geocodedResult.isValid()) {
                logger.info("Geocoded address successfully: {}", mapsAddress);
                return geocodedResult;
            }
        }
        
        // Strategy 4: Return Kenya center coordinates as fallback
        logger.warn("Could not extract coordinates from address '{}', using Kenya center as fallback", mapsAddress);
        return new CoordinateResult(-0.0236, 37.9062, "Used Kenya center coordinates as fallback");
    }
    
    private CoordinateResult parseDirectCoordinates(String address) {
        Matcher matcher = COORDINATES_PATTERN.matcher(address);
        if (matcher.find()) {
            try {
                double lat = Double.parseDouble(matcher.group(1));
                double lng = Double.parseDouble(matcher.group(2));
                
                if (isValidKenyaCoordinate(lat, lng)) {
                    return new CoordinateResult(lat, lng, "Parsed direct coordinates");
                }
            } catch (NumberFormatException e) {
                logger.debug("Failed to parse coordinates: {}", e.getMessage());
            }
        }
        return new CoordinateResult(null, null, "No direct coordinates found");
    }
    
    private CoordinateResult parseNamedCoordinates(String address) {
        Matcher matcher = LAT_LNG_PATTERN.matcher(address);
        if (matcher.find()) {
            try {
                double lat = Double.parseDouble(matcher.group(1));
                double lng = Double.parseDouble(matcher.group(2));
                
                if (isValidKenyaCoordinate(lat, lng)) {
                    return new CoordinateResult(lat, lng, "Parsed named coordinates");
                }
            } catch (NumberFormatException e) {
                logger.debug("Failed to parse named coordinates: {}", e.getMessage());
            }
        }
        return new CoordinateResult(null, null, "No named coordinates found");
    }
    
    private CoordinateResult geocodeAddress(String address) {
        try {
            // Using OpenStreetMap Nominatim API as a free alternative
            // For production, consider using Google Maps Geocoding API or similar
            String url = UriComponentsBuilder
                .fromHttpUrl("https://nominatim.openstreetmap.org/search")
                .queryParam("q", address + ", Kenya") // Add Kenya to improve accuracy
                .queryParam("format", "json")
                .queryParam("limit", "1")
                .queryParam("countrycodes", "ke") // Restrict to Kenya
                .build()
                .toUriString();
            
            // Add a user agent header as required by Nominatim
            restTemplate.getInterceptors().clear();
            restTemplate.getInterceptors().add((request, body, execution) -> {
                request.getHeaders().add("User-Agent", "TujulishaneHub/1.0 (contact@tujulishanehub.org)");
                return execution.execute(request, body);
            });
            
            var response = restTemplate.getForObject(url, Object[].class);
            
            if (response != null && response.length > 0) {
                var firstResult = (java.util.Map<?, ?>) response[0];
                double lat = Double.parseDouble(firstResult.get("lat").toString());
                double lng = Double.parseDouble(firstResult.get("lon").toString());
                
                if (isValidKenyaCoordinate(lat, lng)) {
                    return new CoordinateResult(lat, lng, "Geocoded using Nominatim API");
                }
            }
        } catch (Exception e) {
            logger.warn("Geocoding failed for address '{}': {}", address, e.getMessage());
        }
        
        return new CoordinateResult(null, null, "Geocoding failed");
    }
    
    /**
     * Validate if coordinates are within Kenya's approximate bounds
     */
    private boolean isValidKenyaCoordinate(double lat, double lng) {
        // Kenya approximate bounds: lat -5 to 5, lng 34 to 42
        return lat >= -5.0 && lat <= 5.0 && lng >= 34.0 && lng <= 42.0;
    }
    
    /**
     * Result class for coordinate extraction
     */
    public static class CoordinateResult implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        private final Double latitude;
        private final Double longitude;
        private final String message;
        
        public CoordinateResult(Double latitude, Double longitude, String message) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.message = message;
        }
        
        public Double getLatitude() {
            return latitude;
        }
        
        public Double getLongitude() {
            return longitude;
        }
        
        public String getMessage() {
            return message;
        }
        
        public boolean isValid() {
            return latitude != null && longitude != null;
        }
        
        @Override
        public String toString() {
            if (isValid()) {
                return String.format("CoordinateResult{lat=%.6f, lng=%.6f, message='%s'}", 
                    latitude, longitude, message);
            } else {
                return String.format("CoordinateResult{invalid, message='%s'}", message);
            }
        }
    }
}