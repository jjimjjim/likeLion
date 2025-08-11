package dongneidle.DayMaker.repository;

import dongneidle.DayMaker.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

//User 엔티티의 데이터 접근을 위한 JPA 레포지토리 인터페이스
public interface UserRepository extends JpaRepository<User, String> {
}
