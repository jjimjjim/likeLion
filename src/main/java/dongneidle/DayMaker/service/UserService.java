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
     */
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public String register(UserRegisterRequest request) {
        if (request.getEmail() == null || request.getEmail().isEmpty()) {
            return "Email is required";
        }
        if (request.getPassword() == null || request.getPassword().isEmpty()) {
            return "Password is required";
        }
        if (request.getNickname() == null || request.getNickname().isEmpty()) {
            return "Nickname is required";
        }
        if (userRepository.existsById(request.getEmail())) {
            return "Email already exists";
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .build();

        userRepository.save(user);
        return "User registered successfully";
    }

    public String login(UserLoginRequest request) {
        User user = userRepository.findById(request.getEmail()).orElse(null);
        if (user == null) {
            return "User not found";
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return "Invalid password";
        }
        return "Login successful (Welcome, " + user.getNickname() + ")";
    }
}
