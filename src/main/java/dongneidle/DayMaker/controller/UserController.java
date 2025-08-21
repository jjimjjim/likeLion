package dongneidle.DayMaker.controller;

import dongneidle.DayMaker.DTO.UserProfileResponse;
import dongneidle.DayMaker.DTO.UserLoginRequest;
import dongneidle.DayMaker.DTO.UserRegisterRequest;
import dongneidle.DayMaker.service.UserService;
import dongneidle.DayMaker.util.JwtTokenProvider;
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
    private final JwtTokenProvider jwtTokenProvider;

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

    // 내 프로필 조회
    @GetMapping("/me")
    @Operation(summary = "내 프로필", description = "JWT의 이메일을 이용해 프로필 조회")
    public ResponseEntity<?> me(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        if (authorization == null || authorization.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false, "message", "인증이 필요합니다."));
        }
        String value = authorization.trim();
        String token = value.regionMatches(true, 0, "Bearer ", 0, 7) ? value.substring(7).trim() : value;
        if ((token.startsWith("\"") && token.endsWith("\"")) || (token.startsWith("'") && token.endsWith("'"))) {
            token = token.substring(1, token.length() - 1);
        }
        String email = jwtTokenProvider.validateAndGetEmail(token);
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false, "message", "토큰이 유효하지 않습니다."));
        }
        UserProfileResponse profile = userService.getProfileByEmail(email);
        if (profile == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", "사용자를 찾을 수 없습니다."));
        }
        return ResponseEntity.ok(profile);
    }
}
