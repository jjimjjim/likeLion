package dongneidle.DayMaker.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FoodType {
    CAFE("카페", "cafe", "카페"),
    KOREAN("한식", "restaurant", "한식"),
    CHINESE("중식", "restaurant", "중식"),
    WESTERN("양식", "restaurant", "양식"),
    JAPANESE("일식", "restaurant", "일식"),
    OTHER("기타", "restaurant", "맛집");

    private final String displayName;
    private final String googleType;
    private final String searchKeyword;

    public static FoodType fromDisplayName(String displayName) {
        for (FoodType foodType : values()) {
            if (foodType.displayName.equals(displayName)) {
                return foodType;
            }
        }
        throw new IllegalArgumentException("Unknown food type: " + displayName);
    }
}

