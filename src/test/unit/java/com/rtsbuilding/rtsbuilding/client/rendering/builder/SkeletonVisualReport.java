package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningValidator;
import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Generates an interactive local HTML report for merged skeleton stress scenes.
 *
 * <p>The report is intentionally test-only. It replays the actual skeleton
 * cache path, records sampled frames, and writes a self-contained canvas viewer
 * under {@code build/reports/...} so the destructive outline can be inspected
 * from multiple angles without launching Minecraft.</p>
 */
public final class SkeletonVisualReport {

    private SkeletonVisualReport() {
    }

    public static void main(String[] args) throws IOException {
        Path outputDir = Path.of(System.getProperty(
                "rtsbuilding.skeletonReportDir",
                "build/reports/rtsbuilding/skeleton-stress"));
        Files.createDirectories(outputDir);

        List<InteractiveDataset> datasets = buildDatasets();
        Path output = outputDir.resolve("index.html");
        Files.writeString(output, buildInteractiveReport(datasets), StandardCharsets.UTF_8);
        Path summary = outputDir.resolve("summary.json");
        Files.writeString(summary, buildSummaryJson(datasets), StandardCharsets.UTF_8);
        System.out.println("[RTSBuilding] Skeleton visual report: " + output.toAbsolutePath());
        System.out.println("[RTSBuilding] Skeleton report data: " + summary.toAbsolutePath());
    }

    private static List<InteractiveDataset> buildDatasets() {
        List<InteractiveDataset> datasets = new ArrayList<>();
        SkeletonScene cube = cube3x3StepScene();
        datasets.add(InteractiveDataset.analyze(
                "cube_3x3x3_every_step",
                cube,
                "3x3x3 debug cube, one block per tick",
                cube.blocks(),
                1,
                true));

        for (SkeletonScene scene : SkeletonSceneFixtures.reportScenes()) {
            datasets.add(InteractiveDataset.analyze(
                    scene.name() + "_range_destroy",
                    scene,
                    "RtsMiningTargetQueue.collectExplicitDestroyTargets",
                    SkeletonMiningSequenceFixtures.actualRangeDestroyTargets(scene),
                    RtsMiningValidator.ULTIMINE_BLOCKS_PER_TICK,
                    false));
        }

        SkeletonScene worldSpawn = SkeletonSceneFixtures.worldSpawnAreaTenK();
        datasets.add(InteractiveDataset.analyze(
                worldSpawn.name() + "_chain_destroy",
                worldSpawn,
                "RtsUltimineCollector.collect + RtsMiningTargetQueue",
                SkeletonMiningSequenceFixtures.actualUltimineTargets(worldSpawn),
                RtsMiningValidator.ULTIMINE_BLOCKS_PER_TICK,
                false));

        return List.copyOf(datasets);
    }

    private static SkeletonScene cube3x3StepScene() {
        List<BlockPos> blocks = new ArrayList<>(27);
        Map<Long, String> ids = new HashMap<>();
        for (int y = 2; y >= 0; y--) {
            for (int z = 0; z < 3; z++) {
                for (int x = 0; x < 3; x++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    blocks.add(pos);
                    ids.put(pos.asLong(), "minecraft:stone");
                }
            }
        }
        return new SkeletonScene("cube_3x3x3", "debug cube top-down destroy order", blocks, ids);
    }

