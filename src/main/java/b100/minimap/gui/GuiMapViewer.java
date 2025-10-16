package b100.minimap.gui;

import java.awt.image.BufferedImage;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

import javax.imageio.ImageIO;

import b100.minimap.Minimap;
import b100.minimap.data.ChunkStorage;
import b100.minimap.waypoint.Waypoint;
import b100.minimap.mc.IDimension;
import net.minecraft.client.render.texture.Texture;

import static org.lwjgl.opengl.GL11.*;

public class GuiMapViewer extends GuiScreen {

	private static final int CACHE_LIMIT = 1024;
	private static final int MAX_PENDING_UPLOADS = 100;
	private static final int TILE_SIZE = 16;
	private static final int HUD_DISPLAY_TIME_MS = 4000;
	private static final String EXPORT_DIR = "map_exports";
	private static final String SCREENSHOT_DIR = "map_screenshots";
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMdd_HHmmss");

	// Map position and scale
	private double offsetX;
	private double offsetY;
	private double zoom = 2.0;

	// Dragging status
	private boolean dragging = false;
	private int lastMouseX;
	private int lastMouseY;

	// Texture caching
	private final Map<Long, Integer> textureCache = new LinkedHashMap<>(256, 0.75f, true);
	private final Set<Long> missing = ConcurrentHashMap.newKeySet();
	private final Set<Long> loading = ConcurrentHashMap.newKeySet();
	private final Set<Long> retainKeys = new HashSet<>();

	// Asynchronous loading
	private final Queue<PendingTexture> pendingTextures = new ConcurrentLinkedQueue<>();
	private final Queue<Integer> texturesToDelete = new ConcurrentLinkedQueue<>();
	private ExecutorService loader;

	// Storage and Settings
	private ChunkStorage storageRef;
	private boolean noLight = true;

	// UI components
	private GuiNavigationContainer navTop;
	private GuiContainerBox navBox;

	// HUD notifications
	private String hudMessage = null;
	private long hudUntilMs = 0L;

	// Status flags
	private volatile boolean closed = false;
	private boolean awaitPlayerCenter = false;

	private static class PendingTexture {
		final long key;
		final BufferedImage image;

		PendingTexture(long key, BufferedImage image) {
			this.key = key;
			this.image = image;
		}
	}

	public GuiMapViewer(GuiScreen parentScreen) {
		super(parentScreen);
	}

	@Override
	public void onInit() {
		navTop = add(new GuiNavigationContainer(this, null, GuiNavigationContainer.Position.TOP));
		navBox = new GuiContainerBox();

		GuiButtonNavigation toggleButton = new GuiButtonNavigation(this, getLightingLabel(), navBox);
		toggleButton.addActionListener(e -> toggleLighting(toggleButton));
		navTop.add(toggleButton);

		navTop.add(new GuiButtonNavigation(this, "Save PNG", navBox)
				.addActionListener(e -> saveWorldToPng()));
	}

	private void toggleLighting(GuiButtonNavigation button) {
		button.setText(getLightingLabel());
		clearAllCaches();
	}

	private void clearAllCaches() {
		missing.clear();
		loading.clear();
		pendingTextures.clear();
		textureCache.values().forEach(texturesToDelete::add);
		textureCache.clear();
	}

	private String getLightingLabel() {
		return noLight ? "Lighting: Off" : "Lighting: On";
	}

	@Override
	public void draw(float partialTicks) {
		drawBackground();
		handlePlayerCentering();
		handleDragging();

		processPendingUploads();
		drawChunks();
		drawWaypoints();
		drawPlayerArrow();
		processDeferredDeletes();
		trimCache();

		drawUI(partialTicks);
	}

	private void drawBackground() {
		glDisable(GL_TEXTURE_2D);
		utils.drawRectangle(0, 0, width, height, 0xFF19324d);
		glEnable(GL_TEXTURE_2D);
	}

	private void handlePlayerCentering() {
		if (!awaitPlayerCenter) return;

		b100.minimap.mc.IPlayer player = Minimap.instance.minecraftHelper.getThePlayer();
		if (player != null) {
			offsetX = player.getPosX(1.0f);
			offsetY = player.getPosZ(1.0f);
			awaitPlayerCenter = false;
		}
	}

