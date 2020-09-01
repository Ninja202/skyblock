package skyblock;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.EndPortalFrameBlock;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.world.ServerLightingProvider;
import net.minecraft.structure.NetherFortressGenerator;
import net.minecraft.structure.BastionTreasureData;
import net.minecraft.structure.StrongholdGenerator;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.collection.PackedIntegerArray;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.feature.StructureFeature;
import skyblock.mixin.ProtoChunkAccessor;
import skyblock.mixin.StructurePieceAccessor;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;

public class SkyBlockUtils {
    public static void deleteBlocks(ProtoChunk chunk, WorldAccess world) {
        ChunkSection[] sections = chunk.getSectionArray();
        Arrays.fill(sections, WorldChunk.EMPTY_SECTION);
        for (BlockPos bePos : chunk.getBlockEntityPositions()) {
            chunk.removeBlockEntity(bePos);
        }
        ((ProtoChunkAccessor) chunk).getLightSources().clear();
        long[] emptyHeightmap = new PackedIntegerArray(9, 256).getStorage();
        for (Map.Entry<Heightmap.Type, Heightmap> heightmapEntry : chunk.getHeightmaps()) {
            heightmapEntry.getValue().setTo(emptyHeightmap);
        }
        processStronghold(chunk, world);
        processFortress(chunk, world);
        processBastion(chunk, world);
        Heightmap.populateHeightmaps(chunk, EnumSet.allOf(Heightmap.Type.class));
    }

    private static void processBastion(ProtoChunk chunk, WorldAccess world) {
        for (long startPosLong : chunk.getStructureReferences(StructureFeature.BASTION_REMNANT)) {
            ChunkPos startPos = new ChunkPos(startPosLong);
            ProtoChunk startChunk = (ProtoChunk) world.getChunk(startPos.x, startPos.z, ChunkStatus.STRUCTURE_STARTS);
            StructureStart<?> bastion = startChunk.getStructureStart(StructureFeature.BASTION_REMNANT);
            ChunkPos pos = chunk.getPos();
            if (bastion != null && bastion.getBoundingBox().intersectsXZ(pos.getStartX(), pos.getStartZ(), pos.getEndX(), pos.getEndZ())) {
                for (StructurePiece piece : bastion.getChildren()) {
                    if (piece.toString().contains("bastion/treasure/bases/lava_basin")) {
                        if (piece.getBoundingBox().intersectsXZ(pos.getStartX(), pos.getStartZ(), pos.getEndX(), pos.getEndZ())) {
                            generateMagmaSpawner(chunk, piece);
                        }
                    }
                }
            }
        }
    }

    private static void processFortress(ProtoChunk chunk, WorldAccess world) {
        for (long startPosLong : chunk.getStructureReferences(StructureFeature.FORTRESS)) {
            ChunkPos startPos = new ChunkPos(startPosLong);
            ProtoChunk startChunk = (ProtoChunk) world.getChunk(startPos.x, startPos.z, ChunkStatus.STRUCTURE_STARTS);
            StructureStart<?> fortress = startChunk.getStructureStart(StructureFeature.FORTRESS);
            ChunkPos pos = chunk.getPos();
            if (fortress != null && fortress.getBoundingBox().intersectsXZ(pos.getStartX(), pos.getStartZ(), pos.getEndX(), pos.getEndZ())) {
                for (StructurePiece piece : fortress.getChildren()) {
                    if (piece instanceof NetherFortressGenerator.BridgePlatform) {
                        if (piece.getBoundingBox().intersectsXZ(pos.getStartX(), pos.getStartZ(), pos.getEndX(), pos.getEndZ())) {
                            generateBlazeSpawner(chunk, (NetherFortressGenerator.BridgePlatform) piece);
                        }
                    }
                }
            }
        }
    }

    private static void processStronghold(ProtoChunk chunk, WorldAccess world) {
        for (long startPosLong : chunk.getStructureReferences(StructureFeature.STRONGHOLD)) {
            ChunkPos startPos = new ChunkPos(startPosLong);
            ProtoChunk startChunk = (ProtoChunk) world.getChunk(startPos.x, startPos.z, ChunkStatus.STRUCTURE_STARTS);
            StructureStart<?> stronghold = startChunk.getStructureStart(StructureFeature.STRONGHOLD);
            ChunkPos pos = chunk.getPos();
            if (stronghold != null && stronghold.getBoundingBox().intersectsXZ(pos.getStartX(), pos.getStartZ(), pos.getEndX(), pos.getEndZ())) {
                for (StructurePiece piece : stronghold.getChildren()) {
                    if (piece instanceof StrongholdGenerator.PortalRoom) {
                        if (piece.getBoundingBox().intersectsXZ(pos.getStartX(), pos.getStartZ(), pos.getEndX(), pos.getEndZ())) {
                            generateStrongholdPortal(chunk, (StrongholdGenerator.PortalRoom) piece, new Random(startPosLong));
                        }
                    }
                }
            }
        }
    }

