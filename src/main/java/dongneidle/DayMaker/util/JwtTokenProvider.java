package dongneidle.DayMaker.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtTokenProvider {

	@Value("${security.jwt.secret:change-me-secret}")
	private String secret;

	@Value("${security.jwt.issuer:daymaker}")
	private String issuer;

	@Value("${security.jwt.expiration-seconds:86400}")
	private long expirationSeconds;

	private final ObjectMapper objectMapper = new ObjectMapper();

	public String createToken(String userEmail) {
		try {
			Map<String, Object> header = new HashMap<>();
			header.put("alg", "HS256");
			header.put("typ", "JWT");
			header.put("iss", issuer);

			long now = Instant.now().getEpochSecond();
			Map<String, Object> payload = new HashMap<>();
			payload.put("sub", userEmail);
			payload.put("iat", now);
			payload.put("exp", now + expirationSeconds);

			String headerJson = objectMapper.writeValueAsString(header);
			String payloadJson = objectMapper.writeValueAsString(payload);

			String headerB64 = base64UrlEncode(headerJson.getBytes(StandardCharsets.UTF_8));
			String payloadB64 = base64UrlEncode(payloadJson.getBytes(StandardCharsets.UTF_8));
			String unsigned = headerB64 + "." + payloadB64;
			String signature = sign(unsigned, secret);
			return unsigned + "." + signature;
		} catch (JsonProcessingException e) {
			throw new RuntimeException("토큰 생성 실패", e);
		}
	}

	public String validateAndGetEmail(String token) {
		try {
			String[] parts = token.split("\\.");
			if (parts.length != 3) return null;
			String unsigned = parts[0] + "." + parts[1];
			String expectedSig = sign(unsigned, secret);
			if (!constantTimeEquals(expectedSig, parts[2])) return null;

			String payloadJson = new String(base64UrlDecode(parts[1]), StandardCharsets.UTF_8);
			@SuppressWarnings("unchecked")
			Map<String, Object> payload = objectMapper.readValue(payloadJson, Map.class);
			// exp 검증
			Object expObj = payload.get("exp");
			long now = Instant.now().getEpochSecond();
			long exp = (expObj instanceof Number) ? ((Number) expObj).longValue() : 0L;
			if (exp < now) return null;
			// sub 반환
			Object sub = payload.get("sub");
			return sub != null ? sub.toString() : null;
		} catch (Exception e) {
			return null;
		}
	}

	private String sign(String data, String secret) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
			return base64UrlEncode(raw);
		} catch (Exception e) {
			throw new RuntimeException("토큰 서명 실패", e);
		}
	}

	private static String base64UrlEncode(byte[] bytes) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static byte[] base64UrlDecode(String str) {
		return Base64.getUrlDecoder().decode(str);
	}

	private static boolean constantTimeEquals(String a, String b) {
		if (a == null || b == null || a.length() != b.length()) return false;
		int result = 0;
		for (int i = 0; i < a.length(); i++) {
			result |= a.charAt(i) ^ b.charAt(i);
		}
		return result == 0;
	}
}


