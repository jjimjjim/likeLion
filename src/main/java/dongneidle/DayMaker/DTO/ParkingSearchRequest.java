package dongneidle.DayMaker.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParkingSearchRequest {
    private double lat;              // 위도
    private double lng;              // 경도
    private Integer radius;          // 미터 (기본 1500)
    private Integer limit;           // 최대 개수 (기본 20, 최대 50)
    private String language;         // 기본 ko
    private String rankPreference;   // DISTANCE | RELEVANCE (기본 DISTANCE)
}