    private static BlockPos getBlockInStructurePiece(StructurePiece piece, int x, int y, int z) {
        StructurePieceAccessor access = (StructurePieceAccessor) piece;
        return new BlockPos(access.invokeApplyXTransform(x, z), access.invokeApplyYTransform(y), access.invokeApplyZTransform(x, z));
    }

    private static void setBlockInStructure(StructurePiece piece, ProtoChunk chunk, BlockState state, int x, int y, int z) {
        StructurePieceAccessor access = (StructurePieceAccessor) piece;
        BlockPos pos = getBlockInStructurePiece(piece, x, y, z);
        if (piece.getBoundingBox().contains(pos)) {
            BlockMirror mirror = access.getMirror();
            if (mirror != BlockMirror.NONE) state = state.mirror(mirror);
            BlockRotation rotation = piece.getRotation();
            if (rotation != BlockRotation.NONE) state = state.rotate(rotation);

            setBlockInChunk(chunk, pos, state);
        }
    }

    private static void setBlockInChunk(ProtoChunk chunk, BlockPos pos, BlockState state) {
        if (chunk.getPos().equals(new ChunkPos(pos))) {
            chunk.setBlockState(pos, state, false);
        }
    }

    private static void setBlockEntityInChunk(ProtoChunk chunk, BlockPos pos, CompoundTag tag) {
        if (chunk.getPos().equals(new ChunkPos(pos))) {
            tag.putInt("x", pos.getX());
            tag.putInt("y", pos.getY());
            tag.putInt("z", pos.getZ());
            System.out.println(tag);
            chunk.addPendingBlockEntityTag(tag);
        }
    }

    private static void generateMagmaSpawner(ProtoChunk chunk, StructurePiece room) {
        BlockPos spawnerPos = new BlockPos(room.getBoundingBox().getCenter());
        setBlockInChunk(chunk, spawnerPos, Blocks.SPAWNER.getDefaultState());
        CompoundTag spawnerTag = new CompoundTag();
        spawnerTag.putString("id", "minecraft:mob_spawner");
        ListTag spawnPotentials = new ListTag();
        spawnerTag.put("SpawnPotentials", spawnPotentials);
        CompoundTag spawnEntry = new CompoundTag();
        spawnPotentials.addTag(0, spawnEntry);
        CompoundTag entity = new CompoundTag();
        spawnEntry.put("Entity", entity);
        entity.putString("id", "minecraft:magma_cube");
        spawnEntry.putInt("Weight", 1);
        spawnerTag.put("SpawnData", entity.copy());
        // System.out.println(room.getBoundingBox().getCenter());
        setBlockEntityInChunk(chunk, spawnerPos, spawnerTag);
    }

    private static void generateBlazeSpawner(ProtoChunk chunk, NetherFortressGenerator.BridgePlatform room) {
        BlockPos spawnerPos = getBlockInStructurePiece(room, 3, 5, 5);
        setBlockInChunk(chunk, spawnerPos, Blocks.SPAWNER.getDefaultState());
        CompoundTag spawnerTag = new CompoundTag();
        spawnerTag.putString("id", "minecraft:mob_spawner");
        ListTag spawnPotentials = new ListTag();
        spawnerTag.put("SpawnPotentials", spawnPotentials);
        CompoundTag spawnEntry = new CompoundTag();
        spawnPotentials.addTag(0, spawnEntry);
        CompoundTag entity = new CompoundTag();
        spawnEntry.put("Entity", entity);
        entity.putString("id", "minecraft:blaze");
        spawnEntry.putInt("Weight", 1);
        spawnerTag.put("SpawnData", entity.copy());
        setBlockEntityInChunk(chunk, spawnerPos, spawnerTag);
    }

