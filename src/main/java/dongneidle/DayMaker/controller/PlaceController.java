package dongneidle.DayMaker.controller;

import dongneidle.DayMaker.DTO.PlaceDetailsDto;
import dongneidle.DayMaker.DTO.ParkingSearchRequest;
import dongneidle.DayMaker.service.GooglePlacesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class PlaceController {

    private final GooglePlacesService googlePlacesService;

    @GetMapping("/{placeId}")
    @Operation(summary = "Place details", description = "v1 Place Details로 상세정보/사진 조회")
    public ResponseEntity<PlaceDetailsDto> getPlaceDetails(
            @Parameter(description = "Google Place ID") @PathVariable String placeId,
            @Parameter(description = "최대 사진 개수") @RequestParam(name = "maxPhotos", defaultValue = "3") int maxPhotos
    ) {
        PlaceDetailsDto dto = googlePlacesService.fetchPlaceDetailsV1(placeId, maxPhotos);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/parking")
    @Operation(summary = "주차장 검색(쿼리)", description = "좌표/반경 기반 v1 Nearby 주차장 검색")
    public ResponseEntity<java.util.List<dongneidle.DayMaker.DTO.ItineraryResponse.PlaceDto>> searchParking(
            @Parameter(description = "위도") @RequestParam double lat,
            @Parameter(description = "경도") @RequestParam double lng,
            @Parameter(description = "반경(m)") @RequestParam int radius,
            @Parameter(description = "최대 개수") @RequestParam(name = "limit", defaultValue = "20") int limit
    ) {
        var result = googlePlacesService.searchNearbyParkingV1(lat, lng, radius, limit);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/parking/search")
    @Operation(summary = "주차장 검색(바디)", description = "DTO 바디로 좌표/반경/옵션 전달")
    public ResponseEntity<java.util.List<dongneidle.DayMaker.DTO.ItineraryResponse.PlaceDto>> searchParkingPost(
            @org.springframework.web.bind.annotation.RequestBody ParkingSearchRequest request
    ) {
        int radius = request.getRadius() != null ? Math.max(50, Math.min(request.getRadius(), 50000)) : 1500;
        int limit = request.getLimit() != null ? Math.max(1, Math.min(request.getLimit(), 50)) : 20;
        String lang = request.getLanguage() != null ? request.getLanguage() : "ko";
        String rank = request.getRankPreference() != null ? request.getRankPreference() : "DISTANCE";
        var result = googlePlacesService.searchNearbyParkingV1(request.getLat(), request.getLng(), radius, limit, lang, rank);
        return ResponseEntity.ok(result);
    }
}


