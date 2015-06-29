/*
 *   __               .__       .__  ._____.           
 * _/  |_  _______  __|__| ____ |  | |__\_ |__   ______
 * \   __\/  _ \  \/  /  |/ ___\|  | |  || __ \ /  ___/
 *  |  | (  <_> >    <|  \  \___|  |_|  || \_\ \\___ \ 
 *  |__|  \____/__/\_ \__|\___  >____/__||___  /____  >
 *                   \/       \/             \/     \/ 
 *
 * Copyright (c) 2006-2011 Karsten Schmidt
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * http://creativecommons.org/licenses/LGPL/2.1/
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301, USA
 */

package toxi.geom.mesh;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import toxi.geom.AABB;
import toxi.geom.Intersector3D;
import toxi.geom.IsectData3D;
import toxi.geom.Matrix4x4;
import toxi.geom.Quaternion;
import toxi.geom.Ray3D;
import toxi.geom.ReadonlyVec3D;
import toxi.geom.Sphere;
import toxi.geom.Triangle3D;
import toxi.geom.TriangleIntersector;
import toxi.geom.Vec2D;
import toxi.geom.Vec3D;
import toxi.math.MathUtils;

/**
 * An extensible class to dynamically build, manipulate & export triangle
 * meshes. Meshes are built face by face. This implementation automatically
 * re-uses existing vertices and can generate smooth vertex normals. Vertice and
 * face lists are directly accessible for speed & convenience.
 */
public class TriangleMesh implements Mesh3D, Intersector3D {

    /**
     * Default size for vertex list
     */
    public static final int DEFAULT_NUM_VERTICES = 1000;

    /**
     * Default size for face list
     */
    public static final int DEFAULT_NUM_FACES = 3000;

    /**
     * Default stride setting used for serializing mesh properties into arrays.
     */
    public static final int DEFAULT_STRIDE = 4;

    protected static final Logger logger = Logger.getLogger(TriangleMesh.class
            .getName());

    /**
     * Mesh name
     */
    public String name;

    /**
     * Vertex buffer & lookup index when adding new faces
     */
    public LinkedHashMap<Vec3D, Vertex> vertices;

    /**
     * Face list
     */
    public ArrayList<Face> faces;

    protected AABB bounds;
    protected Vec3D centroid = new Vec3D();
    protected int numVertices;
    protected int numFaces;

    protected Matrix4x4 matrix = new Matrix4x4();
    protected TriangleIntersector intersector = new TriangleIntersector();

    protected int uniqueVertexID;

    public TriangleMesh() {
        this("untitled");
    }

    /**
     * Creates a new mesh instance with initial default buffer sizes.
     * 
     * @param name
     *            mesh name
     */
    public TriangleMesh(String name) {
        this(name, DEFAULT_NUM_VERTICES, DEFAULT_NUM_FACES);
    }

    /**
     * Creates a new mesh instance with the given initial buffer sizes. These
     * numbers are no limits and the mesh can be smaller or grow later on.
     * They're only used to initialise the underlying collections.
     * 
     * @param name
     *            mesh name
     * @param numV
     *            initial vertex buffer size
     * @param numF
     *            initial face list size
     */
    public TriangleMesh(String name, int numV, int numF) {
        init(name, numV, numF);
    }

    @Override
    public TriangleMesh addFace(Vec3D a, Vec3D b, Vec3D c) {
        return addFace(a, b, c, null, null, null, null);
    }

    @Override
    public TriangleMesh addFace(Vec3D a, Vec3D b, Vec3D c, Vec2D uvA,
            Vec2D uvB, Vec2D uvC) {
        return addFace(a, b, c, null, uvA, uvB, uvC);
    }

    @Override
    public TriangleMesh addFace(Vec3D a, Vec3D b, Vec3D c, Vec3D n) {
        return addFace(a, b, c, n, null, null, null);
    }

