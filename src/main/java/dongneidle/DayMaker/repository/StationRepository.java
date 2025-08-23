package dongneidle.DayMaker.repository;

import dongneidle.DayMaker.entity.Station;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StationRepository extends JpaRepository<Station, Long> {
    
    @Query("SELECT s FROM Station s WHERE s.name = :name")
    Optional<Station> findByName(@Param("name") String name);
    
    List<Station> findAllByOrderByName();
}