    private static String buildInteractiveReport(List<InteractiveDataset> datasets) {
        StringBuilder html = new StringBuilder(4_000_000);
        html.append("""
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <title>RTSBuilding Skeleton Interactive Report</title>
                  <style>
                    :root { color-scheme: dark; font-family: Segoe UI, Arial, sans-serif; background: #10151b; color: #e8edf2; }
                    body { margin: 0; padding: 18px; background: #10151b; }
                    h1 { margin: 0 0 10px; font-size: 21px; }
                    .panel { max-width: 1320px; border: 1px solid #2d3947; background: #171d24; border-radius: 6px; padding: 12px; }
                    .bar { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; margin-bottom: 12px; }
                    button, select { border: 1px solid #405167; background: #223042; color: #e8edf2; border-radius: 4px; padding: 6px 9px; font: inherit; }
                    button:hover, select:hover { background: #2b3b50; }
                    input[type="range"] { width: min(560px, 42vw); }
                    .pill { display: inline-flex; align-items: center; min-height: 26px; border: 1px solid #344356; background: #202936; color: #cdd7e3; border-radius: 4px; padding: 2px 8px; font-size: 13px; }
                    .ok { color: #95e9a4; }
                    .bad { color: #ff8e80; }
                    canvas { display: block; width: min(100%, 1320px); height: 760px; border: 1px solid #2d3947; background: radial-gradient(circle at 50% 42%, #18212b, #0b0f14 72%); cursor: grab; }
                    canvas:active { cursor: grabbing; }
                    .note { margin: 10px 0 0; color: #aeb9c6; font-size: 13px; line-height: 1.45; max-width: 1180px; }
                  </style>
                </head>
                <body>
                  <h1>RTSBuilding Skeleton Interactive Report</h1>
                  <div class="panel">
                    <div class="bar">
                      <button id="play">Play</button>
                      <select id="dataset"></select>
                      <label class="pill">Frame <span id="frameLabel">0</span></label>
                      <input id="frame" type="range" min="0" max="0" value="0">
                      <button data-view="iso">ISO</button>
                      <button data-view="top">Top</button>
                      <button data-view="front">Front</button>
                      <button data-view="side">Side</button>
                      <span class="pill">remaining: <span id="remainingLabel">0</span></span>
                      <span class="pill">edges: <span id="edgeLabel">0</span></span>
                      <span class="pill">tick build: <span id="buildLabel">0 ms</span></span>
                      <span class="pill">elapsed from tick 1: <span id="elapsedLabel">0 ms</span></span>
                      <span class="pill">status: <span id="statusLabel" class="ok">MATCH</span></span>
                    </div>
                    <canvas id="view" width="1320" height="760"></canvas>
                    <p class="note">
                      Drag the canvas to rotate. The 3x3x3 dataset includes every block-break step. Larger datasets show ticks 1, 2, 4, 8, 25%, 50%, and 100%.
                      Time starts when the first tick breaks blocks; the initial preview warm-up is not included in elapsed time.
                    </p>
                  </div>
                  <script>
                """);
        html.append("const DATASETS = ");
        appendDatasetsJson(html, datasets);
        html.append(";\n");
        html.append(interactiveScript());
        html.append("""
                  </script>
                </body>
                </html>
                """);
        return html.toString();
    }

    private static void appendDatasetsJson(StringBuilder out, List<InteractiveDataset> datasets) {
        out.append("[\n");
        for (int i = 0; i < datasets.size(); i++) {
            InteractiveDataset data = datasets.get(i);
            out.append("{");
            jsonProp(out, "name", data.name()).append(',');
            jsonProp(out, "source", data.sourceDescription()).append(',');
            jsonProp(out, "batchSize", data.batchSize()).append(',');
            jsonProp(out, "totalTicks", data.totalTicks()).append(',');
            jsonProp(out, "lockedTargets", data.lockedTargets().size()).append(',');
            jsonProp(out, "firstMismatchTick", data.firstMismatchTick()).append(',');
            jsonProp(out, "everyStep", data.everyStep()).append(',');
            out.append("\"bounds\":");
            appendBounds(out, data.bounds());
            out.append(",\"blocks\":");
            appendBlocks(out, data.lockedTargets());
            out.append(",\"frames\":[");
            for (int j = 0; j < data.frames().size(); j++) {
                InteractiveFrame frame = data.frames().get(j);
                out.append('{');
                jsonProp(out, "tick", frame.tick()).append(',');
                jsonProp(out, "destroyed", frame.destroyed()).append(',');
                jsonProp(out, "remaining", frame.remaining()).append(',');
                jsonProp(out, "actualEdges", frame.actualEdges()).append(',');
                jsonProp(out, "expectedEdges", frame.expectedEdges()).append(',');
                jsonProp(out, "missingEdges", frame.missingEdges()).append(',');
                jsonProp(out, "extraEdges", frame.extraEdges()).append(',');
                jsonProp(out, "tickBuildMs", format(frame.actualNanos() / 1_000_000.0D)).append(',');
                jsonProp(out, "elapsedMs", format(frame.elapsedNanos() / 1_000_000.0D)).append(',');
                out.append("\"edges\":");
                appendEdges(out, frame.edges());
                out.append('}');
                if (j + 1 < data.frames().size()) {
                    out.append(',');
                }
            }
            out.append("]}");
            if (i + 1 < datasets.size()) {
                out.append(',');
            }
            out.append('\n');
        }
        out.append(']');
    }

