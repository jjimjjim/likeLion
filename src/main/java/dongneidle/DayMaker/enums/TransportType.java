package dongneidle.DayMaker.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TransportType {
    PUBLIC("대중교통", "transit"),
    CAR("자동차", "driving"),
    WALK("도보", "walking"),
    OTHER("기타", "driving");

    private final String displayName;
    private final String googleMode;

    public static TransportType fromDisplayName(String displayName) {
        for (TransportType transportType : values()) {
            if (transportType.displayName.equals(displayName)) {
                return transportType;
            }
        }
        throw new IllegalArgumentException("Unknown transport type: " + displayName);
    }
}

