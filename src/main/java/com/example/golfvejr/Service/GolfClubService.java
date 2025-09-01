package com.example.golfvejr.Service;

import com.example.golfvejr.Model.GolfClub;
import com.example.golfvejr.Repository.GolfClubRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GolfClubService {

    private final GolfClubRepository golfClubRepository;

    public List<GolfClub> getAllClubs(){
        return golfClubRepository.findAll();
    }

    public GolfClub getClubById(Long id){
        return golfClubRepository.getReferenceById(id);
    }

    public GolfClub saveClub(GolfClub golfClub){
        return golfClubRepository.save(golfClub);
    }

    public void deleteClub(GolfClub golfClub){
        golfClubRepository.delete(golfClub);
    }
}
