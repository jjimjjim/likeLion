package dongneidle.DayMaker.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "stations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Station {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String name; // 역명 (명학, 안양, 관악, 석수, 범계, 평촌, 인덕원)
    
    @Column(nullable = false)
    private Double latitude; // 위도
    
    @Column(nullable = false)
    private Double longitude; // 경도
    
    @Column(nullable = false)
    private String line; // 호선 정보 (예: 1호선, 4호선)
    
    @Column
    private String description; // 역 설명
}
