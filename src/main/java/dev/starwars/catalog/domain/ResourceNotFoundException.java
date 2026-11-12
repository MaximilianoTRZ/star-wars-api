package dev.starwars.catalog.domain;

/** The entity requested by ID does not exist in SWAPI. Mapped to 404. */
public class ResourceNotFoundException extends RuntimeException {

    private final CatalogResource resource;
    private final String id;

    public ResourceNotFoundException(CatalogResource resource, String id) {
        super("No " + resource.label() + " found with id " + id);
        this.resource = resource;
        this.id = id;
    }

    public CatalogResource resource() {
        return resource;
    }

    public String id() {
        return id;
    }
}
