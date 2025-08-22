package dongneidle.DayMaker.repository;

import dongneidle.DayMaker.entity.Station;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StationRepository extends JpaRepository<Station, Long> {
    Optional<Station> findByName(String name);
    List<Station> findAllByOrderByName();
}
