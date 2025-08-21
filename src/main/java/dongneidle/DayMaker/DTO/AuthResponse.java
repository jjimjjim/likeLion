package dongneidle.DayMaker.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {
    //인증 요청
	private boolean success;// JWT 또는 세션 토큰 생성 성공 여부
	private String message;// 성공/실패 메시지
	private String token;// JWT 또는 세션 토큰
	private String email;// 사용자 이메일
	private String nickname;// 사용자 닉네임
}


