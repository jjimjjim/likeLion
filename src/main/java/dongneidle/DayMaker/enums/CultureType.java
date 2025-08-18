package dongneidle.DayMaker.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CultureType {
    MOVIE("영화", "movie_theater", "영화관"),
    PERFORMANCE("공연/전시", "art_gallery|museum", "전시관"),
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

