package dongneidle.DayMaker.repository;

import dongneidle.DayMaker.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

//User 엔티티의 데이터 접근을 위한 JPA 레포지토리 인터페이스
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmail(String email);
    List<User> findByNickname(String nickname);
    boolean existsByEmail(String email);
    // 검색형
    @org.springframework.data.jpa.repository.Query("select u from User u where u.nickname like %:kw%")
    List<User> searchByNickname(@org.springframework.data.repository.query.Param("kw") String kw);
}
