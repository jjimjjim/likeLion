package dongneidle.DayMaker.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 프론트 입력 스펙을 그대로 받는 요청 DTO
 * - peopleCount: 1인, 2인, 3인, 4인 이상
 * - food(s): 감성카페, 한식, 중식, 양식, 일식, 기타 (복수 선택 가능)
 * - culture(s): 영화, 공연/전시, 체험, 지역축제, 기타 (복수 선택 가능)
 * - transport: 대중교통, 자동차, 도보, 기타
 * - date: 방문하고 싶은 날짜 (YYYY-MM-DD 형식)
 * - numPlaces: 원하는 반환 장소 개수 (예: 3, 4)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItineraryRequest {
    private String date;
    private String peopleCount;
    // 단일 선택(하위호환)
    private String food;
    private String culture;
    // 복수 선택
    private java.util.List<String> foods;
    private java.util.List<String> cultures;
    private String transport;
    private Integer numPlaces; // 원하는 반환 장소 개수
    private String selectedStation; // 선택된 역 (새로 추가)
}


