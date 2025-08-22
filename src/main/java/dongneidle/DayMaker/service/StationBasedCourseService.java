package dongneidle.DayMaker.service;

import dongneidle.DayMaker.DTO.StationRequest;
import dongneidle.DayMaker.DTO.ItineraryResponse;
import dongneidle.DayMaker.entity.Station;
import dongneidle.DayMaker.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class StationBasedCourseService {
    
    private final StationRepository stationRepository;
    private final GooglePlacesService googlePlacesService;
    private final GptService gptService;
    
    /**
     * 사용 가능한 모든 역 목록 조회
     */
    public List<Station> getAvailableStations() {
        return stationRepository.findAllByOrderByName();
    }
    
    /**
     * 선택된 역 정보 조회
     */
    public Optional<Station> getStationByName(String stationName) {
        return stationRepository.findByName(stationName);
    }
    
    /**
     * 선택된 역을 기준으로 코스 추천
     */
    public String recommendCourseFromStation(StationRequest request) {
        log.info("역 기반 코스 추천 시작: {}", request.getSelectedStation());
        
        // 1. 선택된 역 정보 조회
        Optional<Station> stationOpt = stationRepository.findByName(request.getSelectedStation());
        if (stationOpt.isEmpty()) {
            return "선택된 역을 찾을 수 없습니다: " + request.getSelectedStation();
        }
        
        Station station = stationOpt.get();
        log.info("선택된 역: {} (위도: {}, 경도: {})", 
                station.getName(), station.getLatitude(), station.getLongitude());
        
        // 2. 역 근처 장소 검색 (Google Places API 사용)
        String nearbyPlaces = searchNearbyPlaces(station);
        
        // 3. GPT를 사용하여 코스 추천
        String courseRecommendation = generateCourseWithGPT(request, station, nearbyPlaces);
        
        return courseRecommendation;
    }
    
    /**
     * 역 근처 장소 검색
     */
    private String searchNearbyPlaces(Station station) {
        try {
            // Google Places API를 사용하여 역 근처 장소 검색
            // 반경 2km 내의 장소들을 검색
            List<ItineraryResponse.PlaceDto> places = googlePlacesService.searchPlaces("restaurant", station.getName());
            
            // 장소 정보를 문자열로 변환
            StringBuilder result = new StringBuilder();
            for (ItineraryResponse.PlaceDto place : places) {
                result.append("- ").append(place.getName())
                      .append(" (평점: ").append(place.getRating()).append(")")
                      .append(": ").append(place.getAddress()).append("\n");
            }
            return result.toString();
        } catch (Exception e) {
            log.error("역 근처 장소 검색 중 오류 발생", e);
            return "장소 검색에 실패했습니다.";
        }
    }
    
    /**
     * GPT를 사용하여 코스 추천 생성 (GptService 방식 사용)
     */
    private String generateCourseWithGPT(StationRequest request, Station station, String nearbyPlaces) {
        try {
            // 역 근처 장소들을 PlaceDto 형태로 변환
            List<ItineraryResponse.PlaceDto> places = convertToPlaceDtos(nearbyPlaces);
            
            // GptService의 selectOptimalPlaces 사용 (더 정확하고 실용적)
            List<ItineraryResponse.PlaceDto> selectedPlaces = gptService.selectOptimalPlaces(
                places,
                request.getPeopleCount(),
                request.getTransportType(),
                4, // 기본 4개 장소
                request.getFoodType()
            );
            
            // 선택된 장소들로 코스 구성
            return buildCourseFromSelectedPlaces(selectedPlaces, station);
            
        } catch (Exception e) {
            log.error("GPT 코스 추천 생성 중 오류 발생", e);
            return "코스 추천 생성에 실패했습니다.";
        }
    }
    
    /**
     * 선택된 장소들로 코스 구성
     */
    private String buildCourseFromSelectedPlaces(List<ItineraryResponse.PlaceDto> places, Station station) {
        StringBuilder course = new StringBuilder();
        course.append("🚇 ").append(station.getName()).append("역 기준 추천 코스\n\n");
        
        for (int i = 0; i < places.size(); i++) {
            ItineraryResponse.PlaceDto place = places.get(i);
            course.append(i + 1).append(". ").append(place.getName())
                  .append(" (").append(place.getCategory()).append(")\n");
            course.append("   📍 ").append(place.getAddress()).append("\n");
            course.append("   ⭐ ").append(place.getRating()).append("점\n\n");
        }
        
        course.append("💡 이 코스는 ").append(station.getName()).append("역에서 도보로 이동 가능한 거리에 있는 장소들로 구성되었습니다.");
        
        return course.toString();
    }
    
    /**
     * 문자열 형태의 장소 정보를 PlaceDto로 변환 (간단한 구현)
     */
    private List<ItineraryResponse.PlaceDto> convertToPlaceDtos(String nearbyPlaces) {
        // 실제 구현에서는 Google Places API 결과를 직접 사용하는 것이 좋습니다
        // 여기서는 간단한 더미 데이터로 대체
        List<ItineraryResponse.PlaceDto> places = new ArrayList<>();
        
        // 더미 데이터 생성 (실제로는 Google Places API 결과 사용)
        places.add(ItineraryResponse.PlaceDto.builder()
            .name("역 근처 맛집")
            .category("RESTAURANT")
            .address("역 근처 주소")
            .rating(4.5)
            .placeId("dummy1")
            .build());
        
        places.add(ItineraryResponse.PlaceDto.builder()
            .name("역 근처 카페")
            .category("CAFE")
            .address("역 근처 주소")
            .rating(4.2)
            .placeId("dummy2")
            .build());
        
        return places;
    }
}
