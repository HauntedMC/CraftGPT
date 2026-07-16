package nl.hauntedmc.craftgpt.generation.compiled.parser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import nl.hauntedmc.craftgpt.generation.Axis;
import nl.hauntedmc.craftgpt.generation.GenerationLimits;
import nl.hauntedmc.craftgpt.generation.IntVec3;
import nl.hauntedmc.craftgpt.generation.QuarterTurn;
import nl.hauntedmc.craftgpt.generation.compiled.BoxOperation;
import nl.hauntedmc.craftgpt.generation.compiled.ComponentDefinition;
import nl.hauntedmc.craftgpt.generation.compiled.ComponentInstance;
import nl.hauntedmc.craftgpt.generation.compiled.CylinderOperation;
import nl.hauntedmc.craftgpt.generation.compiled.EllipsoidOperation;
import nl.hauntedmc.craftgpt.generation.compiled.LineOperation;
import nl.hauntedmc.craftgpt.generation.compiled.OperationMode;
import nl.hauntedmc.craftgpt.generation.compiled.PaletteEntry;
import nl.hauntedmc.craftgpt.generation.compiled.PointOperation;
import nl.hauntedmc.craftgpt.generation.compiled.PointSetOperation;
import nl.hauntedmc.craftgpt.generation.compiled.PrimitiveKind;
import nl.hauntedmc.craftgpt.generation.compiled.PrimitiveOperation;
import nl.hauntedmc.craftgpt.generation.compiled.ProfileOperation;
import nl.hauntedmc.craftgpt.generation.compiled.RawCuboidOperation;
import nl.hauntedmc.craftgpt.generation.compiled.BuildProgram;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class BuildProgramParser {
    private static final Pattern PALETTE_ID_PATTERN = Pattern.compile("^[1-9A-Z]$");
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,32}$");

    public BuildProgram parse(String rawJson, GenerationLimits limits) throws BuildProgramParseException {
        try {
            JsonElement element = JsonParser.parseString(stripCodeFences(rawJson));
            if (!element.isJsonObject()) {
                throw new BuildProgramParseException("Build program root must be an object.");
            }
            return parseRoot(element.getAsJsonObject(), limits);
        } catch (BuildProgramParseException e) {
            throw e;
        } catch (JsonParseException | IllegalStateException e) {
            throw new BuildProgramParseException("Malformed Build Program JSON: " + e.getMessage());
        }
    }

    private BuildProgram parseRoot(JsonObject root, GenerationLimits limits) throws BuildProgramParseException {
        requireOnlyKeys(root, "v", "o", "p", "c", "i", "a");
        int version = getRequiredInt(root, "v");
        if (version != 2) {
            throw new BuildProgramParseException("Unsupported DSL version: " + version + ".");
        }
        IntVec3 origin = parseVec3(getRequiredArray(root, "o"), "o");
        if (!IntVec3.ZERO.equals(origin)) {
            throw new BuildProgramParseException("Compiled mode requires o to be [0,0,0].");
        }

        List<PaletteEntry> paletteEntries = parsePalette(getRequiredArray(root, "p"));
        if (paletteEntries.size() > 35) {
            throw new BuildProgramParseException("Palette entry count exceeded the supported limit.");
        }

        List<ComponentDefinition> components = parseComponents(getOptionalArray(root, "c"), limits);
        if (components.size() > limits.maxDslComponents()) {
            throw new BuildProgramParseException("Component count exceeded the configured limit.");
        }

        List<ComponentInstance> instances = parseInstances(getOptionalArray(root, "i"), limits);
        int expandedInstanceCount = instances.stream().mapToInt(ComponentInstance::expandedPlacementCount).sum();
        if (expandedInstanceCount > limits.maxDslInstances()) {
            throw new BuildProgramParseException("Instance count exceeded the configured limit.");
        }

        List<PrimitiveOperation> operations = parseOperations(getOptionalArray(root, "a"));
        int totalDslOperations = operations.size() + components.stream().mapToInt(component -> component.operations().size()).sum();
        if (totalDslOperations > limits.maxDslOperations()) {
            throw new BuildProgramParseException("Operation count exceeded the configured limit.");
        }

        return new BuildProgram(version, origin, paletteEntries, components, instances, operations);
    }

    private List<PaletteEntry> parsePalette(JsonArray array) throws BuildProgramParseException {
        List<PaletteEntry> paletteEntries = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (JsonElement element : array) {
            JsonObject object = asObject(element, "palette entry");
            requireOnlyKeys(object, "i", "b");
            String id = getRequiredString(object, "i");
            if (!PALETTE_ID_PATTERN.matcher(id).matches()) {
                throw new BuildProgramParseException("Palette IDs must match ^[1-9A-Z]$.");
            }
            if (!seenIds.add(id)) {
                throw new BuildProgramParseException("Duplicate palette ID: " + id + ".");
            }
            String blockState = getRequiredString(object, "b");
            paletteEntries.add(new PaletteEntry(id, blockState));
        }
        if (paletteEntries.isEmpty()) {
            throw new BuildProgramParseException("At least one palette entry is required.");
        }
        return paletteEntries;
    }

    private List<ComponentDefinition> parseComponents(JsonArray array, GenerationLimits limits) throws BuildProgramParseException {
        List<ComponentDefinition> components = new ArrayList<>();
        if (array == null) {
            return components;
        }
        Set<String> names = new HashSet<>();
        for (JsonElement element : array) {
            JsonObject object = asObject(element, "component definition");
            requireOnlyKeys(object, "n", "a");
            String name = getRequiredString(object, "n");
            if (!NAME_PATTERN.matcher(name).matches()) {
                throw new BuildProgramParseException("Component names must match ^[a-zA-Z0-9_-]{1,32}$.");
            }
            if (!names.add(name)) {
                throw new BuildProgramParseException("Duplicate component name: " + name + ".");
            }
            List<PrimitiveOperation> operations = parseOperations(getRequiredArray(object, "a"));
            if (operations.size() > limits.maxDslOperationsPerComponent()) {
                throw new BuildProgramParseException("Component '" + name + "' exceeded max_dsl_operations_per_component.");
            }
            components.add(new ComponentDefinition(name, operations));
        }
        return components;
    }

    private List<ComponentInstance> parseInstances(JsonArray array, GenerationLimits limits) throws BuildProgramParseException {
        List<ComponentInstance> instances = new ArrayList<>();
        if (array == null) {
            return instances;
        }
        int expandedCount = 0;
        for (JsonElement element : array) {
            JsonObject object = asObject(element, "component instance");
            requireOnlyKeys(object, "c", "at", "r", "mx", "mz", "s", "n", "s2", "n2");
            String componentName = getRequiredString(object, "c");
            if (!NAME_PATTERN.matcher(componentName).matches()) {
                throw new BuildProgramParseException("Invalid component reference name: " + componentName + ".");
            }
            IntVec3 at = parseVec3(getRequiredArray(object, "at"), "at");
            QuarterTurn rotation = QuarterTurn.fromDegrees(getRequiredInt(object, "r"));
            if (rotation == null) {
                throw new BuildProgramParseException("Invalid rotation. Only 0, 90, 180 and 270 are allowed.");
            }
            boolean mirrorX = getRequiredBoolean(object, "mx");
            boolean mirrorZ = getRequiredBoolean(object, "mz");
            RepeatSpec primary = parseRepeatSpec(object, "s", "n", "instance repetition");
            RepeatSpec secondary = parseRepeatSpec(object, "s2", "n2", "secondary instance repetition");
            if (secondary.count() > 1 && primary.count() <= 1) {
                throw new BuildProgramParseException("Secondary instance repetition requires a primary repetition.");
            }

            ComponentInstance instance = new ComponentInstance(
                    componentName,
                    at,
                    rotation,
                    mirrorX,
                    mirrorZ,
                    primary.step(),
                    primary.count(),
                    secondary.step(),
                    secondary.count()
            );
            try {
                expandedCount = Math.addExact(expandedCount, instance.expandedPlacementCount());
            } catch (ArithmeticException e) {
                throw new BuildProgramParseException("Instance count exceeded the configured limit.");
            }
            if (expandedCount > limits.maxDslInstances()) {
                throw new BuildProgramParseException("Instance count exceeded the configured limit.");
            }
            instances.add(instance);
        }
        return instances;
    }

    private List<PrimitiveOperation> parseOperations(JsonArray array) throws BuildProgramParseException {
        List<PrimitiveOperation> operations = new ArrayList<>();
        if (array == null) {
            return operations;
        }
        for (JsonElement element : array) {
            operations.add(parseOperation(asObject(element, "operation")));
        }
        return operations;
    }

    private PrimitiveOperation parseOperation(JsonObject object) throws BuildProgramParseException {
        String kindValue = getRequiredString(object, "k");
        PrimitiveKind kind = PrimitiveKind.fromDslValue(kindValue);
        if (kind == null) {
            throw new BuildProgramParseException("Unknown primitive kind: " + kindValue + ".");
        }

        OperationMode mode = OperationMode.fromDslValue(getRequiredString(object, "m"));
        if (mode == null) {
            throw new BuildProgramParseException("Unknown operation mode.");
        }

        return switch (kind) {
            case BOX -> parseBox(object, mode);
            case CYLINDER -> parseCylinder(object, mode);
            case ELLIPSOID -> parseEllipsoid(object, mode);
            case LINE -> parseLine(object, mode);
            case POINT -> parsePoint(object, mode);
            case POINT_SET -> parsePointSet(object, mode);
            case PROFILE -> parseProfile(object, mode);
            case RAW_CUBOID -> parseRawCuboid(object, mode);
        };
    }

    private PrimitiveOperation parseBox(JsonObject object, OperationMode mode) throws BuildProgramParseException {
        requireOnlyKeys(object, "k", "m", "i", "f", "t", "ho", "wt");
        String paletteId = mode == OperationMode.ADD ? parseRequiredPaletteId(object) : null;
        boolean hollow = getOptionalBoolean(object, "ho", false);
        int wallThickness = getOptionalPositiveInt(object, "wt", 1);
        return new BoxOperation(
                mode,
                paletteId,
                parseVec3(getRequiredArray(object, "f"), "f"),
                parseVec3(getRequiredArray(object, "t"), "t"),
                hollow,
                wallThickness
        );
    }

    private PrimitiveOperation parseCylinder(JsonObject object, OperationMode mode) throws BuildProgramParseException {
        requireOnlyKeys(object, "k", "m", "i", "c", "r", "h", "ax", "ho", "wt");
        if (mode != OperationMode.ADD) {
            throw new BuildProgramParseException("Cylinder operations only support add mode.");
        }
        Axis axis = Axis.fromDslValue(getRequiredString(object, "ax"));
        if (axis == null) {
            throw new BuildProgramParseException("Cylinder axis must be x, y or z.");
        }
        return new CylinderOperation(
                mode,
                parseRequiredPaletteId(object),
                parseVec3(getRequiredArray(object, "c"), "c"),
                getRequiredPositiveInt(object, "r"),
                getRequiredPositiveInt(object, "h"),
                axis,
                getOptionalBoolean(object, "ho", false),
                getOptionalPositiveInt(object, "wt", 1)
        );
    }

    private PrimitiveOperation parseEllipsoid(JsonObject object, OperationMode mode) throws BuildProgramParseException {
        requireOnlyKeys(object, "k", "m", "i", "c", "rx", "ry", "rz", "ho", "wt");
        if (mode != OperationMode.ADD) {
            throw new BuildProgramParseException("Ellipsoid operations only support add mode.");
        }
        return new EllipsoidOperation(
                mode,
                parseRequiredPaletteId(object),
                parseVec3(getRequiredArray(object, "c"), "c"),
                getRequiredPositiveInt(object, "rx"),
                getRequiredPositiveInt(object, "ry"),
                getRequiredPositiveInt(object, "rz"),
                getOptionalBoolean(object, "ho", false),
                getOptionalPositiveInt(object, "wt", 1)
        );
    }

    private PrimitiveOperation parseLine(JsonObject object, OperationMode mode) throws BuildProgramParseException {
        requireOnlyKeys(object, "k", "m", "i", "f", "t", "w");
        if (mode != OperationMode.ADD) {
            throw new BuildProgramParseException("Line operations only support add mode.");
        }
        return new LineOperation(
                mode,
                parseRequiredPaletteId(object),
                parseVec3(getRequiredArray(object, "f"), "f"),
                parseVec3(getRequiredArray(object, "t"), "t"),
                getRequiredNonNegativeInt(object, "w")
        );
    }

    private PrimitiveOperation parsePoint(JsonObject object, OperationMode mode) throws BuildProgramParseException {
        if (mode == OperationMode.ADD) {
            requireOnlyKeys(object, "k", "m", "i", "at");
            return new PointOperation(mode, parseRequiredPaletteId(object), parseVec3(getRequiredArray(object, "at"), "at"));
        }
        requireOnlyKeys(object, "k", "m", "at");
        return new PointOperation(mode, null, parseVec3(getRequiredArray(object, "at"), "at"));
    }

    private PrimitiveOperation parsePointSet(JsonObject object, OperationMode mode) throws BuildProgramParseException {
        if (mode == OperationMode.ADD) {
            requireOnlyKeys(object, "k", "m", "i", "ps");
            return new PointSetOperation(mode, parseRequiredPaletteId(object), parseVec3List(getRequiredArray(object, "ps"), "ps"));
        }
        requireOnlyKeys(object, "k", "m", "ps");
        return new PointSetOperation(mode, null, parseVec3List(getRequiredArray(object, "ps"), "ps"));
    }

    private PrimitiveOperation parseProfile(JsonObject object, OperationMode mode) throws BuildProgramParseException {
        requireOnlyKeys(object, "k", "m", "i", "f", "t", "ax", "d", "wt");
        if (mode != OperationMode.ADD) {
            throw new BuildProgramParseException("Profile operations only support add mode.");
        }
        Axis axis = Axis.fromDslValue(getRequiredString(object, "ax"));
        if (axis == null) {
            throw new BuildProgramParseException("Profile axis must be x, y or z.");
        }
        IntVec3 from = parseVec3(getRequiredArray(object, "f"), "f");
        IntVec3 to = parseVec3(getRequiredArray(object, "t"), "t");
        validateProfilePlane(axis, from, to);
        return new ProfileOperation(
                mode,
                parseRequiredPaletteId(object),
                from,
                to,
                axis,
                getRequiredPositiveInt(object, "d"),
                getOptionalPositiveInt(object, "wt", 1)
        );
    }

    private PrimitiveOperation parseRawCuboid(JsonObject object, OperationMode mode) throws BuildProgramParseException {
        requireOnlyKeys(object, "k", "m", "i", "f", "t");
        if (mode != OperationMode.ADD) {
            throw new BuildProgramParseException("Raw cuboid operations only support add mode.");
        }
        return new RawCuboidOperation(
                mode,
                parseRequiredPaletteId(object),
                parseVec3(getRequiredArray(object, "f"), "f"),
                parseVec3(getRequiredArray(object, "t"), "t")
        );
    }

    private String parseRequiredPaletteId(JsonObject object) throws BuildProgramParseException {
        String paletteId = getRequiredString(object, "i");
        if (!PALETTE_ID_PATTERN.matcher(paletteId).matches()) {
            throw new BuildProgramParseException("Palette IDs must match ^[1-9A-Z]$.");
        }
        return paletteId;
    }

    private IntVec3 parseVec3(JsonArray array, String field) throws BuildProgramParseException {
        if (array.size() != 3) {
            throw new BuildProgramParseException("Field '" + field + "' must contain exactly three integers.");
        }
        return new IntVec3(
                asInt(array.get(0), field + "[0]"),
                asInt(array.get(1), field + "[1]"),
                asInt(array.get(2), field + "[2]")
        );
    }

    private List<IntVec3> parseVec3List(JsonArray array, String field) throws BuildProgramParseException {
        if (array.isEmpty()) {
            throw new BuildProgramParseException("Field '" + field + "' must contain at least one coordinate.");
        }
        List<IntVec3> points = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            points.add(parseVec3(asArray(array.get(index), field + "[" + index + "]"), field + "[" + index + "]"));
        }
        return points;
    }

    private void validateProfilePlane(Axis axis, IntVec3 from, IntVec3 to) throws BuildProgramParseException {
        boolean valid = switch (axis) {
            case X -> from.x() == to.x();
            case Y -> from.y() == to.y();
            case Z -> from.z() == to.z();
        };
        if (!valid) {
            String requirement = switch (axis) {
                case X -> "ax:'x' requires f.x == t.x";
                case Y -> "ax:'y' requires f.y == t.y";
                case Z -> "ax:'z' requires f.z == t.z";
            };
            throw new BuildProgramParseException(
                    "Profile rectangles must stay on a plane perpendicular to their axis; "
                            + requirement
                            + ". If that cannot be satisfied exactly, use box, line, cyl, ell, or seg instead of prof."
            );
        }
    }

    private RepeatSpec parseRepeatSpec(JsonObject object, String stepKey, String countKey, String context) throws BuildProgramParseException {
        boolean hasStep = object.has(stepKey);
        boolean hasCount = object.has(countKey);
        if (hasStep != hasCount) {
            throw new BuildProgramParseException("Fields '" + stepKey + "' and '" + countKey + "' must be provided together for " + context + ".");
        }
        if (!hasStep) {
            return new RepeatSpec(IntVec3.ZERO, 1);
        }
        IntVec3 step = parseVec3(getRequiredArray(object, stepKey), stepKey);
        int count = getRequiredPositiveInt(object, countKey);
        if (count > 1 && IntVec3.ZERO.equals(step)) {
            throw new BuildProgramParseException("Field '" + stepKey + "' must be non-zero when '" + countKey + "' is greater than 1.");
        }
        return new RepeatSpec(step, count);
    }

    private JsonObject asObject(JsonElement element, String description) throws BuildProgramParseException {
        if (element == null || !element.isJsonObject()) {
            throw new BuildProgramParseException("Expected " + description + " to be an object.");
        }
        return element.getAsJsonObject();
    }

    private JsonArray asArray(JsonElement element, String description) throws BuildProgramParseException {
        if (element == null || !element.isJsonArray()) {
            throw new BuildProgramParseException("Expected " + description + " to be an array.");
        }
        return element.getAsJsonArray();
    }

    private JsonArray getRequiredArray(JsonObject object, String key) throws BuildProgramParseException {
        if (!object.has(key) || !object.get(key).isJsonArray()) {
            throw new BuildProgramParseException("Missing required array field '" + key + "'.");
        }
        return object.getAsJsonArray(key);
    }

    private JsonArray getOptionalArray(JsonObject object, String key) throws BuildProgramParseException {
        if (!object.has(key)) {
            return null;
        }
        if (!object.get(key).isJsonArray()) {
            throw new BuildProgramParseException("Field '" + key + "' must be an array.");
        }
        return object.getAsJsonArray(key);
    }

    private String getRequiredString(JsonObject object, String key) throws BuildProgramParseException {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            throw new BuildProgramParseException("Missing required string field '" + key + "'.");
        }
        return object.get(key).getAsString();
    }

    private int getRequiredInt(JsonObject object, String key) throws BuildProgramParseException {
        if (!object.has(key)) {
            throw new BuildProgramParseException("Missing required integer field '" + key + "'.");
        }
        return asInt(object.get(key), key);
    }

    private int getOptionalInt(JsonObject object, String key, int defaultValue) throws BuildProgramParseException {
        return object.has(key) ? asInt(object.get(key), key) : defaultValue;
    }

    private int getRequiredPositiveInt(JsonObject object, String key) throws BuildProgramParseException {
        int value = getRequiredInt(object, key);
        if (value <= 0) {
            throw new BuildProgramParseException("Field '" + key + "' must be a positive integer.");
        }
        return value;
    }

    private int getOptionalPositiveInt(JsonObject object, String key, int defaultValue) throws BuildProgramParseException {
        int value = getOptionalInt(object, key, defaultValue);
        if (value <= 0) {
            throw new BuildProgramParseException("Field '" + key + "' must be a positive integer.");
        }
        return value;
    }

    private int getRequiredNonNegativeInt(JsonObject object, String key) throws BuildProgramParseException {
        int value = getRequiredInt(object, key);
        if (value < 0) {
            throw new BuildProgramParseException("Field '" + key + "' must be zero or greater.");
        }
        return value;
    }

    private boolean getRequiredBoolean(JsonObject object, String key) throws BuildProgramParseException {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            throw new BuildProgramParseException("Missing required boolean field '" + key + "'.");
        }
        return object.get(key).getAsBoolean();
    }

    private boolean getOptionalBoolean(JsonObject object, String key, boolean defaultValue) throws BuildProgramParseException {
        if (!object.has(key)) {
            return defaultValue;
        }
        if (!object.get(key).isJsonPrimitive()) {
            throw new BuildProgramParseException("Field '" + key + "' must be a boolean.");
        }
        return object.get(key).getAsBoolean();
    }

    private int asInt(JsonElement element, String field) throws BuildProgramParseException {
        try {
            return element.getAsInt();
        } catch (NumberFormatException | UnsupportedOperationException | IllegalStateException ignored) {
            throw new BuildProgramParseException("Field '" + field + "' must be an integer.");
        }
    }

    private void requireOnlyKeys(JsonObject object, String... allowedKeys) throws BuildProgramParseException {
        Set<String> allowed = Set.of(allowedKeys);
        for (String key : object.keySet()) {
            if (!allowed.contains(key)) {
                throw new BuildProgramParseException("Field '" + key + "' is not allowed in this object.");
            }
        }
    }

    private String stripCodeFences(String rawJson) {
        String trimmed = rawJson == null ? "" : rawJson.trim();
        if (!trimmed.startsWith("```") || !trimmed.endsWith("```")) {
            return trimmed;
        }
        String withoutStart = trimmed.substring(trimmed.indexOf('\n') + 1);
        int closing = withoutStart.lastIndexOf("```");
        return closing < 0 ? withoutStart.trim() : withoutStart.substring(0, closing).trim();
    }

    private record RepeatSpec(IntVec3 step, int count) {
    }
}
