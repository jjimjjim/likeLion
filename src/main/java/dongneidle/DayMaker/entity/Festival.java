package dongneidle.DayMaker.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "festivals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Festival {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name;           // 축제 이름
    private String description;    // 축제 설명
    private String location;       // 축제 장소
    private Double latitude;       // 위도
    private Double longitude;      // 경도
    private LocalDate startDate;   // 시작일
    private LocalDate endDate;     // 종료일
    private String imageUrl;       // 축제 이미지
    private String category;       // 축제 카테고리 (문화, 음식, 전통 등)
    private String address;        // 상세 주소
}

