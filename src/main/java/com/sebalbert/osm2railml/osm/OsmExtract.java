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