	private void handleDragging() {
		if (!dragging) return;

		int dx = cursorX - lastMouseX;
		int dy = cursorY - lastMouseY;
		lastMouseX = cursorX;
		lastMouseY = cursorY;
		offsetX -= dx / zoom;
		offsetY -= dy / zoom;
	}

	private void drawUI(float partialTicks) {
		glEnable(GL_TEXTURE_2D);
		glColor3f(1f, 1f, 1f);
		super.draw(partialTicks);

		if (hudMessage != null) {
			if (System.currentTimeMillis() < hudUntilMs) {
				drawHudMessage();
			} else {
				hudMessage = null;
			}
		}
	}

	private void drawHudMessage() {
		int pad = 4;
		int msgW = utils.getStringWidth(hudMessage);
		int x = width - msgW - pad - 6;
		int y = 6;

		glDisable(GL_TEXTURE_2D);
		utils.drawRectangle(x - pad, y - pad, msgW + pad * 2, 12 + pad * 2, 0x80000000);
		glEnable(GL_TEXTURE_2D);
		utils.drawString(hudMessage, x, y, 0xFFFFFFFF);
	}

	private void drawChunks() {
		updateStorageReference();
		ChunkStorage storage = getActiveStorage();
		if (storage == null) {
			setHudMessage("No storage (server?)");
			return;
		}

		if (!hasAnyStoredData(storage)) {
			setHudMessage("No map data yet - explore the world!");
			return;
		}

		validateOffsets();
		int centerChunkX = (int) Math.floor(offsetX / TILE_SIZE);
		int centerChunkZ = (int) Math.floor(offsetY / TILE_SIZE);

		int radiusX = calculateRadius(width);
		int radiusZ = calculateRadius(height);

		retainKeys.clear();
		renderChunkRange(storage, centerChunkX, centerChunkZ, radiusX, radiusZ);
	}

	private boolean hasAnyStoredData(ChunkStorage storage) {
		try {
			Set<Long> keys = indexPositionsCompat(storage, true);
			if (keys != null && !keys.isEmpty()) return true;

			keys = indexPositionsCompat(storage, false);
			return keys != null && !keys.isEmpty();
		} catch (Exception e) {
			return true;
		}
	}

	private void updateStorageReference() {
		ChunkStorage current = (Minimap.instance.worldData != null)
				? Minimap.instance.worldData.getChunkStorage()
				: null;

		if (current != null && current != storageRef) {
			storageRef = current;
		}
	}

	private ChunkStorage getActiveStorage() {
		ChunkStorage current = (Minimap.instance.worldData != null)
				? Minimap.instance.worldData.getChunkStorage()
				: null;
		return (storageRef != null) ? storageRef : current;
	}

	private void validateOffsets() {
		b100.minimap.mc.IPlayer player = Minimap.instance.minecraftHelper.getThePlayer();
		double px = player != null ? player.getPosX(1.0f) : 0.0;
		double pz = player != null ? player.getPosZ(1.0f) : 0.0;

		if (awaitPlayerCenter && player == null) {
			offsetX = 0.0;
			offsetY = 0.0;
		}

		if (Double.isNaN(offsetX) || Double.isInfinite(offsetX)) offsetX = px;
		if (Double.isNaN(offsetY) || Double.isInfinite(offsetY)) offsetY = pz;
	}

	private int calculateRadius(int dimension) {
		return (int) Math.ceil((dimension / 2.0) / (TILE_SIZE * zoom)) + 6;
	}

	private void renderChunkRange(ChunkStorage storage, int centerX, int centerZ, int radiusX, int radiusZ) {
		for (int dz = -radiusZ; dz <= radiusZ; dz++) {
			for (int dx = -radiusX; dx <= radiusX; dx++) {
				int cx = centerX + dx;
				int cz = centerZ + dz;
				long key = makeChunkKey(cx, cz);
				retainKeys.add(key);

				int tex = getOrLoadChunkTexture(storage, cx, cz);
				if (tex == 0) continue;

				renderChunkTexture(tex, cx, cz);
			}
		}
	}

	private long makeChunkKey(int cx, int cz) {
		return (((long) cx) << 32) | (cz & 0xffffffffL);
	}

	private void renderChunkTexture(int tex, int cx, int cz) {
		glBindTexture(GL_TEXTURE_2D, tex);
		double screenX = (cx * TILE_SIZE - offsetX) * zoom + width / 2.0;
		double screenY = (cz * TILE_SIZE - offsetY) * zoom + height / 2.0;
		drawTexturedQuad(screenX, screenY, TILE_SIZE * zoom, TILE_SIZE * zoom);
	}

