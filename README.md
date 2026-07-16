# CraftGPT

CraftGPT is an AI Minecraft build generator plugin for Paper servers that uses WorldEdit selections as hard build bounds.

If you want a text-to-Minecraft-build workflow that can plan, critique, refine, and then place a validated result inside a selected area, this is what the plugin is built for.

## What CraftGPT Does

CraftGPT takes a WorldEdit cuboid selection and a text prompt, then runs a staged generation pipeline:

1. AI plans the build.
2. AI authors a structured Build Program DSL.
3. Java compiles and validates the real voxel result.
4. CraftGPT renders preview images from the compiled build.
5. AI can inspect those previews and refine the design.
6. Only the final valid result is compressed and applied with WorldEdit.

That split matters. The AI handles design. Java handles geometry, validation, preview rendering, safety limits, compression, and execution.

## Why It Exists

Most Minecraft AI build systems stop at "model returned some blocks." That tends to produce:
- generic shapes
- inconsistent symmetry
- poor bounds handling
- weak detailing
- brittle retry behavior
- no real validation before placement

CraftGPT is designed around a stricter goal: make AI-authored builds feel closer to intentional Minecraft building rather than loose procedural output.

## Pipeline At A Glance

```text
Player prompt + WorldEdit selection
    |
    v
Semantic design plan
    |
    v
Build Program DSL v2
    |
    v
Java parser
    |
    v
Java voxel compiler
    |
    v
Validation + metrics
    |
    v
Headless previews
    |
    v
AI critique
    |
    v
Refined Build Program
    |
    v
Recompile + revalidate
    |
    v
Sparse cuboid compression
    |
    v
WorldEdit placement
```

## What The AI Sees vs What Java Enforces

```text
AI side
- design intent
- composition
- materials
- detailing choices
- critique and repair decisions

Java side
- strict JSON schema
- deterministic geometry expansion
- bounds checks
- block-state validation
- preview rendering
- sparse compression
- safe WorldEdit application

Feedback loop
AI authors -> Java compiles -> Java renders previews and diagnostics -> AI refines
```

## Why The Pipeline Produces Better Builds

Compared to direct block dumping, CraftGPT has a better division of labor:

| Problem | Direct AI output | CraftGPT pipeline |
|---|---|---|
| Reuse | repeated JSON spam | reusable components and repeated instances |
| Symmetry | approximate | exact mirroring and quarter-turn transforms |
| Detail | expensive and inconsistent | coarse forms plus exact point/detail passes |
| Validation | usually weak | strict parsing, compile checks, bounds checks |
| Review | blind | multi-angle preview images |
| Repair | guesswork | critique based on compiled output and metrics |
| Placement safety | risky | invalid intermediate results are never applied |

## Quick Start

### Requirements

- Java 25
- Paper 1.21.11
- WorldEdit 7.3.18

### Basic Setup

1. Install WorldEdit on your Paper server.
2. Install CraftGPT.
3. Configure at least one model preset in `config.yml`.
4. Start the server.
5. Make a cuboid WorldEdit selection.
6. Run a generate command.

## Commands

Generate from a normal prompt:

```text
/craftgpt generate MODEL_PRESET DESCRIPTION
```

Generate from the book in your hand:

```text
/craftgpt generate MODEL_PRESET --book
```

Reload `config.yml` and `messages.yml`:

```text
/craftgpt reload
```

## Example

```text
/craftgpt generate gpt-5-mini detailed spruce windmill with stone base, working-looking gear housing, asymmetrical support beams and a complete back side
```

## Workflow Profiles

CraftGPT supports three generation workflows:

| Profile | Behavior | Best for |
|---|---|---|
| `fast` | generate once, minimal review | quick tests and low-latency iteration |
| `balanced` | plan, compile, preview, critique, refine once | normal use |
| `maximum_quality` | plan, extended previews, multiple refinement passes | best visual quality |

## Preview System

CraftGPT renders the compiled result before placement. Depending on workflow settings, the AI can inspect:

- front orthographic preview
- right-side orthographic preview
- back orthographic preview
- left-side orthographic preview
- top orthographic preview
- isometric preview

These images are generated headlessly from the compiled voxel model. They are not pasted into the world as temporary scaffolding.

## Safety Model

CraftGPT is intentionally conservative about world placement.

It enforces:
- DSL version checks
- strict JSON parsing
- component and operation limits
- occupied block limits
- palette and block-state validation
- final bounds inside the selected cuboid
- sparse segment limits after compression

It also guarantees:
- generation stays local to the WorldEdit selection
- invalid intermediate builds are never applied
- the final placement goes through WorldEdit batching
- valid builds can survive failed later critique passes when configured to do so

## Build Program DSL v2

CraftGPT does not ask the model to emit final sparse WorldEdit cuboids directly. Instead, it uses a structured intermediate format.

Top-level shape:

```json
{
  "v": 2,
  "o": [0, 0, 0],
  "p": [{"i":"1","b":"minecraft:dark_oak_planks"}],
  "c": [{"n":"column","a":[...]}],
  "i": [{"c":"column","at":[10,0,10],"r":0,"mx":false,"mz":false}],
  "a": [...]
}
```

Core rules:

- `v` must be `2`
- `o` must be `[0,0,0]`
- palette IDs use `^[1-9A-Z]$`
- component definitions contain only deterministic operations
- component instances do not recurse
- transform order is mirror X, mirror Z, rotate around local Y origin, then translate
- repeated instances can place rows or grids compactly with `s` / `n` and optional `s2` / `n2`

Supported operation kinds:

- `box`
- `cyl`
- `ell`
- `line`
- `pt`
- `pts`
- `prof`
- `seg`

Why this matters:
- the AI can think in reusable geometry instead of raw cuboid spam
- exact block placements still exist for fine trim and silhouette work
- Java can compile and validate the actual structure before placement

## Local Coordinates

CraftGPT always works in local coordinates inside the active WorldEdit selection:

- minimum corner: `[0,0,0]`
- maximum corner: `[width-1,height-1,depth-1]`

The plugin translates local coordinates to world coordinates exactly once during final application.

## Configuration

Main files:

- `config.yml` for models, limits, workflow settings, prompts, and review behavior
- `messages.yml` for all in-game messages, prefix formatting, and action-bar text

Important config areas:

- workflow profile selection
- request timeout
- WorldEdit batch size and pacing
- occupied-cell and sparse-segment safety caps
- DSL component / instance / operation limits
- visual review and refinement controls
- debug preview artifact output
- model preset endpoint, API key, and payload settings

`/craftgpt reload` reloads both `config.yml` and `messages.yml`.
