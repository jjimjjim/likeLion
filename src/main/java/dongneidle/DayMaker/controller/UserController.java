package dongneidle.DayMaker.controller;

import dongneidle.DayMaker.DTO.UserLoginRequest;
import dongneidle.DayMaker.DTO.UserRegisterRequest;
import dongneidle.DayMaker.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class UserController {
    private final UserService userService;

    // 회원가입
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody UserRegisterRequest request) {
        String result = userService.register(request);
        boolean success = result.startsWith("회원가입 완료");

        return ResponseEntity
                .status(success ? 200 : 400) // 성공: 200, 실패: 400
                .body(Map.of(
                        "success", success,
                        "message", result
                ));
    }

    // 로그인
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody UserLoginRequest request) {
        String result = userService.login(request);
        boolean success = result.startsWith("로그인 완료");

        return ResponseEntity
                .status(success ? 200 : 400)
                .body(Map.of(
                        "success", success,
                        "message", result
                ));
    }
}
