package com.rtsbuilding.rtsbuilding.server.service.page;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LatestPlayerPageRequestQueueTest {
    @Test
    void samePlayerKeepsOnlyLatestRequestPerDrain() {
        LatestPlayerPageRequestQueue<String, String> queue = new LatestPlayerPageRequestQueue<>();
        queue.offer("alice", "initial-open");
        queue.offer("alice", "latest-search");
        queue.offer("bob", "page-two");

        List<String> executed = new ArrayList<>();
        queue.drain(executed::add);

        assertEquals(List.of("latest-search", "page-two"), executed);
        assertEquals(0, queue.size());
    }

    @Test
    void nextTickCanExecuteANewRequest() {
        LatestPlayerPageRequestQueue<String, String> queue = new LatestPlayerPageRequestQueue<>();
        List<String> executed = new ArrayList<>();

        queue.offer("alice", "tick-one");
        queue.drain(executed::add);
        queue.offer("alice", "tick-two");
        queue.drain(executed::add);

        assertEquals(List.of("tick-one", "tick-two"), executed);
    }

    @Test
    void emptyTickExecutesNothing() {
        LatestPlayerPageRequestQueue<String, String> queue = new LatestPlayerPageRequestQueue<>();
        List<String> executed = new ArrayList<>();

        queue.drain(executed::add);

        assertEquals(List.of(), executed);
    }
}
