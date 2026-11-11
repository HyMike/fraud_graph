package com.fraudgraph.ringdetector.controller;

import com.fraudgraph.ringdetector.constants.ApiPaths;
import com.fraudgraph.ringdetector.dto.response.CounterpartyFlag;
import com.fraudgraph.ringdetector.dto.response.CycleFlag;
import com.fraudgraph.ringdetector.service.RingDetectionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.fraudgraph.ringdetector.constants.DetectionConstants.DEFAULT_CYCLE_LIMIT;
import static com.fraudgraph.ringdetector.constants.DetectionConstants.DEFAULT_CYCLE_MAX_LENGTH;
import static com.fraudgraph.ringdetector.constants.DetectionConstants.DEFAULT_MIN_DISTINCT_COUNTERPARTIES;
import static com.fraudgraph.ringdetector.constants.DetectionConstants.DEFAULT_WINDOW_HOURS;

@RestController
@RequestMapping(ApiPaths.DETECTION)
public class DetectionController {

    private final RingDetectionService ringDetectionService;

    public DetectionController(RingDetectionService ringDetectionService) {
        this.ringDetectionService = ringDetectionService;
    }

    @GetMapping(ApiPaths.FAN_IN)
    public List<CounterpartyFlag> fanIn(
            @RequestParam(defaultValue = DEFAULT_MIN_DISTINCT_COUNTERPARTIES) long minDistinct,
            @RequestParam(defaultValue = DEFAULT_WINDOW_HOURS) long windowHours) {
        return ringDetectionService.detectFanIn(minDistinct, windowHours);
    }

    @GetMapping(ApiPaths.FAN_OUT)
    public List<CounterpartyFlag> fanOut(
            @RequestParam(defaultValue = DEFAULT_MIN_DISTINCT_COUNTERPARTIES) long minDistinct,
            @RequestParam(defaultValue = DEFAULT_WINDOW_HOURS) long windowHours) {
        return ringDetectionService.detectFanOut(minDistinct, windowHours);
    }

    @GetMapping(ApiPaths.CYCLES)
    public List<CycleFlag> cycles(
            @RequestParam(defaultValue = DEFAULT_CYCLE_MAX_LENGTH) int maxLength,
            @RequestParam(defaultValue = DEFAULT_CYCLE_LIMIT) int limit) {
        return ringDetectionService.detectCycles(maxLength, limit);
    }
}
