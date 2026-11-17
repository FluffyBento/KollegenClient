package dev.kollegen.client.map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

public class BlockColorPalette {
    private static final Map<String, Integer> PALETTE = new HashMap<>(512);
    private static final Map<Block, Integer> BLOCK_CACHE = new IdentityHashMap<>(512);

    static {
        register("stone", 0x767676);
        register("granite", 0x976B5B);
        register("polished_granite", 0x9D7160);
        register("diorite", 0xB5B5B8);
        register("polished_diorite", 0xC0C0C3);
        register("andesite", 0x828284);
        register("polished_andesite", 0x8A8A8E);
        register("deepslate", 0x3F3F47);
        register("cobbled_deepslate", 0x3A3A42);
        register("polished_deepslate", 0x34343B);
        register("deepslate_tiles", 0x2E2E35);
        register("deepslate_bricks", 0x303036);
        register("tuff", 0x5D5E55);
        register("polished_tuff", 0x64665C);
        register("calcite", 0xE1E2DC);
        register("dripstone_block", 0x886B5C);
        register("cobblestone", 0x6B6B6B);
        register("mossy_cobblestone", 0x526B52);
        register("smooth_stone", 0x868686);
        register("stone_bricks", 0x727272);
        register("mossy_stone_bricks", 0x567256);
        register("cracked_stone_bricks", 0x646464);
        register("bedrock", 0x2B2B2B);
        register("obsidian", 0x141121);
        register("crying_obsidian", 0x22133A);

        register("dirt", 0x866043);
        register("coarse_dirt", 0x77543A);
        register("rooted_dirt", 0x8A6548);
        register("mud", 0x3C3938);
        register("mud_bricks", 0x7B5844);
        register("packed_mud", 0x88644C);
        register("dirt_path", 0x9E7C3E);
        register("farmland", 0x5F3B1F);
        register("podzol", 0x5C4328);
        register("mycelium", 0x6F6365);
        register("clay", 0x9DA4B0);
        register("gravel", 0x8A8585);

        register("sand", 0xDCDB9E);
        register("red_sand", 0xBF6727);
        register("sandstone", 0xD6C896);
        register("cut_sandstone", 0xD8CA99);
        register("smooth_sandstone", 0xD9CB9B);
        register("red_sandstone", 0xBD5E21);
        register("cut_red_sandstone", 0xBE6023);
        register("smooth_red_sandstone", 0xC06225);

        register("snow", 0xF7FAFA);
        register("snow_block", 0xF7FAFA);
        register("powder_snow", 0xFAFDFD);
        register("ice", 0x92BAEE);
        register("packed_ice", 0x85ABEC);
        register("blue_ice", 0x6FA6FA);

        register("oak_planks", 0xA68553);
        register("spruce_planks", 0x674B30);
        register("birch_planks", 0xC5B47B);
        register("jungle_planks", 0x9D7054);
        register("acacia_planks", 0x9F5532);
        register("dark_oak_planks", 0x3E2914);
        register("mangrove_planks", 0x703232);
        register("cherry_planks", 0xE3B0A5);
        register("bamboo_planks", 0xC6A954);
        register("bamboo_mosaic", 0xC3A650);
        register("crimson_planks", 0x6A3048);
        register("warped_planks", 0x2C6C66);

        register("oak_log", 0x675235);
        register("spruce_log", 0x3B2A1C);
        register("birch_log", 0xDCDCCE);
        register("jungle_log", 0x584428);
        register("acacia_log", 0x676057);
        register("dark_oak_log", 0x352818);
        register("mangrove_log", 0x542626);
        register("cherry_log", 0x3B2529);
        register("bamboo_block", 0x769535);
        register("crimson_stem", 0x5A1F36);
        register("warped_stem", 0x3A2447);

        register("netherrack", 0x682323);
        register("crimson_nylium", 0x871818);
        register("warped_nylium", 0x2A6F6B);
        register("soul_sand", 0x4D392E);
        register("soul_soil", 0x402E24);
        register("basalt", 0x4A4A51);
        register("smooth_basalt", 0x47474C);
        register("blackstone", 0x27232B);
        register("polished_blackstone", 0x302A34);
        register("glowstone", 0xF3CD75);
        register("magma_block", 0x93431A);
        register("nether_bricks", 0x2D161B);
        register("red_nether_bricks", 0x450C10);
        register("nether_wart_block", 0x731111);
        register("warped_wart_block", 0x16766D);
        register("bone_block", 0xD7D2AF);

        register("end_stone", 0xDADE9E);
        register("end_stone_bricks", 0xD6DA99);
        register("purpur_block", 0xA778A7);
        register("purpur_pillar", 0xA474A4);

        register("prismarine", 0x5E9086);
        register("prismarine_bricks", 0x589E90);
        register("dark_prismarine", 0x355047);
        register("sea_lantern", 0xADCFC6);
        register("sponge", 0xC3C24D);
        register("wet_sponge", 0xA2A63D);

        register("terracotta", 0x935B42);
        register("white_terracotta", 0xD2B2A2);
        register("orange_terracotta", 0xA15426);
        register("magenta_terracotta", 0x96586D);
        register("light_blue_terracotta", 0x726D8B);
        register("yellow_terracotta", 0xBB8624);
        register("lime_terracotta", 0x687635);
        register("pink_terracotta", 0xA14E4F);
        register("gray_terracotta", 0x3B2A24);
        register("light_gray_terracotta", 0x886B62);
        register("cyan_terracotta", 0x575B5B);
        register("purple_terracotta", 0x774657);
        register("blue_terracotta", 0x4B3C5C);
        register("brown_terracotta", 0x4E3324);
        register("green_terracotta", 0x4D542B);
        register("red_terracotta", 0x8F3D2F);
        register("black_terracotta", 0x261711);

        register("white_concrete", 0xCFD5D6);
        register("orange_concrete", 0xE06100);
        register("magenta_concrete", 0xA9309F);
        register("light_blue_concrete", 0x2389C7);
        register("yellow_concrete", 0xF1AF15);
        register("lime_concrete", 0x5EA818);
        register("pink_concrete", 0xD6658F);
        register("gray_concrete", 0x36393D);
        register("light_gray_concrete", 0x7D7D73);
        register("cyan_concrete", 0x157788);
        register("purple_concrete", 0x64209C);
        register("blue_concrete", 0x2D2F8F);
        register("brown_concrete", 0x603B20);
        register("green_concrete", 0x495B24);
        register("red_concrete", 0x8E2020);
        register("black_concrete", 0x080A0F);

        register("white_wool", 0xE9ECEC);
        register("orange_wool", 0xF07613);
        register("magenta_wool", 0xBD44B3);
        register("light_blue_wool", 0x3AAFD9);
        register("yellow_wool", 0xF8C627);
        register("lime_wool", 0x70B91A);
        register("pink_wool", 0xED8FAC);
        register("gray_wool", 0x3E4447);
        register("light_gray_wool", 0x8E8E86);
        register("cyan_wool", 0x158991);
        register("purple_wool", 0x792AAC);
        register("blue_wool", 0x35399D);
        register("brown_wool", 0x724728);
        register("green_wool", 0x546D1B);
        register("red_wool", 0xA12722);
        register("black_wool", 0x141519);

        register("coal_ore", 0x4A4A4A);
        register("iron_ore", 0x88786B);
        register("copper_ore", 0x7C6B5F);
        register("gold_ore", 0x93885C);
        register("redstone_ore", 0x915555);
        register("emerald_ore", 0x5B8465);
        register("lapis_ore", 0x596884);
        register("diamond_ore", 0x628C91);

        register("bricks", 0x966153);
        register("hay_block", 0xA68B0B);
        register("melon", 0x719323);
        register("pumpkin", 0xC6761A);
        register("carved_pumpkin", 0xC6761A);
        register("jack_o_lantern", 0xD1831F);
        register("cacti", 0x517424);
        register("cactus", 0x517424);
        register("sugar_cane", 0x95C265);
        register("moss_block", 0x596F29);
        register("moss_carpet", 0x596F29);
    }

