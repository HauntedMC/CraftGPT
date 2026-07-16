package nl.hauntedmc.craftgpt.generation.worldedit;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.factory.parser.DefaultBlockParser;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.world.block.BaseBlock;
import nl.hauntedmc.craftgpt.generation.compiled.PaletteResolver;
import nl.hauntedmc.craftgpt.generation.compiled.PaletteValidationException;
import nl.hauntedmc.craftgpt.generation.compiled.ResolvedBlock;

public final class WorldEditPaletteResolver implements PaletteResolver {
    private final DefaultBlockParser blockParser = new DefaultBlockParser(WorldEdit.getInstance());

    @Override
    public ResolvedBlock resolve(String blockState) throws PaletteValidationException {
        String trimmed = blockState == null ? "" : blockState.trim();
        if (trimmed.isEmpty()) {
            throw new PaletteValidationException("The block state is blank.");
        }

        ParserContext context = new ParserContext();
        context.setRestricted(false);
        context.setTryLegacy(false);
        try {
            BaseBlock parsed = blockParser.parseFromInput(trimmed, context);
            return new ResolvedBlock(
                    parsed.getAsString(),
                    parsed.getBlockType().id(),
                    parsed.toImmutableState()
            );
        } catch (InputParseException e) {
            throw new PaletteValidationException(e.getMessage());
        }
    }
}
