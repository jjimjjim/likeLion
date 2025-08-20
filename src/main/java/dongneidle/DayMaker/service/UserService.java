package dongneidle.DayMaker.service;

import dongneidle.DayMaker.DTO.UserLoginRequest;
import dongneidle.DayMaker.DTO.UserRegisterRequest;
import dongneidle.DayMaker.entity.User;
import dongneidle.DayMaker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
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
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

//    public String register(UserRegisterRequest request) {
//        if (request.getEmail() == null || request.getEmail().isEmpty()) {
//            return "이메일 형식이 올바르지 않습니다.";
//        }
//        if (request.getPassword() == null || request.getPassword().isEmpty()) {
//            return "비밀번호: 8자 이상 20자 미만이며 대소문자, 숫자, 특수문자를 각각 1개 이상 포함해야 합니다.";
//        }
//        if (request.getNickname() == null || request.getNickname().isEmpty()) {
//            return "Nickname is required";
//        }
//        if (userRepository.existsById(request.getEmail())) {
//            return "Email already exists";
//        }
//
//        User user = User.builder()
//                .email(request.getEmail())
//                .password(passwordEncoder.encode(request.getPassword()))
//                .nickname(request.getNickname())
//                .build();
//
//        userRepository.save(user);
//        return "User registered successfully";
//    }

    // 회원가입
    public String register(UserRegisterRequest request) {
        // 이메일 형식 체크
        if (!isValidEmail(request.getEmail())) {
            return "이메일 형식이 올바르지 않습니다.";
        }

        // 비밀번호 형식 체크
        if (!isValidPassword(request.getPassword())) {
            return "비밀번호: 8자 이상 20자 미만이며 대소문자, 숫자, 특수문자를 각각 1개 이상 포함해야 합니다.";
        }

        // 닉네임 길이 체크 (영한문 상관없이 3~10자)
        if (!isValidNickname(request.getNickname())) {
            return "닉네임: 3자 이상, 10자 미만이어야 합니다.";
        }

        // 이메일 중복 체크
        if (userRepository.existsById(request.getEmail())) {
            return "이미 존재하는 이메일입니다.";
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .build();

        userRepository.save(user);
        return "회원가입 완료";
    }

    // 로그인
    public String login(UserLoginRequest request) {
        User user = userRepository.findById(request.getEmail()).orElse(null);
        if (user == null) {
            return "이메일 정보가 맞지 않습니다.";
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return "비밀번호가 틀렸습니다.";
        }
        return "로그인 완료 (환영합니다, " + user.getNickname() + "님)";
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
}
