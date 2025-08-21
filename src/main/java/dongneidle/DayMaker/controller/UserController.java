package dongneidle.DayMaker.controller;

import dongneidle.DayMaker.DTO.AuthResponse;
import dongneidle.DayMaker.DTO.UserLoginRequest;
import dongneidle.DayMaker.DTO.UserRegisterRequest;
import dongneidle.DayMaker.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // 회원가입
    @PostMapping("/register")
    @Operation(summary = "회원가입", description = "간단한 데모용 회원가입")
    public ResponseEntity<?> register(@RequestBody UserRegisterRequest request) {
        String result = userService.register(request);
        boolean success = result.startsWith("회원가입 완료");
        return ResponseEntity.ok(Map.of(
                "success", success,
                "message", result
        ));
    }

    // 로그인
    @PostMapping("/login")
    @Operation(summary = "로그인", description = "간단한 데모용 로그인")
    public ResponseEntity<?> login(@RequestBody UserLoginRequest request) {
        var result = userService.login(request);
        HttpStatus status = result.isSuccess() ? HttpStatus.OK : HttpStatus.UNAUTHORIZED;
        return ResponseEntity.status(status).body(result);
    }
}