    private static StringBuilder jsonProp(StringBuilder out, String key, String value) {
        return out.append('"').append(jsonEscape(key)).append("\":\"").append(jsonEscape(value)).append('"');
    }

    private static StringBuilder jsonProp(StringBuilder out, String key, int value) {
        return out.append('"').append(jsonEscape(key)).append("\":").append(value);
    }

    private static StringBuilder jsonProp(StringBuilder out, String key, boolean value) {
        return out.append('"').append(jsonEscape(key)).append("\":").append(value);
    }

    private static void appendBlocks(StringBuilder out, List<BlockPos> blocks) {
        out.append('[');
        for (int i = 0; i < blocks.size(); i++) {
            BlockPos pos = blocks.get(i);
            out.append('[').append(pos.getX()).append(',').append(pos.getY()).append(',').append(pos.getZ()).append(']');
            if (i + 1 < blocks.size()) {
                out.append(',');
            }
        }
        out.append(']');
    }

    private static void appendEdges(StringBuilder out, List<SkeletonEdgeKey> edges) {
        out.append('[');
        for (int i = 0; i < edges.size(); i++) {
            SkeletonEdgeKey edge = edges.get(i);
            out.append('[')
                    .append(edge.x1()).append(',').append(edge.y1()).append(',').append(edge.z1()).append(',')
                    .append(edge.x2()).append(',').append(edge.y2()).append(',').append(edge.z2())
                    .append(']');
            if (i + 1 < edges.size()) {
                out.append(',');
            }
        }
        out.append(']');
    }

    private static void appendBounds(StringBuilder out, Bounds bounds) {
        out.append('{')
                .append("\"minX\":").append(bounds.minX()).append(',')
                .append("\"maxX\":").append(bounds.maxX()).append(',')
                .append("\"minY\":").append(bounds.minY()).append(',')
                .append("\"maxY\":").append(bounds.maxY()).append(',')
                .append("\"minZ\":").append(bounds.minZ()).append(',')
                .append("\"maxZ\":").append(bounds.maxZ())
                .append('}');
    }

    private static String buildSummaryJson(List<InteractiveDataset> datasets) {
        StringBuilder json = new StringBuilder(8192);
        json.append("{\n  \"datasets\": [\n");
        for (int i = 0; i < datasets.size(); i++) {
            InteractiveDataset data = datasets.get(i);
            json.append("    {\n")
                    .append("      \"name\": \"").append(jsonEscape(data.name())).append("\",\n")
                    .append("      \"source\": \"").append(jsonEscape(data.sourceDescription())).append("\",\n")
                    .append("      \"lockedTargets\": ").append(data.lockedTargets().size()).append(",\n")
                    .append("      \"batchSize\": ").append(data.batchSize()).append(",\n")
                    .append("      \"totalTicks\": ").append(data.totalTicks()).append(",\n")
                    .append("      \"firstMismatchTick\": ").append(data.firstMismatchTick()).append(",\n")
                    .append("      \"frames\": [\n");
            for (int j = 0; j < data.frames().size(); j++) {
                InteractiveFrame frame = data.frames().get(j);
                json.append("        {\n")
                        .append("          \"tick\": ").append(frame.tick()).append(",\n")
                        .append("          \"destroyed\": ").append(frame.destroyed()).append(",\n")
                        .append("          \"remaining\": ").append(frame.remaining()).append(",\n")
                        .append("          \"actualEdges\": ").append(frame.actualEdges()).append(",\n")
                        .append("          \"expectedEdges\": ").append(frame.expectedEdges()).append(",\n")
                        .append("          \"missingEdges\": ").append(frame.missingEdges()).append(",\n")
                        .append("          \"extraEdges\": ").append(frame.extraEdges()).append(",\n")
                        .append("          \"tickBuildMs\": ").append(format(frame.actualNanos() / 1_000_000.0D)).append(",\n")
                        .append("          \"elapsedMs\": ").append(format(frame.elapsedNanos() / 1_000_000.0D)).append("\n")
                        .append("        }");
                if (j + 1 < data.frames().size()) {
                    json.append(',');
                }
                json.append('\n');
            }
            json.append("      ]\n    }");
            if (i + 1 < datasets.size()) {
                json.append(',');
            }
            json.append('\n');
        }
        json.append("  ]\n}\n");
        return json.toString();
    }

