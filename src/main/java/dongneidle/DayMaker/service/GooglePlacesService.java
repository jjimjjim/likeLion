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

/**
 * Google Places API를 활용한 장소 검색 및 상세 정보 조회 서비스
 * 
 * 주요 기능:
 * 1. 안양시 중심 좌표 기반 장소 검색 (음식점, 카페, 문화시설 등)
 * 2. 특정 좌표 기준 주변 장소 검색 (역 기반 검색용)
 * 3. Google Places API v1을 활용한 상세 정보 조회
 * 4. 주차장 검색 (v1 API + 레거시 폴백)
 * 
 * 연결되는 컴포넌트:
 * - ItineraryService: 여행 일정 생성 시 장소 검색
 * - StationBasedCourseService: 역 기반 코스 추천 시 주변 장소 검색
 * - PlaceController: 프론트엔드에서 장소 검색 요청 처리
 * - ItineraryController: 일정 생성 시 장소 정보 제공
 * 
 * 프론트엔드 연동 포인트:
 * - 장소 검색 결과를 PlaceDto 형태로 반환하여 카드, 지도 마커 등으로 표시
 * - 장소 상세 정보를 PlaceDetailsDto로 제공하여 상세 페이지 구성
 * - 주차장 정보를 통한 주차 옵션 제공
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GooglePlacesService {
    
    /**
     * Google Places API 키 (application.yml에서 주입)
     * 프론트엔드에서는 이 키를 직접 사용하지 않고, 백엔드에서만 API 호출
     * 보안상 API 키는 백엔드에서만 관리
     */
    @Value("${google.api.key:}")
    private String googleApiKey;
    
    /**
     * HTTP 요청을 위한 RestTemplate (Spring에서 제공하는 HTTP 클라이언트)
     * Google Places API 호출 시 사용
     */
    private final RestTemplate restTemplate;
    
    // ===================== 기본 설정 상수 =====================
    
    /**
     * 안양시 중심 좌표 (대략적인 위치)
     * 프론트엔드에서 지도 표시 시 이 좌표를 중심으로 초기화 가능
     * 지도 컴포넌트의 초기 중심점으로 활용
     */
    private static final double ANYANG_LAT = 37.3942;
    private static final double ANYANG_LNG = 126.9569;
    
    /**
     * 기본 검색 반경 (10km)
     * 프론트엔드에서 사용자가 반경을 조정할 수 있도록 UI 제공 가능
     * 슬라이더나 드롭다운으로 반경 선택 기능 구현 가능
     */
    private static final int DEFAULT_RADIUS = 10000;

    /**
     * 기본 품질 필터 기준
     * 프론트엔드에서 필터 옵션으로 제공 가능
     * 체크박스나 토글 버튼으로 사용자가 필터 조정 가능
     */
    private static final double BASE_MIN_RATING = 4.0; // 최소 평점
    private static final int BASE_MIN_REVIEWS = 20;    // 최소 리뷰 수
    private static final int DEFAULT_MAX_RESULTS = 12;  // 기본 최대 반환 개수
    
    /**
     * Google Places API v1 기본 URL
     * v1 API는 더 상세한 정보와 사진을 제공
     * 레거시 API 대비 더 풍부한 데이터 제공
     */
    private static final String V1_BASE = "https://places.googleapis.com/v1";

    // ===================== 공개 API 메서드 =====================

    /**
     * 고정 기본 설정으로 장소 검색 (하위 호환성 유지)
     * 
     * @param type 검색할 장소 타입 (restaurant, cafe, movie_theater 등)
     * @param keyword 검색 키워드
     * @return 장소 목록 (ItineraryResponse.PlaceDto 형태)
     * 
     * 프론트엔드 연결:
     * - PlaceController.searchPlaces()에서 호출
     * - 사용자가 장소 타입과 키워드로 검색할 때 사용
     * - 검색 결과를 카드 형태나 리스트로 표시
     * - 지도에 마커로 표시하여 위치 확인 가능
     */
    public List<ItineraryResponse.PlaceDto> searchPlaces(String type, String keyword) {
        return searchPlaces(type, keyword, DEFAULT_MAX_RESULTS);
    }

    /**
     * 원하는 후보 개수에 따라 동적으로 결과 수/필터/반경을 조정하여 검색
     * 
     * 검색 전략:
     * 1. 기본 설정으로 검색 (10km, 평점 4.0+, 리뷰 20+)
     * 2. 결과가 부족하면 반경 확대 (13km, 16km)
     * 3. 품질 기준을 점진적으로 완화 (평점 3.8+, 리뷰 10+)
     * 
     * @param type 검색할 장소 타입
     * @param keyword 검색 키워드
     * @param desiredCount 원하는 결과 개수
     * @return 필터링된 장소 목록
     * 
     * 프론트엔드 연결:
     * - ItineraryService.generateItinerary()에서 호출
     * - 일정 생성 시 필요한 장소 개수만큼 검색
     * - 검색 결과를 페이지네이션이나 무한 스크롤로 표시
     * - 로딩 상태 표시 (검색 중일 때 스피너나 스켈레톤 UI)
     * 
     * UI 구성 예시:
     * - 검색 타입 선택: 드롭다운 (음식점, 카페, 문화시설 등)
     * - 키워드 입력: 텍스트 필드
     * - 결과 개수 선택: 슬라이더 또는 숫자 입력
     * - 검색 결과: 카드 그리드 또는 리스트
     */
    public List<ItineraryResponse.PlaceDto> searchPlaces(String type, String keyword, int desiredCount) {
        // Google API 키가 설정되지 않은 경우 더미 데이터 반환
        if (googleApiKey.isEmpty()) {
            log.warn("Google API key not configured, returning dummy data");
            return getDummyPlaces(type, keyword);
        }

        // 동적 목표 개수: N*3 이상 확보 시도 (최대 후보군)
        final int targetMax = Math.max(DEFAULT_MAX_RESULTS, desiredCount * 3);

        // 점진적 완화 전략: 반경, 평점, 리뷰 기준을 단계별로 완화
        int[] radii = new int[]{DEFAULT_RADIUS, (int)(DEFAULT_RADIUS * 1.3), (int)(DEFAULT_RADIUS * 1.6)}; // 10km, 13km, 16km
        double[] minRatings = new double[]{BASE_MIN_RATING, 3.8, 3.5};
        int[] minReviews = new int[]{BASE_MIN_REVIEWS, 10, 0};

        Set<String> seenIds = new HashSet<>(); // 중복 장소 제거용
        List<ItineraryResponse.PlaceDto> aggregated = new ArrayList<>();

        // 단계별로 검색하여 목표 개수 달성 시도
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
     * 
     * @param type 검색할 장소 타입
     * @param keyword 검색 키워드
     * @param latitude 위도
     * @param longitude 경도
     * @param radiusMeters 검색 반경 (미터)
     * @param desiredCount 원하는 결과 개수
     * @return 주변 장소 목록
     * 
     * 프론트엔드 연결:
     * - StationBasedCourseService에서 호출
     * - 사용자가 특정 역을 선택했을 때 주변 장소 검색
     * - 지도에서 특정 위치 클릭 시 주변 장소 표시
     * - 역 기반 코스 추천 시 주변 관광지, 맛집 등 검색
     * 
     * UI 구성 예시:
     * - 지도에서 역 선택: 클릭 이벤트로 좌표 획득
     * - 반경 설정: 슬라이더로 1km~20km 조정
     * - 장소 타입 필터: 체크박스로 음식점, 카페, 문화시설 등 선택
     * - 결과 표시: 지도에 마커, 사이드바에 리스트
     * 
     * 사용 시나리오:
     * 1. 사용자가 지하철역을 선택
     * 2. 해당 역 주변의 맛집/카페 검색
     * 3. 결과를 지도에 마커로 표시
     * 4. 사용자가 관심 있는 장소 선택하여 일정에 추가
     */
    public List<ItineraryResponse.PlaceDto> searchPlacesNearLocation(String type, String keyword, double latitude, double longitude, int radiusMeters, int desiredCount) {
        if (googleApiKey.isEmpty()) {
            log.warn("Google API key not configured, returning dummy data");
            return getDummyPlaces(type, keyword);
        }

        try {
            // Google Places Nearby Search API 호출
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

    // ===================== 내부 API 호출 메서드 =====================

    /**
     * Google Places API 호출 (안양시 중심 좌표 기준)
     * 
     * @param radiusMeters 검색 반경
     * @param type 장소 타입
     * @param keyword 검색 키워드
     * @return Google API 응답 결과 (Map 형태)
     * 
     * 프론트엔드 연결:
     * - 직접 호출되지 않음
     * - searchPlaces() 메서드에서 내부적으로 사용
     * - API 호출 실패 시 로그를 통해 디버깅 정보 제공
     * 
     * 에러 처리:
     * - API 응답 상태가 "OK"가 아닌 경우 빈 리스트 반환
     * - 예외 발생 시 로그 기록 후 빈 리스트 반환
     */
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

    // ===================== 데이터 변환 및 필터링 =====================

    /**
     * Google API 응답을 PlaceDto로 변환하고 품질 기준으로 필터링
     * 
     * 필터링 기준:
     * 1. 안양시 지역 필터링 (vicinity에 "안양" 포함)
     * 2. 중복 장소 제거 (place_id 기준)
     * 3. 품질 기준 필터링 (평점, 리뷰 수)
     * 
     * @param googleResults Google API 응답 결과
     * @param minRating 최소 평점
     * @param minReviews 최소 리뷰 수
     * @param seenPlaceIds 이미 처리된 장소 ID들
     * @return 필터링된 장소 목록
     * 
     * 프론트엔드 연결:
     * - ItineraryResponse.PlaceDto 형태로 반환
     * - 프론트엔드에서 장소 카드, 지도 마커 등으로 표시
     * - 각 장소의 이름, 카테고리, 주소, 좌표, 평점 정보 제공
     * 
     * 데이터 구조:
     * - name: 장소명 (프론트엔드에서 제목으로 표시)
     * - category: 카테고리 (아이콘, 색상 구분에 활용)
     * - address: 주소 (위치 정보 표시)
     * - latitude/longitude: 좌표 (지도 마커 위치)
     * - rating: 평점 (별점 UI 구성)
     * - placeId: 고유 ID (상세 정보 조회, 즐겨찾기 등에 활용)
     * 
     * 필터링 로직:
     * - 안양시 지역만 포함 (vicinity 필드 검사)
     * - 중복 제거 (placeId 기준)
     * - 품질 기준 통과 (평점, 리뷰 수)
     */
    private List<ItineraryResponse.PlaceDto> convertAndFilter(List<Map<String, Object>> googleResults,
                                                              double minRating,
                                                              int minReviews,
                                                              Set<String> seenPlaceIds) {
        List<ItineraryResponse.PlaceDto> places = new ArrayList<>();
        for (Map<String, Object> result : googleResults) {
            try {
                // Google API 응답에서 좌표 정보 추출
                Map<String, Object> geometry = (Map<String, Object>) result.get("geometry");
                Map<String, Object> location = (Map<String, Object>) geometry.get("location");

                // 안양시 필터링 (vicinity에 "안양" 포함)
                String vicinity = (String) result.get("vicinity");
                if (vicinity == null || !vicinity.contains("안양")) {
                    continue;
                }

                // 중복 장소 제거
                String placeId = (String) result.get("place_id");
                if (placeId == null || seenPlaceIds.contains(placeId)) {
                    continue;
                }

                // 품질 기준 확인
                double rating = safeDouble(result.get("rating"), 0.0);
                int reviews = safeInt(result.get("user_ratings_total"), 0);

                if (rating < minRating || reviews < minReviews) {
                    continue;
                }

                // PlaceDto 생성 (프론트엔드에서 사용할 형태)
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

    /**
     * Google Places 타입을 애플리케이션 카테고리로 변환
     * 
     * @param types Google Places API에서 제공하는 타입 목록
     * @return 애플리케이션에서 사용하는 카테고리
     * 
     * 프론트엔드 연결:
     * - 카테고리별 아이콘, 색상 구분
     * - 카테고리별 필터링 기능
     * - 장소 카드의 시각적 구분
     * 
     * 매핑 규칙:
     * - restaurant → RESTAURANT (음식점)
     * - cafe → CAFE (카페)
     * - movie_theater → MOVIE (영화관)
     * - art_gallery, museum → CULTURE (문화시설)
     * - tourist_attraction → ATTRACTION (관광지)
     * - 기타 → OTHER (기타)
     * 
     * UI 활용:
     * - 각 카테고리별 고유 아이콘 표시
     * - 카테고리별 색상 테마 적용
     * - 카테고리 필터링 기능 제공
     */
    private String determineCategory(List<String> types) {
        if (types == null) return "OTHER";
        if (types.contains("restaurant")) return "RESTAURANT";
        if (types.contains("cafe")) return "CAFE";
        if (types.contains("movie_theater")) return "MOVIE";
        if (types.contains("art_gallery") || types.contains("museum")) return "CULTURE";
        if (types.contains("tourist_attraction")) return "ATTRACTION";
        return "OTHER";
    }

    // ===================== 안전한 데이터 변환 유틸리티 =====================

    /**
     * Object를 double로 안전하게 변환 (null 체크 및 예외 처리)
     * 
     * @param value 변환할 값
     * @param defaultValue 변환 실패 시 기본값
     * @return 변환된 double 값
     * 
     * 사용 목적:
     * - Google API 응답의 다양한 데이터 타입을 안전하게 처리
     * - null 값이나 예상치 못한 데이터 타입에 대한 방어적 프로그래밍
     * - 프론트엔드에 안정적인 데이터 제공
     * 
     * 처리 로직:
     * 1. Number 타입인 경우 doubleValue() 사용
     * 2. String인 경우 Double.parseDouble() 시도
     * 3. 실패 시 기본값 반환
     */
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

    /**
     * Object를 Double 객체로 안전하게 변환
     * 
     * @param value 변환할 값
     * @param defaultValue 변환 실패 시 기본값
     * @return 변환된 Double 객체 (null 가능)
     * 
     * 사용 목적:
     * - null 값을 허용하는 Double 타입이 필요한 경우 사용
     * - 위도/경도 등 선택적 좌표 정보 처리
     * - 프론트엔드에서 null 체크 후 지도 표시 여부 결정
     */
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

    /**
     * Object를 int로 안전하게 변환
     * 
     * @param value 변환할 값
     * @param defaultValue 변환 실패 시 기본값
     * @return 변환된 int 값
     * 
     * 사용 목적:
     * - 리뷰 수, 평점 등 정수형 데이터 처리
     * - 프론트엔드에서 숫자 표시 및 정렬에 활용
     */
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

    // ===================== 더미 데이터 (API 키 미설정 시) =====================

    /**
     * Google API 키가 설정되지 않은 경우 사용하는 더미 데이터
     * 
     * @param type 장소 타입
     * @param keyword 검색 키워드
     * @return 더미 장소 목록
     * 
     * 프론트엔드 연결:
     * - 개발/테스트 환경에서 API 키 없이도 UI 테스트 가능
     * - 실제 데이터와 동일한 구조로 반환하여 프론트엔드 호환성 보장
     * - API 키 설정 전까지 기본 UI 동작 확인 가능
     * 
     * 제공하는 더미 데이터:
     * - 음식점/카페: "안양 감성 카페", "안양 한식 맛집"
     * - 영화관/문화시설: "안양 영화관"
     * - 각 장소는 실제 안양시 좌표를 사용하여 지도 표시 가능
     * 
     * 개발 환경 활용:
     * - 프론트엔드 개발자들이 백엔드 API 없이도 UI 개발 가능
     * - 디자인 시스템 및 컴포넌트 테스트에 활용
     * - API 연동 전 사용자 플로우 테스트 가능
     */
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

    // ===================== Google Places API v1 기능 =====================

    /**
     * Google Places API v1을 사용한 장소 상세 정보 조회
     * 
     * v1 API의 장점:
     * - 더 상세한 정보 (영업시간, 전화번호, 주차 옵션 등)
     * - 고품질 사진 제공
     * - FieldMask를 통한 필요한 필드만 요청 가능
     * - API 비용 최적화
     * 
     * @param placeId Google Places ID
     * @param maxPhotos 최대 사진 개수
     * @return 상세 정보가 포함된 PlaceDetailsDto
     * 
     * 프론트엔드 연결:
     * - PlaceController에서 장소 상세 정보 요청 시 사용
     * - 장소 상세 페이지, 모달 등에서 표시
     * - 사진 갤러리, 영업시간, 주차 정보 등 제공
     * - 장소 상세 정보를 통한 사용자 의사결정 지원
     * 
     * 제공하는 상세 정보:
     * - 기본 정보: 이름, 카테고리, 주소, 좌표
     * - 평가 정보: 평점, 리뷰 수
     * - 영업 정보: 현재 영업 상태, 요일별 영업시간
     * - 연락처: 전화번호
     * - 설명: 장소 개요 (editorial summary)
     * - 주차: 주차 옵션 (무료, 유료, 발렛 등)
     * - 사진: 고품질 이미지 URL 목록
     * 
     * UI 구성 예시:
     * - 상단: 장소명, 카테고리, 평점
     * - 중간: 사진 갤러리 (슬라이더 또는 그리드)
     * - 하단: 상세 정보 (주소, 전화번호, 영업시간, 주차 정보)
     * - 사이드: 장소 설명 및 추가 정보
     */
    public PlaceDetailsDto fetchPlaceDetailsV1(String placeId, int maxPhotos) {
        if (googleApiKey.isEmpty()) {
            log.warn("Google API key not configured, returning minimal details");
            return PlaceDetailsDto.builder().placeId(placeId).build();
        }

        try {
            // 필요한 필드만 요청하여 API 비용 절약
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

            // v1 API는 헤더에 API 키와 FieldMask를 포함해야 함
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

            // 응답 데이터 파싱 및 PlaceDetailsDto 생성
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

            // 영업시간 정보 파싱
            Boolean openingNow = null;
            List<String> weekday = null;
            Map coh = (Map) body.get("currentOpeningHours");
            if (coh != null) {
                openingNow = (Boolean) coh.get("openNow");
                weekday = (List<String>) coh.get("weekdayDescriptions");
            }

            String phone = (String) body.get("internationalPhoneNumber");

            // 장소 설명 (editorial summary)
            String overview = null;
            Map es = (Map) body.get("editorialSummary");
            if (es != null) overview = (String) es.get("overview");

            // 주차 옵션 정보 파싱
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

            // 사진 URL 생성 (v1 Media API 사용)
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

    // ===================== 주차장 검색 기능 =====================

    /**
     * 좌표/반경 기반 주차장 검색 (Places API v1 Nearby)
     * 
     * v1 API의 장점:
     * - 더 정확한 주차장 정보
     * - FieldMask를 통한 필요한 필드만 요청
     * - 실패 시 레거시 API로 자동 폴백
     * - 거리순 정렬로 가까운 주차장 우선 제공
     * 
     * @param latitude 위도
     * @param longitude 경도
     * @param radiusMeters 검색 반경 (미터)
     * @param maxResults 최대 결과 개수
     * @return 주차장 목록
     * 
     * 프론트엔드 연결:
     * - 사용자가 특정 위치의 주차장을 찾을 때 사용
     * - 지도에서 주차장 마커 표시
     * - 주차 옵션 정보 제공
     * - 일정 계획 시 주차 가능 여부 확인
     * 
     * 사용 시나리오:
     * 1. 사용자가 관심 장소 선택
     * 2. 주변 주차장 검색 (기본 1km 반경)
     * 3. 주차장 목록을 지도에 마커로 표시
     * 4. 각 주차장의 상세 정보 (주소, 평점, 거리) 제공
     * 5. 사용자가 적합한 주차장 선택하여 일정에 추가
     * 
     * UI 구성 예시:
     * - 주차장 검색 버튼: 장소 상세 페이지에 배치
     * - 검색 결과: 지도 마커 + 사이드바 리스트
     * - 주차장 정보: 이름, 주소, 거리, 평점
     * - 필터링: 거리순, 평점순 정렬 옵션
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

            // FieldMask: 필요한 필드만 요청하여 API 비용 절약
            String fieldMask = String.join(",",
                    "places.name",
                    "places.displayName",
                    "places.location",
                    "places.formattedAddress",
                    "places.rating",
                    "places.userRatingCount",
                    "places.googleMapsUri"
            );

            // v1 API는 POST 요청으로 JSON body 전송
            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("includedTypes", java.util.List.of("parking"));
            body.put("languageCode", "ko");
            
            // 원형 검색 영역 설정
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
            body.put("rankPreference", "DISTANCE"); // 거리순 정렬

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

            // 응답 데이터를 PlaceDto로 변환
            java.util.List<ItineraryResponse.PlaceDto> results = new java.util.ArrayList<>();
            for (java.util.Map p : places) {
                // displayName 파싱
                String display = null;
                Object dn = p.get("displayName");
                if (dn instanceof java.util.Map dm) {
                    Object text = dm.get("text");
                    if (text != null) display = text.toString();
                }
                // 주소 정보
                String addr = (String) p.get("formattedAddress");
                // 좌표 정보
                java.util.Map loc = (java.util.Map) p.get("location");
                Double lat = loc != null ? safeDoubleObj(loc.get("latitude"), null) : null;
                Double lng = loc != null ? safeDoubleObj(loc.get("longitude"), null) : null;
                // 평점 정보
                Double rating = safeDoubleObj(p.get("rating"), null);
                // placeId 추출 (name은 places/{place_id} 형태)
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

    /**
     * 확장된 주차장 검색 (언어 코드, 정렬 기준 설정 가능)
     * 
     * @param latitude 위도
     * @param longitude 경도
     * @param radiusMeters 검색 반경
     * @param maxResults 최대 결과 개수
     * @param languageCode 언어 코드 (기본값: ko)
     * @param rankPreference 정렬 기준 (기본값: DISTANCE)
     * @return 주차장 목록
     * 
     * 프론트엔드 연결:
     * - 다국어 지원이 필요한 경우 사용
     * - 사용자가 정렬 기준을 선택할 수 있는 UI 제공 가능
     * - 국제 사용자를 위한 영어/일본어 등 지원
     * 
     * 정렬 기준 옵션:
     * - DISTANCE: 거리순 (가까운 주차장 우선)
     * - RELEVANCE: 관련성순 (Google 알고리즘 기반)
     * 
     * 언어 코드 예시:
     * - ko: 한국어 (기본값)
     * - en: 영어
     * - ja: 일본어
     * - zh: 중국어
     * 
     * UI 구성 예시:
     * - 언어 선택: 드롭다운 또는 플래그 아이콘
     * - 정렬 기준: 라디오 버튼 또는 토글
     * - 검색 결과: 선택된 언어와 정렬 기준으로 표시
     */
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

    // ===================== 유틸리티 메서드 =====================

    /**
     * Google Places v1 API 응답에서 placeId 추출
     * 
     * @param nameResource "places/{place_id}" 형태의 문자열
     * @return 추출된 place_id
     * 
     * 사용 목적:
     * - v1 API 응답의 name 필드에서 실제 place_id 추출
     * - 프론트엔드에서 장소 상세 정보 조회 시 사용
     * - 즐겨찾기, 리뷰 등에 고유 식별자로 활용
     * 
     * 예시:
     * - 입력: "places/ChIJN1t_tDeuEmsRUsoyG83frY4"
     * - 출력: "ChIJN1t_tDeuEmsRUsoyG83frY4"
     */
    private String extractPlaceIdFromName(String nameResource) {
        if (nameResource == null) return null;
        int idx = nameResource.lastIndexOf('/');
        return idx >= 0 ? nameResource.substring(idx + 1) : nameResource;
    }

    /**
     * 레거시 NearbySearch를 사용한 주차장 검색 폴백 (필터 완화)
     * 
     * v1 API 실패 시 사용하는 백업 방법
     * 
     * @param latitude 위도
     * @param longitude 경도
     * @param radiusMeters 검색 반경
     * @param maxResults 최대 결과 개수
     * @return 주차장 목록
     * 
     * 프론트엔드 연결:
     * - v1 API 실패 시에도 주차장 정보를 제공하여 사용자 경험 보장
     * - 동일한 PlaceDto 형태로 반환하여 프론트엔드 호환성 유지
     * - 사용자는 API 버전 차이를 인지하지 못함
     * 
     * 폴백 전략:
     * 1. v1 API 호출 시도
     * 2. v1 API 실패 시 레거시 API 호출
     * 3. 레거시 API도 실패 시 빈 리스트 반환
     * 
     * 장점:
     * - 높은 가용성 (API 장애 시에도 동작)
     * - 기존 코드와의 호환성 유지
     * - 사용자 경험의 일관성 보장
     * 
     * 단점:
     * - 레거시 API의 제한된 정보 (사진, 상세 정보 부족)
     * - API 비용 증가 가능성
     * - 응답 시간 증가
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

