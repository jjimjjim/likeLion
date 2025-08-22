package dongneidle.DayMaker.controller;

import dongneidle.DayMaker.DTO.StationRequest;
import dongneidle.DayMaker.entity.Station;
import dongneidle.DayMaker.service.StationBasedCourseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stations")
@RequiredArgsConstructor
@Slf4j
public class StationController {

    private final StationBasedCourseService stationBasedCourseService;

    /**
     * 사용 가능한 모든 역 목록 조회
     */
    @GetMapping
    public ResponseEntity<List<Station>> getAvailableStations() {
        log.info("사용 가능한 역 목록 조회 요청");
        List<Station> stations = stationBasedCourseService.getAvailableStations();
        return ResponseEntity.ok(stations);
    }

    /**
     * 특정 역 정보 조회
     */
    @GetMapping("/{stationName}")
    public ResponseEntity<Station> getStationByName(@PathVariable String stationName) {
        log.info("역 정보 조회 요청: {}", stationName);
        return stationBasedCourseService.getStationByName(stationName)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 선택된 역을 기준으로 코스 추천
     */
    @PostMapping("/recommend-course")
    public ResponseEntity<?> recommendCourseFromStation(@RequestBody StationRequest request) {
        try {
            log.info("역 기반 코스 추천 요청: {}", request.getSelectedStation());
            
            if (request.getSelectedStation() == null || request.getSelectedStation().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "역을 선택해주세요."
                ));
            }
            
            String courseRecommendation = stationBasedCourseService.recommendCourseFromStation(request);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "코스 추천이 완료되었습니다.",
                "data", courseRecommendation
            ));
            
        } catch (Exception e) {
            log.error("역 기반 코스 추천 중 오류 발생", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "코스 추천 중 오류가 발생했습니다: " + e.getMessage()
            ));
        }
    }
}
