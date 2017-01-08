/**
 osm2railML - creating railML infrastructure from OpenStreetMap data
 Copyright (C) 2016-2017  Sebastian Albert

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.

 */

package com.sebalbert.osm2railml.osm;

import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicData;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlIDREF;
import javax.xml.bind.annotation.XmlTransient;
import java.util.LinkedList;


/**
 * POJO for a Way in the sense of OpenStreetMap data
 */
public class Way extends Taggable {

    @XmlAttribute
    public String id = null;

    @XmlElement(name = "nd")
    public LinkedList<NodeRef> nd = new LinkedList<NodeRef>();

    public void afterUnmarshal(Unmarshaller u, Object parent) {
        NodeRef prev = null;
        for (NodeRef r : nd) {
            if (prev != null) prev.next = r;
            r.prev = prev;
            prev = r;
        }
    }

    public static class NodeRef {

        public final static int FIRST = 1, LAST = -1, INTERIOR = 0;

        @XmlAttribute(name = "ref") @XmlIDREF
        public Node node;

        @XmlTransient
        public Way way;

        @XmlTransient
        public NodeRef prev, next;

        public void afterUnmarshal(Unmarshaller u, Object parent) {
            this.way = (Way) parent;
            this.node.wayRefs.add(this);
        }

        public int topologicalPosition() {
            if (prev == null) return FIRST;
            if (next == null) return LAST;
            return INTERIOR;
        }

        private GeodesicData geodesicData = null;
        private Double position = null;
        private Double azimuth = null;

        public GeodesicData geodesicData() {
            if (geodesicData == null && topologicalPosition() != FIRST)
                geodesicData = Geodesic.WGS84.Inverse(prev.node.lat, prev.node.lon, node.lat, node.lon);
            return geodesicData;
        }

        public double position() {
            if (position == null) position = topologicalPosition() == FIRST ? 0.0 :
                    prev.position() + geodesicData().s12;
            return position;
        }

        public double azimuth() {
            if (azimuth == null) azimuth = Math.toRadians(topologicalPosition() == FIRST ? next.geodesicData().azi1 :
                    geodesicData().azi2);
            return azimuth;
        }

        public double azimuthTowardsWay() {
            // turn around by 180° if this is the last point (because azimuth points in direction prev -> this)
            return azimuth() + (topologicalPosition() == LAST ? Math.PI : 0.0);
        }

    }

}

