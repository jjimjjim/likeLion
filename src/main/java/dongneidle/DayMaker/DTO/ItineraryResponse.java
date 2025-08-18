package dongneidle.DayMaker.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItineraryResponse {
    private List<PlaceDto> recommendedPlaces;
    private List<RouteStep> optimizedRoute;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlaceDto {
        private String name;
        private String category; // RESTAURANT, CAFE, CULTURE, ATTRACTION, FESTIVAL
        private String address;
        private Double latitude;
        private Double longitude;
        private Double rating;
        private String placeId; // 외부(구글) 식별자
        private String imageUrl;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RouteStep {
        private int orderIndex;      // 방문 순서
        private String name;         // 장소명
        private Double latitude;     // 위도
        private Double longitude;    // 경도
    }
}


