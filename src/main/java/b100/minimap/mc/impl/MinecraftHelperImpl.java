package b100.minimap.mc.impl;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import b100.minimap.mc.IDimension;
import b100.minimap.mc.IMinecraftHelper;
import b100.minimap.mc.IPlayer;
import com.b100.utils.ReflectUtils;

import b100.minimap.render.WorldListener;
import b100.minimap.render.block.BlockRenderManager;
import b100.minimap.render.block.RenderType;
import b100.minimap.render.block.TileColors;
import net.minecraft.client.GLAllocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.PlayerLocal;
import net.minecraft.client.gui.chat.ScreenChat;
import net.minecraft.client.net.handler.PacketHandlerClient;
import net.minecraft.client.render.texture.Texture;
import net.minecraft.client.world.WorldClient;
import net.minecraft.client.world.WorldClientMP;
import net.minecraft.client.world.save.SaveHandlerClientSP;
import net.minecraft.core.Global;
import net.minecraft.core.block.Blocks;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.Items;
import net.minecraft.core.net.NetworkManager;
import net.minecraft.core.util.helper.Buffer;
import net.minecraft.core.util.helper.ChatAllowedCharacters;
import net.minecraft.core.world.Dimension;
import net.minecraft.core.world.World;
import net.minecraft.core.world.save.SaveHandlerBase;

public class MinecraftHelperImpl implements IMinecraftHelper {

	private Minecraft mc = Minecraft.getMinecraft();
	private PlayerWrapper playerWrapper = new PlayerWrapper();
	public WorldAccessImpl worldAccessImpl = new WorldAccessImpl();
	private Map<Dimension, DimensionWrapper> dimensionWrappers = new HashMap<>();
	
	@Override
	public Minecraft getMinecraftInstance() {
		return mc;
	}

	@Override
	public File getMinecraftDir() {
		return Global.accessor.getMinecraftDir();
	}

	@Override
	public int getDisplayWidth() {
		return mc.resolution.getWidthScreenCoords();
	}

	@Override
	public int getDisplayHeight() {
		return mc.resolution.getHeightScreenCoords();
	}

	@Override
	public int getScaledWidth() {
		return mc.resolution.getScaledWidthScreenCoords();
	}

	@Override
	public int getScaledHeight() {
		return mc.resolution.getScaledHeightScreenCoords();
	}

	@Override
	public int getGuiScaleFactor() {
		return mc.resolution.getScale();
	}

	@Override
	public void addWorldListener(World world, WorldListener listener) {
		worldAccessImpl.listeners.add(listener);
	}

	@Override
	public void removeWorldListener(World world, WorldListener listener) {
		worldAccessImpl.listeners.remove(listener);
	}

	@Override
	public IPlayer getThePlayer() {
		PlayerLocal player = mc.thePlayer;
		if(player != playerWrapper.player) {
			playerWrapper.player = player;
		}
		return playerWrapper;
	}

	@Override
	public int generateTexture() {
		return GLAllocation.generateTexture();
	}

	@Override
	public Texture getTexture(String path) {
		return mc.textureManager.loadTexture(path);
	}

	@Override
	public BufferedImage getTextureAsImage(String path) {
		InputStream stream = null;
		
		try {
			stream = mc.texturePackList.getResourceAsStream(path);
			
			return ImageIO.read(stream);
		}catch (Exception e) {
			e.printStackTrace();
		}finally {
			try {
				stream.close();
			}catch (Exception e) {}
		}
		
		return null;
	}

	@Override
	public ByteBuffer getBufferWithCapacity(int capacity) {
		Buffer.checkBufferSize(capacity);
		return Buffer.buffer;
	}

	@Override
	public boolean isGuiVisible() {
		return mc.gameSettings.immersiveMode.value <= 1;
	}

	@Override
	public boolean isChatOpened() {
		return mc.currentScreen instanceof ScreenChat;
	}

