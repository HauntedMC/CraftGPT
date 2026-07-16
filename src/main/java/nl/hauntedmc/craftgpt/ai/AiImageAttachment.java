package nl.hauntedmc.craftgpt.ai;

import java.nio.file.Path;

public record AiImageAttachment(String label, String mimeType, Path file) {
}
