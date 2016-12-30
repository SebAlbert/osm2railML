package com.github.aptd.osm2railml.osm;

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
public class COsmExtract {

    @XmlElement(name = "node")
    public final List<CNode> nodes = new ArrayList<CNode>();

    private COsmExtract() { }

    /**
     * Construct from XML file via JAXB
     * @param file - A File object of an XML file containing OSM data to be read
     * @return - a COsmExtract object representing the data from the XML file
     * @throws JAXBException
     */
    public static COsmExtract fromFile(File file) throws JAXBException {
        JAXBContext context = JAXBContext.newInstance(COsmExtract.class);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        return (COsmExtract) unmarshaller.unmarshal(file);
    }

}
