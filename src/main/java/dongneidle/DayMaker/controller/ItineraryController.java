package dongneidle.DayMaker.controller;

import dongneidle.DayMaker.DTO.ItineraryRequest;
import dongneidle.DayMaker.DTO.ItineraryResponse;
import dongneidle.DayMaker.DTO.ItinerarySaveRequest;
import dongneidle.DayMaker.DTO.ItinerarySummaryResponse;
import dongneidle.DayMaker.service.ItineraryService;
import dongneidle.DayMaker.service.ItinerarySaveService;
import dongneidle.DayMaker.util.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/itineraries")
@RequiredArgsConstructor
public class ItineraryController {

    private final ItineraryService itineraryService;
    private final ItinerarySaveService itinerarySaveService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping
    @Operation(summary = "여행 추천 생성", description = "입력 선호에 맞춘 장소 N개와 최적 동선 반환")
    public ResponseEntity<ItineraryResponse> create(@RequestBody ItineraryRequest request) {
        ItineraryResponse response = itineraryService.createItinerary(request);
        return ResponseEntity.ok(response);
    }
//AI 코스 생성 기능
    @PostMapping("/generate")
    @Operation(summary = "AI 여행 추천 생성", description = "입력 선호에 맞춘 장소 N개와 최적 동선 반환")
    public ResponseEntity<ItineraryResponse> generateItinerary(@RequestBody ItineraryRequest request) {
        ItineraryResponse response = itineraryService.createItinerary(request);
        return ResponseEntity.ok(response);
    }

    ////저장기능
    @PostMapping("/save")//코스 저장 → 생성된 코스 id 반환
    public ResponseEntity<?> save(
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ItinerarySaveRequest request) {
        String email = extractEmailFromAuth(authorization);
        if (email == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED)
                    .body(java.util.Map.of("success", false, "message", "인증이 필요합니다."));
        }
        request.setUserEmail(email);
        Long id = itinerarySaveService.save(request);
        return ResponseEntity.ok(id);
    }

    @GetMapping("/mine")//내 코스 목록 요약 반환
    public ResponseEntity<?> myList(@org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorization) {
        String email = extractEmailFromAuth(authorization);
        if (email == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED)
                    .body(java.util.Map.of("success", false, "message", "인증이 필요합니다."));
        }
        java.util.List<ItinerarySummaryResponse> list = itinerarySaveService.listByUser(email);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")//코스 상세(ItineraryResponse) 반환
    public ResponseEntity<?> detail(
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id) {
        String email = extractEmailFromAuth(authorization);
        if (email == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED)
                    .body(java.util.Map.of("success", false, "message", "인증이 필요합니다."));
        }
        ItineraryResponse detail = itinerarySaveService.getDetail(id, email);
        return ResponseEntity.ok(detail);
    }

    private String extractEmailFromAuth(String authorization) {
        if (authorization == null || authorization.isBlank()) return null;
        String value = authorization.trim();
        // 허용: 'Bearer xxx', 'bearer xxx', 'xxx' (접두어 없이 바로 토큰), 따옴표 포함 케이스
        String token;
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = value.substring(7).trim();
        } else {
            token = value;
        }
        // 양 끝 따옴표 제거
        if ((token.startsWith("\"") && token.endsWith("\"")) || (token.startsWith("'") && token.endsWith("'"))) {
            token = token.substring(1, token.length() - 1);
        }
        // 매우 기본적인 JWT 형태 검사(.
        int dotCount = (int) token.chars().filter(c -> c == '.').count();
        if (dotCount != 2) return null;
        String email = jwtTokenProvider.validateAndGetEmail(token);
        return email;
    }
}


