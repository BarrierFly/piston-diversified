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

# --- rule 2: face-overlap fix (adjacent-cell offset) ---
patch('src/main/java/dev/zcode/piston_diversified/logic/PotatoStructureResolver.java', [
 ('''    /** Whether box {@code a}'s {@code side} face overlaps box {@code b}'s opposite face. */
    private static boolean faceOverlap(net.minecraft.world.phys.AABB a, net.minecraft.world.phys.AABB b, Direction side) {
        return switch (side.getAxis()) {
            case X -> a.minY < b.maxY && a.maxY > b.minY && a.minZ < b.maxZ && a.maxZ > b.minZ
                && Math.abs(a.minX - b.maxX) < 1.0E-6F;
            case Y -> a.minX < b.maxX && a.maxX > b.minX && a.minZ < b.maxZ && a.maxZ > b.minZ
                && Math.abs(a.maxY - b.minY) < 1.0E-6F;
            case Z -> a.minX < b.maxX && a.maxX > b.minX && a.minY < b.maxY && a.maxY > b.minY
                && Math.abs(a.maxZ - b.minZ) < 1.0E-6F;
        };
    }''',
  '''    private static final float FACE_EPS = 1.0E-5F;

    /**
     * Whether box {@code a}'s {@code side} face touches box {@code b}'s opposite face. Both AABBs
     * are in their own cell's local space (0..1): the touch means a's far face sits at the shared
     * cell boundary and b's near face sits at the same boundary from the other side.
     */
    private static boolean faceOverlap(net.minecraft.world.phys.AABB a, net.minecraft.world.phys.AABB b, Direction side) {
        boolean touching = switch (side) {
            case EAST -> a.maxX > 1.0F - FACE_EPS && b.minX < FACE_EPS;
            case WEST -> a.minX < FACE_EPS && b.maxX > 1.0F - FACE_EPS;
            case UP -> a.maxY > 1.0F - FACE_EPS && b.minY < FACE_EPS;
            case DOWN -> a.minY < FACE_EPS && b.maxY > 1.0F - FACE_EPS;
            case SOUTH -> a.maxZ > 1.0F - FACE_EPS && b.minZ < FACE_EPS;
            case NORTH -> a.minZ < FACE_EPS && b.maxZ > 1.0F - FACE_EPS;
        };
        if (!touching) {
            return false;
        }
        // the contact patch must overlap on the two tangential axes (strict: edge-touch is not overlap)
        return switch (side.getAxis()) {
            case X -> a.minY < b.maxY && a.maxY > b.minY && a.minZ < b.maxZ && a.maxZ > b.minZ;
            case Y -> a.minX < b.maxX && a.maxX > b.minX && a.minZ < b.maxZ && a.maxZ > b.minZ;
            default -> a.minX < b.maxX && a.maxX > b.minX && a.minY < b.maxY && a.maxY > b.minY;
        };
    }'''),
])

print('part 2 done')
