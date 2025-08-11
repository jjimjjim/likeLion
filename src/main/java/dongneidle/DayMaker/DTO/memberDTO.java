package dongneidle.DayMaker.DTO;

public class memberDTO {
    @Getter
    @Setter
    public class UserRegisterRequest {
        private String email;
        private String password;
        private String nickname;
    }

    @Getter
    @Setter
    public class UserLoginRequest {
        private String email;
        private String password;
    }
}