	private void drawTexturedQuad(double x, double y, double w, double h) {
		glColor3f(1f, 1f, 1f);
		glBegin(GL_QUADS);
		glTexCoord2f(0f, 0f); glVertex2d(x, y);
		glTexCoord2f(1f, 0f); glVertex2d(x + w, y);
		glTexCoord2f(1f, 1f); glVertex2d(x + w, y + h);
		glTexCoord2f(0f, 1f); glVertex2d(x, y + h);
		glEnd();
	}

	private int getOrLoadChunkTexture(ChunkStorage storage, int cx, int cz) {
		long key = makeChunkKey(cx, cz);

		Integer tex = textureCache.get(key);
		if (tex != null) return tex;
		if (missing.contains(key)) return 0;

		BufferedImage img = syncLoadTileFromFiles(storage, cx, cz);
		if (img != null) {
			int t = utils.createTextureFromImage(img, false, false);
			textureCache.put(key, t);
			return t;
		}

		if (!loading.contains(key)) {
			loading.add(key);
			submitLoad(storage, key, cx, cz);
		}
		return 0;
	}

	private BufferedImage syncLoadTileFromFiles(ChunkStorage storage, int cx, int cz) {
		try {
			String hash = loadHashCompat(storage, cx, cz, true);
			if (hash == null) return null;

			int[] argb = storage.loadTileARGB(hash);
			if (argb == null || argb.length != TILE_SIZE * TILE_SIZE) return null;

			return createImageFromARGB(argb);
		} catch (Throwable t) {
			return null;
		}
	}

	private BufferedImage createImageFromARGB(int[] argb) {
		BufferedImage img = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
		img.setRGB(0, 0, TILE_SIZE, TILE_SIZE, argb, 0, TILE_SIZE);
		return img;
	}

	private void submitLoad(ChunkStorage storage, long key, int cx, int cz) {
		if (loader == null) {
			loader = Executors.newSingleThreadExecutor(r ->
					new Thread(r, "Minimap-ChunkLoader"));
		}

		loader.submit(() -> loadChunkAsync(storage, key, cx, cz));
	}

	private void loadChunkAsync(ChunkStorage storage, long key, int cx, int cz) {
		try {
			// Trying to load without lighting (priority)
			String hash = loadHashCompat(storage, cx, cz, true);

			if (hash == null) {
				hash = loadHashCompat(storage, cx, cz, false);
			}

			BufferedImage img = null;

			if (hash != null) {
				int[] argb = storage.loadTileARGB(hash);
				if (argb != null && argb.length == TILE_SIZE * TILE_SIZE) {
					img = createImageFromARGB(argb);
				}
			}

			if (img != null && pendingTextures.size() < MAX_PENDING_UPLOADS) {
				pendingTextures.add(new PendingTexture(key, img));
			} else if (img == null) {
				missing.add(key);
			}
		} catch (Exception e) {
			// Logging the error for debugging
			Minimap.log("Failed to load chunk " + cx + ", " + cz + ": " + e.getMessage());
			missing.add(key);
		} finally {
			loading.remove(key);
		}
	}

	private void processPendingUploads() {
		PendingTexture p;
		int processed = 0;
		while ((p = pendingTextures.poll()) != null && processed++ < 10) {
			if (Thread.currentThread().isInterrupted()) break;
			int tex = utils.createTextureFromImage(p.image, false, false);
			textureCache.put(p.key, tex);
		}
	}

	private void processDeferredDeletes() {
		Integer tex;
		while ((tex = texturesToDelete.poll()) != null) {
			glDeleteTextures(tex);
		}
	}

	private void trimCache() {
		if (textureCache.size() <= CACHE_LIMIT) return;

		Iterator<Map.Entry<Long, Integer>> it = textureCache.entrySet().iterator();
		while (textureCache.size() > CACHE_LIMIT && it.hasNext()) {
			Map.Entry<Long, Integer> e = it.next();
			if (!retainKeys.contains(e.getKey())) {
				texturesToDelete.add(e.getValue());
				it.remove();
			}
		}
	}

