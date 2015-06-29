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

package toxi.geom.mesh2d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import toxi.geom.Polygon2D;
import toxi.geom.Triangle2D;
import toxi.geom.Vec2D;

public class Voronoi {

    public static float DEFAULT_SIZE = 10000;

    protected DelaunayTriangulation delaunay;
    protected DelaunayTriangle initialTriangle;
    protected List<Vec2D> sites = new ArrayList<>();

    public Voronoi() {
        this(DEFAULT_SIZE);
    }

    public Voronoi(float size) {
        initialTriangle = new DelaunayTriangle(
                new DelaunayVertex(-size, -size), new DelaunayVertex(size,
                        -size), new DelaunayVertex(0, size));
        this.delaunay = new DelaunayTriangulation(initialTriangle);
    }

    public void addPoint(Vec2D p) {
        sites.add(p.copy());
        delaunay.delaunayPlace(new DelaunayVertex(p.x, p.y));
    }

    public void addPoints(Collection<? extends Vec2D> points) {
        points.stream().forEach((p) -> {
            addPoint(p);
        });
    }

    public List<Polygon2D> getRegions() {
        List<Polygon2D> regions = new LinkedList<>();
        HashSet<DelaunayVertex> done = new HashSet<>(
                initialTriangle);
        delaunay.stream().forEach((DelaunayTriangle triangle) -> {
            triangle.stream().filter((site) -> !(done.contains(site))).map((site) -> {
                done.add(site);
                return site;
            }).map((site) -> delaunay.surroundingTriangles(
                    site, triangle)).map((List<DelaunayTriangle> list) -> {
                        Polygon2D poly = new Polygon2D();
                        list.stream().map((tri) -> tri.getCircumcenter()).forEach((circumeter) -> {
                            poly.add(new Vec2D((float) circumeter.coord(0),
                                    (float) circumeter.coord(1)));
                });
                        return poly;
                    }).forEach((poly) -> {
                        regions.add(poly);
                    });
        });
        return regions;
    }

    public List<Vec2D> getSites() {
        return sites;
    }

    public List<Triangle2D> getTriangles() {
        List<Triangle2D> tris = new ArrayList<>();
        for (DelaunayTriangle t : delaunay) {
            tris.add(new Triangle2D(t.get(0).toVec2D(), t.get(1).toVec2D(), t
                    .get(2).toVec2D()));
        }
        return tris;
    }
}
