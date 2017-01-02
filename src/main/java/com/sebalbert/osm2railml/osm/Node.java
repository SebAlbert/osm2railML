/**
 osm2railML - creating railML infrastructure from OpenStreetMap data
 Copyright (C) 2016  Sebastian Albert

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
import javax.xml.bind.annotation.XmlID;

/**
 * POJO for a Node in the sense of OSM data
 */

public class Node extends Taggable {

    @XmlAttribute @XmlID
    public final String id = null;
    /* Note that OSM XML may contain duplicate IDs in the sense of XML
       because nodes, ways and relations have their own numberings which overlap,
       and the "id" attributes are not prefixed to make them unique.
       Thus, using @XmlID is not generally possible for more than one class
       unless XmlJavaTypeAdapters are used or the XML is otherwise preprocessed
     */

    @XmlAttribute
    public final String lat = null, lon = null;

    private Node() { }

}