	@Override
	public void onGuiClosed() {
		// mark closed before touching queues to avoid late uploads
		try { closed = true; } catch (Throwable ignore) {}
		for (Map.Entry<Long, Integer> e : textureCache.entrySet()) {
			glDeleteTextures(e.getValue());
		}
		textureCache.clear();
		missing.clear();
		loading.clear();
		pendingTextures.clear();
		processDeferredDeletes();
		if (loader != null) {
			try { loader.shutdownNow(); } catch (Throwable ignore) {}
			loader = null;
		}
	}

	@Override
	public void onGuiOpened() {
		initializePlayerPosition();
		storageRef = (Minimap.instance.worldData != null)
				? Minimap.instance.worldData.getChunkStorage()
				: null;
	}

	private void initializePlayerPosition() {
		b100.minimap.mc.IPlayer player = Minimap.instance.minecraftHelper.getThePlayer();
		if (player != null) {
			offsetX = player.getPosX(1.0f);
			offsetY = player.getPosZ(1.0f);
			awaitPlayerCenter = false;
		} else {
			offsetX = 0.0;
			offsetY = 0.0;
			awaitPlayerCenter = true;
		}
	}

	@Override
	public void mouseEvent(int button, boolean pressed, int mouseX, int mouseY) {
		if (button == 0) {
			dragging = pressed;
			lastMouseX = mouseX;
			lastMouseY = mouseY;
		}
		super.mouseEvent(button, pressed, mouseX, mouseY);
	}

	@Override
	public void scrollEvent(int dir, int mouseX, int mouseY) {
		double beforeZoom = zoom;
		zoom *= (dir > 0) ? 1.25 : (dir < 0) ? 0.8 : 1.0;

		// Zooming to the cursor
		double worldX = offsetX + (mouseX - width / 2.0) / beforeZoom;
		double worldY = offsetY + (mouseY - height / 2.0) / beforeZoom;
		offsetX = worldX - (mouseX - width / 2.0) / zoom;
		offsetY = worldY - (mouseY - height / 2.0) / zoom;

		super.scrollEvent(dir, mouseX, mouseY);
	}

	private String loadHashCompat(ChunkStorage storage, int cx, int cz, boolean noLightReq) {
		try {
			java.lang.reflect.Method m = storage.getClass()
					.getMethod("loadChunkHash", int.class, int.class, boolean.class);
			Object v = m.invoke(storage, cx, cz, noLightReq);
			return v instanceof String ? (String) v : null;
		} catch (Exception ignore) {
			try {
				java.lang.reflect.Method m2 = storage.getClass()
						.getMethod("loadChunkHash", int.class, int.class);
				Object v2 = m2.invoke(storage, cx, cz);
				return v2 instanceof String ? (String) v2 : null;
			} catch (Exception e) {
				return null;
			}
		}
	}

	private Set<Long> indexPositionsCompat(ChunkStorage storage, boolean noLightReq) {
		try {
			java.lang.reflect.Method m = storage.getClass()
					.getMethod("indexChunkPositions", boolean.class);
			Object v = m.invoke(storage, noLightReq);
			if (v instanceof Set) return (Set<Long>) v;
		} catch (Exception ignore) {}

		return indexFromFiles(storage, noLightReq);
	}

	private Set<Long> indexFromFiles(ChunkStorage storage, boolean noLightReq) {
		try {
			java.lang.reflect.Field f = storage.getClass().getDeclaredField("chunksDir");
			f.setAccessible(true);
			Object dirObj = f.get(storage);
			if (!(dirObj instanceof File)) return Collections.emptySet();

			File chunksDir = (File) dirObj;
			File[] files = chunksDir.listFiles();
			if (files == null) return Collections.emptySet();

			Set<Long> keys = new HashSet<>();
			String suffix = noLightReq ? ".nl.hash" : ".hash";

			for (File file : files) {
				parseChunkFile(file, suffix, keys);
			}
			return keys;
		} catch (Throwable t) {
			return Collections.emptySet();
		}
	}

	private void parseChunkFile(File file, String suffix, Set<Long> keys) {
		String name = file.getName();
		if (!name.endsWith(suffix)) return;

		int us = name.indexOf('_');
		int dot = name.lastIndexOf('.');
		if (us <= 0 || dot <= us) return;

		try {
			int x = Integer.parseInt(name.substring(0, us));
			int z = Integer.parseInt(name.substring(us + 1, dot));
			keys.add(makeChunkKey(x, z));
		} catch (Throwable ignored) {}
	}

