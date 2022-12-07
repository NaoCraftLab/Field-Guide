/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */

package net.dries007.tfc.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.google.common.collect.Iterators;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.RandomSource;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleRandomFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.util.thread.EffectiveSide;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import net.dries007.tfc.client.ClientHelpers;
import net.dries007.tfc.common.TFCEffects;
import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.capabilities.Capabilities;
import net.dries007.tfc.common.capabilities.food.FoodCapability;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.dries007.tfc.common.capabilities.size.IItemSize;
import net.dries007.tfc.common.capabilities.size.ItemSizeManager;
import net.dries007.tfc.common.capabilities.size.Size;
import net.dries007.tfc.common.capabilities.size.Weight;
import net.dries007.tfc.common.entities.ai.prey.PestAi;
import net.dries007.tfc.common.entities.prey.Pest;
import net.dries007.tfc.common.items.TFCShieldItem;
import net.dries007.tfc.mixin.accessor.RecipeManagerAccessor;
import net.dries007.tfc.world.feature.MultipleFeature;

import static net.dries007.tfc.TerraFirmaCraft.*;

public final class Helpers
{
    public static final Direction[] DIRECTIONS = Direction.values();
    public static final Direction[] DIRECTIONS_NOT_DOWN = Arrays.stream(DIRECTIONS).filter(d -> d != Direction.DOWN).toArray(Direction[]::new);
    public static final DyeColor[] DYE_COLORS = DyeColor.values();

    /**
     * If assertions (-ea) are enabled. Used to selectively enable various self-test mechanisms
     */
    public static final boolean ASSERTIONS_ENABLED = detectAssertionsEnabled();

    /**
     * If the current environment is a bootstrapped one, i.e. one outside the transforming class loader, such as /gradlew test launch
     */
    public static final boolean BOOTSTRAP_ENVIRONMENT = detectBootstrapEnvironment();

    /**
     * If the current one includes test source sets, i.e. gametest, indev, or ./gradlew test
     */
    public static final boolean TEST_ENVIRONMENT = detectTestSourcesPresent();

    public static final String BLOCK_ENTITY_TAG = "BlockEntityTag"; // BlockItem.BLOCK_ENTITY_TAG;
    public static final String BLOCK_STATE_TAG = BlockItem.BLOCK_STATE_TAG;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int PRIME_X = 501125321;
    private static final int PRIME_Y = 1136930381;

    @Nullable private static RecipeManager CACHED_RECIPE_MANAGER = null;

    /**
     * Default {@link ResourceLocation}, except with a TFC namespace
     */
    public static ResourceLocation identifier(String name)
    {
        return new ResourceLocation(MOD_ID, name);
    }

    @SuppressWarnings("ConstantConditions")
    public static <T> Capability<T> capability(CapabilityToken<T> token)
    {
        return BOOTSTRAP_ENVIRONMENT ? null : CapabilityManager.get(token);
    }

    @Nullable
    @SuppressWarnings("ConstantConditions")
    public static <T> T getCapability(ICapabilityProvider provider, Capability<T> capability)
    {
        return provider.getCapability(capability).orElse(null);
    }

    /**
     * Creates a map of each enum constant to the value as provided by the value mapper.
     */
    public static <E extends Enum<E>, V> EnumMap<E, V> mapOfKeys(Class<E> enumClass, Function<E, V> valueMapper)
    {
        return mapOfKeys(enumClass, key -> true, valueMapper);
    }

    /**
     * Creates a map of each enum constant to the value as provided by the value mapper, only using enum constants that match the provided predicate.
     */
    public static <E extends Enum<E>, V> EnumMap<E, V> mapOfKeys(Class<E> enumClass, Predicate<E> keyPredicate, Function<E, V> valueMapper)
    {
        return Arrays.stream(enumClass.getEnumConstants()).filter(keyPredicate).collect(Collectors.toMap(Function.identity(), valueMapper, (v, v2) -> v, () -> new EnumMap<>(enumClass)));
    }

    public static <K, V> V getRandomValue(Map<K, V> map, Random random)
    {
        return Iterators.get(map.values().iterator(), random.nextInt(map.size()));
    }

    /**
     * Flattens a homogeneous stream of {@code Collection<T>}, {@code Stream<T>} and {@code T}s together into a {@code Stream<T>}
     * Usage: {@code stream.flatMap(Helpers::flatten)}
     */
    @SuppressWarnings("unchecked")
    public static <R> Stream<? extends R> flatten(Object t)
    {
        return t instanceof Collection<?> c ? (Stream<? extends R>) c.stream() : (t instanceof Stream<?> s ? (Stream<? extends R>) s : Stream.of((R) t));
    }

    public static TranslatableComponent translateEnum(Enum<?> anEnum)
    {
        return Helpers.translatable(getEnumTranslationKey(anEnum));
    }

    /**
     * Gets the translation key name for an enum. For instance, Metal.UNKNOWN would map to "tfc.enum.metal.unknown"
     */
    public static String getEnumTranslationKey(Enum<?> anEnum)
    {
        return getEnumTranslationKey(anEnum, anEnum.getDeclaringClass().getSimpleName());
    }

    /**
     * Use over invoking the constructor, as Mojang refactors this in 1.19
     */
    public static TranslatableComponent translatable(String key)
    {
        return new TranslatableComponent(key);
    }

    /**
     * Use over invoking the constructor, as Mojang refactors this in 1.19
     */
    public static TranslatableComponent translatable(String key, Object... args)
    {
        return new TranslatableComponent(key, args);
    }

    /**
     * Use over invoking the constructor, as Mojang refactors this in 1.19
     */
    public static TextComponent literal(String literalText)
    {
        return new TextComponent(literalText);
    }

    /**
     * Gets the translation key name for an enum, using a custom name instead of the enum class name
     */
    public static String getEnumTranslationKey(Enum<?> anEnum, String enumName)
    {
        return String.join(".", MOD_ID, "enum", enumName, anEnum.name()).toLowerCase(Locale.ROOT);
    }

