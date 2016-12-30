package com.github.aptd.osm2railml;

import com.github.aptd.osm2railml.osm.CNode;
import com.github.aptd.osm2railml.osm.COsmExtract;

import javax.xml.bind.JAXBException;
import java.io.File;

/**
 * Main executable.
 *
 */
public class CMain
{

    /**
     * Reads XML from OpenStreetMap in order to generate railML infrastructure from it
     * @param args - first argument is expected to be a local (relative) filename
     * @throws JAXBException
     */
    public static void main( String[] args ) throws JAXBException {
        COsmExtract osm = COsmExtract.fromFile(new File(args[0]));
        for (CNode n : osm.nodes)
            System.out.println(n.id + ": " + n.lat + "/" + n.lon);

    }
}
