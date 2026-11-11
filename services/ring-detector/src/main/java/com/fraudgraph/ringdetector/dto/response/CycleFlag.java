package com.fraudgraph.ringdetector.dto.response;

import java.util.List;

/** A bounded-length TRANSFER cycle, e.g. A -> B -> C -> A. */
public record CycleFlag(List<String> accountIds) {
}