    @Override
    public TriangleMesh addFace(Vec3D a, Vec3D b, Vec3D c, Vec3D n, Vec2D uvA,
            Vec2D uvB, Vec2D uvC) {
        Vertex va = checkVertex(a);
        Vertex vb = checkVertex(b);
        Vertex vc = checkVertex(c);
        if (va.id == vb.id || va.id == vc.id || vb.id == vc.id) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "ignorning invalid face: {0},{1},{2}", new Object[]{a, b, c});
            }
        } else {
            if (n != null) {
                Vec3D nc = va.sub(vc).crossSelf(va.sub(vb));
                if (n.dot(nc) < 0) {
                    Vertex t = va;
                    va = vb;
                    vb = t;
                }
            }
            Face f = new Face(va, vb, vc, uvA, uvB, uvC);
            faces.add(f);
            numFaces++;
        }
        return this;
    }

    /**
     * Adds all faces from the given mesh to this one.
     * 
     * @param m
     *            source mesh instance
     * @return 
     */
    @Override
    public TriangleMesh addMesh(Mesh3D m) {
        m.getFaces().stream().forEach((f) -> {
            addFace(f.a, f.b, f.c, f.uvA, f.uvB, f.uvC);
        });
        return this;
    }

    @Override
    public AABB center(ReadonlyVec3D origin) {
        computeCentroid();
        Vec3D delta = origin != null ? origin.sub(centroid) : centroid
                .getInverted();
        vertices.values().stream().forEach((v) -> {
            v.addSelf(delta);
        });
        getBoundingBox();
        return bounds;
    }

    private Vertex checkVertex(Vec3D v) {
        Vertex vertex = vertices.get(v);
        if (vertex == null) {
            vertex = createVertex(v, uniqueVertexID++);
            vertices.put(vertex, vertex);
            numVertices++;
        }
        return vertex;
    }

    /**
     * Clears all counters, and vertex & face buffers.
     */
    @Override
    public TriangleMesh clear() {
        vertices.clear();
        faces.clear();
        bounds = null;
        numVertices = 0;
        numFaces = 0;
        uniqueVertexID = 0;
        return this;
    }

    @Override
    public Vec3D computeCentroid() {
        centroid.clear();
        vertices.values().stream().forEach((v) -> {
            centroid.addSelf(v);
        });
        return centroid.scaleSelf(1f / numVertices).copy();
    }

    /**
     * Re-calculates all face normals.
     */
    @Override
    public TriangleMesh computeFaceNormals() {
        faces.stream().forEach((f) -> {
            f.computeNormal();
        });
        return this;
    }

    /**
     * Computes the smooth vertex normals for the entire mesh.
     */
    @Override
    public TriangleMesh computeVertexNormals() {
        vertices.values().stream().forEach((v) -> {
            v.clearNormal();
        });
        faces.stream().map((f) -> {
            f.a.addFaceNormal(f.normal);
            return f;
        }).map((f) -> {
            f.b.addFaceNormal(f.normal);
            return f;
        }).forEach((f) -> {
            f.c.addFaceNormal(f.normal);
        });
        vertices.values().stream().forEach((v) -> {
            v.computeNormal();
        });
        return this;
    }

    /**
     * Creates a deep clone of the mesh. The new mesh name will have "-copy" as
     * suffix.
     * 
     * @return new mesh instance
     */
    public TriangleMesh copy() {
        TriangleMesh m = new TriangleMesh(name + "-copy", numVertices, numFaces);
        faces.stream().forEach((f) -> {
            m.addFace(f.a, f.b, f.c, f.normal, f.uvA, f.uvB, f.uvC);
        });
        return m;
    }

    protected Vertex createVertex(Vec3D v, int id) {
        return new Vertex(v, id);
    }

    @Override
    public TriangleMesh faceOutwards() {
        computeCentroid();
        faces.stream().forEach((f) -> {
            Vec3D n = f.getCentroid().sub(centroid);
            float dot = n.dot(f.normal);
            if (dot < 0) {
                f.flipVertexOrder();
            }
        });
        return this;
    }

    @Override
    public TriangleMesh flipVertexOrder() {
        faces.stream().map((f) -> {
            Vertex t = f.a;
            f.a = f.b;
            f.b = t;
            return f;
        }).map((f) -> {
            Vec2D tuv = f.uvA;
            f.uvA = f.uvB;
            f.uvB = tuv;
            return f;
        }).forEach((f) -> {
            f.normal.invert();
        });
        return this;
    }

    @Override
    public TriangleMesh flipYAxis() {
        transform(new Matrix4x4().scaleSelf(1, -1, 1));
        flipVertexOrder();
        return this;
    }

    @Override
    public AABB getBoundingBox() {
        final Vec3D minBounds = Vec3D.MAX_VALUE.copy();
        final Vec3D maxBounds = Vec3D.NEG_MAX_VALUE.copy();
        vertices.values().stream().map((v) -> {
            minBounds.minSelf(v);
            return v;
        }).forEach((v) -> {
            maxBounds.maxSelf(v);
        });
        bounds = AABB.fromMinMax(minBounds, maxBounds);
        return bounds;
    }

    @Override
    public Sphere getBoundingSphere() {
        float radius = 0;
        computeCentroid();
        for (Vertex v : vertices.values()) {
            radius = MathUtils.max(radius, v.distanceToSquared(centroid));
        }
        return new Sphere(centroid, (float) Math.sqrt(radius));
    }

    @Override
    public Vertex getClosestVertexToPoint(ReadonlyVec3D p) {
        Vertex closest = null;
        float minDist = Float.MAX_VALUE;
        for (Vertex v : vertices.values()) {
            float d = v.distanceToSquared(p);
            if (d < minDist) {
                closest = v;
                minDist = d;
            }
        }
        return closest;
    }

    /**
     * Creates an array of unravelled normal coordinates. For each vertex the
     * normal vector of its parent face is used. This is a convienence
     * invocation of {@link #getFaceNormalsAsArray(float[], int, int)} with a
     * default stride = 4.
     * 
     * @return array of xyz normal coords
     */
    public float[] getFaceNormalsAsArray() {
        return getFaceNormalsAsArray(null, 0, DEFAULT_STRIDE);
    }

    /**
     * Creates an array of unravelled normal coordinates. For each vertex the
     * normal vector of its parent face is used. This method can be used to
     * translate the internal mesh data structure into a format suitable for
     * OpenGL Vertex Buffer Objects (by choosing stride=4). For more detail,
     * please see {@link #getMeshAsVertexArray(float[], int, int)}
     * 
     * @see #getMeshAsVertexArray(float[], int, int)
     * 
     * @param normals
     *            existing float array or null to automatically create one
     * @param offset
     *            start index in array to place normals
     * @param stride
     *            stride/alignment setting for individual coordinates (min value
     *            = 3)
     * @return array of xyz normal coords
     */
    public float[] getFaceNormalsAsArray(float[] normals, int offset, int stride) {
        stride = MathUtils.max(stride, 3);
        if (normals == null) {
            normals = new float[faces.size() * 3 * stride];
        }
        int i = offset;
        for (Face f : faces) {
            normals[i] = f.normal.x;
            normals[i + 1] = f.normal.y;
            normals[i + 2] = f.normal.z;
            i += stride;
            normals[i] = f.normal.x;
            normals[i + 1] = f.normal.y;
            normals[i + 2] = f.normal.z;
            i += stride;
            normals[i] = f.normal.x;
            normals[i + 1] = f.normal.y;
            normals[i + 2] = f.normal.z;
            i += stride;
        }
        return normals;
    }

    @Override
    public List<Face> getFaces() {
        return faces;
    }

    /**
     * Builds an array of vertex indices of all faces. Each vertex ID
     * corresponds to its position in the {@link #vertices} HashMap. The
     * resulting array will be 3 times the face count.
     * 
     * @return array of vertex indices
     */
    public int[] getFacesAsArray() {
        int[] faceList = new int[faces.size() * 3];
        int i = 0;
        for (Face f : faces) {
            faceList[i++] = f.a.id;
            faceList[i++] = f.b.id;
            faceList[i++] = f.c.id;
        }
        return faceList;
    }

    @Override
    public IsectData3D getIntersectionData() {
        return intersector.getIntersectionData();
    }

    /**
     * Creates an array of unravelled vertex coordinates for all faces using a
     * stride setting of 4, resulting in a serialized version of all mesh vertex
     * coordinates suitable for VBOs.
     * 
     * @see #getMeshAsVertexArray(float[], int, int)
     * @return float array of vertex coordinates
     */
    public float[] getMeshAsVertexArray() {
        return getMeshAsVertexArray(null, 0, DEFAULT_STRIDE);
    }

    /**
     * Creates an array of unravelled vertex coordinates for all faces. This
     * method can be used to translate the internal mesh data structure into a
     * format suitable for OpenGL Vertex Buffer Objects (by choosing stride=4).
     * The order of the array will be as follows:
     * 
     * <ul>
     * <li>Face 1:
     * <ul>
     * <li>Vertex #1
     * <ul>
     * <li>x</li>
     * <li>y</li>
     * <li>z</li>
     * <li>[optional empty indices to match stride setting]</li>
     * </ul>
     * </li>
     * <li>Vertex #2
     * <ul>
     * <li>x</li>
     * <li>y</li>
     * <li>z</li>
     * <li>[optional empty indices to match stride setting]</li>
     * </ul>
     * </li>
     * <li>Vertex #3
     * <ul>
     * <li>x</li>
     * <li>y</li>
     * <li>z</li>
     * <li>[optional empty indices to match stride setting]</li>
     * </ul>
     * </li>
     * </ul>
     * <li>Face 2:
     * <ul>
     * <li>Vertex #1</li>
     * <li>...etc.</li>
     * </ul>
     * </ul>
     * 
     * @param verts
     *            an existing target array or null to automatically create one
     * @param offset
     *            start index in arrtay to place vertices
     * @param stride
     *            stride/alignment setting for individual coordinates
     * @return array of xyz vertex coords
     */
    public float[] getMeshAsVertexArray(float[] verts, int offset, int stride) {
        stride = MathUtils.max(stride, 3);
        if (verts == null) {
            verts = new float[faces.size() * 3 * stride];
        }
        int i = offset;
        for (Face f : faces) {
            verts[i] = f.a.x;
            verts[i + 1] = f.a.y;
            verts[i + 2] = f.a.z;
            i += stride;
            verts[i] = f.b.x;
            verts[i + 1] = f.b.y;
            verts[i + 2] = f.b.z;
            i += stride;
            verts[i] = f.c.x;
            verts[i + 1] = f.c.y;
            verts[i + 2] = f.c.z;
            i += stride;
        }
        return verts;
    }

    public float[] getNormalsForUniqueVerticesAsArray() {
        float[] normals = new float[numVertices * 3];
        int i = 0;
        for (Vertex v : vertices.values()) {
            normals[i++] = v.normal.x;
            normals[i++] = v.normal.y;
            normals[i++] = v.normal.z;
        }
        return normals;
    }

    @Override
    public int getNumFaces() {
        return numFaces;
    }

    @Override
    public int getNumVertices() {
        return numVertices;
    }

    public TriangleMesh getRotatedAroundAxis(Vec3D axis, float theta) {
        return copy().rotateAroundAxis(axis, theta);
    }

    public TriangleMesh getRotatedX(float theta) {
        return copy().rotateX(theta);
    }

    public TriangleMesh getRotatedY(float theta) {
        return copy().rotateY(theta);
    }

    public TriangleMesh getRotatedZ(float theta) {
        return copy().rotateZ(theta);
    }

    public TriangleMesh getScaled(float scale) {
        return copy().scale(scale);
    }

    public TriangleMesh getScaled(Vec3D scale) {
        return copy().scale(scale);
    }

    public TriangleMesh getTranslated(Vec3D trans) {
        return copy().translate(trans);
    }

    public float[] getUniqueVerticesAsArray() {
        float[] verts = new float[numVertices * 3];
        int i = 0;
        for (Vertex v : vertices.values()) {
            verts[i++] = v.x;
            verts[i++] = v.y;
            verts[i++] = v.z;
        }
        return verts;
    }

    public Vertex getVertexAtPoint(Vec3D v) {
        return vertices.get(v);
    }

    public Vertex getVertexForID(int id) {
        Vertex vertex = null;
        for (Vertex v : vertices.values()) {
            if (v.id == id) {
                vertex = v;
                break;
            }
        }
        return vertex;
    }

    /**
     * Creates an array of unravelled vertex normal coordinates for all faces.
     * Uses default stride = 4.
     * 
     * @see #getVertexNormalsAsArray(float[], int, int)
     * @return array of xyz normal coords
     */
    public float[] getVertexNormalsAsArray() {
        return getVertexNormalsAsArray(null, 0, DEFAULT_STRIDE);
    }

    /**
     * Creates an array of unravelled vertex normal coordinates for all faces.
     * This method can be used to translate the internal mesh data structure
     * into a format suitable for OpenGL Vertex Buffer Objects (by choosing
     * stride=4). For more detail, please see
     * {@link #getMeshAsVertexArray(float[], int, int)}
     * 
     * @see #getMeshAsVertexArray(float[], int, int)
     * 
     * @param normals
     *            existing float array or null to automatically create one
     * @param offset
     *            start index in array to place normals
     * @param stride
     *            stride/alignment setting for individual coordinates (min value
     *            = 3)
     * @return array of xyz normal coords
     */
    public float[] getVertexNormalsAsArray(float[] normals, int offset,
            int stride) {
        stride = MathUtils.max(stride, 3);
        if (normals == null) {
            normals = new float[faces.size() * 3 * stride];
        }
        int i = offset;
        for (Face f : faces) {
            normals[i] = f.a.normal.x;
            normals[i + 1] = f.a.normal.y;
            normals[i + 2] = f.a.normal.z;
            i += stride;
            normals[i] = f.b.normal.x;
            normals[i + 1] = f.b.normal.y;
            normals[i + 2] = f.b.normal.z;
            i += stride;
            normals[i] = f.c.normal.x;
            normals[i + 1] = f.c.normal.y;
            normals[i + 2] = f.c.normal.z;
            i += stride;
        }
        return normals;
    }

    @Override
    public Collection<Vertex> getVertices() {
        return vertices.values();
    }

    protected void handleSaveAsSTL(STLWriter stl, boolean useFlippedY) {
        if (useFlippedY) {
            stl.setScale(new Vec3D(1, -1, 1));
            faces.stream().forEach((f) -> {
                stl.face(f.a, f.b, f.c, f.normal, STLWriter.DEFAULT_RGB);
            });
        } else {
            faces.stream().forEach((f) -> {
                stl.face(f.b, f.a, f.c, f.normal, STLWriter.DEFAULT_RGB);
            });
        }
        stl.endSave();
        logger.log(Level.INFO, "{0} faces written", numFaces);
    }

    /**
     *
     * @param name
     * @param numV
     * @param numF
     * @return
     */
    @Override
    public TriangleMesh init(String name, int numV, int numF) {
        setName(name);
        vertices = new LinkedHashMap<>(numV, 1.5f, false);
        faces = new ArrayList<>(numF);
        return this;
    }

    @Override
    public boolean intersectsRay(Ray3D ray) {
        Triangle3D tri = intersector.getTriangle();
        return faces.stream().map((f) -> {
            tri.set(f.a, f.b, f.c);
            return f;
        }).anyMatch((_item) -> (intersector.intersectsRay(ray)));
    }

    public Triangle3D perforateFace(Face f, float size) {
        Vec3D centrd = f.getCentroid();
        float d = 1 - size;
        Vec3D a2 = f.a.interpolateTo(centrd, d);
        Vec3D b2 = f.b.interpolateTo(centrd, d);
        Vec3D c2 = f.c.interpolateTo(centrd, d);
        removeFace(f);
        addFace(f.a, b2, a2);
        addFace(f.a, f.b, b2);
        addFace(f.b, c2, b2);
        addFace(f.b, f.c, c2);
        addFace(f.c, a2, c2);
        addFace(f.c, f.a, a2);
        return new Triangle3D(a2, b2, c2);
    }

    /**
     * Rotates the mesh in such a way so that its "forward" axis is aligned with
     * the given direction. This version uses the positive Z-axis as default
     * forward direction.
     * 
     * @param dir
     *            new target direction to point in
     * @return itself
     */
    public TriangleMesh pointTowards(ReadonlyVec3D dir) {
        return transform(Quaternion.getAlignmentQuat(dir, Vec3D.Z_AXIS)
                .toMatrix4x4(matrix), true);
    }

    /**
     * Rotates the mesh in such a way so that its "forward" axis is aligned with
     * the given direction. This version allows to specify the forward
     * direction.
     * 
     * @param dir
     *            new target direction to point in
     * @param forward
     *            current forward axis
     * @return itself
     */
    public TriangleMesh pointTowards(ReadonlyVec3D dir, ReadonlyVec3D forward) {
        return transform(
                Quaternion.getAlignmentQuat(dir, forward).toMatrix4x4(matrix),
                true);
    }

    public void removeFace(Face f) {
        faces.remove(f);
    }

    public TriangleMesh rotateAroundAxis(Vec3D axis, float theta) {
        return transform(matrix.identity().rotateAroundAxis(axis, theta));
    }

    public TriangleMesh rotateX(float theta) {
        return transform(matrix.identity().rotateX(theta));
    }

    public TriangleMesh rotateY(float theta) {
        return transform(matrix.identity().rotateY(theta));
    }

    public TriangleMesh rotateZ(float theta) {
        return transform(matrix.identity().rotateZ(theta));
    }

    /**
     * Saves the mesh as OBJ format by appending it to the given mesh
     * {@link OBJWriter} instance.
     * 
     * @param obj
     */
    public void saveAsOBJ(OBJWriter obj) {
        saveAsOBJ(obj, true);
    }

    public void saveAsOBJ(OBJWriter obj, boolean saveNormals) {
        int vOffset = obj.getCurrVertexOffset() + 1;
        int nOffset = obj.getCurrNormalOffset() + 1;
        logger.log(Level.INFO, "writing OBJMesh: {0}", this.toString());
        obj.newObject(name);
        // vertices
        vertices.values().stream().forEach((v) -> {
            obj.vertex(v);
        });
        // faces
        if (saveNormals) {
            // normals
            vertices.values().stream().forEach((v) -> {
                obj.normal(v.normal);
            });
            faces.stream().forEach((f) -> {
                obj.faceWithNormals(f.b.id + vOffset, f.a.id + vOffset, f.c.id
                        + vOffset, f.b.id + nOffset, f.a.id + nOffset, f.c.id
                                + nOffset);
            });
        } else {
            faces.stream().forEach((f) -> {
                obj.face(f.b.id + vOffset, f.a.id + vOffset, f.c.id + vOffset);
            });
        }
    }

    /**
     * Saves the mesh as OBJ format to the given {@link OutputStream}. Currently
     * no texture coordinates are supported or written.
     * 
     * @param stream
     */
    public void saveAsOBJ(OutputStream stream) {
        OBJWriter obj = new OBJWriter();
        obj.beginSave(stream);
        saveAsOBJ(obj);
        obj.endSave();
    }

    /**
     * Saves the mesh as OBJ format to the given file path. Existing files will
     * be overwritten.
     * 
     * @param path
     */
    public void saveAsOBJ(String path) {
        saveAsOBJ(path, true);
    }

    public void saveAsOBJ(String path, boolean saveNormals) {
        OBJWriter obj = new OBJWriter();
        obj.beginSave(path);
        saveAsOBJ(obj, saveNormals);
        obj.endSave();
    }

    /**
     * Saves the mesh as binary STL format to the given {@link OutputStream}.
     * 
     * @param stream
     * @see #saveAsSTL(OutputStream, boolean)
     */
    public final void saveAsSTL(OutputStream stream) {
        saveAsSTL(stream, false);
    }

    /**
     * Saves the mesh as binary STL format to the given {@link OutputStream}.
     * The exported mesh can optionally have it's Y axis flipped by setting the
     * useFlippedY flag to true.
     * 
     * @param stream
     * @param useFlippedY
     */
    public final void saveAsSTL(OutputStream stream, boolean useFlippedY) {
        STLWriter stl = new STLWriter();
        stl.beginSave(stream, numFaces);
        handleSaveAsSTL(stl, useFlippedY);
    }

    /**
     * Saves the mesh as binary STL format to the given {@link OutputStream} and
     * using the supplied {@link STLWriter} instance. Use this method to export
     * data in a custom {@link STLColorModel}. The exported mesh can optionally
     * have it's Y axis flipped by setting the useFlippedY flag to true.
     * 
     * @param stream
     * @param stl
     * @param useFlippedY
     */
    public final void saveAsSTL(OutputStream stream, STLWriter stl,
            boolean useFlippedY) {
        stl.beginSave(stream, numFaces);
        handleSaveAsSTL(stl, useFlippedY);
    }

    /**
     * Saves the mesh as binary STL format to the given file path. Existing
     * files will be overwritten.
     * 
     * @param fileName
     */
    public final void saveAsSTL(String fileName) {
        saveAsSTL(fileName, false);
    }

    /**
     * Saves the mesh as binary STL format to the given file path. The exported
     * mesh can optionally have it's Y axis flipped by setting the useFlippedY
     * flag to true. Existing files will be overwritten.
     * 
     * @param fileName
     * @param useFlippedY
     */
    public final void saveAsSTL(String fileName, boolean useFlippedY) {
        saveAsSTL(fileName, new STLWriter(), useFlippedY);
    }

    public final void saveAsSTL(String fileName, STLWriter stl,
            boolean useFlippedY) {
        stl.beginSave(fileName, numFaces);
        handleSaveAsSTL(stl, useFlippedY);
    }

    public TriangleMesh scale(float scale) {
        return transform(matrix.identity().scaleSelf(scale));
    }

    public TriangleMesh scale(float x, float y, float z) {
        return transform(matrix.identity().scaleSelf(x, y, z));
    }

    public TriangleMesh scale(Vec3D scale) {
        return transform(matrix.identity().scaleSelf(scale));
    }

    @Override
    public TriangleMesh setName(String name) {
        this.name = name;
        return this;
    }

    @Override
    public String toString() {
        return "TriangleMesh: " + name + " vertices: " + getNumVertices()
                + " faces: " + getNumFaces();
    }

    public WETriangleMesh toWEMesh() {
        return new WETriangleMesh(name, vertices.size(), faces.size())
                .addMesh(this);
    }

    /**
     * Applies the given matrix transform to all mesh vertices and updates all
     * face normals.
     * 
     * @param mat
     * @return itself
     */
    public TriangleMesh transform(Matrix4x4 mat) {
        return transform(mat, true);
    }

    /**
     * Applies the given matrix transform to all mesh vertices. If the
     * updateNormals flag is true, all face normals are updated automatically,
     * however vertex normals need a manual update.
     * 
     * @param mat
     * @param updateNormals
     * @return itself
     */
    public TriangleMesh transform(Matrix4x4 mat, boolean updateNormals) {
        vertices.values().stream().forEach((v) -> {
            v.set(mat.applyTo(v));
        });
        if (updateNormals) {
            computeFaceNormals();
        }
        return this;
    }

    public TriangleMesh translate(float x, float y, float z) {
        return transform(matrix.identity().translateSelf(x, y, z));
    }

    public TriangleMesh translate(Vec3D trans) {
        return transform(matrix.identity().translateSelf(trans));
    }

    public TriangleMesh updateVertex(Vec3D orig, Vec3D newPos) {
        Vertex v = vertices.get(orig);
        if (v != null) {
            vertices.remove(v);
            v.set(newPos);
            vertices.put(v, v);
        }
        return this;
    }
}
