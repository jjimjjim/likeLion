package dongneidle.DayMaker.service;

import dongneidle.DayMaker.DTO.ItineraryResponse;
import dongneidle.DayMaker.DTO.PlaceDetailsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class GooglePlacesService {
    
    @Value("${google.api.key:}")
    private String googleApiKey;
    
    private final RestTemplate restTemplate;
    
    // 안양시 중심 좌표 (대략적인 위치)
    private static final double ANYANG_LAT = 37.3942;
    private static final double ANYANG_LNG = 126.9569;
    private static final int DEFAULT_RADIUS = 10000; // 10km 반경

    // 기본 품질 필터 기준
    private static final double BASE_MIN_RATING = 4.0; // 최소 평점
    private static final int BASE_MIN_REVIEWS = 20;    // 최소 리뷰 수
    private static final int DEFAULT_MAX_RESULTS = 12;  // 기본 최대 반환 개수 (상향)
    private static final String V1_BASE = "https://places.googleapis.com/v1";

    /*
     * 고정 기본 설정으로 검색 (하위 호환)
     */
    public List<ItineraryResponse.PlaceDto> searchPlaces(String type, String keyword) {
        return searchPlaces(type, keyword, DEFAULT_MAX_RESULTS);
    }

    /*
     * 원하는 후보 개수에 따라 동적으로 결과 수/필터/반경을 조정하여 검색
     */
    public List<ItineraryResponse.PlaceDto> searchPlaces(String type, String keyword, int desiredCount) {
        if (googleApiKey.isEmpty()) {
            log.warn("Google API key not configured, returning dummy data");
            return getDummyPlaces(type, keyword);
        }

        // 동적 목표 개수: N*3 이상 확보 시도 (최대 후보군)
        final int targetMax = Math.max(DEFAULT_MAX_RESULTS, desiredCount * 3);

        // 점진적 완화 전략
        int[] radii = new int[]{DEFAULT_RADIUS, (int)(DEFAULT_RADIUS * 1.3), (int)(DEFAULT_RADIUS * 1.6)}; // 10km, 13km, 16km
        double[] minRatings = new double[]{BASE_MIN_RATING, 3.8, 3.5};
        int[] minReviews = new int[]{BASE_MIN_REVIEWS, 10, 0};

        Set<String> seenIds = new HashSet<>();
        List<ItineraryResponse.PlaceDto> aggregated = new ArrayList<>();

        for (int i = 0; i < radii.length; i++) {
            List<Map<String, Object>> rawResults = callPlacesApi(radii[i], type, keyword);
            List<ItineraryResponse.PlaceDto> filtered = convertAndFilter(rawResults, minRatings[i], minReviews[i], seenIds);
            aggregated.addAll(filtered);
            log.info("Places fetched (radius={}m, rating>={}, reviews>={}): +{} (agg={})", radii[i], minRatings[i], minReviews[i], filtered.size(), aggregated.size());
            if (aggregated.size() >= targetMax) break;
        }

        // 목표 개수까지 자르기
        if (aggregated.size() > targetMax) {
            aggregated = aggregated.subList(0, targetMax);
        }
        return aggregated;
    }

    /**
    * 특정 좌표 기준으로 장소 검색 (역 기반 검색용)
     */
    public List<ItineraryResponse.PlaceDto> searchPlacesNearLocation(String type, String keyword, double latitude, double longitude, int radiusMeters, int desiredCount) {
        if (googleApiKey.isEmpty()) {
            log.warn("Google API key not configured, returning dummy data");
            return getDummyPlaces(type, keyword);
        }

        try {
            String url = String.format(
                "https://maps.googleapis.com/maps/api/place/nearbysearch/json?" +
                "location=%f,%f&radius=%d&type=%s&keyword=%s&key=%s&language=ko",
                latitude, longitude, radiusMeters, type, keyword, googleApiKey
            );
            log.info("Calling Google Places API (location-based): {} (radius: {}m)", url.replace(googleApiKey, "***"), radiusMeters);
            
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response != null && "OK".equals(response.get("status"))) {
                List<Map<String, Object>> rawResults = (List<Map<String, Object>>) response.get("results");
                List<ItineraryResponse.PlaceDto> places = convertAndFilter(rawResults, BASE_MIN_RATING, BASE_MIN_REVIEWS, new HashSet<>());
                
                // 원하는 개수만큼 반환
                if (places.size() > desiredCount) {
                    places = places.subList(0, desiredCount);
                }
                return places;
            } else {
                log.error("Google Places API error: {}", response);
                return List.of();
            }
        } catch (Exception e) {
            log.error("Error calling Google Places API (location-based)", e);
            return List.of();
        }
    }

    private List<Map<String, Object>> callPlacesApi(int radiusMeters, String type, String keyword) {
        try {
            String url = String.format(
                "https://maps.googleapis.com/maps/api/place/nearbysearch/json?" +
                "location=%f,%f&radius=%d&type=%s&keyword=%s&key=%s&language=ko",
                ANYANG_LAT, ANYANG_LNG, radiusMeters, type, keyword, googleApiKey
            );
            log.info("Calling Google Places API: {}", url.replace(googleApiKey, "***"));
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response != null && "OK".equals(response.get("status"))) {
                return (List<Map<String, Object>>) response.get("results");
            } else {
                log.error("Google Places API error: {}", response);
                return List.of();
            }
        } catch (Exception e) {
            log.error("Error calling Google Places API", e);
            return List.of();
        }
    }

    private List<ItineraryResponse.PlaceDto> convertAndFilter(List<Map<String, Object>> googleResults,
                                                              double minRating,
                                                              int minReviews,
                                                              Set<String> seenPlaceIds) {
        List<ItineraryResponse.PlaceDto> places = new ArrayList<>();
        for (Map<String, Object> result : googleResults) {
            try {
                Map<String, Object> geometry = (Map<String, Object>) result.get("geometry");
                Map<String, Object> location = (Map<String, Object>) geometry.get("location");

                // 안양시 필터링 (vicinity에 "안양" 포함)
                String vicinity = (String) result.get("vicinity");
                if (vicinity == null || !vicinity.contains("안양")) {
                    continue;
                }

                String placeId = (String) result.get("place_id");
                if (placeId == null || seenPlaceIds.contains(placeId)) {
                    continue;
                }

                double rating = safeDouble(result.get("rating"), 0.0);
                int reviews = safeInt(result.get("user_ratings_total"), 0);

                // 품질 필터
                if (rating < minRating || reviews < minReviews) {
                    continue;
                }

                ItineraryResponse.PlaceDto place = ItineraryResponse.PlaceDto.builder()
                        .name((String) result.get("name"))
                        .category(determineCategory((List<String>) result.get("types")))
                        .address(vicinity)
                        .latitude(safeDouble(location.get("lat"), 0.0))
                        .longitude(safeDouble(location.get("lng"), 0.0))
                        .rating(rating)
                        .placeId(placeId)
                        .imageUrl(null)
                        .build();

                places.add(place);
                seenPlaceIds.add(placeId);
            } catch (Exception e) {
                log.warn("Error converting Google result: {}", e.getMessage());
            }
        }
        return places;
    }

    private String determineCategory(List<String> types) {
        if (types == null) return "OTHER";
        if (types.contains("restaurant")) return "RESTAURANT";
        if (types.contains("cafe")) return "CAFE";
        if (types.contains("movie_theater")) return "MOVIE";
        if (types.contains("art_gallery") || types.contains("museum")) return "CULTURE";
        if (types.contains("tourist_attraction")) return "ATTRACTION";
        return "OTHER";
    }

    private double safeDouble(Object value, double defaultValue) {
        if (value instanceof Number num) {
            return num.doubleValue();
        }
        try {
            return value != null ? Double.parseDouble(value.toString()) : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private Double safeDoubleObj(Object value, Double defaultValue) {
        if (value instanceof Number num) {
            return num.doubleValue();
        }
        try {
            return value != null ? Double.parseDouble(value.toString()) : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private int safeInt(Object value, int defaultValue) {
        if (value instanceof Number num) {
            return num.intValue();
        }
        try {
            return value != null ? Integer.parseInt(value.toString()) : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private List<ItineraryResponse.PlaceDto> getDummyPlaces(String type, String keyword) {
        List<ItineraryResponse.PlaceDto> places = new ArrayList<>();
        if (type.contains("restaurant") || type.contains("cafe")) {
            places.add(ItineraryResponse.PlaceDto.builder()
                    .name("안양 감성 카페")
                    .category("CAFE")
                    .address("안양시 만안구")
                    .latitude(37.3942)
                    .longitude(126.9569)
                    .rating(4.5)
                    .placeId("dummy-cafe-1")
                    .imageUrl(null)
                    .build());
            places.add(ItineraryResponse.PlaceDto.builder()
                    .name("안양 한식 맛집")
                    .category("RESTAURANT")
                    .address("안양시 동안구")
                    .latitude(37.4016)
                    .longitude(126.9228)
                    .rating(4.2)
                    .placeId("dummy-restaurant-1")
                    .imageUrl(null)
                    .build());
        }
        if (type.contains("movie_theater") || type.contains("art_gallery")) {
            places.add(ItineraryResponse.PlaceDto.builder()
                    .name("안양 영화관")
                    .category("MOVIE")
                    .address("안양시 동안구")
                    .latitude(37.3980)
                    .longitude(126.9300)
                    .rating(4.0)
                    .placeId("dummy-movie-1")
                    .imageUrl(null)
                    .build());
        }
        return places;
    }

    // ===================== v1 Place Details / Photos =====================

    /**
     * v1 Place Details 호출 (FieldMask 사용)
     */
    public PlaceDetailsDto fetchPlaceDetailsV1(String placeId, int maxPhotos) {
        if (googleApiKey.isEmpty()) {
            log.warn("Google API key not configured, returning minimal details");
            return PlaceDetailsDto.builder().placeId(placeId).build();
        }

        try {
            String fields = String.join(",",
                    "displayName",
                    "types",
                    "primaryType",
                    "rating",
                    "userRatingCount",
                    "formattedAddress",
                    "currentOpeningHours",
                    "internationalPhoneNumber",
                    "location",
                    "editorialSummary",
                    "parkingOptions",
                    "photos"
            );

            String url = V1_BASE + "/places/" + placeId + "?languageCode=ko";

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.add("X-Goog-Api-Key", googleApiKey);
            headers.add("X-Goog-FieldMask", fields);
            org.springframework.http.HttpEntity<Void> entity = new org.springframework.http.HttpEntity<>(headers);

            org.springframework.http.ResponseEntity<Map> resp =
                    new org.springframework.web.client.RestTemplate().exchange(
                            url,
                            org.springframework.http.HttpMethod.GET,
                            entity,
                            Map.class
                    );

            Map body = resp.getBody();
            if (body == null) return PlaceDetailsDto.builder().placeId(placeId).build();

            // 파싱
            String name = null;
            Object displayName = body.get("displayName");
            if (displayName instanceof Map dn) {
                Object text = dn.get("text");
                if (text != null) name = text.toString();
            }

            String primaryType = (String) body.get("primaryType");
            List<String> types = (List<String>) body.get("types");
            String category = primaryType != null ? primaryType : (types != null && !types.isEmpty() ? types.get(0) : null);

            Map loc = (Map) body.get("location");
            Double lat = loc != null ? safeDoubleObj(loc.get("latitude"), null) : null;
            Double lng = loc != null ? safeDoubleObj(loc.get("longitude"), null) : null;

            Double rating = safeDoubleObj(body.get("rating"), null);
            Integer userRatingCount = body.get("userRatingCount") instanceof Number n ? n.intValue() : null;

            String formattedAddress = (String) body.get("formattedAddress");

            // opening
            Boolean openingNow = null;
            List<String> weekday = null;
            Map coh = (Map) body.get("currentOpeningHours");
            if (coh != null) {
                openingNow = (Boolean) coh.get("openNow");
                weekday = (List<String>) coh.get("weekdayDescriptions");
            }

            String phone = (String) body.get("internationalPhoneNumber");

            // editorial summary
            String overview = null;
            Map es = (Map) body.get("editorialSummary");
            if (es != null) overview = (String) es.get("overview");

            // parking options: 다양한 불리언 필드를 요약 문자열로 변환
            List<String> parkingSummaries = new java.util.ArrayList<>();
            Map po = (Map) body.get("parkingOptions");
            if (po != null) {
                for (Object k : po.keySet()) {
                    Object v = po.get(k);
                    if (v instanceof Boolean b && b) {
                        parkingSummaries.add(k.toString());
                    }
                }
            }

            // photos -> media url 목록 생성
            List<String> photoUrls = new java.util.ArrayList<>();
            List<Map> photos = (List<Map>) body.get("photos");
            if (photos != null) {
                int limit = Math.min(maxPhotos, photos.size());
                for (int i = 0; i < limit; i++) {
                    Map p = photos.get(i);
                    String namePath = (String) p.get("name"); // e.g., places/XXXX/photos/XXXX
                    if (namePath != null) {
                        // v1 Media: GET https://places.googleapis.com/v1/{name}:media
                        String mediaUrl = V1_BASE + "/" + namePath + ":media?key=" + googleApiKey + "&maxHeightPx=800";
                        photoUrls.add(mediaUrl);
                    }
                }
            }

            return PlaceDetailsDto.builder()
                    .placeId(placeId)
                    .name(name)
                    .category(category)
                    .address(formattedAddress)
                    .latitude(lat)
                    .longitude(lng)
                    .rating(rating)
                    .userRatingCount(userRatingCount)
                    .overview(overview)
                    .openingNow(openingNow)
                    .openingHours(weekday)
                    .phone(phone)
                    .parkingOptions(parkingSummaries)
                    .photoUrls(photoUrls)
                    .build();
        } catch (Exception e) {
            log.error("Error calling v1 Place Details for {}: {}", placeId, e.getMessage());
            return PlaceDetailsDto.builder().placeId(placeId).build();
        }
    }

    // ===================== v1 Nearby: Parking =====================

    /**
     * 좌표/반경 기반 주차장 검색 (Places API v1 Nearby)
     */
    public List<ItineraryResponse.PlaceDto> searchNearbyParkingV1(double latitude,
                                                                  double longitude,
                                                                  int radiusMeters,
                                                                  int maxResults) {
        if (googleApiKey.isEmpty()) {
            log.warn("Google API key not configured, returning empty list for parking");
            return java.util.List.of();
        }

        try {
            String url = V1_BASE + "/places:searchNearby";

            // FieldMask: 필요한 필드만 요청
            String fieldMask = String.join(",",
                    "places.name",
                    "places.displayName",
                    "places.location",
                    "places.formattedAddress",
                    "places.rating",
                    "places.userRatingCount",
                    "places.googleMapsUri"
            );

            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("includedTypes", java.util.List.of("parking"));
            body.put("languageCode", "ko");
            java.util.Map<String, Object> circle = new java.util.HashMap<>();
            java.util.Map<String, Object> center = new java.util.HashMap<>();
            center.put("latitude", latitude);
            center.put("longitude", longitude);
            circle.put("center", center);
            circle.put("radius", radiusMeters);
            java.util.Map<String, Object> locationRestriction = new java.util.HashMap<>();
            locationRestriction.put("circle", circle);
            body.put("locationRestriction", locationRestriction);
            body.put("maxResultCount", Math.max(1, Math.min(maxResults, 50))); // v1 제한 보호
            body.put("rankPreference", "DISTANCE");

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.add("X-Goog-Api-Key", googleApiKey);
            headers.add("X-Goog-FieldMask", fieldMask);
            headers.add("Content-Type", "application/json");

            org.springframework.http.HttpEntity<java.util.Map<String, Object>> entity =
                    new org.springframework.http.HttpEntity<>(body, headers);

            org.springframework.http.ResponseEntity<java.util.Map> resp = restTemplate.exchange(
                    url,
                    org.springframework.http.HttpMethod.POST,
                    entity,
                    java.util.Map.class
            );

            java.util.Map response = resp.getBody();
            if (response == null) return java.util.List.of();

            java.util.List<java.util.Map> places = (java.util.List<java.util.Map>) response.get("places");
            if (places == null || places.isEmpty()) {
                // 1차 실패 시 primaryTypes로 재시도
                body.remove("includedTypes");
                body.put("includedPrimaryTypes", java.util.List.of("parking"));
                entity = new org.springframework.http.HttpEntity<>(body, headers);
                resp = restTemplate.exchange(url, org.springframework.http.HttpMethod.POST, entity, java.util.Map.class);
                response = resp.getBody();
                if (response != null) {
                    places = (java.util.List<java.util.Map>) response.get("places");
                }
            }
            if (places == null || places.isEmpty()) {
                // 2차 실패 시 레거시 NearbySearch로 폴백
                return searchNearbyParkingLegacy(latitude, longitude, radiusMeters, maxResults);
            }

            java.util.List<ItineraryResponse.PlaceDto> results = new java.util.ArrayList<>();
            for (java.util.Map p : places) {
                // displayName
                String display = null;
                Object dn = p.get("displayName");
                if (dn instanceof java.util.Map dm) {
                    Object text = dm.get("text");
                    if (text != null) display = text.toString();
                }
                // address
                String addr = (String) p.get("formattedAddress");
                // location
                java.util.Map loc = (java.util.Map) p.get("location");
                Double lat = loc != null ? safeDoubleObj(loc.get("latitude"), null) : null;
                Double lng = loc != null ? safeDoubleObj(loc.get("longitude"), null) : null;
                // rating
                Double rating = safeDoubleObj(p.get("rating"), null);
                // placeId 추출 (name은 places/{place_id})
                String nameRes = (String) p.get("name");
                String placeId = extractPlaceIdFromName(nameRes);

                ItineraryResponse.PlaceDto dto = ItineraryResponse.PlaceDto.builder()
                        .name(display)
                        .category("PARKING")
                        .address(addr)
                        .latitude(lat)
                        .longitude(lng)
                        .rating(rating)
                        .placeId(placeId)
                        .imageUrl(null)
                        .build();
                results.add(dto);
            }
            return results;
        } catch (Exception e) {
            log.error("Error calling v1 Nearby parking: {}", e.getMessage());
            // 예외 시 레거시로 폴백
            return searchNearbyParkingLegacy(latitude, longitude, radiusMeters, maxResults);
        }
    }

    public List<ItineraryResponse.PlaceDto> searchNearbyParkingV1(double latitude,
                                                                  double longitude,
                                                                  int radiusMeters,
                                                                  int maxResults,
                                                                  String languageCode,
                                                                  String rankPreference) {
        if (languageCode == null || languageCode.isBlank()) languageCode = "ko";
        if (!"RELEVANCE".equals(rankPreference)) rankPreference = "DISTANCE";
        if (googleApiKey.isEmpty()) {
            log.warn("Google API key not configured, returning empty list for parking");
            return java.util.List.of();
        }
        try {
            String url = V1_BASE + "/places:searchNearby";
            String fieldMask = String.join(",",
                    "places.name","places.displayName","places.location","places.formattedAddress","places.rating","places.userRatingCount","places.googleMapsUri");
            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("includedTypes", java.util.List.of("parking"));
            body.put("languageCode", languageCode);
            java.util.Map<String, Object> circle = new java.util.HashMap<>();
            java.util.Map<String, Object> center = new java.util.HashMap<>();
            center.put("latitude", latitude);
            center.put("longitude", longitude);
            circle.put("center", center);
            circle.put("radius", radiusMeters);
            java.util.Map<String, Object> locationRestriction = new java.util.HashMap<>();
            locationRestriction.put("circle", circle);
            body.put("locationRestriction", locationRestriction);
            body.put("maxResultCount", Math.max(1, Math.min(maxResults, 50)));
            body.put("rankPreference", rankPreference);

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.add("X-Goog-Api-Key", googleApiKey);
            headers.add("X-Goog-FieldMask", fieldMask);
            headers.add("Content-Type", "application/json");

            org.springframework.http.HttpEntity<java.util.Map<String, Object>> entity =
                    new org.springframework.http.HttpEntity<>(body, headers);

            org.springframework.http.ResponseEntity<java.util.Map> resp = restTemplate.exchange(
                    url,
                    org.springframework.http.HttpMethod.POST,
                    entity,
                    java.util.Map.class
            );

            java.util.Map response = resp.getBody();
            java.util.List<java.util.Map> places = response != null ? (java.util.List<java.util.Map>) response.get("places") : null;
            if (places == null || places.isEmpty()) {
                return searchNearbyParkingLegacy(latitude, longitude, radiusMeters, maxResults);
            }
            java.util.List<ItineraryResponse.PlaceDto> results = new java.util.ArrayList<>();
            for (java.util.Map p : places) {
                String display = null;
                Object dn = p.get("displayName");
                if (dn instanceof java.util.Map dm) {
                    Object text = dm.get("text");
                    if (text != null) display = text.toString();
                }
                String addr = (String) p.get("formattedAddress");
                java.util.Map loc = (java.util.Map) p.get("location");
                Double lat = loc != null ? safeDoubleObj(loc.get("latitude"), null) : null;
                Double lng = loc != null ? safeDoubleObj(loc.get("longitude"), null) : null;
                Double rating = safeDoubleObj(p.get("rating"), null);
                String nameRes = (String) p.get("name");
                String placeId = extractPlaceIdFromName(nameRes);
                results.add(ItineraryResponse.PlaceDto.builder()
                        .name(display)
                        .category("PARKING")
                        .address(addr)
                        .latitude(lat)
                        .longitude(lng)
                        .rating(rating)
                        .placeId(placeId)
                        .imageUrl(null)
                        .build());
            }
            return results;
        } catch (Exception e) {
            log.error("Error calling v1 Nearby parking (extended): {}", e.getMessage());
            return searchNearbyParkingLegacy(latitude, longitude, radiusMeters, maxResults);
        }
    }

    private String extractPlaceIdFromName(String nameResource) {
        if (nameResource == null) return null;
        int idx = nameResource.lastIndexOf('/');
        return idx >= 0 ? nameResource.substring(idx + 1) : nameResource;
    }

    /**
     * 레거시 NearbySearch를 사용한 주차장 검색 폴백 (필터 완화)
     */
    private List<ItineraryResponse.PlaceDto> searchNearbyParkingLegacy(double latitude,
                                                                       double longitude,
                                                                       int radiusMeters,
                                                                       int maxResults) {
        try {
            String url = String.format(
                    "https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=%f,%f&radius=%d&type=parking&key=%s&language=ko",
                    latitude, longitude, radiusMeters, googleApiKey
            );
            log.info("Fallback Legacy Nearby parking: {}", url.replace(googleApiKey, "***"));
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null || response.get("results") == null) return java.util.List.of();
            List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
            java.util.List<ItineraryResponse.PlaceDto> out = new java.util.ArrayList<>();
            for (Map<String, Object> r : results) {
                try {
                    Map<String, Object> geometry = (Map<String, Object>) r.get("geometry");
                    Map<String, Object> location = geometry != null ? (Map<String, Object>) geometry.get("location") : null;
                    String placeId = (String) r.get("place_id");
                    String name = (String) r.get("name");
                    String addr = (String) r.get("vicinity");
                    Double lat = location != null ? safeDoubleObj(location.get("lat"), null) : null;
                    Double lng = location != null ? safeDoubleObj(location.get("lng"), null) : null;
                    Double rating = safeDoubleObj(r.get("rating"), null);
                    out.add(ItineraryResponse.PlaceDto.builder()
                            .name(name)
                            .category("PARKING")
                            .address(addr)
                            .latitude(lat)
                            .longitude(lng)
                            .rating(rating)
                            .placeId(placeId)
                            .imageUrl(null)
                            .build());
                    if (out.size() >= maxResults) break;
                } catch (Exception ignore) {}
            }
            return out;
        } catch (Exception e) {
            log.error("Legacy Nearby parking failed: {}", e.getMessage());
            return java.util.List.of();
        }
    }

}

