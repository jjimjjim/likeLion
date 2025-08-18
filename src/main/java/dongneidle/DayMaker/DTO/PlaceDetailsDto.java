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
public class PlaceDetailsDto {
    private String placeId;                 // places/{place_id} 또는 place_id
    private String name;                    // displayName.text
    private String category;                // primaryType 또는 types[0]
    private String address;                 // formattedAddress
    private Double latitude;                // location.latitude
    private Double longitude;               // location.longitude
    private Double rating;                  // rating
    private Integer userRatingCount;        // userRatingCount
    private String overview;                // editorialSummary.overview
    private Boolean openingNow;             // currentOpeningHours.openNow
    private List<String> openingHours;      // currentOpeningHours.weekdayDescriptions
    private String phone;                   // internationalPhoneNumber
    private List<String> parkingOptions;    // parkingOptions 요약
    private List<String> photoUrls;         // 상위 N개 사진 URL(v1 media photoUri)
}


