package dongneidle.DayMaker.service;

import dongneidle.DayMaker.DTO.ItineraryResponse;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.ArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class GptService {
    
    @Value("${openai.api.key:}")
    private String openaiApiKey;
    
    @Value("${openai.api.model:gpt-3.5-turbo}")
    private String openaiModel;
    
    private OpenAiService openAiService;
    
    /**
     * GPT API를 사용하여 최적의 장소들을 선택
     * @param allPlaces 모든 추천 장소
     * @param peopleCount 인원수
     * @param transport 교통수단
     * @param maxPlaces 선택할 최대 장소 수
     * @param foodType 음식 타입 (음식점 제한 로직용)
     * @return GPT가 선택한 최적 장소들
     */
    public List<ItineraryResponse.PlaceDto> selectOptimalPlaces(
            List<ItineraryResponse.PlaceDto> allPlaces,
            String peopleCount,
            String transport,
            int maxPlaces,
            List<String> foodType) {
        
        if (openaiApiKey.isEmpty()) {
            log.warn("OpenAI API key not configured, returning first {} places", maxPlaces);
            return applyNRestriction(allPlaces.stream().limit(maxPlaces).collect(Collectors.toList()), foodType);
        }
        
        try {
            // GPT API 서비스 초기화
            if (openAiService == null) {
                openAiService = new OpenAiService(openaiApiKey, Duration.ofSeconds(30));
            }
            
            // 프롬프트 생성
            String prompt = createSelectionPrompt(allPlaces, peopleCount, transport, maxPlaces, foodType);
            
            // GPT API 호출
            String response = callGptApi(prompt);
            
            // 응답 파싱 및 장소 선택
            List<ItineraryResponse.PlaceDto> selectedPlaces = parseGptResponse(response, allPlaces, maxPlaces);
            
            // N개 제한 로직 적용
            return applyNRestriction(selectedPlaces, foodType);
            
        } catch (Exception e) {
            log.error("Error calling GPT API", e);
            // GPT API 실패 시 기본 로직으로 fallback
            List<ItineraryResponse.PlaceDto> fallbackPlaces = allPlaces.stream().limit(maxPlaces).collect(Collectors.toList());
            return applyNRestriction(fallbackPlaces, foodType);
        }
    }
    
    /**
     * 장소 선택을 위한 프롬프트 생성
     */
    private String createSelectionPrompt(
            List<ItineraryResponse.PlaceDto> allPlaces,
            String peopleCount,
            String transport,
            int maxPlaces,
            List<String> foodType) {
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("다음 장소들 중에서 사용자 상황에 맞는 최적의 ").append(maxPlaces).append("개 장소를 선택해주세요.\n\n");
        prompt.append("사용자 정보:\n");
        prompt.append("- 인원수: ").append(peopleCount).append("\n");
        prompt.append("- 교통수단: ").append(transport).append("\n");
        prompt.append("- 음식 타입: ").append(String.join(", ", foodType)).append("\n\n");
        prompt.append("선택 기준:\n");
        prompt.append("1. 평점이 높은 장소 우선\n");
        prompt.append("2. 사용자 인원수에 적합한 장소\n");
        prompt.append("3. 교통수단을 고려한 접근성\n");
        prompt.append("4. 음식 타입과 문화시설의 균형잡힌 선택 (반드시 포함해야 함)\n");
        prompt.append("5. 장소 유형의 다양성 (음식점, 카페, 문화시설 등)\n");
        prompt.append("6. 지역축제 요청 시: 음식점 2-3개 + 문화시설 1-2개로 구성\n");
        prompt.append("7. 음식점이 없으면 안됨! 반드시 음식점과 문화시설을 모두 포함\n\n");
        prompt.append("장소 목록:\n");
        
        for (int i = 0; i < allPlaces.size(); i++) {
            ItineraryResponse.PlaceDto place = allPlaces.get(i);
            prompt.append(i + 1).append(". ").append(place.getName())
                  .append(" (평점: ").append(place.getRating())
                  .append(", 카테고리: ").append(place.getCategory())
                  .append(", 주소: ").append(place.getAddress()).append(")\n");
        }
        
        prompt.append("\n응답 형식: 선택한 장소의 번호만 쉼표로 구분하여 답변해주세요. (예: 1,3,5)");
        
        return prompt.toString();
    }
    
    /**
     * GPT API 호출
     */
    private String callGptApi(String prompt) {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(openaiModel)
                .messages(List.of(new ChatMessage("user", prompt)))
                .maxTokens(100)
                .temperature(0.3)
                .build();
        
        return openAiService.createChatCompletion(request)
                .getChoices().get(0).getMessage().getContent();
    }
    
    /**
     * GPT 응답을 파싱하여 선택된 장소들 반환
     */
    private List<ItineraryResponse.PlaceDto> parseGptResponse(
            String response,
            List<ItineraryResponse.PlaceDto> allPlaces,
            int maxPlaces) {
        
        try {
            // 응답에서 번호 추출 (예: "1,3,5" -> [1,3,5])
            String[] numbers = response.replaceAll("[^0-9,]", "").split(",");
            
            List<ItineraryResponse.PlaceDto> selectedPlaces = new ArrayList<>();
            for (String numberStr : numbers) {
                if (selectedPlaces.size() >= maxPlaces) break;
                
                int index = Integer.parseInt(numberStr.trim()) - 1; // 0-based index
                if (index >= 0 && index < allPlaces.size()) {
                    selectedPlaces.add(allPlaces.get(index));
                }
            }
            
            log.info("GPT selected {} places: {}", selectedPlaces.size(), 
                    selectedPlaces.stream().map(ItineraryResponse.PlaceDto::getName).collect(Collectors.toList()));
            
            return selectedPlaces;
            
        } catch (Exception e) {
            log.error("Error parsing GPT response: {}", response, e);
            // 파싱 실패 시 기본 로직으로 fallback
            return allPlaces.stream().limit(maxPlaces).collect(Collectors.toList());
        }
    }
    
    /**
     * N개 제한 로직 적용
     * - N==3: 음식점 1개
     * - N>=4: 음식점 2개
     * - 먹거리에서 음식점을 골랐을 때만 해당
     */
    private List<ItineraryResponse.PlaceDto> applyNRestriction(
            List<ItineraryResponse.PlaceDto> selectedPlaces, 
            List<String> foodType) {
        
        // 음식점이 아닌 경우 제한 없음
        if (!isRestaurantType(foodType)) {
            return selectedPlaces;
        }
        
        List<ItineraryResponse.PlaceDto> result = new ArrayList<>();
        List<ItineraryResponse.PlaceDto> restaurants = new ArrayList<>();
        List<ItineraryResponse.PlaceDto> nonRestaurants = new ArrayList<>();
        
        // 장소들을 음식점과 비음식점으로 분류
        for (ItineraryResponse.PlaceDto place : selectedPlaces) {
            if (isRestaurant(place)) {
                restaurants.add(place);
            } else {
                nonRestaurants.add(place);
            }
        }
        
        // N개 제한 로직 적용
        int maxRestaurants = selectedPlaces.size() >= 4 ? 2 : 1;
        
        // 음식점 제한 적용
        List<ItineraryResponse.PlaceDto> limitedRestaurants = restaurants.stream()
                .limit(maxRestaurants)
                .collect(Collectors.toList());
        
        // 결과 조합
        result.addAll(limitedRestaurants);
        result.addAll(nonRestaurants);
        
        // 최종 결과가 원래 개수를 넘지 않도록 조정
        if (result.size() > selectedPlaces.size()) {
            result = result.stream().limit(selectedPlaces.size()).collect(Collectors.toList());
        }
        
        log.info("Applied N restriction: {} restaurants (max: {}), {} non-restaurants, total: {}", 
                limitedRestaurants.size(), maxRestaurants, nonRestaurants.size(), result.size());
        
        return result;
    }
    
    /**
     * 음식 타입이 음식점인지 확인
     */
    private boolean isRestaurantType(List<String> foodType) {
        if (foodType == null || foodType.isEmpty()) {
            return false;
        }
        return foodType.stream().anyMatch(type -> 
            "중식".equals(type) || "양식".equals(type) || "일식".equals(type) || "기타".equals(type)
        );
    }
    
    /**
     * 장소가 음식점인지 확인
     */
    private boolean isRestaurant(ItineraryResponse.PlaceDto place) {
        return "RESTAURANT".equals(place.getCategory());
    }
}
