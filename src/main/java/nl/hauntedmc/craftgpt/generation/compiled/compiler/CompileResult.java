package nl.hauntedmc.craftgpt.generation.compiled.compiler;

import nl.hauntedmc.craftgpt.generation.compiled.BuildFailure;

import java.util.List;

public record CompileResult(CompiledBuild compiledBuild, List<BuildFailure> failures) {
    public CompileResult {
        failures = List.copyOf(failures);
    }

    public boolean isSuccess() {
        return compiledBuild != null && failures.isEmpty();
    }
}