    private static void register(String path, int rgb) {
        PALETTE.put(path, 0xFF000000 | rgb);
    }

    public static int getColor(BlockState state, int worldX, int worldZ, Biome biome) {
        if (state == null) return 0xFF181B20;
        Block block = state.getBlock();
        String path = BuiltInRegistries.BLOCK.getKey(block).getPath();

        if (path.contains("water")) {
            int wc = biome != null ? biome.getWaterColor() : 0x2A72D4;
            return 0xFF000000 | wc;
        }

        if (path.equals("grass_block") || path.equals("short_grass") || path.equals("tall_grass") || path.equals("fern") || path.equals("large_fern")) {
            int gc = biome != null ? biome.getGrassColor(worldX, worldZ) : 0x5D9E38;
            return 0xFF000000 | gc;
        }

        if (path.equals("oak_leaves") || path.equals("jungle_leaves") || path.equals("dark_oak_leaves") || path.equals("vine")) {
            int fc = biome != null ? biome.getFoliageColor() : 0x487B24;
            return 0xFF000000 | fc;
        }
        if (path.equals("birch_leaves")) return 0xFF789C48;
        if (path.equals("spruce_leaves")) return 0xFF517E51;
        if (path.equals("cherry_leaves")) return 0xFFF09DBE;
        if (path.equals("mangrove_leaves")) return 0xFF739722;
        if (path.equals("azalea_leaves")) return 0xFF5C892A;
        if (path.equals("flowering_azalea_leaves")) return 0xFFB8528E;

        Integer cached = BLOCK_CACHE.get(block);
        if (cached != null) {
            return cached;
        }

        Integer direct = PALETTE.get(path);
        if (direct != null) {
            BLOCK_CACHE.put(block, direct);
            return direct;
        }

        for (Map.Entry<String, Integer> entry : PALETTE.entrySet()) {
            if (path.contains(entry.getKey())) {
                BLOCK_CACHE.put(block, entry.getValue());
                return entry.getValue();
            }
        }

        int fallback = 0xFF555555;
        try {
            net.minecraft.world.level.material.MapColor mc = state.getMapColor(null, null);
            if (mc != null && mc.col != 0) {
                fallback = 0xFF000000 | mc.col;
            }
        } catch (Throwable ignored) {
        }
        BLOCK_CACHE.put(block, fallback);
        return fallback;
    }
}
