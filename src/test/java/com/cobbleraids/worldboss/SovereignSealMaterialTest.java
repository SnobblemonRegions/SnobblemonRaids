package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class SovereignSealMaterialTest {
    private record Face(int element, String side, java.util.List<Vector3d> vertices) {}

    @Test
    void crestFacesHaveClearDepthSeparation() throws Exception {
        var faces = new java.util.ArrayList<Face>();
        try (var input = getClass().getResourceAsStream("/assets/cobbleraids/models/item/worldboss_sovereign_crest.json")) {
            assertNotNull(input);
            var elements = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("elements");
            String[] sides = {"north", "south", "west", "east", "up", "down"};
            int[][] indices = {{0,2,3,1},{4,5,7,6},{0,4,6,2},{1,3,7,5},{2,6,7,3},{0,1,5,4}};
            for (int index = 0; index < elements.size(); index++) {
                var element = elements.get(index).getAsJsonObject();
                var corners = new Vector3d[8];
                for (int corner = 0; corner < 8; corner++) {
                    var point = new Vector3d();
                    for (int axis = 0; axis < 3; axis++) point.setComponent(axis,
                            element.getAsJsonArray((corner & (1 << axis)) == 0 ? "from" : "to").get(axis).getAsDouble());
                    if (element.has("rotation")) {
                        var rotation = element.getAsJsonObject("rotation");
                        var values = rotation.getAsJsonArray("origin");
                        var origin = new Vector3d(values.get(0).getAsDouble(), values.get(1).getAsDouble(), values.get(2).getAsDouble());
                        point.sub(origin);
                        double angle = Math.toRadians(rotation.get("angle").getAsDouble());
                        switch (rotation.get("axis").getAsString()) {
                            case "x" -> point.rotateX(angle);
                            case "y" -> point.rotateY(angle);
                            case "z" -> point.rotateZ(angle);
                            default -> fail("Unknown model rotation axis");
                        }
                        point.add(origin);
                    }
                    corners[corner] = point;
                }
                for (int side = 0; side < sides.length; side++) {
                    if (!element.getAsJsonObject("faces").has(sides[side])) continue;
                    var vertices = new java.util.ArrayList<Vector3d>();
                    for (int corner : indices[side]) vertices.add(corners[corner]);
                    faces.add(new Face(index, sides[side], vertices));
                }
            }
        }
        var conflicts = new java.util.ArrayList<String>();
        for (int a = 0; a < faces.size(); a++) for (int b = a + 1; b < faces.size(); b++) {
            var first = faces.get(a); var second = faces.get(b);
            var normal = normal(first); var other = normal(second);
            // A quarter model pixel is 0.21875 blocks at the crest's scale, keeping layered faces distinct at distance.
            if (normal.dot(other) < 1 - 1e-8 || Math.abs(normal.dot(new Vector3d(second.vertices().getFirst()).sub(first.vertices().getFirst()))) >= 0.25 - 1e-7) continue;
            int drop = Math.abs(normal.x) > Math.abs(normal.y) ? 0 : 1;
            if (Math.abs(normal.z) > Math.abs(normal.get(drop))) drop = 2;
            var overlap = clip(project(first, drop), project(second, drop));
            if (Math.abs(area(overlap)) >= 1e-7) conflicts.add("element " + first.element() + " " + first.side()
                    + " and element " + second.element() + " " + second.side());
        }
        assertTrue(conflicts.isEmpty(), "Crest faces lack depth clearance: " + String.join("; ", conflicts));
    }

    private static Vector3d normal(Face face) {
        var p = face.vertices();
        return new Vector3d(p.get(1)).sub(p.get(0)).cross(new Vector3d(p.get(2)).sub(p.get(0))).normalize();
    }

    private static java.util.List<double[]> project(Face face, int drop) {
        var points = new java.util.ArrayList<double[]>();
        for (var point : face.vertices()) {
            points.add(drop == 0 ? new double[]{point.y,point.z} : drop == 1 ? new double[]{point.x,point.z} : new double[]{point.x,point.y});
        }
        if (area(points) < 0) java.util.Collections.reverse(points);
        return points;
    }

    private static double area(java.util.List<double[]> points) {
        double sum = 0;
        for (int i = 0; i < points.size(); i++) {
            var a = points.get(i); var b = points.get((i + 1) % points.size());
            sum += a[0]*b[1] - b[0]*a[1];
        }
        return sum / 2;
    }

    private static double side(double[] a, double[] b, double[] point) {
        return (b[0]-a[0])*(point[1]-a[1]) - (b[1]-a[1])*(point[0]-a[0]);
    }

    private static java.util.List<double[]> clip(java.util.List<double[]> subject, java.util.List<double[]> boundary) {
        var output = subject;
        for (int edge = 0; edge < boundary.size() && !output.isEmpty(); edge++) {
            var a = boundary.get(edge); var b = boundary.get((edge + 1) % boundary.size());
            var input = output;
            output = new java.util.ArrayList<>();
            var previous = input.getLast(); double previousSide = side(a,b,previous);
            for (var current : input) {
                double currentSide = side(a,b,current);
                if ((previousSide >= -1e-9) != (currentSide >= -1e-9)) {
                    double t = previousSide / (previousSide - currentSide);
                    output.add(new double[]{previous[0]+t*(current[0]-previous[0]), previous[1]+t*(current[1]-previous[1])});
                }
                if (currentSide >= -1e-9) output.add(current);
                previous = current; previousSide = currentSide;
            }
        }
        return output;
    }

    @Test
    void solidModelsRespectMinecraftBoundsAndKeepTheOrbitalBandAbovePlayers() throws Exception {
        for (String name : new String[]{"worldboss_sovereign_band", "worldboss_sovereign_crest", "worldboss_sovereign_halo",
                "worldboss_sovereign_energy", "worldboss_sovereign_ground"}) {
            try (var input = getClass().getResourceAsStream("/assets/cobbleraids/models/item/" + name + ".json")) {
                assertNotNull(input, "prototype needs actual 3D models, not the old dome texture");
                var model = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
                for (var texture : model.getAsJsonObject("textures").asMap().values()) {
                    String resource = texture.getAsString();
                    assertTrue(resource.startsWith("cobbleraids:item/sovereign_"));
                    try (var png = getClass().getResourceAsStream("/assets/" + resource.replace(":", "/textures/") + ".png")) {
                        assertNotNull(png);
                    }
                }
                assertTrue(model.getAsJsonArray("elements").size() > 1);
                for (var element : model.getAsJsonArray("elements")) {
                    var e = element.getAsJsonObject();
                    for (int axis = 0; axis < 3; axis++) {
                        double from = e.getAsJsonArray("from").get(axis).getAsDouble();
                        double to = e.getAsJsonArray("to").get(axis).getAsDouble();
                        assertTrue(from >= -16 && to <= 32 && from < to);
                    }
                    if (e.has("rotation")) {
                        double angle = e.getAsJsonObject("rotation").get("angle").getAsDouble();
                        assertTrue(angle == 0 || java.util.Set.of(-45.0, -22.5, 22.5, 45.0).contains(angle));
                    }
                    if (!name.endsWith("band")) continue;
                    for (double radius : new double[]{8, 96, 512, 1024}) {
                      for (long tick : new long[]{0, 450, 900, 1350, 1800, 2700, 3592}) {
                        var pose = SovereignSealGeometry.ring(radius, tick);
                        var rotation = new Quaterniond(pose.qx(), pose.qy(), pose.qz(), pose.qw());
                        for (int corner = 0; corner < 8; corner++) {
                            var v = new Vector3d();
                            for (int axis = 0; axis < 3; axis++) v.setComponent(axis, e.getAsJsonArray((corner & (1 << axis)) == 0 ? "from" : "to").get(axis).getAsDouble());
                            if (e.has("rotation")) {
                                var r = e.getAsJsonObject("rotation"); var o = r.getAsJsonArray("origin");
                                var origin = new Vector3d(o.get(0).getAsDouble(), o.get(1).getAsDouble(), o.get(2).getAsDouble());
                                v.sub(origin).rotateY(Math.toRadians(r.get("angle").getAsDouble())).add(origin);
                            }
                            v.sub(8, 8, 8).div(16).rotateY(Math.PI).mul(pose.scale()).rotate(rotation).add(pose.x(), pose.y(), pose.z());
                            assertTrue(v.y >= 3, "the visible band must clear players at its lowest point");
                            assertTrue(Math.abs(v.x) < radius * 1.15 && Math.abs(v.z) < radius * 1.15, "culling bounds contain the whole band");
                        }
                      }
                    }
                }
            }
        }
    }
}
