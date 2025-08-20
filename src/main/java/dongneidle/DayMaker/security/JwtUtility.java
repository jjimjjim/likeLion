package dongneidle.DayMaker.security;

import dongneidle.DayMaker.util.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtUtility {
    private final JwtTokenProvider jwtTokenProvider;

    // JWT 생성
    public String generateToken(String email) {
        return jwtTokenProvider.createToken(email);
    }

    // JWT에서 이메일 추출
    public String getEmailFromToken(String token) {
        return jwtTokenProvider.validateAndGetEmail(token);
    }

    // 토큰 유효성 검사
    public boolean validateToken(String token) {
        return jwtTokenProvider.validateAndGetEmail(token) != null;
    }
}
