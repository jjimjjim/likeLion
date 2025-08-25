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
import java.util.Map;
import java.util.HashMap;

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
            // 반경 5km 내의 장소들을 검색
            List<ItineraryResponse.PlaceDto> places = googlePlacesService.searchPlacesNearLocation(
                "restaurant", 
                station.getName(), 
                station.getLatitude(), 
                station.getLongitude(), 
                500, // 500 반경
                10    // 최대 10개 장소
            );
            
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
            // 사용자 선호도에 따라 다양한 타입의 장소 검색
            List<ItineraryResponse.PlaceDto> allPlaces = new ArrayList<>();
            
            // 1. 음식점 검색 (foodType에 따라) - 중복 선택 지원
            if (request.getFoodType() != null && !request.getFoodType().isEmpty()) {
                log.info("음식점 검색 시작: {}", request.getFoodType());
                log.info("선택된 음식 타입 개수: {}", request.getFoodType().size());
                
                // 각 음식 타입별로 개별 검색 (중복 선택 가능)
                for (String foodType : request.getFoodType()) {
                    if ("카페".equals(foodType)) {
                        // 카페는 별도로 처리 (아래에서)
                        log.info("카페는 별도 처리 예정");
                        continue;
                    }
                    
                    String searchKeyword = "";
                    switch (foodType) {
                        case "한식":
                            searchKeyword = "한식";
                            break;
                        case "중식":
                            searchKeyword = "중식";
                            break;
                        case "양식":
                            searchKeyword = "양식";
                            break;
                        case "일식":
                            searchKeyword = "일식";
                            break;
                        case "기타":
                            searchKeyword = "음식점";
                            break;
                        default:
                            searchKeyword = foodType;
                            break;
                    }
                    
                    log.info("{} 검색 시작 (키워드: {})", foodType, searchKeyword);
                    List<ItineraryResponse.PlaceDto> foodPlaces = googlePlacesService.searchPlacesNearLocation(
                        "restaurant", 
                        searchKeyword, // 각 음식 타입별 키워드로 검색
                        station.getLatitude(), 
                        station.getLongitude(), 
                        500, // 500m 반경
                        8     // 각 타입별 8개씩
                    );
                    log.info("{} 검색 결과: {}개", foodType, foodPlaces.size());
                    allPlaces.addAll(foodPlaces);
                }
            }
            
            // 2. 문화시설 검색 (cultureType에 따라)
            if (request.getCultureType() != null && !request.getCultureType().isEmpty()) {
                log.info("문화시설 검색 시작: {}", request.getCultureType());
                
                // 공연/전시인 경우 더 구체적인 검색
                if ("공연/전시".equals(request.getCultureType())) {
                    log.info("공연/전시 타입 검색 시작");
                    
                    // 1. 타입 기반 검색 (5km로 확장)
                    List<ItineraryResponse.PlaceDto> moviePlaces = googlePlacesService.searchPlacesNearLocation(
                        "movie_theater", 
                        "", 
                        station.getLatitude(), 
                        station.getLongitude(), 
                        500, // 5 반경으로 확장
                        10    // 영화관 10개
                    );
                    log.info("영화관 검색 결과: {}개", moviePlaces.size());
                    allPlaces.addAll(moviePlaces);
                    
                    List<ItineraryResponse.PlaceDto> museumPlaces = googlePlacesService.searchPlacesNearLocation(
                        "museum", 
                        "", 
                        station.getLatitude(), 
                        station.getLongitude(), 
                        500, // 5 반경으로 확장
                        10    // 박물관 10개
                    );
                    log.info("박물관/미술관 검색 결과: {}개", museumPlaces.size());
                    allPlaces.addAll(museumPlaces);
                    
                    List<ItineraryResponse.PlaceDto> artGalleryPlaces = googlePlacesService.searchPlacesNearLocation(
                        "art_gallery", 
                        "", 
                        station.getLatitude(), 
                        station.getLongitude(), 
                        500, // 5km 반경으로 확장
                        10    // 미술관 10개
                    );
                    log.info("미술관 검색 결과: {}개", artGalleryPlaces.size());
                    allPlaces.addAll(artGalleryPlaces);
                    
                    // 2. 키워드 기반 검색 추가 (타입으로 찾지 못한 경우)
                    List<ItineraryResponse.PlaceDto> keywordPlaces = googlePlacesService.searchPlacesNearLocation(
                        "tourist_attraction", 
                        "공연 전시 문화", // 키워드로 검색
                        station.getLatitude(), 
                        station.getLongitude(), 
                        500, // 5km 반경
                        15    // 15개
                    );
                    log.info("키워드 기반 문화시설 검색 결과: {}개", keywordPlaces.size());
                    allPlaces.addAll(keywordPlaces);
                    
                    // 3. 더 일반적인 관광지 검색
                    List<ItineraryResponse.PlaceDto> generalCulturePlaces = googlePlacesService.searchPlacesNearLocation(
                        "tourist_attraction", 
                        "", 
                        station.getLatitude(), 
                        station.getLongitude(), 
                        500, // 5km 반경
                        20    // 20개
                    );
                    log.info("일반 관광지 검색 결과: {}개", generalCulturePlaces.size());
                    allPlaces.addAll(generalCulturePlaces);
                    
                } else if ("자연/공원".equals(request.getCultureType())) {
                    // 자연/공원인 경우 더 구체적인 검색
                    log.info("자연/공원 타입 검색 시작");
                    
                    // 1. 공원 검색
                    List<ItineraryResponse.PlaceDto> parkPlaces = googlePlacesService.searchPlacesNearLocation(
                        "park", 
                        "", 
                        station.getLatitude(), 
                        station.getLongitude(), 
                        500, // 500m 반경
                        15    // 공원 15개
                    );
                    log.info("공원 검색 결과: {}개", parkPlaces.size());
                    allPlaces.addAll(parkPlaces);
                    
                    // 2. 자연 경관 검색
                    List<ItineraryResponse.PlaceDto> naturePlaces = googlePlacesService.searchPlacesNearLocation(
                        "natural_feature", 
                        "", 
                        station.getLatitude(), 
                        station.getLongitude(), 
                        500, // 500m 반경
                        10    // 자연 경관 10개
                    );
                    log.info("자연 경관 검색 결과: {}개", naturePlaces.size());
                    allPlaces.addAll(naturePlaces);
                    
                    // 3. 키워드 기반 검색 (공원, 산, 강 등)
                    List<ItineraryResponse.PlaceDto> keywordNaturePlaces = googlePlacesService.searchPlacesNearLocation(
                        "tourist_attraction", 
                        "공원 산 강 자연", // 자연 관련 키워드로 검색
                        station.getLatitude(), 
                        station.getLongitude(), 
                        500, // 5km 반경
                        15    // 15개
                    );
                    log.info("키워드 기반 자연시설 검색 결과: {}개", keywordNaturePlaces.size());
                    allPlaces.addAll(keywordNaturePlaces);
                    
                    // 4. 일반 관광지 검색 (자연 관련)
                    List<ItineraryResponse.PlaceDto> generalNaturePlaces = googlePlacesService.searchPlacesNearLocation(
                        "tourist_attraction", 
                        "", 
                        station.getLatitude(), 
                        station.getLongitude(), 
                        500, // 500 반경
                        20    // 20개
                    );
                    log.info("일반 자연시설 검색 결과: {}개", generalNaturePlaces.size());
                    allPlaces.addAll(generalNaturePlaces);
                    
                } else if ("체험".equals(request.getCultureType())) {
                    // 체험인 경우 더 구체적인 검색
                    log.info("체험 타입 검색 시작");
                    
                    // 1. 체험 시설 검색
                    List<ItineraryResponse.PlaceDto> experiencePlaces = googlePlacesService.searchPlacesNearLocation(
                        "tourist_attraction", 
                        "체험", 
                        station.getLatitude(), 
                        station.getLongitude(), 
                        500, // 5 반경
                        15    // 체험 시설 15개
                    );
                    log.info("체험 시설 검색 결과: {}개", experiencePlaces.size());
                    allPlaces.addAll(experiencePlaces);
                    
                    // 2. 키워드 기반 검색 (체험, 만들기, DIY 등)
                    List<ItineraryResponse.PlaceDto> keywordExperiencePlaces = googlePlacesService.searchPlacesNearLocation(
                        "tourist_attraction", 
                        "체험 만들기 DIY", 
                        station.getLatitude(), 
                        station.getLongitude(), 
                        500, // 5 반경
                        15    // 15개
                    );
                    log.info("키워드 기반 체험시설 검색 결과: {}개", keywordExperiencePlaces.size());
                    allPlaces.addAll(keywordExperiencePlaces);
                    
                    // 3. 일반 관광지 검색
                    List<ItineraryResponse.PlaceDto> generalExperiencePlaces = googlePlacesService.searchPlacesNearLocation(
                        "tourist_attraction", 
                        "", 
                        station.getLatitude(), 
                        station.getLongitude(), 
                        500, // 5km 반경
                        20    // 20개
                    );
                    log.info("일반 체험시설 검색 결과: {}개", generalExperiencePlaces.size());
                    allPlaces.addAll(generalExperiencePlaces);
                    
                                 } else if ("지역축제".equals(request.getCultureType())) {
                     // 지역축제인 경우 더 구체적인 검색
                     log.info("지역축제 타입 검색 시작");
                     
                     // 1. 축제 관련 검색 (더 넓은 키워드)
                     List<ItineraryResponse.PlaceDto> festivalPlaces = googlePlacesService.searchPlacesNearLocation(
                         "tourist_attraction", 
                         "축제", 
                         station.getLatitude(), 
                         station.getLongitude(), 
                         500, // 5km 반경
                         15    // 축제 관련 15개
                     );
                     log.info("축제 관련 검색 결과: {}개", festivalPlaces.size());
                     allPlaces.addAll(festivalPlaces);
                     
                     // 2. 이벤트/행사 관련 검색
                     List<ItineraryResponse.PlaceDto> eventPlaces = googlePlacesService.searchPlacesNearLocation(
                         "tourist_attraction", 
                         "이벤트 행사", 
                         station.getLatitude(), 
                         station.getLongitude(), 
                         500, // 5km 반경
                         15    // 15개
                     );
                     log.info("이벤트/행사 관련 검색 결과: {}개", eventPlaces.size());
                     allPlaces.addAll(eventPlaces);
                     
                     // 3. 문화/전통 관련 검색 (축제와 연관성 높음)
                     List<ItineraryResponse.PlaceDto> culturePlaces = googlePlacesService.searchPlacesNearLocation(
                         "tourist_attraction", 
                         "문화 전통", 
                         station.getLatitude(), 
                         station.getLongitude(), 
                         500, // 5km 반경
                         15    // 15개
                     );
                     log.info("문화/전통 관련 검색 결과: {}개", culturePlaces.size());
                     allPlaces.addAll(culturePlaces);
                     
                     // 4. 박물관/전시관 검색 (축제와 연관성 높음)
                     List<ItineraryResponse.PlaceDto> museumPlaces = googlePlacesService.searchPlacesNearLocation(
                         "museum", 
                         "", 
                         station.getLatitude(), 
                         station.getLongitude(), 
                         500, // 5km 반경
                         10    // 10개
                     );
                     log.info("박물관/전시관 검색 결과: {}개", museumPlaces.size());
                     allPlaces.addAll(museumPlaces);
                     
                     // 5. 일반 관광지 검색 (더 많은 결과)
                     List<ItineraryResponse.PlaceDto> generalFestivalPlaces = googlePlacesService.searchPlacesNearLocation(
                         "tourist_attraction", 
                         "", 
                         station.getLatitude(), 
                         station.getLongitude(), 
                         500, // 5km 반경
                         25    // 25개로 증가
                     );
                     log.info("일반 관광지 검색 결과: {}개", generalFestivalPlaces.size());
                     allPlaces.addAll(generalFestivalPlaces);
                     
                     // 6. 추가 문화시설 검색 (축제가 없을 때 대체)
                     List<ItineraryResponse.PlaceDto> additionalCulturePlaces = googlePlacesService.searchPlacesNearLocation(
                         "tourist_attraction", 
                         "문화시설 전시관 갤러리", 
                         station.getLatitude(), 
                         station.getLongitude(), 
                         500, // 5km 반경
                         15    // 15개
                     );
                     log.info("추가 문화시설 검색 결과: {}개", additionalCulturePlaces.size());
                     allPlaces.addAll(additionalCulturePlaces);
                     
                     // 7. 공원/광장 검색 (축제 개최지로 활용 가능)
                     List<ItineraryResponse.PlaceDto> parkPlaces = googlePlacesService.searchPlacesNearLocation(
                         "park", 
                         "", 
                         station.getLatitude(), 
                         station.getLongitude(), 
                         500, // 5km 반경
                         10    // 10개
                     );
                     log.info("공원/광장 검색 결과: {}개", parkPlaces.size());
                     allPlaces.addAll(parkPlaces);
                    
                } else {
                    // 기타 문화 타입 (기타, 또는 새로운 타입)
                    log.info("기타 문화 타입 검색 시작: {}", request.getCultureType());
                    
                    // 1. 키워드 기반 검색
                    List<ItineraryResponse.PlaceDto> keywordCulturePlaces = googlePlacesService.searchPlacesNearLocation(
                        "tourist_attraction", 
                        request.getCultureType(), // 사용자가 입력한 문화 타입을 키워드로 사용
                        station.getLatitude(), 
                        station.getLongitude(), 
                        500, // 5km 반경
                        15    // 15개
                    );
                    log.info("키워드 기반 문화시설 검색 결과: {}개", keywordCulturePlaces.size());
                    allPlaces.addAll(keywordCulturePlaces);
                    
                    // 2. 일반 관광지 검색
                    List<ItineraryResponse.PlaceDto> generalCulturePlaces = googlePlacesService.searchPlacesNearLocation(
                        "tourist_attraction", 
                        "", 
                        station.getLatitude(), 
                        station.getLongitude(), 
                        500, // 5km 반경
                        20    // 20개
                    );
                    log.info("일반 문화시설 검색 결과: {}개", generalCulturePlaces.size());
                    allPlaces.addAll(generalCulturePlaces);
                }
            }
            
            // 3. 카페 검색 (음식 타입에 카페가 포함된 경우) - 중복 선택 지원
            if (request.getFoodType() != null && request.getFoodType().contains("카페")) {
                log.info("카페 검색 시작 (중복 선택 지원)");
                List<ItineraryResponse.PlaceDto> cafePlaces = googlePlacesService.searchPlacesNearLocation(
                    "cafe", 
                    "", 
                    station.getLatitude(), 
                    station.getLongitude(), 
                    500, // 5km 반경
                    10    // 카페 10개
                );
                log.info("카페 검색 결과: {}개", cafePlaces.size());
                allPlaces.addAll(cafePlaces);
            }
            
            // 중복 제거 (placeId 기준)
            Map<String, ItineraryResponse.PlaceDto> uniquePlaces = new HashMap<>();
            for (ItineraryResponse.PlaceDto place : allPlaces) {
                if (place.getPlaceId() != null) {
                    uniquePlaces.put(place.getPlaceId(), place);
                }
            }
            List<ItineraryResponse.PlaceDto> places = new ArrayList<>(uniquePlaces.values());
            
            log.info("전체 검색된 장소 수: {}", allPlaces.size());
            log.info("중복 제거 후 장소 수: {}", places.size());
            log.info("장소 타입별 분포:");
            places.stream()
                .collect(java.util.stream.Collectors.groupingBy(ItineraryResponse.PlaceDto::getCategory, java.util.stream.Collectors.counting()))
                .forEach((category, count) -> log.info("  {}: {}개", category, count));
            
            // GptService의 selectOptimalPlaces 사용 (더 정확하고 실용적)
            List<ItineraryResponse.PlaceDto> selectedPlaces;
            
            // 각 문화 타입별로 적절한 필터링 적용
            if ("공연/전시".equals(request.getCultureType())) {
                log.info("공연/전시 타입: 문화시설 우선 선택 모드");
                
                // 공연/전시 관련 장소만 필터링
                List<ItineraryResponse.PlaceDto> culturePlaces = places.stream()
                    .filter(place -> {
                        String category = place.getCategory();
                        return "MOVIE_THEATER".equals(category) || 
                               "MUSEUM".equals(category) || 
                               "ART_GALLERY".equals(category) ||
                               "CULTURE".equals(category) ||
                               "ATTRACTION".equals(category); // 관광지 중 문화 관련
                    })
                    .collect(java.util.stream.Collectors.toList());
                
                log.info("공연/전시 관련 장소 필터링 후: {}개", culturePlaces.size());
                selectedPlaces = selectPlacesWithFallback(culturePlaces, places, request);
                
            } else if ("자연/공원".equals(request.getCultureType())) {
                log.info("자연/공원 타입: 자연시설 우선 선택 모드");
                
                // 자연/공원 관련 장소만 필터링
                List<ItineraryResponse.PlaceDto> naturePlaces = places.stream()
                    .filter(place -> {
                        String category = place.getCategory();
                        return "PARK".equals(category) || 
                               "NATURAL_FEATURE".equals(category) ||
                               "ATTRACTION".equals(category); // 관광지 중 자연 관련
                    })
                    .collect(java.util.stream.Collectors.toList());
                
                log.info("자연/공원 관련 장소 필터링 후: {}개", naturePlaces.size());
                selectedPlaces = selectPlacesWithFallback(naturePlaces, places, request);
                
            } else if ("체험".equals(request.getCultureType())) {
                log.info("체험 타입: 체험시설 우선 선택 모드");
                
                // 체험 관련 장소만 필터링 (키워드 기반으로 이미 검색됨)
                List<ItineraryResponse.PlaceDto> experiencePlaces = places.stream()
                    .filter(place -> {
                        String category = place.getCategory();
                        return "ATTRACTION".equals(category) || 
                               "CULTURE".equals(category); // 체험 관련 관광지
                    })
                    .collect(java.util.stream.Collectors.toList());
                
                log.info("체험 관련 장소 필터링 후: {}개", experiencePlaces.size());
                selectedPlaces = selectPlacesWithFallback(experiencePlaces, places, request);
                
                         } else if ("지역축제".equals(request.getCultureType())) {
                 log.info("지역축제 타입: 음식점 + 문화시설 균형 선택 모드");
                 
                 // 음식점과 문화시설을 모두 포함하도록 강제
                 List<ItineraryResponse.PlaceDto> foodPlaces = places.stream()
                     .filter(place -> {
                         String category = place.getCategory();
                         return "RESTAURANT".equals(category) || 
                                "CAFE".equals(category); // 음식점과 카페
                     })
                     .collect(java.util.stream.Collectors.toList());
                 
                 List<ItineraryResponse.PlaceDto> culturePlaces = places.stream()
                     .filter(place -> {
                         String category = place.getCategory();
                         return "ATTRACTION".equals(category) || 
                                "CULTURE".equals(category) ||
                                "MUSEUM".equals(category) ||
                                "PARK".equals(category); // 문화시설, 관광지, 공원
                     })
                     .collect(java.util.stream.Collectors.toList());
                 
                 log.info("음식점/카페: {}개, 문화시설: {}개", foodPlaces.size(), culturePlaces.size());
                 
                 // 음식점과 문화시설이 모두 있는 경우에만 선택
                 if (foodPlaces.size() > 0 && culturePlaces.size() > 0) {
                     selectedPlaces = selectPlacesWithBalance(foodPlaces, culturePlaces, places, request);
                 } else {
                     // 부족한 경우 fallback
                     selectedPlaces = selectPlacesWithFallback(culturePlaces, places, request);
                 }
                
            } else {
                // 기타 문화 타입
                log.info("기타 문화 타입: {} - 일반 선택 모드", request.getCultureType());
                selectedPlaces = gptService.selectOptimalPlaces(
                    places,
                    request.getPeopleCount(),
                    "도보",
                    4,
                    request.getFoodType()
                );
            }
            
            // 선택된 장소들로 코스 구성
            return buildCourseFromSelectedPlaces(selectedPlaces, station);
            
        } catch (Exception e) {
            log.error("GPT 코스 추천 생성 중 오류 발생", e);
            return "코스 추천 생성에 실패했습니다.";
        }
    }
    
    /**
     * 음식점과 문화시설의 균형잡힌 선택
     */
    private List<ItineraryResponse.PlaceDto> selectPlacesWithBalance(
            List<ItineraryResponse.PlaceDto> foodPlaces, 
            List<ItineraryResponse.PlaceDto> culturePlaces,
            List<ItineraryResponse.PlaceDto> allPlaces, 
            StationRequest request) {
        
        log.info("균형잡힌 선택 모드: 음식점 {}개, 문화시설 {}개", foodPlaces.size(), culturePlaces.size());
        
        // 음식점 1-2개, 문화시설 2-3개로 구성
        List<ItineraryResponse.PlaceDto> balancedPlaces = new ArrayList<>();
        
        // 음식점 1-2개 선택 (평점 높은 순)
        List<ItineraryResponse.PlaceDto> selectedFood = foodPlaces.stream()
            .sorted((a, b) -> Double.compare(b.getRating(), a.getRating()))
            .limit(2)
            .collect(java.util.stream.Collectors.toList());
        balancedPlaces.addAll(selectedFood);
        
        // 문화시설 2-3개 선택 (평점 높은 순)
        int cultureCount = 4 - selectedFood.size(); // 남은 자리만큼
        List<ItineraryResponse.PlaceDto> selectedCulture = culturePlaces.stream()
            .sorted((a, b) -> Double.compare(b.getRating(), a.getRating()))
            .limit(cultureCount)
            .collect(java.util.stream.Collectors.toList());
        balancedPlaces.addAll(selectedCulture);
        
        log.info("균형잡힌 선택 결과: 음식점 {}개, 문화시설 {}개", selectedFood.size(), selectedCulture.size());
        
        return balancedPlaces;
    }
    
    /**
     * 필터링된 장소가 부족할 경우 fallback 처리
     */
    private List<ItineraryResponse.PlaceDto> selectPlacesWithFallback(
            List<ItineraryResponse.PlaceDto> filteredPlaces, 
            List<ItineraryResponse.PlaceDto> allPlaces, 
            StationRequest request) {
        
        if (filteredPlaces.size() >= 4) {
            // 필터링된 장소가 충분하면 해당 타입만으로 선택
            log.info("필터링된 장소가 충분함 ({}개), 해당 타입만으로 선택", filteredPlaces.size());
            return gptService.selectOptimalPlaces(
                filteredPlaces,
                request.getPeopleCount(),
                "도보",
                4,
                request.getFoodType()
            );
        } else {
            // 필터링된 장소가 부족하면 전체에서 선택하되 해당 타입 우선
            log.info("필터링된 장소가 부족함 ({}개), 전체에서 선택하되 해당 타입 우선", filteredPlaces.size());
            
            // 전체 장소가 4개 미만이면 강제로 4개 만들기
            if (allPlaces.size() < 4) {
                log.warn("전체 장소가 4개 미만 ({}개), 음식점과 카페로 보완", allPlaces.size());
                
                // 음식점과 카페를 추가로 검색하여 4개 이상으로 만들기
                List<ItineraryResponse.PlaceDto> additionalPlaces = new ArrayList<>(allPlaces);
                
                // 음식점 추가 검색
                if (request.getFoodType() != null && !request.getFoodType().isEmpty()) {
                    for (String foodType : request.getFoodType()) {
                        if (!"카페".equals(foodType)) {
                            List<ItineraryResponse.PlaceDto> extraFood = googlePlacesService.searchPlacesNearLocation(
                                "restaurant", 
                                foodType, 
                                allPlaces.get(0).getLatitude(), // 첫 번째 장소의 좌표 사용
                                allPlaces.get(0).getLongitude(), 
                                500,
                                5
                            );
                            additionalPlaces.addAll(extraFood);
                        }
                    }
                }
                
                // 카페 추가 검색
                if (request.getFoodType() != null && request.getFoodType().contains("카페")) {
                    List<ItineraryResponse.PlaceDto> extraCafe = googlePlacesService.searchPlacesNearLocation(
                        "cafe", 
                        "", 
                        allPlaces.get(0).getLatitude(), 
                        allPlaces.get(0).getLongitude(), 
                        500,
                        5
                    );
                    additionalPlaces.addAll(extraCafe);
                }
                
                log.info("추가 검색 후 총 장소 수: {}개", additionalPlaces.size());
                allPlaces = additionalPlaces;
            }
            
            return gptService.selectOptimalPlaces(
                allPlaces,
                request.getPeopleCount(),
                "도보",
                4,
                request.getFoodType()
            );
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
