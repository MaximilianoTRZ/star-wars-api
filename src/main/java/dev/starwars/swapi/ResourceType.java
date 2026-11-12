package dev.starwars.swapi;

/**
 * SWAPI resources exposed by the API, with what each one needs to know about SWAPI.
 *
 * <p>SWAPI is not uniform: {@code films} is not paginated and is searched by {@code title},
 * while the rest are paginated and searched by {@code name}.
 */
public enum ResourceType {
    PEOPLE("people", "name", true),
    FILMS("films", "title", false),
    STARSHIPS("starships", "name", true),
    VEHICLES("vehicles", "name", true);

    private final String path;
    private final String nameAttribute;
    private final boolean paginatedUpstream;

    ResourceType(String path, String nameAttribute, boolean paginatedUpstream) {
        this.path = path;
        this.nameAttribute = nameAttribute;
        this.paginatedUpstream = paginatedUpstream;
    }

    /** SWAPI URL segment, e.g. {@code people}. */
    public String path() {
        return path;
    }

    /** Attribute that names the entity and SWAPI search parameter: {@code name} or {@code title}. */
    public String nameAttribute() {
        return nameAttribute;
    }

    /** {@code true} if SWAPI paginates this resource with {@code page} and {@code limit}. */
    public boolean paginatedUpstream() {
        return paginatedUpstream;
    }
}
