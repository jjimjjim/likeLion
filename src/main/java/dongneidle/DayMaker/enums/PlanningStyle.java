package dongneidle.DayMaker.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PlanningStyle {
    PACKED("빼곡하게", 60),    // 60분 체류
    NORMAL("기본", 90),        // 90분 체류  
    RELAXED("여유롭게", 120);  // 120분 체류

    private final String displayName;
    private final int stayMinutes;

    public static PlanningStyle fromDisplayName(String displayName) {
        for (PlanningStyle planningStyle : values()) {
            if (planningStyle.displayName.equals(displayName)) {
                return planningStyle;
            }
        }
        throw new IllegalArgumentException("Unknown planning style: " + displayName);
    }
}

