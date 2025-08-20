package dongneidle.DayMaker.repository;

import dongneidle.DayMaker.entity.Itinerary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItineraryRepository extends JpaRepository<Itinerary, Long> {
	List<Itinerary> findByUserEmailOrderByCreatedAtDesc(String userEmail);
	Optional<Itinerary> findByIdAndUserEmail(Long id, String userEmail);
}
// 코스 CRUD
// findByUserEmailOrderByCreatedAtDesc(String): 유저별 코스 목록(최신순) 조회

