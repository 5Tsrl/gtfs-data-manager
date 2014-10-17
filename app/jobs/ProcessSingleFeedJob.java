package jobs;

import models.FeedVersion;

/**
 * Process/validate a single GTFS feed
 * @author mattwigway
 *
 */
public class ProcessSingleFeedJob implements Runnable {
    FeedVersion feedVersion;
    
    /**
     * Create a job for the given feed version.
     * @param feedVersion
     */
    public ProcessSingleFeedJob (FeedVersion feedVersion) {
        this.feedVersion = feedVersion;
    }
    
    public void run() {
        feedVersion.hash();
        feedVersion.validate();
        feedVersion.save();
    }

}
