package dongneidle.DayMaker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {//데이터베이스 users 테이블과 매핑
    @Id
    @Column(nullable = false, unique = true)
    // 사용자 이메일 (PK, 고유값)
    private String email;

    @Column(nullable = false)
    private String nickname;

    @Column(nullable = false)
    private String password; // 암호화된 비밀번호 저장
}
