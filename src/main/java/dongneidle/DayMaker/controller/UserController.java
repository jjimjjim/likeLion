package dongneidle.DayMaker.controller;

import dongneidle.DayMaker.DTO.UserLoginRequest;
import dongneidle.DayMaker.DTO.UserRegisterRequest;
import dongneidle.DayMaker.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
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

        HttpStatus status = success ? HttpStatus.OK : (result.contains("이미 존재") ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST);
        return ResponseEntity
                .status(status)
                .body(Map.of(
                        "success", success,
                        "message", result
                ));
    }

    // 로그인
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody UserLoginRequest request) {
        var result = userService.login(request);
        HttpStatus status = result.isSuccess() ? HttpStatus.OK : HttpStatus.UNAUTHORIZED;
        return ResponseEntity.status(status).body(result);
    }
}
