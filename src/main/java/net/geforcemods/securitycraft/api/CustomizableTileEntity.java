package net.geforcemods.securitycraft.api;

import net.fabricmc.fabric.api.util.NbtType;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.collection.DefaultedList;
//import net.minecraftforge.common.util.Constants;
import net.geforcemods.securitycraft.util.WorldUtils;

import java.util.ArrayList;

/**
 * Extend this class in your TileEntity to make it customizable. You will
 * be able to modify it with the various modules in SecurityCraft, and
 * have your block do different functions based on what modules are
 * inserted.
 *
 * @author Geforce
 */
public abstract class CustomizableTileEntity extends SecurityCraftTileEntity implements IModuleInventory, ICustomizable
{
	private boolean linkable = false;
	public ArrayList<LinkedBlock> linkedBlocks = new ArrayList<>();
	private ListTag nbtTagStorage = null;

	private DefaultedList<ItemStack> modules = DefaultedList.<ItemStack>ofSize(getMaxNumberOfModules(), ItemStack.EMPTY);

	public CustomizableTileEntity(BlockEntityType<?> type)
	{
		super(type);
	}

	@Override
	public void tick() {
		super.tick();

		if(hasWorld() && nbtTagStorage != null) {
			readLinkedBlocks(nbtTagStorage);
			sync();
			nbtTagStorage = null;
		}
	}

	@Override
	public void fromTag(BlockState state, CompoundTag tag)
	{
		super.fromTag(state, tag);

		modules = readModuleInventory(tag);
		readOptions(tag);

		if (tag.contains("linkable"))
			linkable = tag.getBoolean("linkable");

		if (linkable && tag.contains("linkedBlocks"))
		{
			if(!hasWorld()) {
				nbtTagStorage = tag.getList("linkedBlocks", NbtType.COMPOUND);
				return;
			}

			readLinkedBlocks(tag.getList("linkedBlocks", NbtType.COMPOUND));
		}
	}

	@Override
	public CompoundTag toTag(CompoundTag tag)
	{
		super.toTag(tag);

		writeModuleInventory(tag);
		writeOptions(tag);
		tag.putBoolean("linkable", linkable);

		if(linkable && hasWorld() && linkedBlocks.size() > 0) {
			ListTag tagList = new ListTag();

			WorldUtils.addScheduledTask(world, () -> {
				for(int i = linkedBlocks.size() - 1; i >= 0; i--)
				{
					LinkedBlock block = linkedBlocks.get(i);
					CompoundTag toAppend = new CompoundTag();

					if(block != null) {
						if(!block.validate(world)) {
							linkedBlocks.remove(i);
							continue;
						}

						toAppend.putString("blockName", block.blockName);
						toAppend.putInt("blockX", block.getX());
						toAppend.putInt("blockY", block.getY());
						toAppend.putInt("blockZ", block.getZ());
					}

					tagList.add(toAppend);
				}

				tag.put("linkedBlocks", tagList);
			});
		}

		return tag;
	}

	private void readLinkedBlocks(ListTag list) {
		if(!linkable) return;

		for(int i = 0; i < list.size(); i++) {
			String name = list.getCompound(i).getString("blockName");
			int x = list.getCompound(i).getInt("blockX");
			int y = list.getCompound(i).getInt("blockY");
			int z = list.getCompound(i).getInt("blockZ");

			LinkedBlock block = new LinkedBlock(name, x, y, z);
			if(hasWorld() && !block.validate(world)) {
				list.remove(i);
				continue;
			}

			if(!linkedBlocks.contains(block))
				link(this, block.asTileEntity(world));
		}
	}

	@Override
	public boolean hasCustomSCName() {
		return (getCustomSCName() != null && !getCustomSCName().getString().equals("name"));
	}

	@Override
	public void onTileEntityDestroyed() {
		if(linkable)
			for(LinkedBlock block : linkedBlocks)
				CustomizableTileEntity.unlink(block.asTileEntity(world), this);
	}

	@Override
	public BlockEntity getTileEntity()
	{
		return this;
	}

	@Override
	public DefaultedList<ItemStack> getInventory()
	{
		return modules;
	}

	/**
	 * Sets this TileEntity able to be "linked" with other blocks,
	 * and being able to do things between them. Call CustomizableSCTE.link()
	 * to link two blocks together.
	 */
	public CustomizableTileEntity linkable() {
		linkable = true;
		return this;
	}

	/**
	 * @return If this TileEntity is able to be linked with.
	 */
	public boolean canBeLinkedWith() {
		return linkable;
	}

