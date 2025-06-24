package b100.minimap.render.block;

import b100.minimap.Minimap;
import net.minecraft.core.block.Block;
import net.minecraft.core.block.Blocks;

public class BlockRenderManager {
	
	public RenderType[] renderTypes = new RenderType[Blocks.blocksList.length];
	
	public BlockRenderManager(TileColors tileColors) {
		addDefaultColors();
	}
	
	public void addDefaultColors() {
		for(int i=0; i < renderTypes.length; i++) {
			renderTypes[i] = RenderType.OPAQUE;
		}
		
		renderTypes[0] = RenderType.INVISIBLE;
		
		Minimap.instance.minecraftHelper.setupBlockRenderTypes(this);
	}
	
	public void setRenderType(Block block, RenderType renderType) {
		renderTypes[block.id()] = renderType;
	}
	
	public RenderType getRenderType(int id) {
		return renderTypes[id];
	}
	
}
