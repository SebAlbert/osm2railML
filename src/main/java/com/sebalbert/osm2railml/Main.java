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
package com.sebalbert.osm2railml;

import com.sebalbert.osm2railml.osm.Node;
import com.sebalbert.osm2railml.osm.OsmExtract;
import com.sebalbert.osm2railml.osm.Way;
import org.railml.schemas._2016.*;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
    public static void main( String[] args ) throws JAXBException, MalformedURLException, SAXException {
        OsmExtract osm = OsmExtract.fromFile(new File(args[0]));
        for (Node n : osm.nodes)
            System.out.println(n.id + ": " + n.lat + "/" + n.lon + " [" + n.wayRefs.size() + " - " +
                    n.wayRefs.get(0).way.id);
        for (Way w : osm.ways)
            System.out.println(w.id + ":" + w.nd.size() + " - " + w.nd.get(0).node.id + " [" + w.tags.size() +
                    " - railway:" + w.getTag("railway"));

        // creation of railML structure to be marshalled in the end
        Infrastructure is = new Infrastructure();
        is.setId("is");
        ETracks tracks = new ETracks();
        is.setTracks(tracks);
        tracks.getTrack().addAll(osm.ways.parallelStream().map(w -> wayToTrack(w)).collect(Collectors.toList()));
        // create missing references now that all objects are created (c.f. the comments on objectById declaration)
        referencesToBeSet.entrySet().parallelStream().forEach(e -> e.getValue()
                .forEach(c -> c.accept(objectById.get(e.getKey()))));
        JAXBContext jc = JAXBContext.newInstance(Infrastructure.class);
        Marshaller marshaller = jc.createMarshaller();
        // SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        // Schema schema = schemaFactory.newSchema(
        //          new URL("http://www.railml.org/files/download/schemas/2016/railML-2.3/schema/infrastructure.xsd"));
        // marshaller.setSchema(schema);
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.marshal(is, System.out);
    }

    private static BigDecimal doubleToBigDecimal(double value, int scale) {
        return new BigDecimal(new BigInteger(Long.toString(Math.round(value * Math.pow(10, scale)))), scale);
    }

    // OSM Ways are a good fit for railML Tracks (1:1)
    private static ETrack wayToTrack(Way way) {
        ETrack t = new ETrack();
        t.setId("w_" + way.id);
        ETrackTopology topo = new ETrackTopology();
        t.setTrackTopology(topo);

        ETrackBegin tB = new ETrackBegin();
        topo.setTrackBegin(tB);
        tB.setId("tB_" + way.id);
        setTrackBeginOrEnd(tB, way.nd.getFirst());

        EConnections connections = new EConnections();
        topo.setConnections(connections);
        for (Way.NodeRef nd : way.nd) {
            // detect and model switches and crossings
            if (nd.node.wayRefs.size() > 1) {
                String nodeType = nd.node.getTag("railway");
                if (nodeType != null && nodeType.equals("railway_crossing")) {
                    // avoid setting a crossing at both respective ends of two sequentially joined tracks
                    if (!isCanonicalNodeRef(nd)) continue;
                    ECrossing crossing = new ECrossing();
                    crossing.setId("crossing_" + way.id + "_" + nd.node.id);
                    crossing.setPos(doubleToBigDecimal(nd.position(), 6));
                    for (Way.NodeRef otherWayRef : nd.node.wayRefs) {
                        if (otherWayRef == nd) continue;
                        // avoid setting a crossing at both respective ends of two sequentially joined tracks
                        if (!isCanonicalNodeRef(otherWayRef)) continue;
                        TSwitchConnectionData conn = new TSwitchConnectionData();
                        String thisConnId = "crossing_conn_" + way.id + "_" + nd.node.id + "_" + otherWayRef.way.id;
                        String thatConnId = "crossing_conn_" + otherWayRef.way.id + "_" + nd.node.id + "_" + way.id;
                        setConnectionIdAndRef(conn, thisConnId, thatConnId);
                    }
                } else {
                    // unless explicitly set as "railway_crossing", we assume a switch
                    // @TODO
                }
            }
        }
        ETrackEnd tE = new ETrackEnd();
        topo.setTrackEnd(tE);
        tE.setId("tE_" + way.id);
        setTrackBeginOrEnd(tE, way.nd.getLast());
        return t;
    };

    // find most probable "partner" at a joining node by computing angles (via geodesic azimuth)
    private static Way.NodeRef oppositeEnd(Way.NodeRef nd) {
        return nd.node.wayRefs.stream()
                .filter(r -> r != nd && r.topologicalPosition() != Way.NodeRef.INTERIOR)
                .min(Comparator.comparingDouble(nd2 -> Math.cos(nd2.azimuthTowardsWay() - nd.azimuthTowardsWay())))
                .orElse(null);
    }

    // a NodeRef is a canonical place to add elements if it's either an interior node
    // or the way ID is lexicographically smaller than its partner's (if any)
    private static boolean isCanonicalNodeRef(Way.NodeRef nd) {
        if (nd.topologicalPosition() == Way.NodeRef.INTERIOR) return true;
        Way.NodeRef partner = oppositeEnd(nd);
        return partner == null || oppositeEnd(partner) != nd || partner.way.id.compareTo(nd.way.id) > 0;
    }

    private static int inferSwitchOrientation(Way.NodeRef nd, Way.NodeRef ndSwitch) {
        double sin = Math.sin((nd.azimuth() - ndSwitch.azimuthTowardsWay()) * 0.5);
        boolean outgoing = Math.abs(sin) < 0.5;
        boolean left = sin > 0.0;

        return 0;
    }

    private static void setTrackBeginOrEnd(ETrackNode trackNode, Way.NodeRef nd) {
        trackNode.setPos(doubleToBigDecimal(nd.position(), 6));
        switch (nd.node.wayRefs.size()) {
            // start/end node is only contained in this way -> no connection, "border" of infrastructure
            case 1:
                String nodeType = nd.node.getTag("railway");
                if (nodeType != null && nodeType.equals("buffer_stop")) {
                    TBufferStop bufferStop = new TBufferStop();
                    bufferStop.setId("bufferStop_" + nd.node.id);
                    trackNode.setBufferStop(bufferStop);
                } else {
                    TOpenEnd openEnd = new TOpenEnd();
                    openEnd.setId("openEnd_" + nd.node.id);
                    trackNode.setOpenEnd(openEnd);
                }
                break;
            // start/end node is contained in 1 other way -> simple connection
            // (may be a switch on the other track if it's not the beginning/end of the other track,
            // but that's not important for the railML part of this track)
            case 2:
                Way.NodeRef otherWayRef = nd.node.wayRefs.stream().filter(r -> r.way != nd.way).findAny()
                        .orElseThrow(() -> new RuntimeException("Way " + nd.way.id + " contains a node twice"));
                TConnectionData conn = new TConnectionData();
                String thisConnId = "conn_" + nd.way.id + "_" + nd.node.id;
                String thatConnId = "conn_" + otherWayRef.way.id + "_" + nd.node.id;
                setConnectionIdAndRef(conn, thisConnId, thatConnId);
                trackNode.setConnection(conn);
            case 3:
                // @TODO
            case 4:
                // @TODO
            default:
                // @TODO
        }
    }

    // some railML objects that need to be referenced may not have been created before, so we need to
    // add those references after they have been created (e.g. after everything is created),
    // so we maintain a "registry" HashMap (objectById) and a "to do" list per ID (referencesToBeSet).
    // these are realised as "callback" closures so we do not need to remember and reflect on which field
    // of the referencing object the reference must be set
    private static Map<String, Object> objectById = Collections.synchronizedMap(new HashMap<>());
    private static Map<String, List<Consumer<Object>>> referencesToBeSet = Collections.synchronizedMap(new HashMap<>());
    private static void setReferenceLater(String id, Consumer<Object> c) {
        Object o = objectById.get(id);
        if (o != null) {
            c.accept(o);
            return;
        }
        List<Consumer<Object>> list = referencesToBeSet.get(id);
        if (list == null) {
            list = Collections.synchronizedList(new LinkedList<>());
            referencesToBeSet.put(id, list);
        }
        list.add(c);
    }

    private static void setConnectionIdAndRef(TConnectionData conn, String thisConnId, String thatConnId) {
        conn.setId(thisConnId);
        objectById.put(thisConnId, conn);
        setReferenceLater(thatConnId, ref -> conn.setRef(ref));
    }

}
