package com.fraudgraph.ringdetector.detection;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/detection")
public class DetectionController {

    private final RingDetectionService ringDetectionService;

    public DetectionController(RingDetectionService ringDetectionService) {
        this.ringDetectionService = ringDetectionService;
    }

    @GetMapping("/fan-in")
    public List<CounterpartyFlag> fanIn(
            @RequestParam(defaultValue = "10") long minDistinct,
            @RequestParam(defaultValue = "24") long windowHours) {
        return ringDetectionService.detectFanIn(minDistinct, windowHours);
    }

    @GetMapping("/fan-out")
    public List<CounterpartyFlag> fanOut(
            @RequestParam(defaultValue = "10") long minDistinct,
            @RequestParam(defaultValue = "24") long windowHours) {
        return ringDetectionService.detectFanOut(minDistinct, windowHours);
    }

    @GetMapping("/cycles")
    public List<CycleFlag> cycles(
            @RequestParam(defaultValue = "4") int maxLength,
            @RequestParam(defaultValue = "25") int limit) {
        return ringDetectionService.detectCycles(maxLength, limit);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }
}
