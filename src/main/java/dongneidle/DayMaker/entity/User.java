package dongneidle.DayMaker.entity;

public class User {
    @Id
    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String nickname;

    @Column(nullable = false)
    private String password; // 암호화된 비밀번호 저장
}
