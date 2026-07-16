package nl.hauntedmc.craftgpt.generation;

public interface BuildGenerationPipeline {
    PipelineResult generate(PipelineRequest request);
}
