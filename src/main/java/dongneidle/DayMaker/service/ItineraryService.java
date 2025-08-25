package dongneidle.DayMaker.service;

import dongneidle.DayMaker.DTO.ItineraryRequest;
import dongneidle.DayMaker.DTO.ItineraryResponse;
import dongneidle.DayMaker.enums.*;
import dongneidle.DayMaker.entity.Station;
import dongneidle.DayMaker.repository.StationRepository;
import dongneidle.DayMaker.util.DistanceCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItineraryService {
    //코스 생성(추천) 로직
    private final GooglePlacesService googlePlacesService;
    private final FestivalService festivalService;
    private final GptService gptService;
    private final StationRepository stationRepository; // 역 정보 조회용
    
    public ItineraryResponse createItinerary(ItineraryRequest request) {
        log.info("Creating itinerary for request: {}", request);
        
        // 1. 입력값을 내부 Enum으로 매핑 (멀티 선택 지원)
        java.util.List<String> foodInputs = request.getFoods() != null && !request.getFoods().isEmpty()
                ? request.getFoods() : (request.getFood() != null ? java.util.List.of(request.getFood()) : java.util.List.of());
        java.util.List<FoodType> selectedFoodTypes = new java.util.ArrayList<>();
        for (String f : foodInputs) {
            try { selectedFoodTypes.add(FoodType.fromDisplayName(f)); } catch (Exception ignored) {}
        }
        // 기본: 단일 값이 감성카페/일식 등으로 들어올 수 있음
        if (selectedFoodTypes.isEmpty() && request.getFood() != null) {
            try { selectedFoodTypes.add(FoodType.fromDisplayName(request.getFood())); } catch (Exception ignored) {}
        }
        // 문화 멀티
        java.util.List<String> cultureInputs = request.getCultures() != null && !request.getCultures().isEmpty()
                ? request.getCultures() : (request.getCulture() != null ? java.util.List.of(request.getCulture()) : java.util.List.of());
        java.util.List<CultureType> selectedCultureTypes = new java.util.ArrayList<>();
        for (String c : cultureInputs) {
            try { selectedCultureTypes.add(CultureType.fromDisplayName(c)); } catch (Exception ignored) {}
        }
        if (selectedCultureTypes.isEmpty() && request.getCulture() != null) {
            try { selectedCultureTypes.add(CultureType.fromDisplayName(request.getCulture())); } catch (Exception ignored) {}
        }
        // 기본 선택 (없으면 문화는 OTHER, 음식은 OTHER 취급)
        FoodType foodTypePrimary = selectedFoodTypes.isEmpty() ? FoodType.OTHER : selectedFoodTypes.get(0);
        CultureType cultureTypePrimary = selectedCultureTypes.isEmpty() ? CultureType.OTHER : selectedCultureTypes.get(0);
        // transport 정규화(공백 제거 등)
        String transportInput = request.getTransport() != null ? request.getTransport().replaceAll("\\s+", "") : null;
        TransportType transportTypePrimary = TransportType.fromDisplayName(transportInput);
        
        // N 개수 (기본 4)
        int numPlaces = request.getNumPlaces() != null && request.getNumPlaces() > 0 ? request.getNumPlaces() : 4;
        
        // 2. 모든 장소 수집 (Google API + 축제)
        List<ItineraryResponse.PlaceDto> allPlaces = new ArrayList<>();

        // 문화 장소 컨테이너(후속 보정에서 사용)
        List<ItineraryResponse.PlaceDto> culturePlaces = new ArrayList<>();
        
        //0823 역 기준 로직
        // 역 기준 검색 좌표 설정 (Lambda에서 사용하므로 final로 선언)
        final double searchLat;
        final double searchLng;
        final int searchRadius;
        
        if (request.getSelectedStation() != null && !request.getSelectedStation().trim().isEmpty()) {
            // 선택된 역 정보 조회
            var stationOpt = stationRepository.findByName(request.getSelectedStation().trim());
            if (stationOpt.isPresent()) {
                var station = stationOpt.get();
                searchLat = station.getLatitude();
                searchLng = station.getLongitude();
                searchRadius = 1000; // 역 기준 2km 반경
                log.info("역 기준 검색: {}역 (위도: {}, 경도: {}), 반경: {}m", 
                        station.getName(), searchLat, searchLng, searchRadius);
            } else {
                log.warn("선택된 역을 찾을 수 없음: {}, 기본 좌표 사용", request.getSelectedStation());
                searchLat = 37.3942; // 기본: 안양시 중심
                searchLng = 126.9569;
                searchRadius = 1000; // 기본: 10km
            }
        } else {
            // 역을 선택하지 않은 경우 기본값 사용
            searchLat = 37.3942; // 기본: 안양시 중심
            searchLng = 126.9569;
            searchRadius = 10000; // 기본: 10km
        }
        
        // 음식 장소 검색 (다중 foodTypes) - 역 기준으로 검색
        List<ItineraryResponse.PlaceDto> foodPlaces = new ArrayList<>();
        java.util.Set<String> seenFoodIds = new java.util.HashSet<>();
        for (FoodType ft : selectedFoodTypes.isEmpty() ? java.util.List.of(foodTypePrimary) : selectedFoodTypes) {
            // 1차: 기본 키워드 (역 기준으로 검색)
            List<ItineraryResponse.PlaceDto> part = googlePlacesService.searchPlacesNearLocation(
                    ft.getGoogleType(), ft.getSearchKeyword(), searchLat, searchLng, searchRadius, numPlaces
            );
            for (ItineraryResponse.PlaceDto p : part) {
                if (p.getPlaceId() != null && seenFoodIds.add(p.getPlaceId())) foodPlaces.add(p);
            }
            // 후보가 적으면(예: < 3) 완화 키워드로 추가 탐색
            if (foodPlaces.size() < 3) {
                String[] altKeywords = switch (ft) {
                    case KOREAN -> new String[]{"한식", "밥집", "백반"};
                    case JAPANESE -> new String[]{"일식", "스시", "라멘"};
                    case CHINESE -> new String[]{"중식", "중국집", "짜장면"};
                    case WESTERN -> new String[]{"양식", "파스타", "스테이크"};
                    default -> new String[]{ft.getSearchKeyword()};
                };
                for (String kw : altKeywords) {
                    List<ItineraryResponse.PlaceDto> more = googlePlacesService.searchPlaces(ft.getGoogleType(), kw, Math.max(numPlaces * 2, 12));
                    for (ItineraryResponse.PlaceDto p : more) {
                        if (p.getPlaceId() != null && seenFoodIds.add(p.getPlaceId())) foodPlaces.add(p);
                    }
                    if (foodPlaces.size() >= numPlaces * 2) break;
                }
            }
        }
        allPlaces.addAll(foodPlaces);
        log.info("Found {} food places", foodPlaces.size());
        
        // 문화 장소 검색 (지역축제 제외) - 다중 cultureTypes 및 다중 타입 분할 호출 지원
        if (!cultureTypePrimary.equals(CultureType.FESTIVAL)) {
            java.util.Set<String> seenCultureIds = new java.util.HashSet<>();
            java.util.List<CultureType> cultureTypesToUse = selectedCultureTypes.isEmpty() ? java.util.List.of(cultureTypePrimary) : selectedCultureTypes;
            java.util.List<ItineraryResponse.PlaceDto> mergedCulture = new java.util.ArrayList<>();
            for (CultureType ct : cultureTypesToUse) {
                String cultureGoogleType = ct.getGoogleType();
                String[] typeParts = cultureGoogleType.contains("|") ? cultureGoogleType.split("\\|") : new String[]{cultureGoogleType};
                for (String tp : typeParts) {
                    List<ItineraryResponse.PlaceDto> part = googlePlacesService.searchPlacesNearLocation(tp, ct.getSearchKeyword(), searchLat, searchLng, searchRadius, numPlaces);
                    for (ItineraryResponse.PlaceDto p : part) {
                        if (p.getPlaceId() != null && seenCultureIds.add(p.getPlaceId())) {
                            mergedCulture.add(p);
                        }
                    }
                }
            }
            culturePlaces = mergedCulture;
            allPlaces.addAll(culturePlaces);
            log.info("Found {} culture places", culturePlaces.size());
        }
        
        // 요청받은 날짜에 진행 중인 축제 추가 (역 기준으로 필터링)
        List<ItineraryResponse.PlaceDto> dateFestivals = festivalService.getFestivalsAsPlacesByRequestDate(request.getDate());
        if (!dateFestivals.isEmpty()) {
                    // 역 기준으로 2km 반경 내 축제만 필터링
        List<ItineraryResponse.PlaceDto> nearbyFestivals = dateFestivals.stream()
            .filter(festival -> {
                if (festival.getLatitude() != null && festival.getLongitude() != null) {
                    double distance = DistanceCalculator.calculateDistance(
                        searchLat, searchLng, 
                        festival.getLatitude(), festival.getLongitude()
                    );
                    return distance <= 2.0; // 2km 이내
                }
                return false;
            })
            .toList();
        
        allPlaces.addAll(nearbyFestivals);
        log.info("Added {} nearby festivals (within 2km) for requested date: {}", nearbyFestivals.size(), request.getDate());
        } else {
            log.info("No festivals found for requested date: {}", request.getDate());
        }
        
        log.info("Total places collected: {}", allPlaces.size());
        
        // 3. GPT가 최적 장소 선택 (정확히 numPlaces개로 보정)
        // 멀티 선택 고려: restaurant 타입이 하나라도 있으면 해당 displayName(첫 번째)을 GPT에 전달
        boolean hasRestaurantSelected = !selectedFoodTypes.isEmpty() && selectedFoodTypes.stream()
                .anyMatch(ft -> "restaurant".equals(ft.getGoogleType()));
        String foodTypeForGpt = hasRestaurantSelected
                ? selectedFoodTypes.stream().filter(ft -> "restaurant".equals(ft.getGoogleType()))
                    .findFirst().map(FoodType::getDisplayName).orElse("기타")
                : (selectedFoodTypes.isEmpty() ? (request.getFood() != null ? request.getFood() : "기타")
                                               : selectedFoodTypes.get(0).getDisplayName());

        // String을 List<String>으로 변환
        List<String> foodTypeListForGpt = List.of(foodTypeForGpt);

        List<ItineraryResponse.PlaceDto> gptSelectedPlaces = gptService.selectOptimalPlaces(
            allPlaces, request.getPeopleCount(), request.getTransport(), numPlaces, foodTypeListForGpt
        );

        // 3-1. 정확한 카테고리 구성 보정 (멀티 선택 기반)
        boolean isRestaurantTypeSelected = hasRestaurantSelected;
        int desiredRestaurants = isRestaurantTypeSelected ? (numPlaces >= 4 ? 2 : 1) : 0;
        int desiredNonRestaurants = numPlaces - desiredRestaurants;

        java.util.Set<String> selectedIds = gptSelectedPlaces.stream()
                .map(ItineraryResponse.PlaceDto::getPlaceId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        // 후보 풀 준비 (랜덤하게 섞기)
        java.util.List<ItineraryResponse.PlaceDto> gptRestaurants = gptSelectedPlaces.stream()
                .filter(p -> "RESTAURANT".equals(p.getCategory()))
                .collect(java.util.stream.Collectors.toList());
        java.util.List<ItineraryResponse.PlaceDto> gptNonRestaurants = gptSelectedPlaces.stream()
                .filter(p -> !"RESTAURANT".equals(p.getCategory()))
                .collect(java.util.stream.Collectors.toList());

        java.util.List<ItineraryResponse.PlaceDto> foodRestaurantPool = foodPlaces.stream()
                .filter(p -> p.getPlaceId() != null && !selectedIds.contains(p.getPlaceId()))
                .filter(p -> "RESTAURANT".equals(p.getCategory()))
                .collect(java.util.stream.Collectors.toList());

        // 문화 선택 카테고리 우선(non-restaurant) 풀 (예: 영화관)
        java.util.List<ItineraryResponse.PlaceDto> preferredNonRestaurantPool = culturePlaces.stream()
                .filter(p -> p.getPlaceId() != null && !selectedIds.contains(p.getPlaceId()))
                .filter(p -> !"RESTAURANT".equals(p.getCategory()))
                .collect(java.util.stream.Collectors.toList());

        // 기타 non-restaurant 후보 (전체에서 남은 것)
        java.util.List<ItineraryResponse.PlaceDto> otherNonRestaurantPool = allPlaces.stream()
                .filter(p -> p.getPlaceId() != null && !selectedIds.contains(p.getPlaceId()))
                .filter(p -> !"RESTAURANT".equals(p.getCategory()))
                .collect(java.util.stream.Collectors.toList());

        java.util.List<ItineraryResponse.PlaceDto> finalSelected = new java.util.ArrayList<>();

        // 1) 식당 정확히 desiredRestaurants개 선발 (우선: GPT → 부족 시 food 풀)
        finalSelected.addAll(gptRestaurants.stream().limit(desiredRestaurants).toList());
        if (finalSelected.size() < desiredRestaurants) {
            int need = desiredRestaurants - finalSelected.size();
            for (ItineraryResponse.PlaceDto cand : foodRestaurantPool) {
                if (finalSelected.stream().noneMatch(s -> s.getPlaceId().equals(cand.getPlaceId()))) {
                    finalSelected.add(cand);
                    if (--need == 0) break;
                }
            }
            // 여전히 부족하면: 다른 음식 타입(restaurant)으로 대체 확보
            if (need > 0) {
                java.util.List<FoodType> fallbackFoods = new java.util.ArrayList<>();
                fallbackFoods.add(FoodType.KOREAN);
                fallbackFoods.add(FoodType.JAPANESE);
                fallbackFoods.add(FoodType.CHINESE);
                fallbackFoods.add(FoodType.WESTERN);
                fallbackFoods.add(FoodType.OTHER);
                // 주선호 제거
                fallbackFoods.remove(foodTypePrimary);
                java.util.Set<String> avoidIds = finalSelected.stream().map(ItineraryResponse.PlaceDto::getPlaceId)
                        .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
                for (FoodType ft : fallbackFoods) {
                    if (need <= 0) break;
                    List<ItineraryResponse.PlaceDto> cands = googlePlacesService.searchPlaces(ft.getGoogleType(), ft.getSearchKeyword(), Math.max(numPlaces, 6));
                    // 레스토랑만, 중복 제거, 랜덤하게 처리
                    cands = cands.stream()
                            .filter(p -> "RESTAURANT".equals(p.getCategory()))
                            .filter(p -> p.getPlaceId() != null && !avoidIds.contains(p.getPlaceId()))
                            .collect(java.util.stream.Collectors.toList());
                    for (ItineraryResponse.PlaceDto p : cands) {
                        if (need <= 0) break;
                        finalSelected.add(p);
                        avoidIds.add(p.getPlaceId());
                        need--;
                    }
                }
            }
        }
        // 초과 시 잘라내기 (안전)
        if (finalSelected.size() > desiredRestaurants) {
            finalSelected = new java.util.ArrayList<>(finalSelected.stream().limit(desiredRestaurants).toList());
        }

        // 2) non-restaurant 정확히 desiredNonRestaurants개 선발 (우선: GPT non-rest → 문화우선 풀 → 기타 풀)
        java.util.List<ItineraryResponse.PlaceDto> nonRestPicked = new java.util.ArrayList<>();
        for (ItineraryResponse.PlaceDto p : gptNonRestaurants) {
            if (nonRestPicked.size() >= desiredNonRestaurants) break;
            if (finalSelected.stream().noneMatch(s -> s.getPlaceId().equals(p.getPlaceId()))) {
                nonRestPicked.add(p);
            }
        }
        if (nonRestPicked.size() < desiredNonRestaurants) {
            for (ItineraryResponse.PlaceDto p : preferredNonRestaurantPool) {
                if (nonRestPicked.size() >= desiredNonRestaurants) break;
                if (finalSelected.stream().noneMatch(s -> s.getPlaceId().equals(p.getPlaceId())) &&
                        nonRestPicked.stream().noneMatch(s -> s.getPlaceId().equals(p.getPlaceId()))) {
                    nonRestPicked.add(p);
                }
            }
        }
        if (nonRestPicked.size() < desiredNonRestaurants) {
            for (ItineraryResponse.PlaceDto p : otherNonRestaurantPool) {
                if (nonRestPicked.size() >= desiredNonRestaurants) break;
                if (finalSelected.stream().noneMatch(s -> s.getPlaceId().equals(p.getPlaceId())) &&
                        nonRestPicked.stream().noneMatch(s -> s.getPlaceId().equals(p.getPlaceId()))) {
                    nonRestPicked.add(p);
                }
            }
        }
        
        // 문화 카테고리 재탐색 (완화 파라미터를 기대하여 desiredCount 크게)
        if (nonRestPicked.size() < desiredNonRestaurants) {
            try {
                String cultureGoogleType2 = cultureTypePrimary.getGoogleType();
                java.util.Set<String> seenIds2 = new java.util.HashSet<>();
                seenIds2.addAll(finalSelected.stream().map(ItineraryResponse.PlaceDto::getPlaceId).filter(java.util.Objects::nonNull).toList());
                seenIds2.addAll(nonRestPicked.stream().map(ItineraryResponse.PlaceDto::getPlaceId).filter(java.util.Objects::nonNull).toList());
                java.util.List<ItineraryResponse.PlaceDto> extraCulture = new java.util.ArrayList<>();
                String[] parts2 = cultureGoogleType2.contains("|") ? cultureGoogleType2.split("\\|") : new String[]{cultureGoogleType2};
                for (String tp : parts2) {
                    List<ItineraryResponse.PlaceDto> part = googlePlacesService.searchPlaces(tp, cultureTypePrimary.getSearchKeyword(), Math.max(numPlaces * 3, 10));
                    for (ItineraryResponse.PlaceDto p : part) {
                        if (p.getPlaceId() != null && !seenIds2.contains(p.getPlaceId()) && !"RESTAURANT".equals(p.getCategory())) {
                            extraCulture.add(p);
                            seenIds2.add(p.getPlaceId());
                        }
                    }
                }
                extraCulture = extraCulture.stream()
                        .sorted((a, b) -> Double.compare(scorePlace(b, foodTypePrimary, cultureTypePrimary), scorePlace(a, foodTypePrimary, cultureTypePrimary)))
                        .toList();
                for (ItineraryResponse.PlaceDto p : extraCulture) {
                    if (nonRestPicked.size() >= desiredNonRestaurants) break;
                    if (nonRestPicked.stream().noneMatch(s -> s.getPlaceId().equals(p.getPlaceId()))) {
                        nonRestPicked.add(p);
                    }
                }
            } catch (Exception ignored) {}
        }
        
        // 문화로도 부족할 경우: 대체 비식당 카테고리(MOVIE -> CULTURE(art_gallery,museum) -> ATTRACTION(체험) -> CAFE) 순으로 보충
        if (nonRestPicked.size() < desiredNonRestaurants) {
            try {
                // 1) MOVIE
                List<ItineraryResponse.PlaceDto> fbMovie = googlePlacesService.searchPlaces("movie_theater", "영화관", numPlaces);
                nonRestPicked = fillNonRest(nonRestPicked, finalSelected, fbMovie, desiredNonRestaurants, foodTypePrimary, cultureTypePrimary);
                // 2) CULTURE: art_gallery, museum
                if (nonRestPicked.size() < desiredNonRestaurants) {
                    List<ItineraryResponse.PlaceDto> fbArt = googlePlacesService.searchPlaces("art_gallery", "전시관", numPlaces);
                    nonRestPicked = fillNonRest(nonRestPicked, finalSelected, fbArt, desiredNonRestaurants, foodTypePrimary, cultureTypePrimary);
                }
                if (nonRestPicked.size() < desiredNonRestaurants) {
                    List<ItineraryResponse.PlaceDto> fbMuseum = googlePlacesService.searchPlaces("museum", "전시관", numPlaces);
                    nonRestPicked = fillNonRest(nonRestPicked, finalSelected, fbMuseum, desiredNonRestaurants, foodTypePrimary, cultureTypePrimary);
                }
                // 3) ATTRACTION (체험)
                if (nonRestPicked.size() < desiredNonRestaurants) {
                    List<ItineraryResponse.PlaceDto> fbAttraction = googlePlacesService.searchPlaces("tourist_attraction", "체험", Math.max(numPlaces, 6));
                    nonRestPicked = fillNonRest(nonRestPicked, finalSelected, fbAttraction, desiredNonRestaurants, foodTypePrimary, cultureTypePrimary);
                }
                // 4) CAFE (기타)
                if (nonRestPicked.size() < desiredNonRestaurants) {
                    List<ItineraryResponse.PlaceDto> fbCafe = googlePlacesService.searchPlaces("cafe", "카페", Math.max(numPlaces, 6));
                    nonRestPicked = fillNonRest(nonRestPicked, finalSelected, fbCafe, desiredNonRestaurants, foodTypePrimary, cultureTypePrimary);
                }
            } catch (Exception ignored) {}
        }
        
        // 초과 방지 및 합치기
        if (nonRestPicked.size() > desiredNonRestaurants) {
            nonRestPicked = nonRestPicked.subList(0, desiredNonRestaurants);
        }
        finalSelected.addAll(nonRestPicked);

        // 3) 혹시 총합이 N이 안 되면(극소수 케이스) 비식당 우선 평점/가중치 순으로 채움, 넘으면 잘라내기
        if (finalSelected.size() < numPlaces) {
            java.util.Set<String> finalIds = finalSelected.stream().map(ItineraryResponse.PlaceDto::getPlaceId)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());
            // 비식당 우선 채움
            List<ItineraryResponse.PlaceDto> nonRestFillers = allPlaces.stream()
                    .filter(p -> p.getPlaceId() != null && !finalIds.contains(p.getPlaceId()))
                    .filter(p -> !"RESTAURANT".equals(p.getCategory()))
                    .sorted((a, b) -> Double.compare(scorePlace(b, foodTypePrimary, cultureTypePrimary), scorePlace(a, foodTypePrimary, cultureTypePrimary)))
                    .limit(numPlaces - finalSelected.size())
                    .toList();
            finalSelected.addAll(nonRestFillers);
            // 아직 남으면 식당으로 채움
            if (finalSelected.size() < numPlaces) {
                List<ItineraryResponse.PlaceDto> restFillers = allPlaces.stream()
                        .filter(p -> p.getPlaceId() != null && !finalIds.contains(p.getPlaceId()))
                        .filter(p -> "RESTAURANT".equals(p.getCategory()))
                        .sorted((a, b) -> Double.compare(scorePlace(b, foodTypePrimary, cultureTypePrimary), scorePlace(a, foodTypePrimary, cultureTypePrimary)))
                        .limit(numPlaces - finalSelected.size())
                        .toList();
                finalSelected.addAll(restFillers);
            }
        }
        if (finalSelected.size() > numPlaces) {
            finalSelected = finalSelected.subList(0, numPlaces);
        }

        gptSelectedPlaces = finalSelected;
        log.info("Enforced composition -> restaurants: {}, nonRestaurants: {} (N={})",
                gptSelectedPlaces.stream().filter(p -> "RESTAURANT".equals(p.getCategory())).count(),
                gptSelectedPlaces.stream().filter(p -> !"RESTAURANT".equals(p.getCategory())).count(),
                numPlaces);

        // 4. Nearest Neighbor 알고리즘으로 최적 동선 생성 (정확히 N개 사용)
        List<ItineraryResponse.RouteStep> optimizedRoute = createOptimizedRouteWithNearestNeighbor(
            gptSelectedPlaces, transportTypePrimary
        );
        
        // 경로도 정확히 N개 보장 (혹시 방어적으로)
        if (optimizedRoute.size() > numPlaces) {
            optimizedRoute = optimizedRoute.subList(0, numPlaces);
        }
        
        return ItineraryResponse.builder()
                .recommendedPlaces(gptSelectedPlaces)
                .optimizedRoute(optimizedRoute)
                .build();
    }

    // 비식당 보충 헬퍼 (중복 제거 + 랜덤하게 처리)
    private List<ItineraryResponse.PlaceDto> fillNonRest(List<ItineraryResponse.PlaceDto> picked,
                                                         List<ItineraryResponse.PlaceDto> already,
                                                         List<ItineraryResponse.PlaceDto> candidates,
                                                         int desiredNonRestaurants,
                                                         FoodType foodType,
                                                         CultureType cultureType) {
        List<ItineraryResponse.PlaceDto> candidatesList = candidates.stream()
                .filter(p -> !"RESTAURANT".equals(p.getCategory()))
                .collect(java.util.stream.Collectors.toList());
        for (ItineraryResponse.PlaceDto p : candidatesList) {
            if (picked.size() >= desiredNonRestaurants) break;
            boolean dup = already.stream().anyMatch(s -> s.getPlaceId().equals(p.getPlaceId())) ||
                          picked.stream().anyMatch(s -> s.getPlaceId().equals(p.getPlaceId()));
            if (!dup) picked.add(p);
        }
        return picked;
    }

    // 선호 타입 가중치 기반 점수 (멀티 선택 반영)
    private double scorePlace(ItineraryResponse.PlaceDto p, FoodType foodTypePrimary, CultureType cultureTypePrimary) {
        double rating = p.getRating() != null ? p.getRating() : 0.0;
        double bonus = 0.0;
        // 음식 선호: 식당 가중치(기본)
        if ("RESTAURANT".equals(p.getCategory())) {
            bonus += 0.3;
        }
        // 문화 선호: 1차 문화와 매칭 시 가중치
        switch (cultureTypePrimary) {
            case MOVIE -> { if ("MOVIE".equals(p.getCategory())) bonus += 0.6; }
            case NATURE -> { if ("NATURE".equals(p.getCategory())) bonus += 0.6; }
            case EXPERIENCE -> { if ("ATTRACTION".equals(p.getCategory())) bonus += 0.6; }
            case FESTIVAL -> { if ("FESTIVAL".equals(p.getCategory())) bonus += 0.8; }
            case OTHER -> { if ("CULTURE".equals(p.getCategory())) bonus += 0.4; }
            default -> {}
        }
        // 유사 카테고리 소폭 가중치
        if ("CAFE".equals(p.getCategory())) bonus += 0.15;
        if ("ATTRACTION".equals(p.getCategory())) bonus += 0.15;
        return rating + bonus;
    }

    /**
     * Nearest Neighbor 알고리즘을 사용하여 거리 기반 최적 동선 생성
     */
    private List<ItineraryResponse.RouteStep> createOptimizedRouteWithNearestNeighbor(
            List<ItineraryResponse.PlaceDto> places, 
            TransportType transportType) {
        
        if (places.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 1. 장소들을 거리 기반으로 최적 순서 정렬
        List<ItineraryResponse.PlaceDto> optimizedPlaces = optimizeOrderByNearestNeighbor(places);
        
        // 2. 간소화된 경로 생성 (시간 정보 없음)
        List<ItineraryResponse.RouteStep> route = new ArrayList<>();
        
        for (int i = 0; i < optimizedPlaces.size(); i++) {
            ItineraryResponse.PlaceDto place = optimizedPlaces.get(i);
            
            ItineraryResponse.RouteStep step = ItineraryResponse.RouteStep.builder()
                    .orderIndex(i + 1)
                    .name(place.getName())
                    .latitude(place.getLatitude())
                    .longitude(place.getLongitude())
                    .build();
            
            route.add(step);
        }
        
        log.info("Optimized route order: {} places", route.size());
        return route;
    }

    /**
     * Nearest Neighbor 알고리즘으로 장소 순서 최적화
     */
    private List<ItineraryResponse.PlaceDto> optimizeOrderByNearestNeighbor(List<ItineraryResponse.PlaceDto> places) {
        if (places.size() <= 1) {
            return new ArrayList<>(places);
        }
        
        List<ItineraryResponse.PlaceDto> optimized = new ArrayList<>();
        List<ItineraryResponse.PlaceDto> remaining = new ArrayList<>(places);
        
        // 첫 번째 장소 선택 (랜덤하게 선택)
        int randomIndex = (int) (Math.random() * remaining.size());
        ItineraryResponse.PlaceDto current = remaining.get(randomIndex);
        
        optimized.add(current);
        remaining.remove(current);
        
        // Nearest Neighbor로 다음 장소 선택
        while (!remaining.isEmpty()) {
            ItineraryResponse.PlaceDto nearest = findNearestPlace(current, remaining);
            optimized.add(nearest);
            remaining.remove(nearest);
            current = nearest;
        }
        
        log.info("Optimized route order: {} places", optimized.size());
        return optimized;
    }

    /**
     * 현재 위치에서 가장 가까운 다음 장소 찾기
     */
    private ItineraryResponse.PlaceDto findNearestPlace(
            ItineraryResponse.PlaceDto current, 
            List<ItineraryResponse.PlaceDto> candidates) {
        
        return candidates.stream()
                .min((p1, p2) -> {
                    double dist1 = DistanceCalculator.calculateDistance(
                        current.getLatitude(), current.getLongitude(),
                        p1.getLatitude(), p1.getLongitude()
                    );
                    double dist2 = DistanceCalculator.calculateDistance(
                        current.getLatitude(), current.getLongitude(),
                        p2.getLatitude(), p2.getLongitude()
                    );
                    return Double.compare(dist1, dist2);
                })
                .orElse(candidates.get(0));
    }
}


