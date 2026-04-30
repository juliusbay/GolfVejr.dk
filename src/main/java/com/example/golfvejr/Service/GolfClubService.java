package com.example.golfvejr.Service;

import com.example.golfvejr.Exception.ClubNotFoundException;
import com.example.golfvejr.Model.Golfclub;
import com.example.golfvejr.Repository.GolfClubRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GolfClubService {

    private final GolfClubRepository golfClubRepository;

    public List<Golfclub> getAllClubs(){
        return golfClubRepository.findAllByOrderByNameAsc();
    }

    public Golfclub getClubById(Long id){
        return golfClubRepository.findById(id)
                .orElseThrow(() -> new ClubNotFoundException(id));
    }

    public Golfclub saveClub(Golfclub golfClub){
        return golfClubRepository.save(golfClub);
    }

    public void deleteClub(Golfclub golfClub){
        golfClubRepository.delete(golfClub);
    }
}
