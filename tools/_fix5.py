import io, sys

def patch(path, subs):
    s = io.open(path, encoding='utf-8').read()
    for old, new in subs:
        if old not in s:
            print('MISS in', path, ':')
            print(old[:300])
            sys.exit(1)
        s = s.replace(old, new)
    io.open(path, 'w', encoding='utf-8', newline='\n').write(s)
    print('ok', path)

BASE = 'src/main/java/dev/zcode/piston_diversified/'

# --- pickaxe piston defaults to a netherite pickaxe ---
patch(BASE + 'entity/PickaxePistonBlockEntity.java', [
 ('''    private ItemStack pickaxe = ItemStack.EMPTY;''',
  '''    private ItemStack pickaxe = defaultPickaxe();'''),
 ('''    public ItemStack getPickaxe() {
        return this.pickaxe;
    }''',
  '''    /** 创造栏取出的活塞默认带下界合金镐（用户 2026-10-05 反馈定稿）。 */
    public static ItemStack defaultPickaxe() {
        return new ItemStack(net.minecraft.world.item.Items.NETHERITE_PICKAXE);
    }

    public ItemStack getPickaxe() {
        return this.pickaxe;
    }'''),
])

patch(BASE + 'block/PickaxePistonBlock.java', [
 ('''    public PickaxePistonBlock(Properties properties) {
        super(false, properties);
        this.registerDefaultState(this.defaultBlockState().setValue(TOOL, PickaxeTool.DEFAULT));
    }''',
  '''    public PickaxePistonBlock(Properties properties) {
        super(false, properties);
        this.registerDefaultState(this.defaultBlockState().setValue(TOOL, PickaxeTool.NETHERITE));
    }'''),
 ('''    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof PickaxePistonBlockEntity be) {
            ItemStack pickaxe = PickaxeData.get(stack, level.registryAccess());
            if (!pickaxe.isEmpty()) {
                be.setPickaxe(pickaxe);
                if (state.hasProperty(TOOL)) {
                    PickaxeTool tool = PickaxeTool.of(pickaxe);
                    if (tool != null && state.getValue(TOOL) != tool) {
                        level.setBlock(pos, state.setValue(TOOL, tool), 2);
                    }
                }
            }
        }
    }''',
  '''    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof PickaxePistonBlockEntity be) {
            ItemStack pickaxe = PickaxeData.get(stack, level.registryAccess());
            if (pickaxe.isEmpty()) {
                pickaxe = PickaxePistonBlockEntity.defaultPickaxe(); // no carry data: keep the default
            }
            be.setPickaxe(pickaxe);
            PickaxeTool tool = PickaxeTool.of(pickaxe);
            if (tool != null && state.getValue(TOOL) != tool) {
                level.setBlock(pos, state.setValue(TOOL, tool), 2);
            }
        }
    }'''),
 # the dropped item must re-carry the (possibly default) pickaxe even when the stack had no data
 ('''        if (be instanceof PickaxePistonBlockEntity pickaxeBe) {
            // 1.19.4's loot builder hands out a ServerLevel already; newer ones a plain Level
            //? if <1.20.5 {
            PickaxeData.set(out, pickaxeBe.getPickaxe(), params.getLevel().registryAccess());
            //?} else {
            if (params.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                PickaxeData.set(out, pickaxeBe.getPickaxe(), serverLevel.registryAccess());
            }
            //?}
        }''',
  '''        if (be instanceof PickaxePistonBlockEntity pickaxeBe) {
            // 1.19.4's loot builder hands out a ServerLevel already; newer ones a plain Level
            //? if <1.20.5 {
            PickaxeData.set(out, pickaxeBe.getPickaxe(), ((net.minecraft.server.level.ServerLevel) params.getLevel()).registryAccess());
            //?} else {
            if (params.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                PickaxeData.set(out, pickaxeBe.getPickaxe(), serverLevel.registryAccess());
            }
            //?}
        }'''),
])

print('part 5 done')
