package dongneidle.DayMaker.controller;

import dongneidle.DayMaker.DTO.ItineraryRequest;
import dongneidle.DayMaker.DTO.ItineraryResponse;
import dongneidle.DayMaker.service.ItineraryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/itineraries")
@RequiredArgsConstructor
public class ItineraryController {

    private final ItineraryService itineraryService;

    @PostMapping
    public ResponseEntity<ItineraryResponse> create(@RequestBody ItineraryRequest request) {
        ItineraryResponse response = itineraryService.createItinerary(request);
        return ResponseEntity.ok(response);
    }
}


