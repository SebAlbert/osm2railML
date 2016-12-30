package com.sebalbert.osm2railml;

import com.sebalbert.osm2railml.osm.Node;
import com.sebalbert.osm2railml.osm.OsmExtract;

import javax.xml.bind.JAXBException;
import java.io.File;

/**
 * Main executable.
 *
 */
public class Main
{

    /**
     * Reads XML from OpenStreetMap in order to generate railML infrastructure from it
     * @param args - first argument is expected to be a local (relative) filename
     * @throws JAXBException
     */
    public static void main( String[] args ) throws JAXBException {
        OsmExtract osm = OsmExtract.fromFile(new File(args[0]));
        for (Node n : osm.nodes)
            System.out.println(n.id + ": " + n.lat + "/" + n.lon);

    }
}
