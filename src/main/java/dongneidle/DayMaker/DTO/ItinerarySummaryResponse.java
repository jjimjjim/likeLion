package dongneidle.DayMaker.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItinerarySummaryResponse {
    //목록용 요약
	private Long id;
	private String title;
	private LocalDateTime createdAt;
}


