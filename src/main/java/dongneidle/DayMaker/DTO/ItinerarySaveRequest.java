package dongneidle.DayMaker.DTO;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ItinerarySaveRequest {
	//저장 요청 바디
	private String title;
	private String userEmail; // 로그인 토큰 도입 전까지 요청에서 받음
	private ItineraryRequest request;
	private ItineraryResponse response;
}


