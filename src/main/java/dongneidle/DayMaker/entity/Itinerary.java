package dongneidle.DayMaker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "itineraries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Itinerary {
//  저장된 코스 한 건을 표현
// 필드: id, userEmail, title, requestJson(요청 DTO 원본), responseJson(추천 결과 원본), createdAt
// 코스 생성 당시의 입력/결과를 그대로 JSON으로 보존해 재현 가능
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String userEmail;

	@Column(nullable = false)
	private String title;

	@Lob
	@Column(nullable = false)
	private String requestJson;

	@Lob
	@Column(nullable = false)
	private String responseJson;

	@Column(nullable = false)
	private LocalDateTime createdAt;
}