	private void drawPlayerArrow() {
		if (mc.getThePlayer() == null) return;

		double px = mc.getThePlayer().getPosX(1.0f);
		double pz = mc.getThePlayer().getPosZ(1.0f);
		double yaw = mc.getThePlayer().getRotationYaw();
		double screenX = (px - offsetX) * zoom + width / 2.0;
		double screenY = (pz - offsetY) * zoom + height / 2.0;

		Texture arrow = Minimap.instance.minecraftHelper
				.getTexture("/assets/minimap/player_arrow.png");
		arrow.bind();

		glPushMatrix();
		glTranslated(screenX, screenY, 0);
		glRotated(-yaw - 90.0, 0, 0, 1);
		glColor3f(1f, 0f, 0f);

		double s = TILE_SIZE * zoom;
		glBegin(GL_QUADS);
		glTexCoord2f(0f, 0f); glVertex2d(-s / 2, -s / 2);
		glTexCoord2f(1f, 0f); glVertex2d(s / 2, -s / 2);
		glTexCoord2f(1f, 1f); glVertex2d(s / 2, s / 2);
		glTexCoord2f(0f, 1f); glVertex2d(-s / 2, s / 2);
		glEnd();
		glPopMatrix();
	}

	private void drawWaypoints() {
		if (!Minimap.instance.config.mapConfig.showWaypoints.value) return;

		List<Waypoint> waypoints = Minimap.instance.worldData.getWaypoints();
		if (waypoints == null || waypoints.isEmpty()) return;

		IDimension currentDimension = Minimap.instance.worldData.dimension;
		Texture waypointTex = Minimap.instance.minecraftHelper
				.getTexture("/assets/minimap/waypoint.png");
		Texture waypointArrowTex = Minimap.instance.minecraftHelper
				.getTexture("/assets/minimap/waypoint_arrow.png");

		Texture current = null;
		double centerX = width / 2.0;
		double centerY = height / 2.0;
		double margin = 24.0;
		double halfW = centerX - margin;
		double halfH = centerY - margin;

		for (Waypoint w : waypoints) {
			if (!w.visible || w.dimension != currentDimension) continue;
			current = renderWaypoint(w, centerX, centerY, halfW, halfH,
					waypointTex, waypointArrowTex, current);
		}
	}

	private Texture renderWaypoint(Waypoint w, double centerX, double centerY,
								   double halfW, double halfH, Texture waypointTex, Texture waypointArrowTex, Texture current) {

		double sx = (w.x + 0.5 - offsetX) * zoom + centerX;
		double sy = (w.z + 0.5 - offsetY) * zoom + centerY;
		boolean onScreen = sx >= 0 && sy >= 0 && sx < width && sy < height;
		double drawX = sx;
		double drawY = sy;
		Double angle = null;

		if (!onScreen) {
			double dx = sx - centerX;
			double dy = sy - centerY;
			double scale = Math.max(Math.abs(dx) / halfW, Math.abs(dy) / halfH);
			scale = Math.max(scale, 1.0);

			double rad2 = (width + TILE_SIZE / 2) / 2.0;
			drawX = centerX + dx / scale;
			drawY = centerY + dy / scale;
			angle = Math.atan2(-dy, dx);

			if (current != waypointArrowTex) {
				waypointArrowTex.bind();
				current = waypointArrowTex;
			}
		} else {
			if (current != waypointTex) {
				waypointTex.bind();
				current = waypointTex;
			}
		}

		renderWaypointIcon(w, drawX, drawY, onScreen, angle);
		return current;
	}

	private void renderWaypointIcon(Waypoint w, double drawX, double drawY,
									boolean onScreen, Double angle) {

		glPushMatrix();
		glTranslated(drawX, drawY, onScreen ? 0 : 100);
		if (angle != null) glRotated(Math.toDegrees(angle), 0, 0, 1);

		glColor4ub((byte) ((w.color >> 16) & 0xFF),
				(byte) ((w.color >> 8) & 0xFF),
				(byte) (w.color & 0xFF), (byte) 0xFF);

		double s = TILE_SIZE;
		glBegin(GL_QUADS);
		glTexCoord2f(0f, 0f); glVertex2d(-s / 2, -s / 2);
		glTexCoord2f(1f, 0f); glVertex2d(s / 2, -s / 2);
		glTexCoord2f(1f, 1f); glVertex2d(s / 2, s / 2);
		glTexCoord2f(0f, 1f); glVertex2d(-s / 2, s / 2);
		glEnd();
		glPopMatrix();

		if (onScreen && Minimap.instance.config.mapConfig.showWaypointLabels.value) {
			renderWaypointLabel(w, drawX, drawY);
		}
	}

