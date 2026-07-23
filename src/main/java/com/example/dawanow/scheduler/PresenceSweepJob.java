// package com.example.dawanow.scheduler;

// import com.example.dawanow.service.PresenceService;
// import lombok.RequiredArgsConstructor;
// import lombok.extern.slf4j.Slf4j;
// import org.springframework.scheduling.annotation.Scheduled;
// import org.springframework.stereotype.Component;

// @Slf4j
// @Component
// @RequiredArgsConstructor
// public class PresenceSweepJob {

//    private final PresenceService presenceService;

//    // runs every 60s — same order of magnitude as the heartbeat interval,
//    // no point sweeping faster than your timeout resolution
//    @Scheduled(fixedRate = 60_000)
//    public void sweep() {
//        int flipped = presenceService.sweepStalePresences();
//        if (flipped > 0) {
//            log.info("Presence sweep: flipped {} stale pharmacist(s) to off-duty", flipped);
//        }
//    }
// }
