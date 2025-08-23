package dongneidle.DayMaker.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CultureType {
    MOVIE("영화/공연/전시", "movie_theater|museum|art_gallery", "영화관"),
    NATURE("자연/공원", "park|nature", "자연"),
    EXPERIENCE("체험", "tourist_attraction", "체험"),
    FESTIVAL("지역축제", "tourist_attraction", "축제"),
    OTHER("기타", "tourist_attraction", "문화시설");

    private final String displayName;
    private final String googleType;
    private final String searchKeyword;

    public static CultureType fromDisplayName(String displayName) {
        for (CultureType cultureType : values()) {
            if (cultureType.displayName.equals(displayName)) {
                return cultureType;
            }
        }
        throw new IllegalArgumentException("Unknown culture type: " + displayName);
    }
}

