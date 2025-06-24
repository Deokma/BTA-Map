package b100.minimap.mc.impl;

import b100.minimap.mc.IPlayer;
import net.minecraft.client.entity.player.PlayerLocal;

public class PlayerWrapper implements IPlayer {
	
	public PlayerLocal player;

	@Override
	public double getRotationYaw() {
		return player.yRot;
	}

	@Override
	public double getRotationPitch() {
		return player.xRot;
	}

	@Override
	public double getPosX(float partialTicks) {
		return player.xo + (player.x - player.xo) * partialTicks;
	}

	@Override
	public double getPosY(float partialTicks) {
		return player.yo + (player.y - player.yo) * partialTicks;
	}

	@Override
	public double getPosZ(float partialTicks) {
		return player.zo + (player.z - player.zo) * partialTicks;
	}

	@Override
	public void teleportTo(int x, int y, int z) {
		double x1 = x + 0.5;
		double y1 = (y - 1) + player.heightOffset + 0.01f;
		double z1 = z + 0.5;
		
		if(player.world.isClientSide) {
			player.sendChatMessage("/tp "+player.username+" "+x1+" "+y1+" "+z1);
		}else {
			player.setPos(x1, y1, z1);
		}
	}

}