    /**
     * Normally, one would just call {@link Level#isClientSide()}
     * HOWEVER
     * There exists a BIG HUGE PROBLEM in very specific scenarios with this
     * Since World's isClientSide() actually returns the isClientSide boolean, which is set AT THE END of the World constructor, many things may happen before this is set correctly. Mostly involving world generation.
     * At this point, THE CLIENT WORLD WILL RETURN {@code false} to {@link Level#isClientSide()}
     *
     * So, this does a roundabout check "is this instanceof ClientWorld or not" without classloading shenanigans.
     */
    public static boolean isClientSide(LevelReader world)
    {
        return world instanceof Level ? !(world instanceof ServerLevel) : world.isClientSide();
    }

    @Nullable
    @SuppressWarnings("deprecation")
    public static Level getUnsafeLevel(Object maybeLevel)
    {
        if (maybeLevel instanceof Level level)
        {
            return level; // Most obvious case, if we can directly cast up to level.
        }
        if (maybeLevel instanceof WorldGenRegion level)
        {
            return level.getLevel(); // Special case for world gen, when we can access the level unsafely
        }
        return null; // A modder has done a strange ass thing
    }

    public static BlockHitResult rayTracePlayer(Level level, Player player, ClipContext.Fluid mode)
    {
        return ItemProtectedAccessor.invokeGetPlayerPOVHitResult(level, player, mode);
    }

    @Deprecated
    public static void slowEntityInBlock(Entity entity, float factor, int fallDamageReduction)
    {
        final Vec3 motion = entity.getDeltaMovement();

        // Affect falling very slightly, and don't affect jumping
        entity.setDeltaMovement(motion.multiply(factor, motion.y < 0 ? 1 - 0.2f * (1 - factor) : 1, factor));
        if (entity.fallDistance > fallDamageReduction)
        {
            entity.causeFallDamage(entity.fallDistance - fallDamageReduction, 1.0f, DamageSource.FALL);
        }
        entity.fallDistance = 0;
    }

    public static BlockState copyProperties(BlockState copyTo, BlockState copyFrom)
    {
        for (Property<?> property : copyFrom.getProperties())
        {
            copyTo = copyProperty(copyTo, copyFrom, property);
        }
        return copyTo;
    }

    public static <T extends Comparable<T>> BlockState copyProperty(BlockState copyTo, BlockState copyFrom, Property<T> property)
    {
        return copyTo.hasProperty(property) ? copyTo.setValue(property, copyFrom.getValue(property)) : copyTo;
    }

    public static <T extends Comparable<T>> BlockState setProperty(BlockState state, Property<T> property, T value)
    {
        return state.hasProperty(property) ? state.setValue(property, value) : state;
    }

    public static <C extends Container, R extends Recipe<C>> Map<ResourceLocation, R> getRecipes(Level level, Supplier<RecipeType<R>> type)
    {
        return getRecipes(level.getRecipeManager(), type);
    }

    @SuppressWarnings("unchecked")
    public static <C extends Container, R extends Recipe<C>> Map<ResourceLocation, R> getRecipes(RecipeManager recipeManager, Supplier<RecipeType<R>> type)
    {
        return (Map<ResourceLocation, R>) ((RecipeManagerAccessor) recipeManager).invoke$byType(type.get());
    }

    public static RecipeManager getUnsafeRecipeManager()
    {
        final MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null)
        {
            return server.getRecipeManager();
        }

        try
        {
            final Level level = ClientHelpers.getLevel();
            if (level != null)
            {
                return level.getRecipeManager();
            }
        }
        catch (Throwable t)
        {
            LOGGER.info("^ This is fine - No client or server recipe manager present upon initial resource reload on physical server");
        }

        if (CACHED_RECIPE_MANAGER != null)
        {
            LOGGER.info("Successfully captured server recipe manager");
            return CACHED_RECIPE_MANAGER;
        }

