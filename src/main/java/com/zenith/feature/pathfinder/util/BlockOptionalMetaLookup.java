package com.zenith.feature.pathfinder.util;

import com.google.common.collect.Sets;
import com.zenith.mc.block.Block;
import com.zenith.mc.block.BlockRegistry;
import com.zenith.mc.block.BlockState;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import lombok.Data;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
public class BlockOptionalMetaLookup {
    private final Set<Block> blockSet;
    // sets preferable for fast contains
    private final IntSet blockStateIds;
    // lists preferable for indexed iteration
    @Getter(lazy = true) private final List<Block> blockList = buildBlockList();
    @Getter(lazy = true) private final IntList blockStateIdList = buildBlockStateIdList();

    public BlockOptionalMetaLookup(Set<Block> blocks) {
        this.blockSet = blocks;
        this.blockStateIds = new IntOpenHashSet();
        for (Block block : blocks) {
            for (int stateId = block.minStateId(); stateId <= block.maxStateId(); stateId++) {
                blockStateIds.add(stateId);
            }
        }
    }

    public BlockOptionalMetaLookup(Block... blocks) {
        this(Sets.newHashSet(blocks));
    }

    public BlockOptionalMetaLookup(IntSet blockStateIds) {
        this.blockSet = new HashSet<>();
        this.blockStateIds = blockStateIds;
        for (int stateId : blockStateIds) {
            Block block = BlockRegistry.REGISTRY.get(stateId);
            if (block != null) {
                this.blockSet.add(block);
            }
        }
    }

    public BlockOptionalMetaLookup(int... blockStateIds) {
        this(new IntOpenHashSet(blockStateIds));
    }

    public boolean has(Block block) {
        return blockSet.contains(block);
    }

    public boolean has(BlockState state) {
        return blockStateIds.contains(state.id());
    }

    public boolean has(int state) {
        return blockStateIds.contains(state);
    }

    private IntList buildBlockStateIdList() {
        return new IntArrayList(blockStateIds);
    }

    private List<Block> buildBlockList() {
        return new ArrayList<>(blockSet);
    }
}
