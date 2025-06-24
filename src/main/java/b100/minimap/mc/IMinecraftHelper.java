package b100.minimap.mc;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;

import b100.minimap.render.WorldListener;
import b100.minimap.render.block.BlockRenderManager;
import b100.minimap.render.block.TileColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.texture.Texture;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.world.World;

public interface IMinecraftHelper {
	
	public Minecraft getMinecraftInstance();
	
	public File getMinecraftDir();
	
	public abstract int getDisplayWidth();
	
	public abstract int getDisplayHeight();
	
	public int getScaledWidth();
	
	public int getScaledHeight();
	
	public int getGuiScaleFactor();
	
	public void addWorldListener(World world, WorldListener listener);
	
	public void removeWorldListener(World world, WorldListener listener);
	
	public IPlayer getThePlayer();
	
	public int generateTexture();
	
	public Texture getTexture(String path);
	
	public BufferedImage getTextureAsImage(String path);
	
	public ByteBuffer getBufferWithCapacity(int capacity);
	
	public boolean isGuiVisible();
	
	public boolean isChatOpened();
	
	public boolean isDebugScreenOpened();
	
	public boolean isMultiplayer(World world);
	
	public String getWorldDirectoryName(World world);
	
	public String getServerName(World world);
	
	public boolean isCharacterAllowed(char c);
	
	public float getScreenPaddingPercentage();
	
	public boolean doesPlayerHaveCompass();
	
	public void onWorldChanged(World world);
	
	public void setupBlockRenderTypes(BlockRenderManager blockRenderManager);
	
	public boolean getEnableCheats();
	
	public IDimension getDimension(String id);
	
	public IDimension getDimensionFromWorld(World world);
	
	public IDimension getDefaultDimension(World world);
	
	public InputStream getResource(String path);
	
	public TileColors getTileColors();
	
}