	@Override
	public boolean doesPlayerHaveCompass() {
		PlayerWrapper wrapper = (PlayerWrapper) getThePlayer();
		PlayerLocal player = wrapper.player;
		for(int i=0; i < player.inventory.getContainerSize(); i++) {
			ItemStack stack = player.inventory.getItem(i);
			if(stack != null && stack.getItem() == Items.TOOL_COMPASS) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean isDebugScreenOpened() {
		return mc.gameSettings.showDebugScreen.value;
	}

	@Override
	public boolean isMultiplayer(World world) {
		return world instanceof WorldClientMP;
	}

	@Override
	public String getWorldDirectoryName(World world) {
		SaveHandlerBase saveHandler = (SaveHandlerBase) world.getSaveHandler();
		File saveDirectory = ReflectUtils.getValue(ReflectUtils.getField(SaveHandlerBase.class, "saveDirectory"), saveHandler, File.class);
		return saveDirectory.getName();
	}

	@Override
	public String getServerName(World world) {
		WorldClientMP worldClient = (WorldClientMP) world;
		PacketHandlerClient sendQueue = ReflectUtils.getValue(ReflectUtils.getField(WorldClientMP.class, "sendQueue"), worldClient, PacketHandlerClient.class);
		NetworkManager netManager = ReflectUtils.getValue(ReflectUtils.getField(PacketHandlerClient.class, "netManager"), sendQueue, NetworkManager.class);
		Socket socket = ReflectUtils.getValue(ReflectUtils.getField(NetworkManager.class, "socket"), netManager, Socket.class);
		return socket.getInetAddress().toString() + ":" + socket.getPort();
	}

	@Override
	public boolean isCharacterAllowed(char c) {
		return ChatAllowedCharacters.ALLOWED_CHARACTERS.indexOf(c) != -1;
	}

	@Override
	public float getScreenPaddingPercentage() {
		return mc.gameSettings.screenPadding.value * 0.125f;
	}

	@Override
	public void onWorldChanged(World world) {
		if(world != null) {
			world.addListener(worldAccessImpl);
		}
	}

	@Override
	public void setupBlockRenderTypes(BlockRenderManager m) {
		m.setRenderType(Blocks.GLASS, RenderType.INVISIBLE);
		m.setRenderType(Blocks.TORCH_COAL, RenderType.INVISIBLE);
		
		m.setRenderType(Blocks.TALLGRASS, RenderType.INVISIBLE);
		m.setRenderType(Blocks.TALLGRASS_FERN, RenderType.INVISIBLE);
		m.setRenderType(Blocks.FLOWER_RED, RenderType.INVISIBLE);
		m.setRenderType(Blocks.FLOWER_YELLOW, RenderType.INVISIBLE);
		m.setRenderType(Blocks.FLOWER_ORANGE, RenderType.INVISIBLE);
		m.setRenderType(Blocks.FLOWER_PINK, RenderType.INVISIBLE);
		m.setRenderType(Blocks.FLOWER_LIGHT_BLUE, RenderType.INVISIBLE);
		m.setRenderType(Blocks.FLOWER_PURPLE, RenderType.INVISIBLE);
		m.setRenderType(Blocks.ALGAE, RenderType.INVISIBLE);

		m.setRenderType(Blocks.FLUID_WATER_FLOWING, RenderType.TRANSPARENT);
		m.setRenderType(Blocks.FLUID_WATER_STILL, RenderType.TRANSPARENT);
		m.setRenderType(Blocks.ICE, RenderType.TRANSPARENT);
	}

	@Override
	public boolean getEnableCheats() {
		return mc.currentWorld.isClientSide || mc.currentWorld.getLevelData().getCheatsEnabled();
	}

	public DimensionWrapper getDimensionWrapper(Dimension dimension) {
		DimensionWrapper wrapper = dimensionWrappers.get(dimension);
		if(wrapper == null) {
			wrapper = new DimensionWrapper(dimension);
			dimensionWrappers.put(dimension, wrapper);
		}
		return wrapper;
	}
	
	@Override
	public IDimension getDimension(String id) {
		try {
			return getDimensionWrapper(Dimension.getDimensionList().get(Integer.parseInt(id)));
		}catch (NumberFormatException e) {
			return null;
		}
	}
	
	@Override
	public IDimension getDimensionFromWorld(World world) {
		return getDimensionWrapper(world.dimension);
	}

	@Override
	public IDimension getDefaultDimension(World world) {
		return getDimensionWrapper(Dimension.OVERWORLD);
	}
	
	@Override
	public TileColors getTileColors() {
		return TileColorsBTA.instance;
	}
	
	@Override
	public InputStream getResource(String path) {
		return mc.texturePackList.getResourceAsStream(path);
	}

}
