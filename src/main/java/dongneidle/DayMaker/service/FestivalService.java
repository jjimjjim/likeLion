package dongneidle.DayMaker.service;

import dongneidle.DayMaker.DTO.ItineraryResponse;
import dongneidle.DayMaker.entity.Festival;
import dongneidle.DayMaker.repository.FestivalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FestivalService {
    
    private final FestivalRepository festivalRepository;
    
    // 오늘 진행 중인 축제 찾기
    public List<Festival> getTodayFestivals() {
        LocalDate today = LocalDate.now();
        List<Festival> festivals = festivalRepository.findByDateRange(today, today);
        log.info("Found {} festivals for today: {}", festivals.size(), today);
        return festivals;
    }
    
    // 특정 날짜의 축제 찾기
    public List<Festival> getFestivalsByDate(LocalDate date) {
        return festivalRepository.findByDateRange(date, date);
    }
    
    // 요청받은 날짜에 진행 중인 축제 찾기 (문자열 날짜 파싱)
    public List<Festival> getFestivalsByRequestDate(String requestDate) {
        try {
            LocalDate date = LocalDate.parse(requestDate);
            List<Festival> festivals = getFestivalsByDate(date);
            log.info("Found {} festivals for requested date: {}", festivals.size(), date);
            return festivals;
        } catch (Exception e) {
            log.warn("Invalid date format: {}, returning today's festivals", requestDate);
            return getTodayFestivals();
        }
    }
    
    // 축제를 Place 형태로 변환
    public List<ItineraryResponse.PlaceDto> getTodayFestivalsAsPlaces() {
        List<Festival> festivals = getTodayFestivals();
        return festivals.stream()
                .map(this::convertFestivalToPlace)
                .collect(Collectors.toList());
    }
    
    // 요청받은 날짜의 축제를 Place 형태로 변환
    public List<ItineraryResponse.PlaceDto> getFestivalsAsPlacesByRequestDate(String requestDate) {
        List<Festival> festivals = getFestivalsByRequestDate(requestDate);
        return festivals.stream()
                .map(this::convertFestivalToPlace)
                .collect(Collectors.toList());
    }
    
    private ItineraryResponse.PlaceDto convertFestivalToPlace(Festival festival) {
        return ItineraryResponse.PlaceDto.builder()
                .name(festival.getName())
                .category("FESTIVAL")
                .address(festival.getAddress() != null ? festival.getAddress() : festival.getLocation())
                .latitude(festival.getLatitude())
                .longitude(festival.getLongitude())
                .rating(5.0) // 축제는 기본 5점
                .placeId("festival-" + festival.getId())
                .imageUrl(festival.getImageUrl())
                .build();
    }
    
    // 초기 축제 데이터 생성 (개발용)
    public void createInitialFestivals() {
        if (festivalRepository.count() == 0) {
            Festival festival1 = Festival.builder()
                    .name("2025 안양충훈벚꽃축제")
                    .description("충훈동 충훈2교 및 벚꽃길 일대에서 열리는 봄맞이 벚꽃축제")
                    .location("충훈동 충훈2교 및 벚꽃길 일대")
                    .latitude(37.3942)
                    .longitude(126.9569)
                    .startDate(LocalDate.of(2025, 4, 5))
                    .endDate(LocalDate.of(2025, 4, 6))
                    .category("자연생태")
                    .address("안양시 만안구 충훈동")
                    .build();
            
            Festival festival2 = Festival.builder()
                    .name("제34회 안양예술제")
                    .description("평촌중앙공원에서 열리는 문화예술 축제")
                    .location("평촌중앙공원")
                    .latitude(37.3902)
                    .longitude(126.9506)
                    .startDate(LocalDate.of(2025, 5, 2))
                    .endDate(LocalDate.of(2025, 5, 3))
                    .category("문화예술")
                    .address("안양시 동안구 평촌중앙공원")
                    .build();
            
            Festival festival3 = Festival.builder()
                    .name("제22회 안양스마T움축제")
                    .description("안양체육관에서 열리는 스마트 기술 축제")
                    .location("안양체육관")
                    .latitude(37.3960)
                    .longitude(126.9540)
                    .startDate(LocalDate.of(2025, 5, 31))
                    .endDate(LocalDate.of(2025, 6, 1))
                    .category("문화예술")
                    .address("안양시 동안구 안양체육관")
                    .build();
            
            Festival festival4 = Festival.builder()
                    .name("2025 안양춤축제")
                    .description("평촌중앙공원과 삼덕공원에서 열리는 춤 축제")
                    .location("평촌중앙공원, 삼덕공원")
                    .latitude(37.3902)
                    .longitude(126.9506)
                    .startDate(LocalDate.of(2025, 9, 26))
                    .endDate(LocalDate.of(2025, 9, 28))
                    .category("문화예술")
                    .address("안양시 동안구 평촌중앙공원")
                    .build();
            
            Festival festival5 = Festival.builder()
                    .name("먹거리 한마당")
                    .description("평촌중앙공원 다목적운동장에서 열리는 음식 축제")
                    .location("평촌중앙공원 다목적운동장")
                    .latitude(37.3902)
                    .longitude(126.9506)
                    .startDate(LocalDate.of(2025, 9, 26))
                    .endDate(LocalDate.of(2025, 9, 28))
                    .category("문화예술")
                    .address("안양시 동안구 평촌중앙공원")
                    .build();
            
            Festival festival6 = Festival.builder()
                    .name("안양1번가 넘버원 페스티벌")
                    .description("안양1번가 일원에서 열리는 주민화합 축제")
                    .location("안양1번가 일원")
                    .latitude(37.4016)
                    .longitude(126.9228)
                    .startDate(LocalDate.of(2025, 10, 17))
                    .endDate(LocalDate.of(2025, 10, 18))
                    .category("주민화합")
                    .address("안양시 만안구 안양1번가")
                    .build();
            
            festivalRepository.save(festival1);
            festivalRepository.save(festival2);
            festivalRepository.save(festival3);
            festivalRepository.save(festival4);
            festivalRepository.save(festival5);
            festivalRepository.save(festival6);
            log.info("Initial festivals created: 6 official Anyang festivals");
        }
    }
}

