package star.sequoia2.client.types.ws.type;

import net.minecraft.util.math.BlockPos;

import java.util.*;

public final class PosCodec {
    public static int[] encode(List<BlockPos> positions) {
        int[] out = new int[positions.size() * 12];
        int i = 0;
        for (BlockPos p : positions) {
            i = putInt(out, i, p.getX());
            i = putInt(out, i, p.getY());
            i = putInt(out, i, p.getZ());
        }
        return out;
    }

    private static int putInt(int[] arr, int i, int value) {
        arr[i++] = (value >>> 24) & 0xFF;
        arr[i++] = (value >>> 16) & 0xFF;
        arr[i++] = (value >>> 8) & 0xFF;
        arr[i++] = value & 0xFF;
        return i;
    }

    public static List<BlockPos> decode(int[] data) {
        int n = data.length / 12;
        List<BlockPos> out = new ArrayList<>(n);
        int i = 0;
        for (int j = 0; j < n; j++) {
            int x = getInt(data, i); i += 4;
            int y = getInt(data, i); i += 4;
            int z = getInt(data, i); i += 4;
            out.add(new BlockPos(x, y, z));
        }
        return out;
    }

    private static int getInt(int[] arr, int i) {
        return ((arr[i] & 0xFF) << 24)
                | ((arr[i + 1] & 0xFF) << 16)
                | ((arr[i + 2] & 0xFF) << 8)
                | (arr[i + 3] & 0xFF);
    }
}