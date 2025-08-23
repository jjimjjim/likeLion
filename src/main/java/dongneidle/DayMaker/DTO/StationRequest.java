package dongneidle.DayMaker.DTO;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class StationRequest {
    private String selectedStation; // 선택된 역명
    private List<String> foodType;  // 음식 타입 (배열)
    private String cultureType;     // 문화 타입
    private String peopleCount;     // 인원 수
}
