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

package toxi.physics3d.behaviors;

import toxi.geom.Vec3D;
import toxi.physics3d.VerletParticle3D;

public class ConstantForceBehavior3D implements ParticleBehavior3D {

    protected Vec3D force;
    protected Vec3D scaledForce = new Vec3D();
    protected float timeStep;

    public ConstantForceBehavior3D(Vec3D force) {
        this.force = force;
    }

    @Override
    public void apply(VerletParticle3D p) {
        p.addForce(scaledForce);
    }

    /**
     *
     * @param timeStep
     */
    @Override
    public void configure(float timeStep) {
        this.timeStep = timeStep;
        setForce(force);
    }

    /**
     * @return the force
     */
    public Vec3D getForce() {
        return force;
    }

    /**
     * @param force
     *            the force to set
     */
    public void setForce(Vec3D force) {
        this.force = force;
        scaledForce = force.scale(timeStep);
    }
}
