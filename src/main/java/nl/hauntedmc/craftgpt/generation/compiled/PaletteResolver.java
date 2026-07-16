package nl.hauntedmc.craftgpt.generation.compiled;

public interface PaletteResolver {
    ResolvedBlock resolve(String blockState) throws PaletteValidationException;
}
