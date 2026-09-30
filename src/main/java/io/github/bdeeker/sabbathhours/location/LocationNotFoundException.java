package io.github.bdeeker.sabbathhours.location;

import java.util.UUID;

public class LocationNotFoundException extends RuntimeException {

    public LocationNotFoundException(UUID id) {
        super("No location with id " + id);
    }
}
