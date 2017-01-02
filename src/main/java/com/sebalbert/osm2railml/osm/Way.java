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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlIDREF;
import java.util.ArrayList;
import java.util.List;


/**
 * POJO for a Way in the sense of OpenStreetMap data
 */
public class Way extends Taggable {

    @XmlAttribute
    public String id = null;

    @XmlElement(name = "nd")
    public List<NodeRef> nd = new ArrayList<NodeRef>();

    public static class NodeRef {
        @XmlAttribute(name = "ref") @XmlIDREF
        public Node node;
    }

}

