package b100.minimap.gui;

public interface IGuiUtils {
	
	public void drawString(String string, int x, int y, int color);
	
	public void drawCenteredString(String string, int x, int y, int color);
	
	public int getStringWidth(String string);
	
	public void drawRectangle(int x, int y, int w, int h, int color);
	
	public void drawTexturedRectangle(int x, int y, int w, int h, float u0, float v0, float u1, float v1, int color);
	
	public void drawIcon(int icon, int x, int y, int color);
	
	public void drawIconWithShadow(int icon, int x, int y, int color);
	
	public void playButtonSound();
	
	public boolean isGuiOpened();
	
	public boolean isMinimapGuiOpened();
	
	public GuiScreen getCurrentScreen();
	
	public void displayGui(GuiScreen screen);

	public int createTextureFromImage(java.awt.image.BufferedImage image, boolean filter, boolean repeat);
	
}
