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

        String code = way.getTag("ref");
        if (code != null) t.setCode(code);

        ETrackBegin tB = new ETrackBegin();
        topo.setTrackBegin(tB);
        tB.setId("tB_" + way.id);
        setTrackBeginOrEnd(tB, way.nd.getFirst());
        ETrackEnd tE = new ETrackEnd();
        topo.setTrackEnd(tE);
        tE.setId("tE_" + way.id);
        setTrackBeginOrEnd(tE, way.nd.getLast());

        EConnections connections = new EConnections();
        topo.setConnections(connections);
        for (Way.NodeRef nd : way.nd) {
            final int waysAtNode = nd.node.wayRefs.size();
            final int topologicalPosition = nd.topologicalPosition();
            final ETrackNode beginOrEnd = topologicalPosition == Way.NodeRef.FIRST ? tB
                    : topologicalPosition == Way.NodeRef.LAST ? tE : null;
            // detect and model switches and crossings
            if (waysAtNode > 1) {
                Way.NodeRef partner = mutuallyOppositeEnd(nd);
                if (beginOrEnd != null && partner != null) makeConnection(beginOrEnd, nd, partner, true);
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
                        setConnectionIdAndRef(conn, thisConnId, thatConnId, true);
                    }
                } else {
                    // unless explicitly set as "railway_crossing", we assume a switch
                    String switchType = nd.node.getTag("railway:switch");
                    if (isCanonicalNodeRef(nd) &&
                            (topologicalPosition == Way.NodeRef.INTERIOR || (waysAtNode > 2 && partner != null))) {
                        String maxSpeedStraight = nd.node.getTag("railway:maxspeed:straight");
                        String maxSpeedDiverging = nd.node.getTag("railway:maxspeed:diverging");
                        BigDecimal maxSpeedDiv = maxSpeedDiverging == null ? null :
                                new BigDecimal(maxSpeedDiverging).setScale(1, BigDecimal.ROUND_DOWN);
                        BigDecimal maxSpeedStr = maxSpeedStraight == null ? null :
                                new BigDecimal(maxSpeedStraight).setScale(1, BigDecimal.ROUND_DOWN);
                        if (switchType != null && switchType.equals("double_slip")) {
                            // @TODO
                            continue;
                        }
                        if (switchType != null && switchType.equals("single_slip")) {
                            System.out.println("Warning: single_slip at " + nd.node.id +
                                    ", choosing arbitrary direction!");
                            // @TODO
                            continue;
                        }
                        String switchId = "switch_" + nd.node.id;
                        if (objectById.containsKey(switchId))
                            throw new RuntimeException("more than 1 straight way on sinmple switch node " + nd.node.id);
                        ESwitch sw = new ESwitch();
                        sw.setId(switchId);
                        sw.setPos(doubleToBigDecimal(nd.position(), 6));
                        objectById.put(switchId, sw);
                        for (Way.NodeRef other : nd.node.wayRefs) {
                            if (other == nd || other == partner) continue;
                            TSwitchConnectionData conn = new TSwitchConnectionData();
                            int orientation = inferSwitchOrientation(nd, other);
                            conn.setOrientation((orientation & INCOMING) > 0 ? "incoming" : "outgoing");
                            conn.setCourse((orientation & LEFT) > 0 ? "left" : "right");
                            makeConnection(conn, nd, other, false);
                            sw.getConnection().add(conn);
                            if (maxSpeedDiv != null) conn.setMaxSpeed(maxSpeedDiv);
                        }
                        connections.getSwitchOrCrossing().add(sw);
                    } else if (partner == null) {
                        if (switchType != null && switchType.equals("double_slip")) {
                            // @TODO
                            continue;
                        }
                        if (switchType != null && switchType.equals("single_slip")) {
                            // @TODO
                            continue;
                        }
                        setReferenceLater("switch_" + nd.node.id, sw -> {
                            for (TSwitchConnectionData swconn : ((ESwitch) sw).getConnection()) {
                                if (swconn.getRef() != null) continue;
                                if (swconn.getId().endsWith("_" + nd.node.id + "_" + way.id)) {
                                    TConnectionData conn = new TConnectionData();
                                    /*String[] tokens = swconn.getId().split("_");
                                    if (tokens.length != 4 || !tokens[0].equals("conn")
                                            || !tokens[2].equals(nd.node.id) || !tokens[3].equals(way.id))
                                        throw new RuntimeException("wrong id on switch connection: " + swconn.getId());
                                    conn.setId("conn_" + way.id + "_" + nd.node.id + "_" + tokens[1]);*/
                                    conn.setId(swconn.getId() + "_cont");
                                    conn.setRef(swconn);
                                    beginOrEnd.setConnection(conn);
                                    swconn.setRef(conn);
                                    return;
                                }
                            }
                            System.out.println("Error: could not hook up way " + way.id + " to switch " + nd.node.id);
                        });
                    }
                }
            }
        }
        return t;
    };

    // find most probable "partner" at a joining node by computing angles (via geodesic azimuth)
    private static Way.NodeRef oppositeEnd(Way.NodeRef nd) {
        return nd.node.wayRefs.stream()
                .filter(r -> r != nd && r.topologicalPosition() != Way.NodeRef.INTERIOR)
                .min(Comparator.comparingDouble(nd2 -> Math.cos(nd2.azimuthTowardsWay() - nd.azimuthTowardsWay())))
                .orElse(null);
    }

    private static Way.NodeRef mutuallyOppositeEnd(Way.NodeRef nd) {
        Way.NodeRef other = oppositeEnd(nd);
        if (other == null || oppositeEnd(other) != nd) return null;
        return other;
    }

    // a NodeRef is a canonical place to add elements if it's either an interior node
    // or the way ID is lexicographically smaller than its partner's (if any)
    private static boolean isCanonicalNodeRef(Way.NodeRef nd) {
        if (nd.topologicalPosition() == Way.NodeRef.INTERIOR) return true;
        Way.NodeRef partner = mutuallyOppositeEnd(nd);
        return partner == null || partner.way.id.compareTo(nd.way.id) > 0;
    }

    private final static int INCOMING = 1, OUTGOING = 2, LEFT = 4, RIGHT = 8;

    private static int inferSwitchOrientation(Way.NodeRef nd, Way.NodeRef nd2) {
        double sin = Math.sin((nd.azimuth() - nd2.azimuthTowardsWay()) * 0.5);
        boolean outgoing = Math.abs(sin) < 0.5;
        return (outgoing ? OUTGOING : INCOMING) | (sin < 0.0 ^ outgoing ? LEFT: RIGHT);
    }

    private static void makeConnection(TConnectionData conn, Way.NodeRef nd, Way.NodeRef other, boolean relink) {
        makeConnection(conn, nd, other, relink, "conn");
    }

    private static void makeConnection(TConnectionData conn, Way.NodeRef nd, Way.NodeRef other, boolean relink,
                                       String prefix) {
        String thisConnId = prefix + "_" + nd.way.id + "_" + nd.node.id + "_" + other.way.id;
        String thatConnId = prefix + "_" + other.way.id + "_" + nd.node.id + "_" + nd.way.id;
        setConnectionIdAndRef(conn, thisConnId, thatConnId, relink);
    }

    private static void makeConnection(ETrackNode trackNode, Way.NodeRef nd, Way.NodeRef other, boolean relink) {
        TConnectionData conn = new TConnectionData();
        makeConnection(conn, nd, other, relink);
        trackNode.setConnection(conn);
    }

    private static void setTrackBeginOrEnd(ETrackNode trackNode, Way.NodeRef nd) {
        trackNode.setPos(doubleToBigDecimal(nd.position(), 6));
        if (nd.node.wayRefs.size() == 1) {
            // start/end node is only contained in this way -> no connection, "border" of infrastructure
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
        // bad idea for parallel execution
        /*if (o != null) {
            c.accept(o);
            return;
        }*/
        List<Consumer<Object>> list = referencesToBeSet.get(id);
        if (list == null) {
            list = Collections.synchronizedList(new LinkedList<>());
            referencesToBeSet.put(id, list);
        }
        list.add(c);
    }

    private static void setConnectionIdAndRef(TConnectionData conn, String thisConnId, String thatConnId,
                                              boolean relink) {
        conn.setId(thisConnId);
        objectById.put(thisConnId, conn);
        if (relink) setReferenceLater(thatConnId, ref -> conn.setRef(ref));
    }

}
