package com.bitesizekira.ell;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class Ell implements ModInitializer {

	private static final List<BlockData> PARSABLE_BLOCKS = new ArrayList<>();
	private static final Map<String, Block> CUSTOM_ALIASES = new HashMap<>();

	// NEW: The Actions Dictionary! Maps a word to an entire Java method.
	private static final Map<String, Consumer<ServerPlayer>> ACTIONS = new HashMap<>();

	private record BlockData(String[] keywords, String readableName, Block block) {}

	@Override
	public void onInitialize() {

		// 1. POPULATE DICTIONARIES
		CUSTOM_ALIASES.put("slime", Blocks.SLIME_BLOCK);
		CUSTOM_ALIASES.put("diamond", Blocks.DIAMOND_ORE);
		CUSTOM_ALIASES.put("emerald", Blocks.EMERALD_ORE);
		CUSTOM_ALIASES.put("gold", Blocks.GOLD_ORE);
		CUSTOM_ALIASES.put("iron", Blocks.IRON_ORE);
		CUSTOM_ALIASES.put("redstone", Blocks.REDSTONE_ORE);
		CUSTOM_ALIASES.put("lapis", Blocks.LAPIS_ORE);
		CUSTOM_ALIASES.put("coal", Blocks.COAL_ORE);
		CUSTOM_ALIASES.put("honey", Blocks.HONEY_BLOCK);

		// Register the "house" command to trigger our new buildHouse method
		ACTIONS.put("house", this::buildHouse);

		// 2. TOKENIZER SCANNER
		for (Block block : BuiltInRegistries.BLOCK) {
			ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
			if (id != null && block != Blocks.AIR) {
				String[] keywords = id.getPath().split("_");
				String readableName = id.getPath().replace('_', ' ').toLowerCase();
				PARSABLE_BLOCKS.add(new BlockData(keywords, readableName, block));
			}
		}
		PARSABLE_BLOCKS.sort(Comparator.comparingInt((BlockData b) -> b.keywords().length).reversed());

		// 3. CHAT LISTENER
		ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
			String text = message.signedContent().toLowerCase();
			ServerLevel world = (ServerLevel) sender.level();

			// STEP A: Check for Actions first (like "house")
			for (Map.Entry<String, Consumer<ServerPlayer>> entry : ACTIONS.entrySet()) {
				if (text.contains(entry.getKey())) {
					world.getServer().execute(() -> {
						entry.getValue().accept(sender);
						sender.sendSystemMessage(Component.literal("<Magik Ell> Construction complete boss! Enjoy! :D"));
					});
					return; // Stop processing entirely if an action was triggered
				}
			}

			// ... [The rest of the parsing logic for placing single blocks remains exactly the same] ...
			Block blockToSpawn = null;
			String replyName = "";

			for (Map.Entry<String, Block> entry : CUSTOM_ALIASES.entrySet()) {
				if (text.contains("ell")) {
					if (text.contains(entry.getKey())) {
						blockToSpawn = entry.getValue();
						replyName = entry.getValue().getName().getString();
						break;
					}
				}
			}

			if (blockToSpawn == null) {
				for (BlockData blockData : PARSABLE_BLOCKS) {
					boolean containsAllWords = true;
					for (String word : blockData.keywords()) {
						if (!text.contains(word)) {
							containsAllWords = false;
							break;
						}
					}
					if (containsAllWords) {
						blockToSpawn = blockData.block();
						replyName = blockData.readableName();
						break;
					}
				}
			}

			if (blockToSpawn != null) {
				Block finalBlock = blockToSpawn;
				String reply = "<Magik Ell> You got it boss! One §d§o" + replyName + "§f§r coming right up! o7";
				world.getServer().execute(() -> {
					placeBlockInFrontOfPlayer(sender, finalBlock);
					sender.sendSystemMessage(Component.literal(reply));
				});
			}
		});
	}

	private void placeBlockInFrontOfPlayer(ServerPlayer player, Block blockToPlace) {
		ServerLevel world = (ServerLevel) player.level();
		Direction playerFacing = player.getDirection();
		BlockPos placePos = player.blockPosition().relative(playerFacing, 2);
		BlockState stateToPlace = blockToPlace.defaultBlockState();
		if (stateToPlace.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
			stateToPlace = stateToPlace.setValue(BlockStateProperties.HORIZONTAL_FACING, playerFacing.getOpposite());
		}
		world.setBlockAndUpdate(placePos, stateToPlace);
	}

	// NEW: The Structure Builder
	private void buildHouse(ServerPlayer player) {
		ServerLevel world = (ServerLevel) player.level();

		// ROTATIONAL MATH: Get our Forward and Right vectors
		Direction facing = player.getDirection();
		Direction right = facing.getClockWise();

		// Start 2 blocks forward, 2 blocks down
		BlockPos startPos = player.blockPosition().relative(facing, 2).below(2);

		// A memory map to track exactly how many of each ore we destroy
		Map<Block, Integer> minedOres = new HashMap<>();

		// Loop using width (Left/Right), height (Up/Down), and depth (Forward/Backward)
		for (int w = -4; w <= 5; w++) {
			for (int h = 0; h < 10; h++) {
				for (int d = 0; d < 10; d++) {

					// Calculate the exact 3D coordinate relative to your facing direction
					BlockPos currentPos = startPos.relative(right, w).above(h).relative(facing, d);

					// PHASE 1: SCAN & MINE
					Block existingBlock = world.getBlockState(currentPos).getBlock();
					ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(existingBlock);

					// If the block we are about to destroy has "ore" in its name, tally it!
					if (blockId != null && blockId.getPath().contains("ore")) {
						minedOres.put(existingBlock, minedOres.getOrDefault(existingBlock, 0) + 1);
					}

					// PHASE 2: BUILD
					if (w == -4 || w == 5 || h == 0 || h == 9 || d == 0 || d == 9) {
						world.setBlockAndUpdate(currentPos, Blocks.OBSIDIAN.defaultBlockState()); // Shell
					} else if (h == 8) {
						world.setBlockAndUpdate(currentPos, Blocks.GLOWSTONE.defaultBlockState()); // Ceiling
					} else if (w == -3 || w == 4 || h == 1 || d == 1 || d == 8) {
						world.setBlockAndUpdate(currentPos, Blocks.OAK_PLANKS.defaultBlockState()); // Inner lining
					} else {
						world.setBlockAndUpdate(currentPos, Blocks.AIR.defaultBlockState()); // Hollow center
					}
				}
			}
		}

		// PHASE 3: FURNISHINGS
		// Because of the rotational vectors, the furniture will ALWAYS stay in the back left corner
		world.setBlockAndUpdate(startPos.relative(right, -2).above(2).relative(facing, 7), Blocks.CRAFTING_TABLE.defaultBlockState());

		// Force the furnace to face the center of the room (opposite of the player's facing direction)
		BlockState furnaceState = Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing.getOpposite());
		world.setBlockAndUpdate(startPos.relative(right, -1).above(2).relative(facing, 7), furnaceState);

		// The DIY Bed (Right side of the room)
		world.setBlockAndUpdate(startPos.relative(right, 2).above(2).relative(facing, 7), Blocks.RED_WOOL.defaultBlockState());
		world.setBlockAndUpdate(startPos.relative(right, 2).above(3).relative(facing, 7), Blocks.RED_WOOL.defaultBlockState());
		world.setBlockAndUpdate(startPos.relative(right, 2).above(4).relative(facing, 7), Blocks.RED_WOOL.defaultBlockState());

		world.setBlockAndUpdate(startPos.relative(right, 3).above(2).relative(facing, 7), Blocks.OAK_PLANKS.defaultBlockState());
		world.setBlockAndUpdate(startPos.relative(right, 3).above(3).relative(facing, 7), Blocks.OAK_PLANKS.defaultBlockState());
		world.setBlockAndUpdate(startPos.relative(right, 3).above(4).relative(facing, 7), Blocks.OAK_PLANKS.defaultBlockState());

		// Punch the 2-high door exactly in the front center wall (cutting through the obsidian at d=0 and planks at d=1)
		world.setBlockAndUpdate(startPos.relative(right, 0).above(2).relative(facing, 0), Blocks.AIR.defaultBlockState());
		world.setBlockAndUpdate(startPos.relative(right, 0).above(3).relative(facing, 0), Blocks.AIR.defaultBlockState());
		world.setBlockAndUpdate(startPos.relative(right, 0).above(2).relative(facing, 1), Blocks.AIR.defaultBlockState());
		world.setBlockAndUpdate(startPos.relative(right, 0).above(3).relative(facing, 1), Blocks.AIR.defaultBlockState());

		// PHASE 4: THE QUARRY REWARD (The Shulker Box)
		// Place the box right in the center of the room
		BlockPos shulkerPos = startPos.relative(right, 0).above(2).relative(facing, 4);
		world.setBlockAndUpdate(shulkerPos, Blocks.SHULKER_BOX.defaultBlockState());

		// Tap directly into the Shulker Box's inventory
		if (world.getBlockEntity(shulkerPos) instanceof ShulkerBoxBlockEntity shulker) {
			int currentSlot = 0;

			for (Map.Entry<Block, Integer> entry : minedOres.entrySet()) {
				Item item = entry.getKey().asItem();
				int totalOreFound = entry.getValue();

				// Chunk the ores into stacks of 64 so we don't break the inventory logic
				while (totalOreFound > 0 && currentSlot < 27) {
					int stackSize = Math.min(totalOreFound, 64);
					shulker.setItem(currentSlot, new ItemStack(item, stackSize));
					totalOreFound -= stackSize;
					currentSlot++;
				}
			}
		}
	}
}
