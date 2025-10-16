package b100.minimap.data;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.imageio.ImageIO;

import b100.minimap.Minimap;

/**
 * File-backed storage for minimap tiles. Uses content-addressed storage by hash.
 * Also maintains per-chunk mapping to tile hash.
 */
public class ChunkStorage {

	private final File chunksDir; // mapping chunk->hash
	private final File tilesDir;  // content-addressed tiles by hash

	public ChunkStorage(File worldDataDir, String dimensionId) {
		File baseDir = new File(worldDataDir, "chunks");
		this.chunksDir = new File(baseDir, dimensionId);
		this.tilesDir = new File(this.chunksDir, "tiles");
		this.chunksDir.mkdirs();
		this.tilesDir.mkdirs();
	}

	// -------- Hash utilities --------

	private static String toHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) sb.append(String.format("%02x", b));
		return sb.toString();
	}

	public String computeHashARGB(int[] argb) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			ByteBuffer bb = ByteBuffer.allocate(argb.length * 4);
			for (int v : argb) bb.putInt(v);
			md.update(bb.array());
			return toHex(md.digest());
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	// -------- Chunk->hash mapping --------

	private File getChunkHashFile(int chunkX, int chunkZ) {
		String name = chunkX + "_" + chunkZ + ".hash";
		return new File(chunksDir, name);
	}

	public void saveChunkHash(int chunkX, int chunkZ, String hash) {
		File f = getChunkHashFile(chunkX, chunkZ);
		try (FileOutputStream out = new FileOutputStream(f)) {
			out.write(hash.getBytes());
		} catch (IOException e) {
			Minimap.log("Failed to save chunk hash ("+chunkX+","+chunkZ+"):"+e.getMessage());
		}
	}

	public String loadChunkHash(int chunkX, int chunkZ) {
		File f = getChunkHashFile(chunkX, chunkZ);
		if (!f.exists()) return null;
		try {
			byte[] buf = java.nio.file.Files.readAllBytes(f.toPath());
			return new String(buf).trim();
		} catch (IOException e) {
			return null;
		}
	}


	// -------- Tile content by hash --------

	private File getTileFile(String hash) {
		// shard by first two hex to avoid huge dirs
		String shard = hash.length() >= 2 ? hash.substring(0, 2) : "zz";
		File dir = new File(tilesDir, shard);
		dir.mkdirs();
		return new File(dir, hash + ".tile");
	}

	public void saveTileARGB(String hash, int[] argb) {
		File f = getTileFile(hash);
		if (f.exists()) return; // already stored
		try (FileOutputStream out = new FileOutputStream(f); FileChannel ch = out.getChannel()) {
			ByteBuffer bb = ByteBuffer.allocate(argb.length * 4);
			for (int v : argb) bb.putInt(v);
			bb.flip();
			ch.write(bb);
		} catch (IOException e) {
			Minimap.log("Failed to save tile "+hash+":"+e.getMessage());
		}
	}

	public int[] loadTileARGB(String hash) {
		File f = getTileFile(hash);
		if (!f.exists()) return null;
		try (FileInputStream in = new FileInputStream(f); FileChannel ch = in.getChannel()) {
			ByteBuffer bb = ByteBuffer.allocate(16 * 16 * 4);
			int read = ch.read(bb);
			if (read < 16*16*4) return null;
			bb.flip();
			int[] data = new int[16 * 16];
			for (int i=0;i<data.length;i++) data[i] = bb.getInt();
			return data;
		} catch (IOException e) {
			return null;
		}
	}

	// Legacy PNG helpers (kept for fallback/testing)
	public File getChunkPngFile(int chunkX, int chunkZ) {
		String name = chunkX + "_" + chunkZ + ".png";
		return new File(chunksDir, name);
	}

	public void saveChunkImage(int chunkX, int chunkZ, BufferedImage image) {
		try {
			File file = getChunkPngFile(chunkX, chunkZ);
			file.getParentFile().mkdirs();
			ImageIO.write(image, "PNG", file);
		} catch (IOException e) {
			Minimap.log("Failed to save chunk image (" + chunkX + "," + chunkZ + "): " + e.getMessage());
		}
	}

	public BufferedImage loadChunkImage(int chunkX, int chunkZ) {
		File file = getChunkPngFile(chunkX, chunkZ);
		if (!file.exists()) return null;
		try {
			return ImageIO.read(file);
		} catch (IOException e) {
			Minimap.log("Failed to load chunk image (" + chunkX + "," + chunkZ + "): " + e.getMessage());
			return null;
		}
	}
}


