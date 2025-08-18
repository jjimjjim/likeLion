package dongneidle.DayMaker.repository;

import dongneidle.DayMaker.entity.Festival;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface FestivalRepository extends JpaRepository<Festival, Long> {
    
    @Query("SELECT f FROM Festival f WHERE f.startDate <= :date AND f.endDate >= :date")
    List<Festival> findByDateRange(@Param("date") LocalDate date);
    
    @Query("SELECT f FROM Festival f WHERE f.startDate <= :endDate AND f.endDate >= :startDate")
    List<Festival> findByDateRange(@Param("startDate") LocalDate startDate, 
                                   @Param("endDate") LocalDate endDate);
}

