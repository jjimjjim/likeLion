package dongneidle.DayMaker.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PeopleCount {
    ONE("1인", 1),
    TWO("2인", 2), 
    THREE("3인", 3),
    FOUR_PLUS("4인 이상", 4);

    private final String displayName;
    private final int count;

    public static PeopleCount fromDisplayName(String displayName) {
        for (PeopleCount peopleCount : values()) {
            if (peopleCount.displayName.equals(displayName)) {
                return peopleCount;
            }
        }
        throw new IllegalArgumentException("Unknown people count: " + displayName);
    }
}