    private static void generateStrongholdPortal(ProtoChunk chunk, StrongholdGenerator.PortalRoom room, Random random) {
        BlockState northFrame = Blocks.END_PORTAL_FRAME.getDefaultState().with(EndPortalFrameBlock.FACING, Direction.NORTH);
        BlockState southFrame = Blocks.END_PORTAL_FRAME.getDefaultState().with(EndPortalFrameBlock.FACING, Direction.SOUTH);
        BlockState eastFrame = Blocks.END_PORTAL_FRAME.getDefaultState().with(EndPortalFrameBlock.FACING, Direction.EAST);
        BlockState westFrame = Blocks.END_PORTAL_FRAME.getDefaultState().with(EndPortalFrameBlock.FACING, Direction.WEST);
        boolean completelyFilled = true;
        boolean[] framesFilled = new boolean[12];

        for(int i = 0; i < framesFilled.length; ++i) {
            framesFilled[i] = random.nextFloat() > 0.9F;
            completelyFilled &= framesFilled[i];
        }
        setBlockInStructure(room, chunk, northFrame.with(EndPortalFrameBlock.EYE, framesFilled[0]), 4, 3, 8);
        setBlockInStructure(room, chunk, northFrame.with(EndPortalFrameBlock.EYE, framesFilled[1]), 5, 3, 8);
        setBlockInStructure(room, chunk, northFrame.with(EndPortalFrameBlock.EYE, framesFilled[2]), 6, 3, 8);
        setBlockInStructure(room, chunk, southFrame.with(EndPortalFrameBlock.EYE, framesFilled[3]), 4, 3, 12);
        setBlockInStructure(room, chunk, southFrame.with(EndPortalFrameBlock.EYE, framesFilled[4]), 5, 3, 12);
        setBlockInStructure(room, chunk, southFrame.with(EndPortalFrameBlock.EYE, framesFilled[5]), 6, 3, 12);
        setBlockInStructure(room, chunk, eastFrame.with(EndPortalFrameBlock.EYE, framesFilled[6]), 3, 3, 9);
        setBlockInStructure(room, chunk, eastFrame.with(EndPortalFrameBlock.EYE, framesFilled[7]), 3, 3, 10);
        setBlockInStructure(room, chunk, eastFrame.with(EndPortalFrameBlock.EYE, framesFilled[8]), 3, 3, 11);
        setBlockInStructure(room, chunk, westFrame.with(EndPortalFrameBlock.EYE, framesFilled[9]), 7, 3, 9);
        setBlockInStructure(room, chunk, westFrame.with(EndPortalFrameBlock.EYE, framesFilled[10]), 7, 3, 10);
        setBlockInStructure(room, chunk, westFrame.with(EndPortalFrameBlock.EYE, framesFilled[11]), 7, 3, 11);
        if (completelyFilled) {
            BlockState portal = Blocks.END_PORTAL.getDefaultState();
            setBlockInStructure(room, chunk, portal, 4, 3, 9);
            setBlockInStructure(room, chunk, portal, 5, 3, 9);
            setBlockInStructure(room, chunk, portal, 6, 3, 9);
            setBlockInStructure(room, chunk, portal, 4, 3, 10);
            setBlockInStructure(room, chunk, portal, 5, 3, 10);
            setBlockInStructure(room, chunk, portal, 6, 3, 10);
            setBlockInStructure(room, chunk, portal, 4, 3, 11);
            setBlockInStructure(room, chunk, portal, 5, 3, 11);
            setBlockInStructure(room, chunk, portal, 6, 3, 11);
        }
        BlockPos spawnerPos = getBlockInStructurePiece(room, 5, 3, 6);
        setBlockInChunk(chunk, spawnerPos, Blocks.SPAWNER.getDefaultState());
        CompoundTag spawnerTag = new CompoundTag();
        spawnerTag.putString("id", "minecraft:mob_spawner");
        ListTag spawnPotentials = new ListTag();
        spawnerTag.put("SpawnPotentials", spawnPotentials);
        CompoundTag spawnEntry = new CompoundTag();
        spawnPotentials.addTag(0, spawnEntry);
        CompoundTag entity = new CompoundTag();
        spawnEntry.put("Entity", entity);
        entity.putString("id", "minecraft:silverfish");
        spawnEntry.putInt("Weight", 1);
        spawnerTag.put("SpawnData", entity.copy());
        setBlockEntityInChunk(chunk, spawnerPos, spawnerTag);
    }

    private static void clearChunk(ProtoChunk chunk, WorldAccess world) {
        deleteBlocks(chunk, world);
        // erase entities
        chunk.getEntities().clear();
        try {
            ((ServerLightingProvider)chunk.getLightingProvider()).light(chunk, true).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }
}