    private static String interactiveScript() {
        return """
                const canvas = document.getElementById("view");
                const ctx = canvas.getContext("2d");
                const datasetSelect = document.getElementById("dataset");
                const frameInput = document.getElementById("frame");
                const frameLabel = document.getElementById("frameLabel");
                const remainingLabel = document.getElementById("remainingLabel");
                const edgeLabel = document.getElementById("edgeLabel");
                const buildLabel = document.getElementById("buildLabel");
                const elapsedLabel = document.getElementById("elapsedLabel");
                const statusLabel = document.getElementById("statusLabel");
                const playButton = document.getElementById("play");

                const sideNames = ["EAST", "WEST", "UP", "DOWN", "SOUTH", "NORTH"];
                const faceDefs = {
                  EAST:  { d: [ 1,  0,  0], corners: [[1,0,0],[1,1,0],[1,1,1],[1,0,1]] },
                  WEST:  { d: [-1,  0,  0], corners: [[0,0,0],[0,0,1],[0,1,1],[0,1,0]] },
                  UP:    { d: [ 0,  1,  0], corners: [[0,1,0],[0,1,1],[1,1,1],[1,1,0]] },
                  DOWN:  { d: [ 0, -1,  0], corners: [[0,0,0],[1,0,0],[1,0,1],[0,0,1]] },
                  SOUTH: { d: [ 0,  0,  1], corners: [[0,0,1],[1,0,1],[1,1,1],[0,1,1]] },
                  NORTH: { d: [ 0,  0, -1], corners: [[0,0,0],[0,1,0],[1,1,0],[1,0,0]] }
                };

                let datasetIndex = 0;
                let frameIndex = 0;
                let rotX = -0.72;
                let rotY = 0.76;
                let dragging = false;
                let lastMouse = null;
                let timer = null;
                let prepared = null;

                for (let i = 0; i < DATASETS.length; i++) {
                  const option = document.createElement("option");
                  option.value = String(i);
                  option.textContent = DATASETS[i].name;
                  datasetSelect.appendChild(option);
                }

                function keyOf(x, y, z) {
                  return x + "," + y + "," + z;
                }

                function blockKey(block) {
                  return keyOf(block[0], block[1], block[2]);
                }

                function neighborKey(block, side) {
                  const d = faceDefs[side].d;
                  return keyOf(block[0] + d[0], block[1] + d[1], block[2] + d[2]);
                }

                function facePolygon(block, side) {
                  return faceDefs[side].corners.map(c => [block[0] + c[0], block[1] + c[1], block[2] + c[2]]);
                }

                function prepareFrame() {
                  const dataset = DATASETS[datasetIndex];
                  const frame = dataset.frames[frameIndex];
                  const remainingKeys = new Set();
                  for (let i = frame.destroyed; i < dataset.blocks.length; i++) {
                    remainingKeys.add(blockKey(dataset.blocks[i]));
                  }
                  const faces = [];
                  for (let i = frame.destroyed; i < dataset.blocks.length; i++) {
                    const block = dataset.blocks[i];
                    for (const side of sideNames) {
                      if (!remainingKeys.has(neighborKey(block, side))) {
                        faces.push(facePolygon(block, side));
                      }
                    }
                  }
                  prepared = { dataset, frame, faces };
                }

                function rotatePoint(p, dataset) {
                  const b = dataset.bounds;
                  let x = p[0] - (b.minX + b.maxX) / 2;
                  let y = p[1] - (b.minY + b.maxY) / 2;
                  let z = p[2] - (b.minZ + b.maxZ) / 2;
                  const cy = Math.cos(rotY), sy = Math.sin(rotY);
                  let x1 = x * cy + z * sy;
                  let z1 = -x * sy + z * cy;
                  const cx = Math.cos(rotX), sx = Math.sin(rotX);
                  let y2 = y * cx - z1 * sx;
                  let z2 = y * sx + z1 * cx;
                  return [x1, y2, z2];
                }

                function projection(dataset) {
                  const b = dataset.bounds;
                  const points = [
                    [b.minX, b.minY, b.minZ], [b.minX, b.minY, b.maxZ],
                    [b.minX, b.maxY, b.minZ], [b.minX, b.maxY, b.maxZ],
                    [b.maxX, b.minY, b.minZ], [b.maxX, b.minY, b.maxZ],
                    [b.maxX, b.maxY, b.minZ], [b.maxX, b.maxY, b.maxZ]
                  ].map(p => rotatePoint(p, dataset));
                  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
                  for (const p of points) {
                    minX = Math.min(minX, p[0]);
                    maxX = Math.max(maxX, p[0]);
                    minY = Math.min(minY, p[1]);
                    maxY = Math.max(maxY, p[1]);
                  }
                  const pad = 58;
                  const scale = Math.max(3, Math.min(
                    (canvas.width - pad * 2) / Math.max(1, maxX - minX),
                    (canvas.height - pad * 2) / Math.max(1, maxY - minY)));
                  return p => {
                    const r = rotatePoint(p, dataset);
                    return {
                      x: canvas.width / 2 + r[0] * scale,
                      y: canvas.height / 2 - r[1] * scale,
                      z: r[2]
                    };
                  };
                }

                function draw() {
                  if (!prepared) prepareFrame();
                  const { dataset, frame, faces } = prepared;
                  const project = projection(dataset);
                  ctx.clearRect(0, 0, canvas.width, canvas.height);

                  const projectedFaces = [];
                  for (const face of faces) {
                    const pts = face.map(project);
                    const depth = pts.reduce((sum, p) => sum + p.z, 0) / pts.length;
                    projectedFaces.push({ pts, depth });
                  }
                  projectedFaces.sort((a, b) => a.depth - b.depth);

                  for (const face of projectedFaces) {
                    ctx.beginPath();
                    ctx.moveTo(face.pts[0].x, face.pts[0].y);
                    for (let i = 1; i < face.pts.length; i++) ctx.lineTo(face.pts[i].x, face.pts[i].y);
                    ctx.closePath();
                    const shade = Math.max(0.28, Math.min(0.72, 0.46 + face.depth * 0.012));
                    ctx.fillStyle = `rgba(${Math.floor(78 + shade * 56)}, ${Math.floor(102 + shade * 56)}, ${Math.floor(126 + shade * 50)}, 0.30)`;
                    ctx.strokeStyle = "rgba(190, 210, 225, 0.10)";
                    ctx.lineWidth = 1;
                    ctx.fill();
                    ctx.stroke();
                  }

                  ctx.lineCap = "round";
                  ctx.lineJoin = "round";
                  ctx.shadowColor = "rgba(105,255,128,0.68)";
                  ctx.shadowBlur = 7;
                  ctx.strokeStyle = "#76ff86";
                  ctx.lineWidth = dataset.lockedTargets <= 512 ? 4 : 2.2;
                  for (const edge of frame.edges) {
                    const p1 = project([edge[0], edge[1], edge[2]]);
                    const p2 = project([edge[3], edge[4], edge[5]]);
                    ctx.beginPath();
                    ctx.moveTo(p1.x, p1.y);
                    ctx.lineTo(p2.x, p2.y);
                    ctx.stroke();
                  }
                  ctx.shadowBlur = 0;

                  ctx.fillStyle = "rgba(232,237,242,0.84)";
                  ctx.font = "15px Segoe UI, Arial";
                  ctx.fillText(dataset.name, 18, 28);
                  ctx.fillText("tick " + frame.tick + " / " + dataset.totalTicks + "  source: " + dataset.source, 18, 52);
                }

                function updateLabels() {
                  const dataset = DATASETS[datasetIndex];
                  const frame = dataset.frames[frameIndex];
                  frameLabel.textContent = dataset.everyStep
                    ? frame.tick + " / " + dataset.totalTicks
                    : frame.tick + " / " + dataset.totalTicks;
                  remainingLabel.textContent = String(frame.remaining);
                  edgeLabel.textContent = String(frame.actualEdges);
                  buildLabel.textContent = frame.tickBuildMs + " ms";
                  elapsedLabel.textContent = frame.elapsedMs + " ms";
                  const matched = frame.missingEdges === 0 && frame.extraEdges === 0;
                  statusLabel.textContent = matched ? "MATCH" : "DIFF";
                  statusLabel.className = matched ? "ok" : "bad";
                }

                function selectDataset(index) {
                  datasetIndex = index;
                  frameIndex = 0;
                  const dataset = DATASETS[datasetIndex];
                  frameInput.max = String(Math.max(0, dataset.frames.length - 1));
                  frameInput.value = "0";
                  prepared = null;
                  updateLabels();
                  draw();
                }

                datasetSelect.addEventListener("change", () => selectDataset(Number(datasetSelect.value)));
                frameInput.addEventListener("input", () => {
                  frameIndex = Number(frameInput.value);
                  prepared = null;
                  updateLabels();
                  draw();
                });

                playButton.addEventListener("click", () => {
                  if (timer) {
                    clearInterval(timer);
                    timer = null;
                    playButton.textContent = "Play";
                    return;
                  }
                  playButton.textContent = "Pause";
                  timer = setInterval(() => {
                    const dataset = DATASETS[datasetIndex];
                    frameIndex = (frameIndex + 1) % dataset.frames.length;
                    frameInput.value = String(frameIndex);
                    prepared = null;
                    updateLabels();
                    draw();
                  }, DATASETS[datasetIndex].everyStep ? 480 : 780);
                });

                document.querySelectorAll("button[data-view]").forEach(button => {
                  button.addEventListener("click", () => {
                    switch (button.dataset.view) {
                      case "top": rotX = -Math.PI / 2; rotY = 0; break;
                      case "front": rotX = 0; rotY = 0; break;
                      case "side": rotX = 0; rotY = Math.PI / 2; break;
                      default: rotX = -0.72; rotY = 0.76; break;
                    }
                    draw();
                  });
                });

                canvas.addEventListener("mousedown", event => {
                  dragging = true;
                  lastMouse = { x: event.clientX, y: event.clientY };
                });
                window.addEventListener("mouseup", () => {
                  dragging = false;
                  lastMouse = null;
                });
                window.addEventListener("mousemove", event => {
                  if (!dragging || !lastMouse) return;
                  const dx = event.clientX - lastMouse.x;
                  const dy = event.clientY - lastMouse.y;
                  lastMouse = { x: event.clientX, y: event.clientY };
                  rotY += dx * 0.008;
                  rotX += dy * 0.008;
                  rotX = Math.max(-Math.PI * 0.95, Math.min(Math.PI * 0.95, rotX));
                  draw();
                });

                selectDataset(0);
                """;
    }

