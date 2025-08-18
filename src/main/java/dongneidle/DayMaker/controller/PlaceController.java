package dongneidle.DayMaker.controller;

import dongneidle.DayMaker.DTO.PlaceDetailsDto;
import dongneidle.DayMaker.service.GooglePlacesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class PlaceController {

    private final GooglePlacesService googlePlacesService;

    @GetMapping("/{placeId}")
    public ResponseEntity<PlaceDetailsDto> getPlaceDetails(
            @PathVariable String placeId,
            @RequestParam(name = "maxPhotos", defaultValue = "3") int maxPhotos
    ) {
        PlaceDetailsDto dto = googlePlacesService.fetchPlaceDetailsV1(placeId, maxPhotos);
        return ResponseEntity.ok(dto);
    }
}


