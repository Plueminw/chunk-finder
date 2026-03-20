package com.chunkfinder.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.Set;

public class ChunkHighlightRenderer {

    private static boolean highlightEnabled = false;
    private static boolean bordersEnabled = false;

    // How many blocks above natural generation before we consider chunk "modified"
    private static final int MODIFICATION_THRESHOLD = 5;

    // Colors (RGBA)
    private static final float[] BASE_COLOR       = {1.0f, 0.3f, 0.3f, 0.25f}; // Red - suspected base
    private static final float[] MODIFIED_COLOR   = {1.0f, 0.8f, 0.0f, 0.18f}; // Yellow - modified
    private static final float[] BORDER_COLOR     = {0.0f, 1.0f, 1.0f, 0.8f};  // Cyan - chunk border

    public static void toggleHighlight() {
        highlightEnabled = !highlightEnabled;
    }

    public static void toggleBorders() {
        bordersEnabled = !bordersEnabled;
    }

    public static boolean isHighlightEnabled() {
        return highlightEnabled;
    }

    public static boolean isBordersEnabled() {
        return bordersEnabled;
    }

    public static void render(WorldRenderContext context) {
        if (!highlightEnabled && !bordersEnabled) return;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (world == null || client.player == null) return;

        MatrixStack matrices = context.matrixStack();
        if (matrices == null) return;

        Camera camera = context.camera();
        double camX = camera.getPos().x;
        double camY = camera.getPos().y;
        double camZ = camera.getPos().z;

        matrices.push();
        matrices.translate(-camX, -camY, -camZ);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();

        Tessellator tessellator = Tessellator.getInstance();

        int renderDistance = client.options.getViewDistance().getValue();
        ChunkPos playerChunk = client.player.getChunkPos();

        Set<ChunkPos> suspectedBases = new HashSet<>();
        Set<ChunkPos> modifiedChunks = new HashSet<>();

        // Scan nearby chunks
        for (int dx = -renderDistance; dx <= renderDistance; dx++) {
            for (int dz = -renderDistance; dz <= renderDistance; dz++) {
                ChunkPos chunkPos = new ChunkPos(playerChunk.x + dx, playerChunk.z + dz);
                WorldChunk chunk = world.getChunk(chunkPos.x, chunkPos.z);

                ModificationResult result = analyzeChunk(world, chunk, chunkPos);
                if (result == ModificationResult.SUSPECTED_BASE) {
                    suspectedBases.add(chunkPos);
                } else if (result == ModificationResult.MODIFIED) {
                    modifiedChunks.add(chunkPos);
                }
            }
        }

        if (highlightEnabled) {
            // Draw suspected base chunks (red)
            for (ChunkPos pos : suspectedBases) {
                drawChunkFill(tessellator, matrices, pos, BASE_COLOR, world);
            }
            // Draw modified chunks (yellow)
            for (ChunkPos pos : modifiedChunks) {
                drawChunkFill(tessellator, matrices, pos, MODIFIED_COLOR, world);
            }
        }

        if (bordersEnabled) {
            // Draw chunk borders for all nearby chunks
            for (int dx = -renderDistance; dx <= renderDistance; dx++) {
                for (int dz = -renderDistance; dz <= renderDistance; dz++) {
                    ChunkPos chunkPos = new ChunkPos(playerChunk.x + dx, playerChunk.z + dz);
                    drawChunkBorder(tessellator, matrices, chunkPos, BORDER_COLOR, client.player.getY());
                }
            }
        }

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        matrices.pop();
    }

    private enum ModificationResult {
        NATURAL, MODIFIED, SUSPECTED_BASE
    }

