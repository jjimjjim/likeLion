package dongneidle.DayMaker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dongneidle.DayMaker.DTO.ItineraryRequest;
import dongneidle.DayMaker.DTO.ItineraryResponse;
import dongneidle.DayMaker.DTO.ItinerarySaveRequest;
import dongneidle.DayMaker.DTO.ItinerarySummaryResponse;
import dongneidle.DayMaker.entity.Itinerary;
import dongneidle.DayMaker.repository.ItineraryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ItinerarySaveService {
    //저장/조회 로직
// save(ItinerarySaveRequest): 요청/응답 DTO를 JSON으로 직렬화해 Itinerary 저장,저장된 id 반환
// listByUser(String userEmail):해당 유저의 코스 요약 목록 반환
// getDetail(Long id): 저장된 responseJson을 역직렬화하여 ItineraryResponse로 반환

	private final ItineraryRepository itineraryRepository;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public Long save(ItinerarySaveRequest request) {
		try {
			String requestJson = objectMapper.writeValueAsString(request.getRequest());
			String responseJson = objectMapper.writeValueAsString(request.getResponse());
			Itinerary entity = Itinerary.builder()
					.userEmail(request.getUserEmail())
					.title(request.getTitle() != null ? request.getTitle() : buildDefaultTitle(request.getRequest()))
					.requestJson(requestJson)
					.responseJson(responseJson)
					.createdAt(LocalDateTime.now())
					.build();
			Itinerary saved = itineraryRepository.save(entity);
			return saved.getId();
		} catch (JsonProcessingException e) {
			throw new RuntimeException("여행 코스 저장에 실패했습니다.");
		}
	}

	public List<ItinerarySummaryResponse> listByUser(String userEmail) {
		return itineraryRepository.findByUserEmailOrderByCreatedAtDesc(userEmail)
				.stream()
				.map(e -> ItinerarySummaryResponse.builder()
						.id(e.getId())
						.title(e.getTitle())
						.createdAt(e.getCreatedAt())
						.build())
				.collect(Collectors.toList());
	}

	public ItineraryResponse getDetail(Long id, String userEmail) {
		return itineraryRepository.findById(id)
				.filter(e -> userEmail != null && userEmail.equals(e.getUserEmail()))
				.map(e -> {
					try {
						return objectMapper.readValue(e.getResponseJson(), ItineraryResponse.class);
					} catch (JsonProcessingException ex) {
						throw new RuntimeException("저장된 데이터를 읽는 데 실패했습니다.");
					}
				})
				.orElseThrow(() -> new RuntimeException("코스를 찾을 수 없습니다."));
	}

	private String buildDefaultTitle(ItineraryRequest req) {
		String date = req != null && req.getDate() != null ? req.getDate() : "언제든";
		return "여행 코스 - " + date;
	}
}


