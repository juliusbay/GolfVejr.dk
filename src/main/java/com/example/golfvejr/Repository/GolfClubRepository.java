package com.example.golfvejr.Repository;

import com.example.golfvejr.Model.Golfclub;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GolfClubRepository extends JpaRepository<Golfclub, Long> {
    boolean existsByName(String name);
    List<Golfclub> findAllByOrderByNameAsc();
}