    private record InteractiveDataset(
            String name,
            String sourceDescription,
            int batchSize,
            int totalTicks,
            int firstMismatchTick,
            boolean everyStep,
            List<BlockPos> lockedTargets,
            List<InteractiveFrame> frames,
            Bounds bounds) {

        private static InteractiveDataset analyze(String name, SkeletonScene scene, String sourceDescription,
                List<BlockPos> lockedTargets, int batchSize, boolean everyStep) {
            List<List<BlockPos>> batches = tickBatchesForReport(lockedTargets, batchSize);
            Set<Integer> selectedTicks = selectedTicks(batches.size(), everyStep);

            SkeletonTickSequenceOracle.Model expected = SkeletonTickSequenceOracle.start(lockedTargets);
            SkeletonEdgeInspector.resetCachedSkeleton();
            ShapeDataRecords.GhostPreview preview = SkeletonEdgeInspector.preview(lockedTargets);

            List<InteractiveFrame> frames = new ArrayList<>();
            Set<SkeletonEdgeKey> actual = SkeletonEdgeInspector.cachedVisibleEdges(preview);
            Set<SkeletonEdgeKey> expectedEdges = expected.visibleEdges();
            int firstMismatchTick = -1;
            long elapsedNanos = 0L;
            if (selectedTicks.contains(0)) {
                frames.add(InteractiveFrame.of(0, expected.destroyed(), expected.remainingCount(),
                        0L, 0L, actual, expectedEdges));
            }

            for (int i = 0; i < batches.size(); i++) {
                int tick = i + 1;
                List<BlockPos> batch = batches.get(i);
                for (BlockPos pos : batch) {
                    MergedSkeletonRenderer.markDestroyed(pos);
                }

                long tickActualStart = System.nanoTime();
                actual = SkeletonEdgeInspector.cachedVisibleEdges(preview);
                long actualNanos = System.nanoTime() - tickActualStart;
                elapsedNanos += actualNanos;

                expected.applyTick(batch);
                expectedEdges = expected.visibleEdges();

                Set<SkeletonEdgeKey> missing = new HashSet<>(expectedEdges);
                missing.removeAll(actual);
                Set<SkeletonEdgeKey> extra = new HashSet<>(actual);
                extra.removeAll(expectedEdges);
                if (firstMismatchTick < 0 && (!missing.isEmpty() || !extra.isEmpty())) {
                    firstMismatchTick = tick;
                }
                if (selectedTicks.contains(tick)) {
                    frames.add(InteractiveFrame.of(tick, expected.destroyed(), expected.remainingCount(),
                            actualNanos, elapsedNanos, actual, expectedEdges));
                }
            }

            return new InteractiveDataset(
                    name,
                    sourceDescription,
                    batchSize,
                    batches.size(),
                    firstMismatchTick,
                    everyStep,
                    List.copyOf(lockedTargets),
                    List.copyOf(frames),
                    Bounds.of(lockedTargets));
        }

        private static List<List<BlockPos>> tickBatchesForReport(List<BlockPos> lockedTargets, int batchSize) {
            if (batchSize == RtsMiningValidator.ULTIMINE_BLOCKS_PER_TICK) {
                return SkeletonMiningSequenceFixtures.actualTickBatches(lockedTargets);
            }
            if (batchSize <= 0) {
                throw new IllegalArgumentException("batchSize must be positive: " + batchSize);
            }
            List<List<BlockPos>> batches = new ArrayList<>();
            for (int start = 0; start < lockedTargets.size(); start += batchSize) {
                int end = Math.min(start + batchSize, lockedTargets.size());
                batches.add(List.copyOf(lockedTargets.subList(start, end)));
            }
            return batches;
        }

        private static Set<Integer> selectedTicks(int totalTicks, boolean everyStep) {
            Set<Integer> ticks = new TreeSet<>();
            if (everyStep) {
                for (int tick = 0; tick <= totalTicks; tick++) {
                    ticks.add(tick);
                }
                return ticks;
            }
            if (totalTicks <= 0) {
                ticks.add(0);
                return ticks;
            }
            for (int tick : new int[] {
                    1,
                    Math.min(2, totalTicks),
                    Math.min(4, totalTicks),
                    Math.min(8, totalTicks),
                    Math.max(1, totalTicks / 4),
                    Math.max(1, totalTicks / 2),
                    totalTicks
            }) {
                if (tick >= 1 && tick <= totalTicks) {
                    ticks.add(tick);
                }
            }
            return ticks;
        }
    }

