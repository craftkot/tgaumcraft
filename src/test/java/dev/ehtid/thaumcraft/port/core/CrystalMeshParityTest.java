package dev.ehtid.thaumcraft.port.core;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class CrystalMeshParityTest {
    private static int assertions;

    public static void main(String[] args) {
        var groups = LegacyCrystalMeshSelection.originalGroups();
        eq(8, groups.size());
        String[] seed0 = {"s014", "s026", "s017", "s018"};
        String[] seed42 = {"s016", "s024", "s017", "s015"};
        for (int size = 0; size < 4; size++) {
            List<String> a = LegacyCrystalMeshSelection.select(size, 0);
            List<String> b = LegacyCrystalMeshSelection.select(size, 42);
            eq(size+1, a.size());
            eq(size+1, b.size());
            eq(Arrays.asList(seed0).subList(0, size+1), a);
            eq(Arrays.asList(seed42).subList(0, size+1), b);
            eq(a, LegacyCrystalMeshSelection.select(size, 0));
        }

        long[] sourceOffsets = {0L, 5L, 10L, 15L, 20L, 25L};
        var faces = LegacyCrystalMeshSelection.Face.values();
        eq(6, faces.length);
        for (int faceIndex = 0; faceIndex < faces.length; faceIndex++) {
            var face = faces[faceIndex];
            eq(sourceOffsets[faceIndex], face.seedOffset());
            for (int size = 0; size <= 3; size++) {
                eq(LegacyCrystalMeshSelection.select(size, 42L + sourceOffsets[faceIndex]),
                   LegacyCrystalMeshSelection.selectForFace(size, 42L, face));
            }
        }
        // Exact MeshModel rotations map the unrotated DOWN floor-base to
        // the six touching block faces: y=1 UP, y=0 DOWN, x=1 EAST,
        // x=0 WEST, z=0 NORTH, z=1 SOUTH.
        var p = new LegacyCrystalMeshSelection.Point(.3, 0, .7);
        near(new LegacyCrystalMeshSelection.Point(.3,1,.3),
                LegacyCrystalMeshSelection.orient(faces[0],p));
        near(p,LegacyCrystalMeshSelection.orient(faces[1],p));
        near(new LegacyCrystalMeshSelection.Point(1,.3,.3),
                LegacyCrystalMeshSelection.orient(faces[2],p));
        near(new LegacyCrystalMeshSelection.Point(0,.3,.7),
                LegacyCrystalMeshSelection.orient(faces[3],p));
        near(new LegacyCrystalMeshSelection.Point(.3,.3,0),
                LegacyCrystalMeshSelection.orient(faces[4],p));
        near(new LegacyCrystalMeshSelection.Point(.7,.3,1),
                LegacyCrystalMeshSelection.orient(faces[5],p));

        for (var face : faces) {
            var one = new LegacyCrystalMeshSelection.Point(.125,.25,.5);
            var n = new LegacyCrystalMeshSelection.Point(0,1,0);
            var t = LegacyCrystalMeshSelection.orient(face,one);
            var delta = LegacyCrystalMeshSelection.orientNormal(face,n);
            var sum = LegacyCrystalMeshSelection.orient(face,
                    new LegacyCrystalMeshSelection.Point(one.x()+n.x(),one.y()+n.y(),one.z()+n.z()));
            close(t.x()+delta.x(),sum.x());
            close(t.y()+delta.y(),sum.y());
            close(t.z()+delta.z(),sum.z());
        }
        System.out.println("CrystalModel seeded selection parity OK ("+assertions+" assertions)");
    }
    private static void eq(Object a,Object b) {
        assertions++;
        if(!Objects.equals(a,b))throw new AssertionError(a+" != "+b);
    }
    private static void near(LegacyCrystalMeshSelection.Point a, LegacyCrystalMeshSelection.Point b) {
        close(a.x(),b.x());
        close(a.y(),b.y());
        close(a.z(),b.z());
    }
    private static void close(double a,double b) {
        assertions++;
        if(Math.abs(a-b)>1e-7)throw new AssertionError(a+" != "+b);
    }
}