    private static ModificationResult analyzeChunk(ClientWorld world, WorldChunk chunk, ChunkPos pos) {
        if (chunk == null || chunk.isEmpty()) return ModificationResult.NATURAL;

        int nonNaturalBlocks = 0;
        int highBlocks = 0; // blocks placed above y=100

        // Sample points across the chunk
        for (int x = 0; x < 16; x += 2) {
            for (int z = 0; z < 16; z += 2) {
                int worldX = pos.getStartX() + x;
                int worldZ = pos.getStartZ() + z;

                // Check from y=60 upward for player-placed blocks
                for (int y = 60; y < 256; y++) {
                    BlockPos blockPos = new BlockPos(worldX, y, worldZ);
                    var block = world.getBlockState(blockPos);
                    if (!block.isAir()) {
                        String blockId = block.getBlock().toString().toLowerCase();
                        // Check for common building/base blocks
                        if (isPlayerBlock(blockId)) {
                            nonNaturalBlocks++;
                            if (y > 100) highBlocks++;
                        }
                    }
                }
            }
        }

        if (nonNaturalBlocks >= 15) return ModificationResult.SUSPECTED_BASE;
        if (nonNaturalBlocks >= MODIFICATION_THRESHOLD) return ModificationResult.MODIFIED;
        return ModificationResult.NATURAL;
    }

    private static boolean isPlayerBlock(String blockId) {
        // Common player-placed blocks indicating a base
        return blockId.contains("chest") ||
               blockId.contains("furnace") ||
               blockId.contains("crafting") ||
               blockId.contains("bed") ||
               blockId.contains("door") ||
               blockId.contains("torch") ||
               blockId.contains("planks") ||
               blockId.contains("log") ||
               blockId.contains("glass") ||
               blockId.contains("cobblestone") ||
               blockId.contains("stone_brick") ||
               blockId.contains("ladder") ||
               blockId.contains("fence") ||
               blockId.contains("stairs") ||
               blockId.contains("slab") ||
               blockId.contains("barrel") ||
               blockId.contains("hopper") ||
               blockId.contains("dispenser") ||
               blockId.contains("dropper") ||
               blockId.contains("sign");
    }

    private static void drawChunkFill(Tessellator tessellator, MatrixStack matrices,
                                       ChunkPos pos, float[] color, ClientWorld world) {
        int startX = pos.getStartX();
        int startZ = pos.getStartZ();
        int endX = pos.getEndX() + 1;
        int endZ = pos.getEndZ() + 1;

        // Draw a flat colored rectangle slightly above ground
        int y = 64; // sea level - adjust as needed

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        buffer.vertex(matrix, startX, y + 1, startZ).color(color[0], color[1], color[2], color[3]);
        buffer.vertex(matrix, startX, y + 1, endZ).color(color[0], color[1], color[2], color[3]);
        buffer.vertex(matrix, endX, y + 1, endZ).color(color[0], color[1], color[2], color[3]);
        buffer.vertex(matrix, endX, y + 1, startZ).color(color[0], color[1], color[2], color[3]);

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private static void drawChunkBorder(Tessellator tessellator, MatrixStack matrices,
                                         ChunkPos pos, float[] color, double playerY) {
        int startX = pos.getStartX();
        int startZ = pos.getStartZ();
        int endX = pos.getEndX() + 1;
        int endZ = pos.getEndZ() + 1;

        double y = playerY;
        double height = 0.5;

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        // Draw 4 edges of the chunk border
        // North edge
        buffer.vertex(matrix, startX, (float)(y + height), startZ).color(color[0], color[1], color[2], color[3]);
        buffer.vertex(matrix, endX,   (float)(y + height), startZ).color(color[0], color[1], color[2], color[3]);
        // South edge
        buffer.vertex(matrix, startX, (float)(y + height), endZ).color(color[0], color[1], color[2], color[3]);
        buffer.vertex(matrix, endX,   (float)(y + height), endZ).color(color[0], color[1], color[2], color[3]);
        // West edge
        buffer.vertex(matrix, startX, (float)(y + height), startZ).color(color[0], color[1], color[2], color[3]);
        buffer.vertex(matrix, startX, (float)(y + height), endZ).color(color[0], color[1], color[2], color[3]);
        // East edge
        buffer.vertex(matrix, endX,   (float)(y + height), startZ).color(color[0], color[1], color[2], color[3]);
        buffer.vertex(matrix, endX,   (float)(y + height), endZ).color(color[0], color[1], color[2], color[3]);

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }
}