        throw new IllegalStateException("No recipe manager was present - tried server, client, and captured value. This will cause problems!");
    }

    public static void setCachedRecipeManager(RecipeManager manager)
    {
        CACHED_RECIPE_MANAGER = manager;
    }

    public static ItemStack damageCraftingItem(ItemStack stack, int amount)
    {
        Player player = ForgeHooks.getCraftingPlayer(); // Mods may not set this properly
        if (player != null)
        {
            stack.hurtAndBreak(amount, player, entity -> {});
        }
        else
        {
            damageItem(stack, amount);
        }
        return stack;
    }

    /**
     * A replacement for {@link ItemStack#hurtAndBreak(int, LivingEntity, Consumer)} when an entity is not present
     */
    public static void damageItem(ItemStack stack, int amount)
    {
        // There's no player here, so we can't safely do anything.
        //amount = stack.getItem().damageItem(stack, amount, null, e -> {});
        if (stack.hurt(amount, new Random(), null))
        {
            stack.shrink(1);
            stack.setDamageValue(0);
        }
    }

    /**
     * {@link Level#removeBlock(BlockPos, boolean)} but with all flags available.
     */
    public static void removeBlock(LevelAccessor level, BlockPos pos, int flags)
    {
        level.setBlock(pos, level.getFluidState(pos).createLegacyBlock(), flags);
    }

    public static ItemStack copyWithSize(ItemStack stack, int size)
    {
        final ItemStack copy = stack.copy();
        copy.setCount(size);
        return copy;
    }

    /**
     * Iterate through all slots in an {@code inventory}.
     */
    public static Iterable<ItemStack> iterate(IItemHandler inventory)
    {
        return iterate(inventory, 0, inventory.getSlots());
    }

    public static void tickInfestation(Level level, BlockPos pos, int infestation, @Nullable Player player)
    {
        infestation = Mth.clamp(infestation, 0, 5);
        if (infestation == 0)
        {
            return;
        }
        if (level.random.nextInt(120 - (20 * infestation)) == 0)
        {
            final float chanceBasedOnCurrentPests = 1f - Mth.clampedMap(level.getEntitiesOfClass(Pest.class, new AABB(pos).inflate(40d)).size(), 0, 8, 0f, 1f);
            if (level.random.nextFloat() > chanceBasedOnCurrentPests)
            {
                return;
            }
            Helpers.getRandomElement(ForgeRegistries.ENTITIES, TFCTags.Entities.PESTS, level.random).ifPresent(type -> {
                final Entity entity = type.create(level);
                if (entity instanceof PathfinderMob mob && level instanceof ServerLevel serverLevel)
                {
                    mob.moveTo(new Vec3(pos.getX(), pos.getY(), pos.getZ()));
                    final Vec3 checkPos = LandRandomPos.getPos(mob, 15, 5);
                    if (checkPos != null)
                    {
                        mob.moveTo(checkPos);
                        mob.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(new BlockPos(checkPos)), MobSpawnType.EVENT, null, null);
                        serverLevel.addFreshEntity(mob);
                        if (mob instanceof Pest pest)
                        {
                            PestAi.setSmelledPos(pest, pos);
                        }
                        if (player != null)
                        {
                            player.displayClientMessage(Helpers.translatable("tfc.tooltip.infestation"), true);
                        }
                    }
                }
            });
        }

    }

    /**
     * @return 0 (well-burdened), 1 (exhausted), 2 (overburdened, add potion effect)
     */
    public static int countOverburdened(Container container)
    {
        int count = 0;
        for (int i = 0; i < container.getContainerSize(); i++)
        {
            final ItemStack stack = container.getItem(i);
            if (!stack.isEmpty())
            {
                IItemSize size = ItemSizeManager.get(stack);
                if (size.getWeight(stack) == Weight.VERY_HEAVY && size.getSize(stack) == Size.HUGE)
                {
                    count++;
                    if (count == 2)
                    {
                        return count;
                    }
                }
            }
        }
        return count;
    }

    public static MobEffectInstance getOverburdened(boolean visible)
    {
        return new MobEffectInstance(TFCEffects.OVERBURDENED.get(), 25, 0, false, visible);
    }

    public static MobEffectInstance getExhausted(boolean visible)
    {
        return new MobEffectInstance(TFCEffects.EXHAUSTED.get(), 25, 0, false, visible);
    }

    /**
     * Iterate through all slots in an {@code inventory}.
     */
    public static Iterable<ItemStack> iterate(IItemHandler inventory, int startSlotInclusive, int endSlotExclusive)
    {
        return () -> new Iterator<>()
        {
            private int slot = startSlotInclusive;

            @Override
            public boolean hasNext()
            {
                return slot < endSlotExclusive;
            }

            @Override
            public ItemStack next()
            {
                return inventory.getStackInSlot(slot++);
            }

            @Override
            public void remove()
            {
                Helpers.removeStack(inventory, slot - 1); // Remove the previous slot = previous call to next()
            }
        };
    }

    public static ListTag writeItemStacksToNbt(List<ItemStack> stacks)
    {
        final ListTag list = new ListTag();
        for (final ItemStack stack : stacks)
        {
            list.add(stack.save(new CompoundTag()));
        }
        return list;
    }

    public static ListTag writeItemStacksToNbt(@Nullable ItemStack[] stacks)
    {
        final ListTag list = new ListTag();
        for (final ItemStack stack : stacks)
        {
            list.add((stack == null ? ItemStack.EMPTY : stack).save(new CompoundTag()));
        }
        return list;
    }

    public static void readItemStacksFromNbt(List<ItemStack> stacks, ListTag list)
    {
        stacks.clear();
        for (int i = 0; i < list.size(); i++)
        {
            stacks.add(ItemStack.of(list.getCompound(i)));
        }
    }

    public static void readItemStacksFromNbt(ItemStack[] stacks, ListTag list)
    {
        assert list.size() == stacks.length;
        for (int i = 0; i < list.size(); i++)
        {
            stacks[i] = ItemStack.of(list.getCompound(i));
        }
    }

    /**
     * Given a theoretical item stack, of count {@code totalCount}, splits it into optimally sized stacks, up to the stack size limit and feeds these new stacks to {@code consumer}
     */
    public static void consumeInStackSizeIncrements(ItemStack stack, int totalCount, Consumer<ItemStack> consumer)
    {
        while (totalCount > 0)
        {
            final ItemStack splitStack = stack.copy();
            final int splitCount = Math.min(splitStack.getMaxStackSize(), totalCount);
            splitStack.setCount(splitCount);
            totalCount -= splitCount;
            consumer.accept(splitStack);
        }
    }

    public static void gatherAndConsumeItems(Level level, AABB bounds, IItemHandler inventory, int minSlotInclusive, int maxSlotInclusive)
    {
        gatherAndConsumeItems(level.getEntitiesOfClass(ItemEntity.class, bounds, EntitySelector.ENTITY_STILL_ALIVE), inventory, minSlotInclusive, maxSlotInclusive);
    }

    public static void gatherAndConsumeItems(Collection<ItemEntity> items, IItemHandler inventory, int minSlotInclusive, int maxSlotInclusive)
    {
        final List<ItemEntity> availableItemEntities = new ArrayList<>();
        int availableItems = 0;
        for (ItemEntity entity : items)
        {
            if (inventory.isItemValid(maxSlotInclusive, entity.getItem()))
            {
                availableItems += entity.getItem().getCount();
                availableItemEntities.add(entity);
            }
        }
        Helpers.safelyConsumeItemsFromEntitiesIndividually(availableItemEntities, availableItems, item -> Helpers.insertSlots(inventory, item, minSlotInclusive, 1 + maxSlotInclusive).isEmpty());
    }

    /**
     * Removes / Consumes item entities from a list up to a maximum number of items (taking into account the count of each item)
     * Passes each item stack, with stack size = 1, to the provided consumer
     * This method expects the consumption to always succeed (such as when simply adding the items to a list)
     */
    public static void consumeItemsFromEntitiesIndividually(Collection<ItemEntity> entities, int maximum, Consumer<ItemStack> consumer)
    {
        int consumed = 0;
        for (ItemEntity entity : entities)
        {
            final ItemStack stack = entity.getItem();
            while (consumed < maximum && !stack.isEmpty())
            {
                consumer.accept(stack.split(1));
                consumed++;
                if (stack.isEmpty())
                {
                    entity.discard();
                }
            }
        }
    }

    /**
     * Removes / Consumes item entities from a list up to a maximum number of items (taking into account the count of each item)
     * Passes each item stack, with stack size = 1, to the provided consumer
     *
     * @param consumer consumes each stack. Returns {@code true} if the stack was consumed, and {@code false} if it failed, in which case we stop trying.
     */
    public static void safelyConsumeItemsFromEntitiesIndividually(Collection<ItemEntity> entities, int maximum, Predicate<ItemStack> consumer)
    {
        int consumed = 0;
        for (ItemEntity entity : entities)
        {
            final ItemStack stack = entity.getItem();
            while (consumed < maximum && !stack.isEmpty())
            {
                final ItemStack offer = Helpers.copyWithSize(stack, 1);
                if (!consumer.test(offer))
                {
                    return;
                }
                consumed++;
                stack.shrink(1);
                if (stack.isEmpty())
                {
                    entity.discard();
                }
            }
        }
    }

    /**
     * Remove and return a stack in {@code slot}, replacing it with empty.
     */
    public static ItemStack removeStack(IItemHandler inventory, int slot)
    {
        return inventory.extractItem(slot, Integer.MAX_VALUE, false);
    }

    /**
     * Inserts {@code stack} into the inventory ignoring any difference in creation date.
     *
     * @param stack The stack to insert. Will be modified (and returned).
     * @return The remainder of {@code stack} after inserting.
     */
    public static ItemStack mergeInsertStack(IItemHandler inventory, int slot, ItemStack stack)
    {
        final ItemStack existing = removeStack(inventory, slot);
        final ItemStack remainder = stack.copy();
        final ItemStack merged = FoodCapability.mergeItemStacks(existing, remainder); // stack is now remainder
        inventory.insertItem(slot, merged, false); // Should be no remainder because we removed it all to start with
        return remainder;
    }

    /**
     * Attempts to insert a stack across all slots of an item handler
     *
     * @param stack The stack to be inserted
     * @return The remainder after the stack is inserted, if any
     */
    public static ItemStack insertAllSlots(IItemHandler inventory, ItemStack stack)
    {
        return insertSlots(inventory, stack, 0, inventory.getSlots());
    }

    public static ItemStack insertSlots(IItemHandler inventory, ItemStack stack, int slotStartInclusive, int slotEndExclusive)
    {
        for (int slot = slotStartInclusive; slot < slotEndExclusive; slot++)
        {
            stack = inventory.insertItem(slot, stack, false);
            if (stack.isEmpty())
            {
                return ItemStack.EMPTY;
            }
        }
        return stack;
    }

    /**
     * This WILL NOT MUTATE the stack you give it. Do your own handling!
     */
    public static boolean insertOne(Level level, BlockPos pos, BlockEntityType<? extends BlockEntity> type, ItemStack stack)
    {
        return insertOne(level.getBlockEntity(pos, type), stack);
    }

    public static boolean insertOne(Optional<? extends BlockEntity> blockEntity, ItemStack stack)
    {
        ItemStack toInsert = stack.copy();
        return blockEntity.flatMap(entity -> entity.getCapability(Capabilities.ITEM).resolve())
            .map(cap -> {
                toInsert.setCount(1);
                return insertAllSlots(cap, toInsert).isEmpty();
            }).orElse(false);
    }

    /**
     * Extracts all items of an {@code inventory}, and copies them into a list, indexed with the slots.
     *
     * @see #insertAllItems(IItemHandlerModifiable, NonNullList)
     */
    public static NonNullList<ItemStack> extractAllItems(IItemHandlerModifiable inventory)
    {
        NonNullList<ItemStack> saved = NonNullList.withSize(inventory.getSlots(), ItemStack.EMPTY);
        for (int slot = 0; slot < inventory.getSlots(); slot++)
        {
            saved.set(slot, inventory.getStackInSlot(slot).copy());
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
        return saved;
    }

    /**
     * Given a saved copy of an inventory {@code from}, inserts each stack into the provided {@code inventory}, if possible.
     *
     * @see #extractAllItems(IItemHandlerModifiable)
     */
    public static void insertAllItems(IItemHandlerModifiable inventory, NonNullList<ItemStack> from)
    {
        // We allow the list to have a different size than the new inventory
        for (int slot = 0; slot < Math.min(inventory.getSlots(), from.size()); slot++)
        {
            inventory.setStackInSlot(slot, from.get(slot));
        }
    }

    /**
     * @return {@code true} if every slot in the provided inventory is empty.
     */
    public static boolean isEmpty(IItemHandler inventory)
    {
        for (int i = 0; i < inventory.getSlots(); i++)
        {
            if (!inventory.getStackInSlot(i).isEmpty())
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Attempts to spread fire, in a half dome of max {@code radius}. Larger radii check more blocks.
     */
    public static void fireSpreaderTick(ServerLevel level, BlockPos pos, Random random, int radius)
    {
        if (level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK))
        {
            final int x = pos.getX();
            final int y = pos.getY();
            final int z = pos.getZ();
            final int radiusSquared = radius * radius;
            for (BlockPos checkPos : BlockPos.randomBetweenClosed(random, radius * 2, x - radius, y, z - radius, x + radius, y + radius, z + radius))
            {
                if (checkPos.distSqr(pos) < radiusSquared && level.isLoaded(checkPos))
                {
                    final BlockState state = level.getBlockState(checkPos);
                    if (state.isAir())
                    {
                        if (hasFlammableNeighbours(level, checkPos))
                        {
                            level.setBlockAndUpdate(checkPos, Blocks.FIRE.defaultBlockState());
                            return;
                        }
                    }
                }
            }
        }
    }

    private static boolean hasFlammableNeighbours(LevelReader level, BlockPos pos)
    {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (Direction direction : Helpers.DIRECTIONS)
        {
            mutable.setWithOffset(pos, direction);
            if (level.getBlockState(mutable).isFlammable(level, mutable, direction.getOpposite()))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Copied from {@link Level#destroyBlock(BlockPos, boolean, Entity, int)}
     * Allows the loot context to be modified
     */
    public static void destroyBlockAndDropBlocksManually(ServerLevel level, BlockPos pos, Consumer<LootContext.Builder> builder)
    {
        BlockState state = level.getBlockState(pos);
        if (!state.isAir())
        {
            FluidState fluidstate = level.getFluidState(pos);
            if (!(state.getBlock() instanceof BaseFireBlock))
            {
                level.levelEvent(2001, pos, Block.getId(state));
            }
            dropWithContext(level, state, pos, builder, true);
            level.setBlock(pos, fluidstate.createLegacyBlock(), 3, 512);
        }
    }

    public static void dropWithContext(ServerLevel level, BlockState state, BlockPos pos, Consumer<LootContext.Builder> builder, boolean randomized)
    {
        BlockEntity tileEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;

        // Copied from Block.getDrops()
        LootContext.Builder lootContext = new LootContext.Builder(level)
            .withRandom(level.random)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
            .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
            .withOptionalParameter(LootContextParams.THIS_ENTITY, null)
            .withOptionalParameter(LootContextParams.BLOCK_ENTITY, tileEntity);
        builder.accept(lootContext);
        state.getDrops(lootContext).forEach(stackToSpawn -> {
            if (randomized)
            {
                Block.popResource(level, pos, stackToSpawn);
            }
            else
            {
                spawnDropsAtExactCenter(level, pos, stackToSpawn);
            }
        });
        state.spawnAfterBreak(level, pos, ItemStack.EMPTY);
    }

    /**
     * {@link Block#popResource(Level, BlockPos, ItemStack)} but without randomness as to the velocity and position.
     */
    public static void spawnDropsAtExactCenter(Level level, BlockPos pos, ItemStack stack)
    {
        if (!level.isClientSide && !stack.isEmpty() && level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS) && !level.restoringBlockSnapshots)
        {
            ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, stack, 0D, 0D, 0D);
            entity.setDefaultPickUpDelay();
            level.addFreshEntity(entity);
        }
    }

    public static void playSound(Level level, BlockPos pos, SoundEvent sound)
    {
        Random rand = level.getRandom();
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F + rand.nextFloat(), rand.nextFloat() + 0.7F + 0.3F);
    }

    public static boolean spawnItem(Level level, Vec3 pos, ItemStack stack)
    {
        return level.addFreshEntity(new ItemEntity(level, pos.x(), pos.y(), pos.z(), stack));
    }

    public static boolean spawnItem(Level level, BlockPos pos, ItemStack stack, double yOffset)
    {
        return level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5D, pos.getY() + yOffset, pos.getZ() + 0.5D, stack));
    }

    public static boolean spawnItem(Level level, BlockPos pos, ItemStack stack)
    {
        return spawnItem(level, pos, stack, 0.5D);
    }

    public static FluidStack mergeOutputFluidIntoSlot(IItemHandlerModifiable inventory, FluidStack fluidStack, float temperature, int slot)
    {
        if (!fluidStack.isEmpty())
        {
            final ItemStack mergeStack = inventory.getStackInSlot(slot);
            return mergeStack.getCapability(Capabilities.FLUID).map(fluidCap -> {
                int filled = fluidCap.fill(fluidStack, IFluidHandler.FluidAction.EXECUTE);
                if (filled > 0)
                {
                    mergeStack.getCapability(HeatCapability.CAPABILITY).ifPresent(heatCap -> heatCap.setTemperature(temperature));
                }
                FluidStack remainder = fluidStack.copy();
                remainder.shrink(filled);
                return remainder;
            }).orElse(fluidStack);
        }
        return FluidStack.EMPTY;
    }

    public static <E> void encodeArray(FriendlyByteBuf buffer, E[] array, BiConsumer<E, FriendlyByteBuf> encoder)
    {
        buffer.writeVarInt(array.length);
        for (E e : array)
        {
            encoder.accept(e, buffer);
        }
    }

    public static <E> E[] decodeArray(FriendlyByteBuf buffer, IntFunction<E[]> arrayCtor, Function<FriendlyByteBuf, E> decoder)
    {
        final int size = buffer.readVarInt();
        final E[] array = arrayCtor.apply(size);
        for (int i = 0; i < size; i++)
        {
            array[i] = decoder.apply(buffer);
        }
        return array;
    }

    public static <E, C extends Collection<E>> C decodeAll(FriendlyByteBuf buffer, C collection, Function<FriendlyByteBuf, E> decoder)
    {
        final int size = buffer.readVarInt();
        for (int i = 0; i < size; i++)
        {
            collection.add(decoder.apply(buffer));
        }
        return collection;
    }

    public static <E, C extends Collection<E>> void encodeAll(FriendlyByteBuf buffer, C collection, BiConsumer<E, FriendlyByteBuf> encoder)
    {
        buffer.writeVarInt(collection.size());
        for (E e : collection)
        {
            encoder.accept(e, buffer);
        }
    }

    public static <T> void encodeNullable(@Nullable T instance, FriendlyByteBuf buffer, BiConsumer<T, FriendlyByteBuf> encoder)
    {
        if (instance != null)
        {
            buffer.writeBoolean(true);
            encoder.accept(instance, buffer);
        }
        else
        {
            buffer.writeBoolean(false);
        }
    }

    @Nullable
    public static <T> T decodeNullable(FriendlyByteBuf buffer, Function<FriendlyByteBuf, T> decoder)
    {
        if (buffer.readBoolean())
        {
            return decoder.apply(buffer);
        }
        return null;
    }

    /**
     * @see net.minecraft.core.QuartPos#toBlock(int)
     */
    public static BlockPos quartToBlock(int x, int y, int z)
    {
        return new BlockPos(x << 2, y << 2, z << 2);
    }

    /**
     * Rotates a VoxelShape 90 degrees. Assumes that the input facing is NORTH.
     */
    public static VoxelShape rotateShape(Direction direction, double x1, double y1, double z1, double x2, double y2, double z2)
    {
        return switch (direction)
            {
                case NORTH -> Block.box(x1, y1, z1, x2, y2, z2);
                case EAST -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2);
                case SOUTH -> Block.box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
                case WEST -> Block.box(z1, y1, 16 - x2, z2, y2, 16 - x1);
                default -> throw new IllegalArgumentException("Not horizontal!");
            };
    }

    /**
     * Follows indexes for Direction#get2DDataValue()
     */
    public static VoxelShape[] computeHorizontalShapes(Function<Direction, VoxelShape> shapeGetter)
    {
        return new VoxelShape[] {shapeGetter.apply(Direction.SOUTH), shapeGetter.apply(Direction.WEST), shapeGetter.apply(Direction.NORTH), shapeGetter.apply(Direction.EAST)};
    }

    /**
     * Select N unique elements from a list, without having to shuffle the whole list.
     * This involves moving the selected elements to the end of the list. Note: this method will mutate the passed in list!
     * From <a href="https://stackoverflow.com/questions/4702036/take-n-random-elements-from-a-liste">Stack Overflow</a>
     */
    public static <T> List<T> uniqueRandomSample(List<T> list, int n, Random r)
    {
        final int length = list.size();
        if (length < n)
        {
            throw new IllegalArgumentException("Cannot select n=" + n + " from a list of size = " + length);
        }
        for (int i = length - 1; i >= length - n; --i)
        {
            Collections.swap(list, i, r.nextInt(i + 1));
        }
        return list.subList(length - n, length);
    }

    public static <T> void insertBefore(List<? super T> list, T element, T before)
    {
        final int index = list.indexOf(before);
        if (index == -1)
        {
            list.add(element); // Element not found, so just insert at the end
        }
        else
        {
            list.add(index, element); // Insert at the target location, shifts the target forwards
        }
    }

    public static <T extends IForgeRegistryEntry<T>> List<T> getAllTagValues(TagKey<T> tag, IForgeRegistry<T> registry)
    {
        return streamAllTagValues(tag, registry).toList();
    }

    public static <T extends IForgeRegistryEntry<T>> Stream<T> streamAllTagValues(TagKey<T> tag, IForgeRegistry<T> registry)
    {
        return Objects.requireNonNull(registry.tags()).getTag(tag).stream();
    }

    /**
     * For when you want to ignore every possible safety measure in front of you
     */
    @SuppressWarnings("unchecked")
    public static <T> T uncheck(Callable<?> action)
    {
        try
        {
            return (T) action.call();
        }
        catch (Exception e)
        {
            return throwAsUnchecked(e);
        }
    }

    /**
     * Logs a warning and a stacktrace when called from the client thread, heuristically. Used for debugging, and indicates a programming error.
     */
    public static void warnWhenCalledFromClientThread()
    {
        if (ASSERTIONS_ENABLED && EffectiveSide.get().isClient())
        {
            LOGGER.warn("This method should not be called from client thread, this is a bug!", new RuntimeException("Stacktrace"));
        }
    }

    // Math Functions
    // Some are duplicated from Mth, but kept here as they might have slightly different parameter order or names

    /**
     * Linearly interpolates between [min, max].
     */
    public static float lerp(float delta, float min, float max)
    {
        return min + (max - min) * delta;
    }

    /**
     * Linearly interpolates between four values on a unit square.
     */
    public static float lerp4(float value00, float value01, float value10, float value11, float delta0, float delta1)
    {
        final float value0 = lerp(delta1, value00, value01);
        final float value1 = lerp(delta1, value10, value11);
        return lerp(delta0, value0, value1);
    }

    /**
     * @return A t = inverseLerp(value, min, max) s.t. lerp(t, min, max) = value;
     */
    public static float inverseLerp(float value, float min, float max)
    {
        return (value - min) / (max - min);
    }

    public static int hash(long salt, BlockPos pos)
    {
        return hash(salt, pos.getX(), pos.getY(), pos.getZ());
    }

    public static int hash(long salt, int x, int y, int z)
    {
        long hash = salt ^ ((long) x * PRIME_X) ^ ((long) y * PRIME_Y) ^ z;
        hash *= 0x27d4eb2d;
        return (int) hash;
    }

    public static RandomSource fork(Random random)
    {
        return new XoroshiroRandomSource(random.nextLong(), random.nextLong());
    }

    /**
     * todo: remove in 1.19. Borrowed for vanilla animation operability.
     * <a href="https://en.wikipedia.org/wiki/Centripetal_Catmull%E2%80%93Rom_spline">https://en.wikipedia.org/wiki/Centripetal_Catmull%E2%80%93Rom_spline</a>>
     */
    public static float catMullRomSpline(float lerp, float lowAnchor, float start, float end, float endAnchor)
    {
        return 0.5F * (2F * start + (end - lowAnchor) * lerp + (2F * lowAnchor - 5F * start + 4F * end - endAnchor) * lerp * lerp + (3F * start - lowAnchor - 3F * end + endAnchor) * lerp * lerp * lerp);
    }

    /**
     * A triangle function, with input {@code value} and parameters {@code amplitude, midpoint, frequency}.
     * A period T = 1 / frequency, with a sinusoidal shape. triangle(0) = midpoint, with triangle(+/-1 / (4 * frequency)) = the first peak.
     */
    public static float triangle(float amplitude, float midpoint, float frequency, float value)
    {
        return midpoint + amplitude * (Math.abs(4f * frequency * value + 1f - 4f * Mth.floor(frequency * value + 0.75f)) - 1f);
    }

    /**
     * @return A random integer, uniformly distributed in the range [min, max).
     */
    public static int uniform(RandomSource random, int min, int max)
    {
        return min == max ? min : min + random.nextInt(max - min);
    }

    public static int uniform(Random random, int min, int max)
    {
        return min == max ? min : min + random.nextInt(max - min);
    }

    /**
     * @return A random float, uniformly distributed in the range [min, max).
     */
    public static float uniform(RandomSource random, float min, float max)
    {
        return random.nextFloat() * (max - min) + min;
    }

    public static float uniform(Random random, float min, float max)
    {
        return random.nextFloat() * (max - min) + min;
    }

    /**
     * @return A random float, distributed around [-1, 1] in a triangle distribution X ~ pdf(t) = 1 - |t|.
     */
    public static float triangle(Random random)
    {
        return random.nextFloat() - random.nextFloat() * 0.5f;
    }

    /**
     * @return A random integer, distributed around (-range, range) in a triangle distribution X ~ pmf(t) ~= (1 - |t|)
     */
    public static int triangle(Random random, int range)
    {
        return random.nextInt(range) - random.nextInt(range);
    }

    /**
     * @return A random float, distributed around [-delta, delta] in a triangle distribution X ~ pdf(t) ~= (1 - |t|)
     */
    public static float triangle(RandomSource random, float delta)
    {
        return (random.nextFloat() - random.nextFloat()) * delta;
    }

    public static float easeInOutCubic(float x)
    {
        return x < 0.5f ? 4 * x * x * x : 1 - cube(-2 * x + 2) / 2;
    }

    public static float cube(float x)
    {
        return x * x * x;
    }

    /**
     * Returns ceil(num / div)
     *
     * @see Math#floorDiv(int, int)
     */
    public static int ceilDiv(int num, int div)
    {
        return (num + div - 1) / div;
    }

    // todo: 1.19. inline and remove these
    public static void openScreen(ServerPlayer player, MenuProvider containerSupplier)
    {
        NetworkHooks.openGui(player, containerSupplier);
    }

    public static void openScreen(ServerPlayer player, MenuProvider containerSupplier, BlockPos pos)
    {
        NetworkHooks.openGui(player, containerSupplier, pos);
    }

    public static void openScreen(ServerPlayer player, MenuProvider containerSupplier, Consumer<FriendlyByteBuf> extraDataWriter)
    {
        NetworkHooks.openGui(player, containerSupplier, extraDataWriter);
    }

    /**
     * Based on {@link net.minecraft.world.entity.Mob#maybeDisableShield} without hardcoding and whatever
     */
    public static void maybeDisableShield(ItemStack axe, ItemStack shield, Player player, LivingEntity attacker)
    {
        if (!axe.isEmpty() && !shield.isEmpty() && axe.canDisableShield(shield, player, attacker))
        {
            final float vanillaDisableChance = 0.25F + EnchantmentHelper.getBlockEfficiency(attacker) * 0.05F;
            float chanceToDisable = vanillaDisableChance;
            final Item shieldItem = shield.getItem();
            if (shieldItem.equals(Items.SHIELD))
            {
                chanceToDisable = 1f; // deliberately making vanilla shields worse.
            }
            else if (shieldItem instanceof TFCShieldItem tfcShield)
            {
                chanceToDisable = (0.25f * vanillaDisableChance) + (0.75f * tfcShield.getDisableChance());
            }
            if (attacker.getRandom().nextFloat() < chanceToDisable)
            {
                player.getCooldowns().addCooldown(shieldItem, 100);
                attacker.level.broadcastEntityEvent(player, (byte) 30);
            }
        }
    }

    /**
     * Checks the existence of a <a href="https://en.wikipedia.org/wiki/Perfect_matching">perfect matching</a> of a <a href="https://en.wikipedia.org/wiki/Bipartite_graph">bipartite graph</a>.
     * The graph is interpreted as the matches between the set of inputs, and the set of tests.
     * This algorithm computes the <a href="https://en.wikipedia.org/wiki/Edmonds_matrix">Edmonds Matrix</a> of the graph, which has the property that the determinant is identically zero iff the graph does not admit a perfect matching.
     */
    public static <T> boolean perfectMatchExists(List<T> inputs, List<? extends Predicate<T>> tests)
    {
        if (inputs.size() != tests.size())
        {
            return false;
        }
        final int size = inputs.size();
        final boolean[][] matrices = new boolean[size][];
        for (int i = 0; i < size; i++)
        {
            matrices[i] = new boolean[(size + 1) * (size + 1)];
        }
        final boolean[] matrix = matrices[size - 1];
        for (int i = 0; i < size; i++)
        {
            for (int j = 0; j < size; j++)
            {
                matrix[i + size * j] = tests.get(i).test(inputs.get(j));
            }
        }
        return perfectMatchDet(matrices, size);
    }

    /**
     * Adds a tooltip based on an inventory, listing out the items inside.
     * Modified from {@link net.minecraft.world.level.block.ShulkerBoxBlock#appendHoverText(ItemStack, BlockGetter, List, TooltipFlag)}
     */
    public static void addInventoryTooltipInfo(IItemHandler inventory, List<Component> tooltips)
    {
        int maximumItems = 0, totalItems = 0;
        for (ItemStack stack : iterate(inventory))
        {
            if (!stack.isEmpty())
            {
                ++totalItems;
                if (maximumItems <= 4)
                {
                    ++maximumItems;
                    tooltips.add(stack.getHoverName().copy()
                        .append(" x")
                        .append(String.valueOf(stack.getCount())));
                }
            }
        }

        if (totalItems - maximumItems > 0)
        {
            tooltips.add(Helpers.translatable("container.shulkerBox.more", totalItems - maximumItems).withStyle(ChatFormatting.ITALIC));
        }
    }

    /**
     * Adds a tooltip based on a single fluid stack
     */
    @Deprecated(forRemoval = true)
    public static void addFluidStackTooltipInfo(FluidStack fluid, List<Component> tooltips)
    {
        if (!fluid.isEmpty())
        {
            tooltips.add(Tooltips.fluidUnitsOf(fluid));
        }
    }

    public static boolean isItem(ItemStack first, Item second)
    {
        return first.is(second);
    }

    public static boolean isItem(ItemStack stack, TagKey<Item> tag)
    {
        return checkTag(ForgeRegistries.ITEMS, stack.getItem(), tag);
    }

    public static boolean isItem(Item item, TagKey<Item> tag)
    {
        return checkTag(ForgeRegistries.ITEMS, item, tag);
    }

    public static boolean isBlock(BlockState first, Block second)
    {
        return first.is(second);
    }

    public static boolean isBlock(BlockState state, TagKey<Block> tag)
    {
        return isBlock(state.getBlock(), tag);
    }

    public static boolean isBlock(Block block, TagKey<Block> tag)
    {
        return checkTag(ForgeRegistries.BLOCKS, block, tag);
    }

    public static boolean isFluid(FluidState state, TagKey<Fluid> tag)
    {
        return checkTag(ForgeRegistries.FLUIDS, state.getType(), tag);
    }

    public static boolean isFluid(Fluid first, TagKey<Fluid> second)
    {
        return checkTag(ForgeRegistries.FLUIDS, first, second);
    }

    public static boolean isFluid(FluidState first, Fluid second)
    {
        return first.is(second);
    }

    public static boolean isEntity(Entity entity, TagKey<EntityType<?>> tag)
    {
        return checkTag(ForgeRegistries.ENTITIES, entity.getType(), tag);
    }

    public static boolean isEntity(EntityType<?> entity, TagKey<EntityType<?>> tag)
    {
        return checkTag(ForgeRegistries.ENTITIES, entity, tag);
    }

    public static <T extends IForgeRegistryEntry<T>> Holder<T> getHolder(IForgeRegistry<T> registry, T object)
    {
        return registry.getHolder(object).orElseThrow();
    }

    public static <T extends IForgeRegistryEntry<T>> boolean checkTag(IForgeRegistry<T> registry, T object, TagKey<T> tag)
    {
        return Objects.requireNonNull(registry.tags()).getTag(tag).contains(object);
    }

    public static <T extends IForgeRegistryEntry<T>> Optional<T> getRandomElement(IForgeRegistry<T> registry, TagKey<T> tag, Random random)
    {
        return Objects.requireNonNull(registry.tags()).getTag(tag).getRandomElement(random);
    }

    public static double sampleNoiseAndMapToRange(NormalNoise noise, double x, double y, double z, double min, double max)
    {
        return Mth.map(noise.getValue(x, y, z), -1, 1, min, max);
    }

    public static ResourceLocation animalTexture(String name)
    {
        return identifier("textures/entity/animal/" + name + ".png");
    }

    public static List<HolderSet<PlacedFeature>> flattenTopLevelMultipleFeature(BiomeGenerationSettings settings)
    {
        return settings.features()
            .stream()
            .map(set -> (HolderSet<PlacedFeature>) HolderSet.direct(set.stream()
                .flatMap(holder -> {
                    final ConfiguredFeature<?, ?> feature = holder.value().feature().value();
                    if (feature.feature() instanceof MultipleFeature && feature.config() instanceof SimpleRandomFeatureConfiguration features)
                    {
                        return features.features.stream();
                    }
                    return Stream.of(holder);
                })
                .toList()
            ))
            .toList();
    }

    /**
     * This exists to fix a horrible case of vanilla seeding, which led to noticeable issues of feature clustering.
     * The key issue was that features with a chance placement, applied sequentially, would appear to generate on the same chunk much more often than was expected.
     * This was then identified as the problem by the lovely KaptainWutax <3. The following is a excerpt / paraphrase from our conversation:
     *
     * So you're running setSeed(n), setSeed(n + 1) and setSeed(n + 2) on the 3 structure respectively.
     * And n is something we can compute given a chunk and seed.
     * setSeed applies an xor on the lowest 35 bits and assigns that value internally
     * But like, since your seeds are like 1 apart
     * Even after the xor they're at worst 1 apart
     * You can convince yourself of that quite easily
     * So now nextFloat() does seed = 25214903917 * seed + 11 and returns (seed >> 24) / 2^24
     * Sooo lets see what the actual difference in seeds are between your 2 features in the worst case:
     * a = 25214903917, b = 11
     * So a * (seed + 1) + b = a * seed + b + a
     * As you can see the internal seed only varies by "a" amount
     * Now we can measure the effect that big number has no the upper bits since the seed is shifted
     * 25214903917/2^24 = 1502.92539101839
     * And that's by how much the upper 24 bits will vary
     * The effect on the next float are 1502 / 2^24 = 8.95261764526367e-5
     * Blam, so the first nextFloat() between setSeed(n) and setSeed(n + 1) is that distance apart ^
     * Which as you can see... isn't that far from 0
     */
    public static void seedLargeFeatures(Random random, long baseSeed, int index, int decoration)
    {
        random.setSeed(baseSeed);
        final long seed = (index * random.nextLong() * 203704237L) ^ (decoration * random.nextLong() * 758031792L) ^ baseSeed;
        random.setSeed(seed);
    }

    /**
     * Used by {@link Helpers#perfectMatchExists(List, List)}
     * Computes a symbolic determinant
     */
    private static boolean perfectMatchDet(boolean[][] matrices, int size)
    {
        // matrix true = nonzero = matches
        final boolean[] matrix = matrices[size - 1];
        switch (size)
        {
            case 1:
                return matrix[0];
            case 2:
                return (matrix[0] && matrix[3]) || (matrix[1] && matrix[2]);
            default:
            {
                for (int c = 0; c < size; c++)
                {
                    if (matrix[c])
                    {
                        perfectMatchSub(matrices, size, c);
                        if (perfectMatchDet(matrices, size - 1))
                        {
                            return true;
                        }
                    }
                }
                return false;
            }
        }
    }

    /**
     * Used by {@link Helpers#perfectMatchExists(List, List)}
     * Computes the symbolic minor of a matrix by removing an arbitrary column.
     */
    private static void perfectMatchSub(boolean[][] matrices, int size, int dc)
    {
        final int subSize = size - 1;
        final boolean[] matrix = matrices[subSize], sub = matrices[subSize - 1];
        for (int c = 0; c < subSize; c++)
        {
            final int c0 = c + (c >= dc ? 1 : 0);
            for (int r = 0; r < subSize; r++)
            {
                sub[c + subSize * r] = matrix[c0 + size * (r + 1)];
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable, T> T throwAsUnchecked(Exception exception) throws E
    {
        throw (E) exception;
    }

    @SuppressWarnings({"AssertWithSideEffects", "ConstantConditions"})
    private static boolean detectAssertionsEnabled()
    {
        boolean enabled = false;
        assert enabled = true;
        return enabled;
    }

    /**
     * Detect if we are in a bootstrapped environment - one where transforming and many MC/Forge mechanics are not properly setup
     * This detects i.e. when running from /gradlew test, and some things have to be avoided (for instance, invoking Forge registry methods)
     */
    private static boolean detectBootstrapEnvironment()
    {
        return System.getProperty("forge.enabledGameTestNamespaces") == null && detectTestSourcesPresent();
    }

    /**
     * Detect if test sources are present, if we're running from a environment which includes TFC's test sources
     * This can happen through a gametest launch, TFC dev launch (since we include test sources), or through gradle test
     */
    private static boolean detectTestSourcesPresent()
    {
        try
        {
            Class.forName("net.dries007.tfc.TestMarker");
            return true;
        }
        catch (ClassNotFoundException e) { /* Guess not */ }
        return false;
    }

    static abstract class ItemProtectedAccessor extends Item
    {
        static BlockHitResult invokeGetPlayerPOVHitResult(Level level, Player player, ClipContext.Fluid mode)
        {
            return /* protected */ Item.getPlayerPOVHitResult(level, player, mode);
        }

        @SuppressWarnings("ConstantConditions")
        private ItemProtectedAccessor() {super(null);} // Never called
    }
}