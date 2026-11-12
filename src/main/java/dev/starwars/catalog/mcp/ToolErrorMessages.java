package dev.starwars.catalog.mcp;

/**
 * Public message for a failure inside an MCP tool, the counterpart of the {@code detail} of a REST
 * {@code ProblemDetail}. Declared here and implemented next to the REST and GraphQL error handlers, so the tools
 * never see SWAPI exceptions and an internal message never reaches the client.
 */
public interface ToolErrorMessages {

    String describe(RuntimeException ex);
}
