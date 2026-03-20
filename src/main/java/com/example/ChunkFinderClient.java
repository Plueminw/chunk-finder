package com.chunkfinder.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChunkFinderClient implements ClientModInitializer {

    public static final String MOD_ID = "chunkfinder";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static KeyBinding toggleKey;
    public static KeyBinding toggleBordersKey;

    @Override
    public void onInitializeClient() {
        LOGGER.info("ChunkFinder mod initialized!");

        // Register keybindings
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.chunkfinder.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F6,
                "category.chunkfinder"
        ));

        toggleBordersKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.chunkfinder.toggleborders",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F7,
                "category.chunkfinder"
        ));

        // Handle key presses
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                ChunkHighlightRenderer.toggleHighlight();
                if (client.player != null) {
                    client.player.sendMessage(
                            net.minecraft.text.Text.literal(
                                    "[ChunkFinder] Highlight: " + (ChunkHighlightRenderer.isHighlightEnabled() ? "§aON" : "§cOFF")
                            ), true
                    );
                }
            }
            while (toggleBordersKey.wasPressed()) {
                ChunkHighlightRenderer.toggleBorders();
                if (client.player != null) {
                    client.player.sendMessage(
                            net.minecraft.text.Text.literal(
                                    "[ChunkFinder] Borders: " + (ChunkHighlightRenderer.isBordersEnabled() ? "§aON" : "§cOFF")
                            ), true
                    );
                }
            }
        });

        // Register world render event
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ChunkHighlightRenderer::render);

        LOGGER.info("ChunkFinder keybindings registered. F6 = toggle highlights, F7 = toggle borders");
    }
}