	/**
	 * Links two blocks together. Calls onLinkedBlockAction()
	 * whenever certain events (found in {@link LinkedAction}) occur.
	 */
	public static void link(CustomizableTileEntity tileEntity1, CustomizableTileEntity tileEntity2) {
		if(!tileEntity1.linkable || !tileEntity2.linkable) return;
		if(isLinkedWith(tileEntity1, tileEntity2)) return;

		LinkedBlock block1 = new LinkedBlock(tileEntity1);
		LinkedBlock block2 = new LinkedBlock(tileEntity2);

		if(!tileEntity1.linkedBlocks.contains(block2))
			tileEntity1.linkedBlocks.add(block2);

		if(!tileEntity2.linkedBlocks.contains(block1))
			tileEntity2.linkedBlocks.add(block1);
	}

	/**
	 * Unlinks the second TileEntity from the first.
	 *
	 * @param tileEntity1 The TileEntity to unlink from
	 * @param tileEntity2 The TileEntity to unlink
	 */
	public static void unlink(CustomizableTileEntity tileEntity1, CustomizableTileEntity tileEntity2) {
		if(tileEntity1 == null || tileEntity2 == null) return;
		if(!tileEntity1.linkable || !tileEntity2.linkable) return;

		LinkedBlock block = new LinkedBlock(tileEntity2);

		if(tileEntity1.linkedBlocks.contains(block))
			tileEntity1.linkedBlocks.remove(block);
	}

	/**
	 * @return Are the two blocks linked together?
	 */
	public static boolean isLinkedWith(CustomizableTileEntity tileEntity1, CustomizableTileEntity tileEntity2) {
		if(!tileEntity1.linkable || !tileEntity2.linkable) return false;

		return tileEntity1.linkedBlocks.contains(new LinkedBlock(tileEntity2)) && tileEntity2.linkedBlocks.contains(new LinkedBlock(tileEntity1));
	}

	@Override
	public void onOptionChanged(Option<?> option) {
		createLinkedBlockAction(LinkedAction.OPTION_CHANGED, new Option[]{ option }, this);
	}

	/**
	 * Calls onLinkedBlockAction() for every block this TileEntity
	 * is linked to. <p>
	 *
	 * <b>NOTE:</b> Never use this method in onLinkedBlockAction(),
	 * use createLinkedBlockAction(EnumLinkedAction, Object[], ArrayList[CustomizableSCTE] instead.
	 *
	 * @param action The action that occurred
	 * @param parameters Action-specific parameters, see comments in {@link LinkedAction}
	 * @param excludedTE The CustomizableSCTE which called this method, prevents infinite loops.
	 */
	public void createLinkedBlockAction(LinkedAction action, Object[] parameters, CustomizableTileEntity excludedTE) {
		ArrayList<CustomizableTileEntity> list = new ArrayList<>();

		list.add(excludedTE);

		createLinkedBlockAction(action, parameters, list);
	}

	/**
	 * Calls onLinkedBlockAction() for every block this TileEntity
	 * is linked to.
	 *
	 * @param action The action that occurred
	 * @param parameters Action-specific parameters, see comments in {@link LinkedAction}
	 * @param excludedTEs CustomizableSCTEs that shouldn't have onLinkedBlockAction() called on them,
	 *        prevents infinite loops. Always add your TileEntity to the list whenever using this method
	 */
	public void createLinkedBlockAction(LinkedAction action, Object[] parameters, ArrayList<CustomizableTileEntity> excludedTEs) {
		if(!linkable) return;

		for(LinkedBlock block : linkedBlocks)
			if(excludedTEs.contains(block.asTileEntity(world)))
				continue;
			else {
				block.asTileEntity(world).onLinkedBlockAction(action, parameters, excludedTEs);
				block.asTileEntity(world).sync();
			}
	}

	/**
	 * Called whenever certain actions occur in blocks
	 * this TileEntity is linked to. See {@link LinkedAction}
	 * for parameter descriptions. <p>
	 *
	 * @param action The {@link LinkedAction} that occurred
	 * @param parameters Important variables related to the action
	 * @param excludedTEs CustomizableSCTEs that aren't going to have onLinkedBlockAction() called on them,
	 *        always add your TileEntity to the list if you're going to call createLinkedBlockAction() in this method to chain-link multiple blocks (i.e: like Laser Blocks)
	 */
	protected void onLinkedBlockAction(LinkedAction action, Object[] parameters, ArrayList<CustomizableTileEntity> excludedTEs) {}
}
