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

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Main object holding data from OpenStreetMap (Nodes, Ways, Relations)
 */
@XmlRootElement(name = "osm")
public class OsmExtract {

    @XmlElement(name = "node")
    public final List<Node> nodes = new ArrayList<Node>();

    @XmlElement(name = "way")
    public final List<Way> ways = new ArrayList<Way>();

    private OsmExtract() { }

    /**
     * Construct from XML file via JAXB
     * @param file - A File object of an XML file containing OSM data to be read
     * @return - an OsmExtract object representing the data from the XML file
     * @throws JAXBException
     */
    public static OsmExtract fromFile(File file) throws JAXBException {
        JAXBContext context = JAXBContext.newInstance(OsmExtract.class);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        return (OsmExtract) unmarshaller.unmarshal(file);
    }

}
