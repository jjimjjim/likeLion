package dongneidle.DayMaker.service;

import dongneidle.DayMaker.DTO.UserLoginRequest;
import dongneidle.DayMaker.DTO.AuthResponse;
import dongneidle.DayMaker.DTO.UserRegisterRequest;
import dongneidle.DayMaker.DTO.UserProfileResponse;
import dongneidle.DayMaker.entity.User;
import dongneidle.DayMaker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import dongneidle.DayMaker.util.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    /*
     * 회원가입 처리
     * - 필수 입력값 체크(email, password, nickname)
     * - 이메일 중복 검사
     * - 비밀번호 암호화 후 User 엔티티 저장
     * 이메일과 패스워드, 닉네임 입력
        -닉네임 : 영한문 상관 없이 3자 이상 10자 미만
        -이메일 : 이메일 형식 준수할것
        -비밀번호 : 8자 이상 20자 미만이며 대소문자, 숫자, 특수문자를 각각 1개 이상 포함할것
     */
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;// Bean 주입
    private final JwtTokenProvider jwtTokenProvider;

    // 회원가입
    public String register(UserRegisterRequest request) {
        log.info("회원가입 시작: email={}", request.getEmail());
        
        String normalizedEmail = normalizeEmail(request.getEmail());
        String rawPassword = request.getPassword();
        String trimmedNickname = request.getNickname() == null ? null : request.getNickname().trim();

        log.debug("정규화된 이메일: {}, 닉네임: {}", normalizedEmail, trimmedNickname);

        // 이메일 형식 체크
        if (!isValidEmail(normalizedEmail)) {
            log.warn("이메일 형식 오류: {}", normalizedEmail);
            return "이메일 형식이 올바르지 않습니다.";
        }

        // 비밀번호 형식 체크
        if (!isValidPassword(rawPassword)) {
            log.warn("비밀번호 형식 오류");
            return "비밀번호: 8자 이상 20자 미만이며 대소문자, 숫자, 특수문자를 각각 1개 이상 포함해야 합니다.";
        }

        // 닉네임 길이 체크 (영한문 상관없이 3~10자)
        if (!isValidNickname(trimmedNickname)) {
            log.warn("닉네임 길이 오류: {}", trimmedNickname);
            return "닉네임: 3자 이상, 10자 미만이어야 합니다.";
        }

        // 이메일 중복 체크
        if (userRepository.existsById(normalizedEmail)) {
            log.warn("이미 존재하는 이메일: {}", normalizedEmail);
            return "이미 존재하는 이메일입니다.";
        }

        try {
            User user = User.builder()
                    .email(normalizedEmail)
                    .password(passwordEncoder.encode(rawPassword))
                    .nickname(trimmedNickname)
                    .build();

            userRepository.save(user);
            log.info("회원가입 완료: {}", normalizedEmail);
            return "회원가입 완료";
        } catch (Exception e) {
            log.error("회원가입 저장 중 오류 발생", e);
            throw e;
        }
    }

    // 로그인
    public AuthResponse login(UserLoginRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        String rawPassword = request.getPassword();

        if (normalizedEmail == null || normalizedEmail.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            return AuthResponse.builder().success(false).message("이메일 또는 비밀번호가 올바르지 않습니다.").build();
        }

        User user = userRepository.findById(normalizedEmail).orElse(null);
        if (user == null) {
            return AuthResponse.builder().success(false).message("이메일 또는 비밀번호가 올바르지 않습니다.").build();
        }
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            return AuthResponse.builder().success(false).message("이메일 또는 비밀번호가 올바르지 않습니다.").build();
        }
        String token = jwtTokenProvider.createToken(user.getEmail());
        return AuthResponse.builder()
                .success(true)
                .message("로그인 완료 (환영합니다, " + user.getNickname() + "님)")
                .token(token)
                .email(user.getEmail())
                .nickname(user.getNickname())
                .build();
    }

    // 프로필 조회
    public UserProfileResponse getProfileByEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findById(normalizedEmail).orElse(null);
        if (user == null) return null;
        return UserProfileResponse.builder()
                .email(user.getEmail())
                .nickname(user.getNickname())
                .build();
    }

    // 이메일 정규식 검증
    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }

    // 비밀번호 정규식 검증
    private boolean isValidPassword(String password) {
        return password != null &&
                password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,20}$");
    }

    // 닉네임 길이 검증 (영한문 포함)
    private boolean isValidNickname(String nickname) {
        return nickname != null && nickname.length() >= 3 && nickname.length() < 10;
    }

    // 이메일 공백 제거 및 소문자 정규화
    private String normalizeEmail(String email) {
        if (email == null) return null;
        return email.trim().toLowerCase();
    }
}