	private void renderWaypointLabel(Waypoint w, double drawX, double drawY) {
		int size = Minimap.instance.config.mapConfig.waypointLabelSize.value;
		int wtxt = utils.getStringWidth(w.name);
		int x1 = (int) (drawX - (wtxt * size) / 2);
		int y1 = (int) (drawY + 14);

		glPushMatrix();
		glTranslatef(x1, y1, 0.0f);
		glScalef(size, size, size);

		glDisable(GL_TEXTURE_2D);
		utils.drawRectangle(-1, -1, wtxt + 2, 10, 0x80000000);
		glEnable(GL_TEXTURE_2D);
		utils.drawString(w.name, 0, 0, 0xFFFFFFFF);

		glPopMatrix();
	}

	private void saveWorldToPng() {
		try {
			ChunkStorage storage = Minimap.instance.worldData.getChunkStorage();
			Set<Long> keys = indexPositionsCompat(storage, true);
			if (keys == null || keys.isEmpty()) {
				keys = indexPositionsCompat(storage, false);
			}
			if (keys == null || keys.isEmpty()) {
				setHudMessage("Nothing to save (no tiles)");
				return;
			}

			int[] bounds = calculateBounds(keys);
			BufferedImage img = createWorldImage(storage, keys, bounds);
			saveImage(img, EXPORT_DIR, "worldmap", "_nolight");

		} catch (Throwable t) {
			setHudMessage("Save failed: " + t.getMessage());
		}
	}

	private int[] calculateBounds(Set<Long> keys) {
		int minCx = Integer.MAX_VALUE, minCz = Integer.MAX_VALUE;
		int maxCx = Integer.MIN_VALUE, maxCz = Integer.MIN_VALUE;

		for (Long k : keys) {
			int cx = (int) (k >>> 32);
			int cz = (int) (k & 0xffffffffL);
			minCx = Math.min(minCx, cx);
			minCz = Math.min(minCz, cz);
			maxCx = Math.max(maxCx, cx);
			maxCz = Math.max(maxCz, cz);
		}

		return new int[]{minCx, minCz, maxCx, maxCz};
	}

	private BufferedImage createWorldImage(ChunkStorage storage, Set<Long> keys, int[] bounds) {
		int minCx = bounds[0], minCz = bounds[1], maxCx = bounds[2], maxCz = bounds[3];
		int imgW = (maxCx - minCx + 1) * TILE_SIZE;
		int imgH = (maxCz - minCz + 1) * TILE_SIZE;
		BufferedImage img = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);

		for (Long k : keys) {
			int cx = (int) (k >>> 32);
			int cz = (int) (k & 0xffffffffL);

			String hash = loadHashCompat(storage, cx, cz, true);
			if (hash == null) hash = loadHashCompat(storage, cx, cz, false);
			if (hash == null) continue;

			int[] argb = storage.loadTileARGB(hash);
			if (argb == null || argb.length != TILE_SIZE * TILE_SIZE) continue;

			int ox = (cx - minCx) * TILE_SIZE;
			int oy = (cz - minCz) * TILE_SIZE;
			img.setRGB(ox, oy, TILE_SIZE, TILE_SIZE, argb, 0, TILE_SIZE);
		}

		return img;
	}

	private void saveImage(BufferedImage img, String dirName, String prefix, String suffix)
			throws Exception {

		File outDir = new File(Minimap.instance.getConfigFolder(), dirName);
		outDir.mkdirs();

		String ts = DATE_FORMAT.format(new Date());
		File out = new File(outDir, prefix + "_" + ts + suffix + ".png");

		ImageIO.write(img, "PNG", out);
		setHudMessage("Saved: " + out.getName());
		Minimap.log("Saved world map to: " + out.getAbsolutePath());
	}

	private void setHudMessage(String msg) {
		this.hudMessage = msg;
		this.hudUntilMs = System.currentTimeMillis() + HUD_DISPLAY_TIME_MS;
	}
}