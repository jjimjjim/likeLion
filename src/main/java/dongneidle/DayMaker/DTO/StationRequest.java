package dongneidle.DayMaker.DTO;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StationRequest {
    private String selectedStation; // 선택된 역명
    private String planningStyle;   // 계획 스타일
    private String foodType;        // 음식 타입
    private String cultureType;     // 문화 타입
    private String transportType;   // 교통 수단
    private String peopleCount;     // 인원 수
}