    private record InteractiveFrame(
            int tick,
            int destroyed,
            int remaining,
            int actualEdges,
            int expectedEdges,
            int missingEdges,
            int extraEdges,
            long actualNanos,
            long elapsedNanos,
            List<SkeletonEdgeKey> edges) {

        private static InteractiveFrame of(int tick, int destroyed, int remaining,
                long actualNanos, long elapsedNanos, Set<SkeletonEdgeKey> actual, Set<SkeletonEdgeKey> expected) {
            Set<SkeletonEdgeKey> missing = new HashSet<>(expected);
            missing.removeAll(actual);
            Set<SkeletonEdgeKey> extra = new HashSet<>(actual);
            extra.removeAll(expected);
            List<SkeletonEdgeKey> edges = actual.stream()
                    .sorted(EDGE_COMPARATOR)
                    .toList();
            return new InteractiveFrame(
                    tick,
                    destroyed,
                    remaining,
                    actual.size(),
                    expected.size(),
                    missing.size(),
                    extra.size(),
                    actualNanos,
                    elapsedNanos,
                    edges);
        }
    }

    private static final Comparator<SkeletonEdgeKey> EDGE_COMPARATOR = Comparator
            .comparingInt(SkeletonEdgeKey::x1)
            .thenComparingInt(SkeletonEdgeKey::y1)
            .thenComparingInt(SkeletonEdgeKey::z1)
            .thenComparingInt(SkeletonEdgeKey::x2)
            .thenComparingInt(SkeletonEdgeKey::y2)
            .thenComparingInt(SkeletonEdgeKey::z2);

    private static String jsonEscape(String text) {
        return text == null ? "" : text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private record Bounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        private static Bounds of(List<BlockPos> blocks) {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (BlockPos pos : blocks) {
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX() + 1);
                maxY = Math.max(maxY, pos.getY() + 1);
                maxZ = Math.max(maxZ, pos.getZ() + 1);
            }
            if (blocks.isEmpty()) {
                return new Bounds(0, 1, 0, 1, 0, 1);
            }
            return new Bounds(minX, maxX, minY, maxY, minZ, maxZ);
        }
    }
}
