package dev.starwars.swapi;

/** SWAPI answered something unexpected: a 4xx other than 404 or an unreadable body. Mapped to 502. */
public class SwapiClientException extends RuntimeException {

    public SwapiClientException(String message) {
        super(message);
    }

    public SwapiClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
