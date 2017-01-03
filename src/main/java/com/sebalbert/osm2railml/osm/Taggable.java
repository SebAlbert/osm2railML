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
import javax.xml.bind.annotation.XmlElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for Node, Way and Relation, because all of them can have tags
 */
public abstract class Taggable {

    /**
     * OSM tags are implemented as List, not as Map, because it is easier with JAXB
     * and tags are usually few per element so iterating over them is hardly worse than the overhead of hashing
     */
    @XmlElement(name = "tag")
    public List<Tag> tags = new ArrayList<>();

    /**
     * Get the value of a certain key from OSM tags
     * @param key - the key to look up
     * @return - the value of the corresponding tag, or null if there is none
     */
    public String getTag(String key) {
        return tags.stream().filter(t -> t.key.equals(key)).map(t -> t.value).findAny().orElse(null);
    }

    public static class Tag {

        @XmlAttribute(name = "k")
        public String key;

        @XmlAttribute(name = "v")
        public String value;

    }

}
