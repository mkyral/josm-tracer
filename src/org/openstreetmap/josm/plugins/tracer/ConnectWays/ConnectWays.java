/**
 *  Tracer - plugin for JOSM
 *  Dirk Bruenig, Marian Kyral
 *
 *  Improved version from Tracer2 plugin - Many thanks to Dirk Bruenig
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along
 *  with this program; if not, write to the Free Software Foundation, Inc.,
 *  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package org.openstreetmap.josm.plugins.tracer;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Collection;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.ChangeCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.DeleteCommand;
import org.openstreetmap.josm.command.MoveCommand;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.tools.Pair;

import org.openstreetmap.josm.plugins.tracer.TracerDebug;
import org.openstreetmap.josm.plugins.tracer.WaysList;

public class ConnectWays {

// Distance constants
    static double s_dDoubleDiff       = 0.0000001; // Maximal difference for double comparison
    static double s_dMinDistance      = 0.0000005; // Minimal distance, for objects
    static double s_dMinDistanceN2N   = 0.0000005;  // Minimal distance, when nodes are merged
    static double s_dMinDistanceN2oW  = 0.000001;  // Minimal distance, when node is connected to other way
    static double s_dMaxDistanceN2W  = 0.0005;  // Maximal distance to search ways in the near neighbourhood

    static WaysList s_Ways = new WaysList(); // New or updated way
//     static List<Way>  s_oWays; // List of all ways we will work with
    static List<Node> s_oNodes; // List of all nodes we will work with
    static List<Node> sharedNodes; // List of nodes that are part of more ways

    static List<Way>  secondaryWays; // List of ways connected to connected ways (and are not myWay)
    static List<Node> secondarydNodes; // List of nodes of ways connected to connected ways ;-)

    static List<Way>  s_overlapWays; // List of ways that overlap traced way (s_oWay)

// Obsolete
//     static ServerParam s_oParam;
//     static boolean s_bCtrl;
//     static boolean s_bAlt;

    static int maxDebugLevel = 0;

    static boolean s_bAddNewWay;

    static Relation parentRelation;

    final static int BUILDING = 1;
    final static int LANDUSE = 2;
    final static int LANDUSE_RUAIN = 3;
    final static int UNKNOWN = -1;

    private static int wayType; // Type of the way - building or landuse
    private static boolean wayIsForest;  // True if way has key landuse=forest


// -----------------------------------------------------------------------------------------------------
// Public methods
// -----------------------------------------------------------------------------------------------------

    /**
     * Return connected way.
     * @return Connected Way.
     */
    public static Way getWay() {
      return s_Ways.get(0);
    }

    /**
     * Try connect way to other way with the same key.
     * @param newWay The traced way that should replace the old way and connect to neighbour ways.
     * @param pos Position where way was traced
     * @param ctrl Dont't connect to neighbour ways.
     * @param alt Obsolete - Original meaning: Dont't add building tag.
     * @param source The content of the source tag to be added.
     * @return Commands.
     */
    public static Command connect(Way newWay, LatLon pos, boolean ctrl, boolean alt, String source) {
      return connect(newWay, (Relation) null, pos, ctrl, alt, source);
    }

    /**
     * Try connect way to other way with the same key.
     * @param newWay The traced way that should replace the old way and connect to neighbour ways.
     * @param wayRelation Relation which way is outer member
     * @param pos Position where way was traced
     * @param ctrl Dont't connect to neighbour ways.
     * @param alt Obsolete - Original meaning: Dont't add building tag.
     * @param source The content of the source tag to be added.
     * @return Commands.
     */
    public static Command connect(Way newWay, Relation wayRelation, LatLon pos, boolean ctrl, boolean alt, String source) {
        debugMsg("-- connect() --");

        LinkedList<Command> cmds = new LinkedList<Command>();
        LinkedList<Command> cmds2 = new LinkedList<Command>();
        LinkedList<Command> xcmds = new LinkedList<Command>();

        Way s_oWay = new Way();
        Way s_oWayOld = new Way();

        // Obsolete - at least for now
//         s_bCtrl = ctrl;
//         s_bAlt = alt;

//         calcDistance();

        wayIsForest = false;
        // Determine type of way
        if (newWay.hasKey("building")) {
          wayType = BUILDING;
        } else if (newWay.hasKey("landuse") && source.equals("cuzk:ruian")) {
          wayType = LANDUSE_RUAIN;
        } else if (newWay.hasKey("landuse")) {
          wayType = LANDUSE;
          if (newWay.getKeys().get("landuse").equals("forest") &&
              newWay.hasKey("crop")
             ) {
                wayIsForest = true;
          }
        } else {
          // check for parent relations
          if (wayRelation != null) {
            System.out.println("Relation Keys: " + wayRelation.getKeys().toString());
            if (wayRelation.hasKey("building")) {
              System.out.println("Relation: building");
              wayType = BUILDING;
              parentRelation = wayRelation;
            } else if (wayRelation.hasKey("landuse")) {
              System.out.println("Relation: landuse");
              wayType = LANDUSE;
              parentRelation = wayRelation;
              if (wayRelation.getKeys().get("landuse").equals("forest") &&
                  wayRelation.hasKey("crop")
                ) {
                    wayIsForest = true;
                }
            } else {
              wayType = UNKNOWN;
              parentRelation = (Relation) null;
            }
          }
        }

        switch (wayType) {
          case BUILDING: System.out.println("Way is: Ruian building"); break;
          case LANDUSE: System.out.println("Way is: landuse"); break;
          case LANDUSE_RUAIN: System.out.println("Way is: Ruian landuse"); break;
          case UNKNOWN: System.out.println("Way is: unknown"); break;
        }

        s_Ways = new WaysList();
        s_Ways.add(newWay);

        getNodes(newWay);
        getWays(newWay);

        Way oldWay = getOldWay(pos);
        if (oldWay != null && s_Ways.indexOf(oldWay) > 0) {
          s_oWayOld = new Way (oldWay);
          s_Ways.setAsMyWay(s_oWayOld);
        } else {
          s_oWayOld = null;
        }

//         getSharedNodes(s_oWayOld);
        if (s_oWayOld == null) {
          s_bAddNewWay = true;
          if (wayType == BUILDING || wayType == LANDUSE_RUAIN) {
            cmds.add(new AddCommand(newWay));
          }
          s_oWay = new Way( newWay );
        } else {
            s_bAddNewWay = false;
            /*
            * Compare ways
            * Do not continue when way is traced again.
            * Old and new ways are equal - have the same count
            * of nodes and all nodes are on the same place
            */
            int o, n, nodesCount, nodesFound;
            nodesFound = 0;
            nodesCount = newWay.getNodesCount();
            // 1) have the same numbers of nodes?
            if (newWay.getNodesCount() == s_oWayOld.getNodesCount()) {
              debugMsg("   Old and New ways have " + s_oWayOld.getNodesCount() + " nodes");
              // 2) All nodes have the same coordination
              outer: for (n = 0; n < nodesCount; n++) {
                Node newNode = newWay.getNode(n);
                debugMsg("    New.Node(" + n + ") = " + newNode.getCoor().toDisplayString());
                inner: for (o = 0; o < nodesCount; o++) {
                  Node oldNode = s_oWayOld.getNode(o);
                  debugMsg("     -> Old.Node(" + o + ") = " + oldNode.getCoor().toDisplayString());
                  if (oldNode.getCoor().equalsEpsilon(newNode.getCoor())) {
                    debugMsg("     Nodes: New(" + n + ") and Old(" + o + ") are equal.");
                    nodesFound += 1;
                    continue outer;
                  }
                }
              }

              debugMsg("   nodesCount = " + nodesCount + "; nodesFound = " + nodesFound);
              if (nodesCount == nodesFound) {
                debugMsg("   Ways are equal!");
                return new SequenceCommand("Nothing", (Command) null);
              }
            }

            // Ways are different - merging
            debugMsg("   Ways are NOT equal!");
            debugMsg("   -------------------");

            // Create a working copy of the oldWay
            Way tempWay = new Way(s_oWayOld);
            debugMsg("s_oWayOld: " + s_oWayOld);

            // Add New nodes
            for (int i = 0; i < newWay.getNodesCount(); i++) {
              tempWay.addNode(tempWay.getNodesCount(), newWay.getNode(i));
              s_oNodes.add(newWay.getNode(i));
            }

            // Remove Old nodes
            for (int i = 0; i < s_oWayOld.getNodesCount() - 1; i++) {
                tempWay.removeNode( s_oWayOld.getNode(i) );
            }

            replaceWayInList(s_oWayOld, tempWay);
            // Remove old nodes from list of working nodes list
            for (int i = 0; i < s_oWayOld.getNodesCount() - 1; i++) {
              Node nd = s_oWayOld.getNode(i);
              List<Way> ways = getWaysOfNode(nd);
              if (ways.size() == 0 && ! nd.isOutsideDownloadArea() && ! nd.isConnectionNode()) {
                  debugMsg("    Delete node: " + nd);
                  cmds2.add(new DeleteCommand(nd));
                  s_oNodes.remove(nd);
              }

            }
            s_oWay = tempWay;
            s_oWay = updateKeys(s_oWay, newWay, source);
            replaceWayInList(s_oWayOld, s_oWay);
            debugMsg("updatedWay: " + s_oWay);
        }
        xcmds.add(new ChangeCommand(s_Ways.getOriginalWay(s_oWay), s_oWay));
        xcmds.addAll(cmds2);

        cmds.add(new SequenceCommand(tr("Trace"), xcmds));

        if (! ctrl) {
          // Modify the way
          if (wayType == BUILDING) {
            debugMsg("");
            debugMsg("-----------------------------------------");
            xcmds = new LinkedList<Command>(removeFullyCoveredWays());
            if (xcmds.size() > 0) {
              cmds.add(new SequenceCommand(tr("Remove Fully covered ways"), xcmds));
            }
          }

          debugMsg("");
          debugMsg("-----------------------------------------");
          xcmds = new LinkedList<Command>(mergeWithExistingNodes());
          if (xcmds.size() > 0) {
            cmds.add(new SequenceCommand(tr("Connect to other ways"), xcmds));
          }

          debugMsg("");
          debugMsg("-----------------------------------------");
          xcmds = new LinkedList<Command>(fixOverlapedWays());
          if (xcmds.size() > 0) {
            cmds.add(new SequenceCommand(tr("Fix overlaped ways"), xcmds));
          }

  //         if (wayType == BUILDING) {
  //           debugMsg("");
  //           debugMsg("-----------------------------------------");
  //           xcmds = new LinkedList<Command>(removeSpareNodes());
  //           if (xcmds.size() > 0) {
  //             cmds.add(new SequenceCommand(tr("Remove spare nodes"), xcmds));
  //           }
  //         }
        }

        debugMsg("-----------------------------------------");

//         new TracerDebug().OutputCommands(cmds);

        Command cmd = new SequenceCommand(tr("Trace object"), cmds);

        return cmd;
    }

// -----------------------------------------------------------------------------------------------------
// Private methods
// -----------------------------------------------------------------------------------------------------

    /**
     *  Print debug messages - default level is zero
     *  @param msg mesage
     */
    private static void debugMsg(String msg) {
      debugMsg(msg, 0);
    }

    private static void listWays() {

      debugMsg("");
      debugMsg("  --> List of ways: ");
      for (Way w : s_Ways.getWays()) {
        debugMsg(new TracerDebug().FormatPrimitive(w.toString()));
        for(Map.Entry<String, String> entry : w.getKeys().entrySet()) {
          debugMsg("          " + entry.getKey() + " = " + entry.getValue());
        }
      }
    }

    /**
     *  Print debug messages
     *  @param msg mesage
     *  @param level debug level of the message - From 0 - important to up
     */
    private static void debugMsg(String msg, int level) {
      if (level <= maxDebugLevel) {
        System.out.println(msg);
      }
    }

    /**
     *  Replace oldWay by newWay in list of working ways
     *  @param oldWay way to be replaced
     *  @param newWay way to replace
     */
    private static void replaceWayInList(Way oldWay, Way newWay) {
      debugMsg("  -- replaceWayInList() --");
//       debugMsg("     oldWay: " + oldWay);
//       debugMsg("     newWay: " + newWay);

      s_Ways.updateWay(oldWay, newWay);
    }

    /**
     *  Replace oldNode by newNode in list of working nodess
     *  @param oldNode way to be replaced
     *  @param newNode way to replace
     */
    private static void replaceNodeInList(Node oldNode, Node newNode) {
      s_oNodes.remove(oldNode);
      s_oNodes.add(newNode);
    }

    /**
     *  Calculate minimal distances
     */
    private static void calcDistance()
    {
//        double dTileSize = Double.parseDouble(s_oParam.getTileSize());
//        double dResolution = Double.parseDouble(s_oParam.getResolution());
//        double dMin = dTileSize / dResolution;

      debugMsg("-- calcDistance() --");
      double dMin = (double) 0.0004 / (double) 2048;

      s_dMinDistance = dMin * 30;
      s_dMinDistanceN2N = dMin * 2.5;
      s_dMinDistanceN2oW = dMin * 5;
      s_dMaxDistanceN2W = dMin * 5;
    }

    /**
     *  Get ways close to the way
     *  @param way Way
     */
    private static void getWays(Way way) {
      debugMsg("-- getWays() --");
//       s_Ways = new WaysList();
      BBox bbox = new BBox(way);
      bbox.addPrimitive(way,s_dMaxDistanceN2W);

      for (Way w : Main.main.getCurrentDataSet().searchWays(bbox)) {
        if (w.isUsable()) {
          s_Ways.add(w);
        }
      }

      // Get secondary ways and secondary Nodes
//       secondaryWays = new LinkedList<Way>();
      secondarydNodes = new LinkedList<Node>();
      for (Way xw : s_Ways.getWays()) {
        bbox = new BBox(xw);
        bbox.addPrimitive(xw,s_dMinDistance);

        for (Way w : Main.main.getCurrentDataSet().searchWays(bbox)) {
          if (w.isUsable() && s_Ways.indexOf(w) < 0) {
//             secondaryWays.add(w);
            for (Node n : w.getNodes()) {
              if (n.isUsable()) {
                secondarydNodes.add(n);
              }
            }
          }
        }
      }
    }

    /**
     *  Get nodes close to the way
     *  @param way Way
     */
    private static void getNodes(Way way) {
      debugMsg("-- getNodes() --");

      s_oNodes = new LinkedList<Node>();
      BBox bbox = new BBox(way);
      bbox.addPrimitive(way,s_dMinDistance);

      for (Node nd : Main.main.getCurrentDataSet().searchNodes(bbox)) {
        if (nd.isUsable() && ! nd.isOutsideDownloadArea()) {
          s_oNodes.add(nd);
        }
      }
    }

    /**
     *  Get ways connected to the Node
     *  @param node Node
     *  @return connected ways
     */
    private static List<Way> getWaysOfNode(Node node) {
      debugMsg("-- getWaysOfNode() --");
      debugMsg("   param: Node = " + node.getUniqueId());

      List<Way> ways = new LinkedList<Way>();

      if (node.getDataSet() == null) {
        return ways;
      }

      for (Way way : s_Ways.getWays()) {
        if (way.isUsable() && way.containsNode(node)) {
          debugMsg("    Use way:" + way.getUniqueId());
          ways.add(way);
        }
      }
      debugMsg("<< end of getWaysOfNode()");
      return ways;
    }

    /**
     *  Get list of nodes shared with another way (connected node)
     *  @param way Way
     */
    private static void getSharedNodes(Way way) {
      debugMsg("-- getSharedNodes() --");
      if (way == null) {
        return;
      }

      sharedNodes = new LinkedList<Node>();

      for (int i = 0; i < way.getNodesCount(); i++) {
        if (getWaysOfNode(way.getNode(i)).size() > 1) {
          sharedNodes.add(way.getNode(i));
          debugMsg("   Connected node: " + way.getNode(i));
        }
      }
    }

    /**
     *  Calculate angle
     *  @param oP1 Position
     *  @param n   Node
     *  @return deltaAlpha
     */
    private static double calcAlpha(LatLon oP1, Node n) {
      debugMsg("-- calcAlpha() --", 1);
      LatLon oP2 = n.getCoor();

      double dAlpha = Math.atan((oP2.getY() - oP1.getY()) / (oP2.getX() - oP1.getX())) * 180 / Math.PI + (oP1.getX() > oP2.getX() ? 180 : 0);
      return checkAlpha(dAlpha);
    }

    /**
     *  Check angle
     *  @param dAlpha delta Alpha
     *  @return deltaAlpha
     */
    private static Double checkAlpha(Double dAlpha) {
      debugMsg("-- checkAlpha() --", 1);
        if (dAlpha > 180) {
            return dAlpha - 360;
        }
        if (dAlpha <= -180) {
            return dAlpha + 360;
        }
        return dAlpha;
    }

    /**
     *  Check whether point is inside the way
     *  @param pos position
     *  @param way way
     *  @return Return True when all way nodes are around the position (sum of angles to all way nodes is 360 deg)
     */
    private static boolean isNodeInsideWay(LatLon pos, Way way) {
      debugMsg("-- isNodeInsideWay() --");
      List<Node> listNode = way.getNodes();

      double dAlpha;
      double dAlphaOld = calcAlpha(pos, listNode.get(listNode.size()-1));
      double dSumAlpha = 0;

      for (Node n : listNode) {
        dAlpha = calcAlpha(pos, n);
        dSumAlpha += checkAlpha( dAlpha - dAlphaOld );
        dAlphaOld = dAlpha;
      }
      dSumAlpha = Math.abs(dSumAlpha);
      debugMsg("Way: " + way.getUniqueId() + "; Node: " + pos + "; dSumAlpha: " + dSumAlpha);

      return dSumAlpha > 359 && dSumAlpha < 361;
    }

    /**
     *  Check whether point is on the way border
     *  @param pos position
     *  @param way way
     *  @return Return True when point is on way border
     */
    private static boolean isOnBorder(LatLon pos, Way way) {
      debugMsg("-- isOnBorder() --");

      for (int i = 0; i < way.getNodesCount() - 2; i++) {
        if (pointIsOnLine(pos, way.getNode(i).getCoor(), way.getNode(i+1).getCoor())) {
          System.out.println("  ->Point is on border.");
          return true;
        }
      }
      return false;
    }

    /**
     *  Get list of segments of way closest to the given point
     *  @param pos node position
     *  @param way way
     *  @return Return list of way segments closest to the position
     */
    private static List<WaySegment> getClosestWaySegments(LatLon pos, Way way) {
      debugMsg("-- getClosestWaySegments() --");

      List<WaySegment> ws = new LinkedList<WaySegment>();

      double min_distance =999999.;
      for (int i = 0; i < way.getNodesCount()-1; i++) {
        double dst = Math.abs(distance(way.getNode(i).getCoor(), way.getNode(i+1).getCoor()) -
                               (distance(way.getNode(i).getCoor(), pos) + distance(pos, way.getNode(i+1).getCoor()))
                             );
        debugMsg("       First node  : " + way.getNode(i));
        debugMsg("       Second nodes: " + way.getNode(i+1));
        debugMsg("           distance: " + dst);
        if (dst < min_distance && Math.abs(dst - min_distance) > s_dDoubleDiff) {
          ws = new LinkedList<WaySegment>();
          ws.add(new WaySegment(way, i));
          min_distance = dst;
        } else if (Math.abs(dst - min_distance) < s_dDoubleDiff) {
          ws.add(new WaySegment(way, i));
        }
      }

      debugMsg("    Closest segments: ");
      for (WaySegment w : ws) {
        debugMsg("    " + w);
      }

      return ws;
    }

    /**
     *  Get already existing way
     *  @param pos position
     *  @return way
     */
    private static Way getOldWay(LatLon pos) {
      debugMsg("-- getOldWay() --");
      int i;

      if (wayType == LANDUSE) {
        // TODO - Landuses are temporary disabled
        return null;
      }

      for (i = 1; i < s_Ways.size(); i++) {
        Way way = s_Ways.get(i);
        if (!isSameTag(way, wayType)) {
          continue;
        }
        if (isNodeInsideWay(pos, way)) {
          if (wayType == BUILDING || wayType == LANDUSE_RUAIN) {
            return way;
          } else if (wayType == LANDUSE &&
                      ( way.hasKey("landuse") && way.getKeys().get("landuse").equals("farmland") ||
                        way.hasKey("landuse") && way.getKeys().get("landuse").equals("meadow") ||
                        (wayIsForest && way.hasKey("landuse") && way.getKeys().get("landuse").equals("forest"))
                      )
                    ){
            return way;
          }
        }
      }
      return null;
    }

    /**
     *  Check whether ways are overlaped
     *    (some node of one way is inside other way)
     *  @param w1 first way
     *  @param w1 second way
     *  @return True when ways are overlaped
     */
    private static Boolean areWaysOverlaped(Way w1, Way w2) {

      debugMsg("-- areWaysOverlaped() --");
      debugMsg("    w1: " + w1.getUniqueId() + "; w2: " + w2.getUniqueId());

      // It no BBox intersection, ways can't have intersection
      if (! w1.getBBox().intersects(w2.getBBox())) {
        debugMsg("    BBoxes are not overlaped");
        return false;
      }

      // Check for intersection of ways segments
      for (int i = 0; i < w1.getNodesCount() - 1; i++) {
        WaySegment ws1 = new WaySegment(w1, i);
        for (int j = 0; j < w2.getNodesCount() - 1; j++) {
          WaySegment ws2 = new WaySegment(w2, j);
          if (ws1.intersects(ws2)) {
            debugMsg("    Ways are overlaped");
            return true;
          }
        }
      }

      debugMsg("    Ways are not overlaped");
      return false;
    }

    /**
     * Get distance between points
     * @param x First point
     * @param y Second point
     * @return Distance between points
     */
    private static double distance(LatLon x, LatLon y) {
//       debugMsg("    distance()");
      if (x != null && y != null) {
//         debugMsg("x: " + x + " / y: " +y);
        return Math.abs(Math.sqrt( (y.getX() - x.getX()) * (y.getX() - x.getX()) + (y.getY() - x.getY()) * (y.getY() - x.getY()) ) );
      }
      return 999999;
    }

    /**
     * Check whether point is on the line
     * @param p Point
     * @param x First point of line
     * @param y Second point of line
     * @return True when point is on line
     */
    private static boolean pointIsOnLine(LatLon p, LatLon x, LatLon y) {

      // Distance xy should equal to sum of distance xp and yp
      if (Math.abs((distance (x, y) - (distance (x, p) + distance (y, p)))) > s_dDoubleDiff) {
        return false;
      }
      return true;
    }

    /**
     * Check whether point is close to the line
     * @param p Point
     * @param x First point of line
     * @param y Second point of line
     * @return True when point is on line
     */
    private static boolean pointIsCloseToLine(LatLon p, LatLon x, LatLon y) {

      // Distance xy should be close to the sum of distance xp and yp
      if (Math.abs((distance (x, y) - (distance (x, p) + distance (y, p)))) > s_dMinDistanceN2oW) {
        return false;
      }
      return true;
    }

    /**
     * Check where there is some node on given position
     * @param p position
     * @return Existing node if found or null
     */
    private static Node getNodeOnPosition(LatLon pos) {
      for (Node n : s_oNodes ) {
        if (distance(n.getCoor(), pos) <= s_dMinDistanceN2N) {
          return n;
        }
      }

      return null;
    }

    /**
     *  Return middle point coordinations
     *  @param p1 - first point coordinates
     *  @param p2 - second point coordinates
     *  @return middle point
     */
     private static LatLon getMiddlePoint (LatLon p1, LatLon p2) {
      return new LatLon(LatLon.roundToOsmPrecision((p1.lat() + p2.lat())/2),
                        LatLon.roundToOsmPrecision((p1.lon() + p2.lon())/2));
     }

    /**
     * Return intersection node
     * @param ws1 way segment 1
     * @param ws2 way segment 2
     * @return Node with intersection coordinates
     */
    private static Node getIntersectionNode(WaySegment ws1, WaySegment ws2) {

      StraightLine oStraightLine1 = new StraightLine(
            new Point2D.Double(ws1.getFirstNode().getCoor().getX(), ws1.getFirstNode().getCoor().getY()),
            new Point2D.Double(ws1.getSecondNode().getCoor().getX(), ws1.getSecondNode().getCoor().getY()));

        StraightLine oStraightLine2 = new StraightLine(
            new Point2D.Double(ws2.getFirstNode().getCoor().getX(), ws2.getFirstNode().getCoor().getY()),
            new Point2D.Double(ws2.getSecondNode().getCoor().getX(), ws2.getSecondNode().getCoor().getY()));

        Point2D.Double oPoint = oStraightLine1.GetIntersectionPoint(oStraightLine2);

        return new Node(new LatLon(LatLon.roundToOsmPrecision(oPoint.getY()),
                                   LatLon.roundToOsmPrecision(oPoint.getX())));
    }

    /**
     * Check whether segments are overlaped
     * @param ws1 way segment 1
     * @param ws2 way segment 2
     * @return True when one segment overlap other one
     */
    private static boolean segmentOnSegment(WaySegment ws1, WaySegment ws2) {
      if (pointIsOnLine(ws2.getFirstNode().getCoor(),  ws1.getFirstNode().getCoor(), ws1.getSecondNode().getCoor()) ||
          pointIsOnLine(ws2.getSecondNode().getCoor(), ws1.getFirstNode().getCoor(), ws1.getSecondNode().getCoor())
         ) {
        return true;
      }

      return false;
    }

    /**
     * Check whether the way has outer role in relation
     * @param way way
     * @param rel relation
     * @return True when way is outer in the relation
     */
    private static boolean isOuter(Way way, Relation rel) {
      for (RelationMember rm : rel.getMembers()) {
        if ( rm.hasRole("outer") && rm.refersTo((Way) way))
          return true;
      }
      return false;
    }


    /**
     * Correct overlaping of ways
     * @param way overlaped way
     * @return List of Commands.
     */
    private static List<Command> correctOverlaping(Way way) {

      debugMsg("-- correctOverlaping() --");
      debugMsg("    Overlaped way" + way.getUniqueId());

      if (!way.isClosed()) {
        debugMsg(" TODO: Unclosed ways are not supported. ");
        return new LinkedList<Command>();
      }

      List<Command> cmds = new LinkedList<Command>();
      List<Command> cmds2 = new LinkedList<Command>();

      Way myWay = new Way(s_Ways.get(0));
      Way overlapedWay = new Way(way);

      Node iNode;

      // Go through all myWay segments
      // Check an intersection way segment with waysegment of the overlaped way
      // If intersection is found - incorporate the node into both ways
      // Remove all nodes from otherWay that are inside myWay
      // Incorporate all nodes from myWay that are inside otherWay to otherWay border

      // 1) Collect list of intersections
      debugMsg("    --> 1) Collect list of intersections");
      List<Node> intNodes = new LinkedList<Node>();
      for (int i = 0; i < s_Ways.get(0).getNodesCount()-1; i++) {
        WaySegment myWaySegment = new WaySegment(s_Ways.get(0), i);
        if (way.getNodes().indexOf(myWaySegment.getFirstNode()) >= 0 &&
            way.getNodes().indexOf(myWaySegment.getSecondNode()) >= 0) {
          continue;
        }
        for (int j = 0; j < way.getNodesCount()-1; j++) {
          WaySegment overlapedWaySegment = new WaySegment(way, j);
          if (! myWaySegment.intersects(overlapedWaySegment) && !segmentOnSegment(myWaySegment, overlapedWaySegment)) {
            continue;
          }

          // segments are intersected
          iNode = getIntersectionNode(myWaySegment, overlapedWaySegment);
          debugMsg("    --------------------------------");
          debugMsg("    myWaySegment:      " + myWaySegment);
          debugMsg("                       " + myWaySegment.getFirstNode() + ", " + myWaySegment.getSecondNode());
          debugMsg("    overlapedWaySegment: " + overlapedWaySegment);
          debugMsg("                       " + overlapedWaySegment.getFirstNode() + ", " + overlapedWaySegment.getSecondNode());

          if (pointIsCloseToLine(iNode.getCoor(), myWaySegment.getFirstNode().getCoor(), myWaySegment.getSecondNode().getCoor()) &&
              pointIsCloseToLine(iNode.getCoor(), overlapedWaySegment.getFirstNode().getCoor(), overlapedWaySegment.getSecondNode().getCoor())) {
            debugMsg("    Intersection node: " + iNode);

            Node existingNode = getNodeOnPosition(iNode.getCoor());
            if (existingNode != null) {
              // And existing node on intersection position found
              // Use it instead
              debugMsg("    Replaced by: " + existingNode);
              if (intNodes.indexOf(existingNode) == -1) {
                // move node to position of iNode
                cmds.add(new MoveCommand(existingNode,
                          (iNode.getEastNorth().getX() - existingNode.getEastNorth().getX()),
                          (iNode.getEastNorth().getY() - existingNode.getEastNorth().getY())
                          ));
                existingNode.setCoor(iNode.getCoor());
                intNodes.add(existingNode);
              }
            } else {
              // Add intersection point to the list for integration into ways
              if (intNodes.indexOf(iNode) == -1) {
                cmds.add(new AddCommand(iNode));
                intNodes.add(iNode);
                s_oNodes.add(iNode);
              }
            }
          } else {
            if (pointIsCloseToLine(overlapedWaySegment.getFirstNode().getCoor(), myWaySegment.getFirstNode().getCoor(), myWaySegment.getSecondNode().getCoor())) {
              debugMsg("    Intersection node: " + overlapedWaySegment.getFirstNode());
              // Add intersection to both ways
              if (intNodes.indexOf(overlapedWaySegment.getFirstNode()) == -1) {
                intNodes.add(overlapedWaySegment.getFirstNode());
              }
            }
            if (pointIsCloseToLine(overlapedWaySegment.getSecondNode().getCoor(), myWaySegment.getFirstNode().getCoor(), myWaySegment.getSecondNode().getCoor())) {
              debugMsg("    Intersection node: " + overlapedWaySegment.getSecondNode());
              // Add intersection to both ways
              if (intNodes.indexOf(overlapedWaySegment.getSecondNode()) == -1) {
                intNodes.add(overlapedWaySegment.getSecondNode());
              }
            }
          }
        }
      }

      // Exit when no intersection nodes found
      if (intNodes.size() == 0) {
        System.out.println("No ways found.");
        return cmds;
      }

      // 2) Integrate intersection nodes into ways
      debugMsg("    --------------------------------");
      debugMsg("    --> 2) Integrate intersection nodes into ways");
      for (Node intNode : intNodes) {
        // my Way
        if (myWay.getNodes().indexOf(intNode) >= 0) {
          debugMsg("    Node is already in myWay: " + intNode.getUniqueId());
        } else {
          for (int i = 0; i < myWay.getNodesCount()-1; i++) {
            if (pointIsOnLine(intNode.getCoor(), myWay.getNode(i).getCoor(), myWay.getNode(i+1).getCoor())) {
              debugMsg("    --myWay: ");
              debugMsg("      Add node       : "+ intNode.getUniqueId());
              debugMsg("        between nodes: (" + i + ")" + myWay.getNode(i).getUniqueId());
              debugMsg("                     : (" + (i+1) + ")"+ myWay.getNode(i+1).getUniqueId());
              myWay.addNode((i + 1), intNode);
              break;
             }
          }
        }
        // overlap Way
        if (overlapedWay.getNodes().indexOf(intNode) >= 0) {
          debugMsg("    Node is already in overlapedWay: " + intNode.getUniqueId());
//           overlapedWay.removeNode(intNode);
        } else {
          for (int i = 0; i < overlapedWay.getNodesCount()-1; i++) {
            if (pointIsOnLine(intNode.getCoor(), overlapedWay.getNode(i).getCoor(), overlapedWay.getNode(i+1).getCoor())) {
              debugMsg("    --overlapedWay: ");
              debugMsg("      Add node       : "+ intNode.getUniqueId());
              debugMsg("        between nodes: (" + i + ")" + overlapedWay.getNode(i).getUniqueId());
              debugMsg("                     : (" + (i+1) + ")"+ overlapedWay.getNode(i+1).getUniqueId());
              overlapedWay.addNode((i + 1), intNode);
              break;
             }
          }
        }
      }

      replaceWayInList(s_Ways.get(0), myWay);
      replaceWayInList(way, overlapedWay);

      /* Go trough way fragments between intersection nodes
       * If fragment is inside myWay, delete all middle nodes
       * in overlaped way frogment and incorporate myWay nodes
       * into the overlaped Way.
       */

       // First sort the list of intersection Nodes
       // to have correct order according to overlapedWay
       TreeMap <Integer, Node> sortedIntNodesMap = new TreeMap<Integer, Node>();

       for (Node n : intNodes) {
        sortedIntNodesMap.put(overlapedWay.getNodes().indexOf(n), n);
       }

      List<Node> intNodesSorted = new LinkedList<Node>();
      for (Integer i : sortedIntNodesMap.keySet()) {
        intNodesSorted.add(sortedIntNodesMap.get(i));
      }

      Way tmpWay;
      WayFragment overlapedWF;
      WayFragment myWF;

      System.out.println("  --------------------------------");
      System.out.println("         Intersection nodes: " + intNodes);
      System.out.println("  Sorted intersection nodes: " + intNodesSorted);
      for (int idx = 0; idx < intNodesSorted.size(); idx++) {
        System.out.println("");
        System.out.println("  ---- New Fragment("+ idx +") ----");

        overlapedWF = null;;
        myWF = null;

        // First test the overlapedWay segment
        System.out.println("   == Overlaped Way ==");
        int startIdx, endIdx;

        if (idx == intNodesSorted.size() - 1) {
          startIdx = overlapedWay.getNodes().indexOf(intNodesSorted.get(idx));
          endIdx = overlapedWay.getNodes().indexOf(intNodesSorted.get(0));
        } else {
          startIdx = overlapedWay.getNodes().indexOf(intNodesSorted.get(idx));
          endIdx = overlapedWay.getNodes().indexOf(intNodesSorted.get(idx + 1));
        }

        if (startIdx == -1 || endIdx == -1) {
          System.out.println("   ERROR: first or last node has index -1!!!");
          continue;
        }

        overlapedWF = new WayFragment(overlapedWay, startIdx, endIdx);

        if (overlapedWF.hasInnerNodes()) {
          overlapedWF.resetInnerIndex();
          Node n = overlapedWF.getNextInnerNode();
          System.out.println("  Tested node: " + n.getUniqueId());
          if (! isNodeInsideWay(n.getCoor(), myWay)) {
            // Node is not inside myWay - skip this way fragment
            continue;
          }
        } else {
          // when no inner nodes, check whether way segment is crossing the myWay
          // (middle point of the segment is inside myWay).
          LatLon ll = getMiddlePoint(overlapedWF.getFirstNode().getCoor(),
                                     overlapedWF.getLastNode().getCoor());
          if (! isNodeInsideWay(ll, myWay) || isOnBorder(ll, myWay)) {
            // Node is not inside myWay - skip this way fragment
            continue;
          }
        }

        // Now test the myWay segment.
        System.out.println("   == My Way ==");
        startIdx = myWay.getNodes().indexOf(overlapedWF.getFirstNode());
        endIdx = myWay.getNodes().indexOf(overlapedWF.getLastNode());

        System.out.println("   overlapedWay.startIdx: " + overlapedWay.getNodes().indexOf(overlapedWF.getFirstNode()) + " .endIdx: " + overlapedWay.getNodes().indexOf(overlapedWF.getLastNode()));
        System.out.println("   myWay.startIdx: " + startIdx + " .endIdx: " + endIdx);

        if (startIdx == -1 || endIdx == -1) {
          System.out.println("   ERROR: first or last node has index -1!!!");
          continue;
        }

        if ((endIdx - startIdx) > 1 || (startIdx - endIdx) > 1) {
          Node nextNode = myWay.getNode(startIdx == myWay.getRealNodesCount() ? 1 : startIdx + 1);
          System.out.println("  Tested node: " + nextNode.getUniqueId());
          if (isNodeInsideWay(nextNode.getCoor(), overlapedWay)) {
            System.out.println("   -> 1): ");
            myWF = new WayFragment(myWay, startIdx, endIdx);
          } else {
            nextNode = myWay.getNode(startIdx == 0 ? myWay.getRealNodesCount() -1 : startIdx - 1);
            System.out.println("  Tested node: " + nextNode.getUniqueId());
            if (isNodeInsideWay(nextNode.getCoor(), overlapedWay)) {
              System.out.println("   -> 2): ");
              myWF = new WayFragment(myWay, endIdx, startIdx);
            }
          }
        } else {
          LatLon ll = getMiddlePoint(overlapedWF.getFirstNode().getCoor(),
                                    overlapedWF.getLastNode().getCoor());
          if (isNodeInsideWay(ll, overlapedWay) && ! isOnBorder(ll, overlapedWay)) {
            System.out.println("   -> 3): ");
            myWF = new WayFragment(myWay, startIdx, endIdx);
          } else {
            ll = getMiddlePoint(overlapedWF.getLastNode().getCoor(),
                                overlapedWF.getFirstNode().getCoor());
            if (isNodeInsideWay(ll, overlapedWay) && ! isOnBorder(ll, overlapedWay)) {
              System.out.println("   -> 4): ");
              myWF = new WayFragment(myWay, endIdx, startIdx);
            }
          }
        }

        // We have both fragments now.
        // Try to fix overlaping

        System.out.println(" Overlaped Way Fragment: " + overlapedWF.toString());
        if (myWF != null) {
          System.out.println("        My Way Fragment: " + myWF.toString());
        } else {
          System.out.println("        My Way Fragment: N/A");
        }

        if (overlapedWF.hasInnerNodes()) {
          // delete inner nodes from overlapedWay
          debugMsg("    --------------------------------");
          debugMsg("    --> 3) Remove all inner nodes from otherWayFragment");
          boolean wayWasClosed = overlapedWay.isClosed();
          tmpWay = new Way(overlapedWay);
          overlapedWF.resetInnerIndex();
          Node in = overlapedWF.getNextInnerNode();
          while (in != null) {
            debugMsg("      Remove node from way: " + in.getUniqueId());
            overlapedWay.removeNode(in);
            if (wayWasClosed && !overlapedWay.isClosed() ) {
              // FirstLast node removed - close the way
              debugMsg("      Close way: " + in.getUniqueId());
              overlapedWay.addNode(overlapedWay.getNodesCount(), overlapedWay.getNode(0));
              }
              replaceWayInList(tmpWay, overlapedWay);
              if (getWaysOfNode(in).size() == 0 && ! in.hasKeys() && ! in.isOutsideDownloadArea() ) {
                  debugMsg("      Delete node: " + in.getUniqueId());
                  cmds2.add(new DeleteCommand(in));
                  s_oNodes.remove(in);
              }
              in = overlapedWF.getNextInnerNode();
            }
          }

        if (myWF != null) {
          if (myWF.hasInnerNodes()) {
            // Incorporate Inner nodes of MayWay into overlapedWay
            debugMsg("    --------------------------------");
            debugMsg("    --> 4) Incorporate inner nodes from myWayFragment");

            boolean backward;

            tmpWay = new Way(overlapedWay);

            int startIndex = overlapedWay.getNodes().indexOf(overlapedWF.getFirstNode());

            if (overlapedWF.getFirstNode().getUniqueId() == myWF.getFirstNode().getUniqueId()) {
              myWF.resetInnerIndex();
              backward = false;
            } else {
              myWF.setMaxInnerIndex();
              backward = true;
            }

            Node in = backward?myWF.getPreviousInnerNode():myWF.getNextInnerNode();

            while (in != null) {
              startIndex++;
              debugMsg("      Add node into way: " + in);
              debugMsg("      ... at index: " + startIndex);
              overlapedWay.addNode(startIndex , in);
              in = backward?myWF.getPreviousInnerNode():myWF.getNextInnerNode();
            }
            replaceWayInList(tmpWay, overlapedWay);
          }
        }
      }



      debugMsg("    -- -- -- -- --");
      replaceWayInList(s_Ways.get(0), myWay);
      replaceWayInList(way, overlapedWay);

      cmds.add(new ChangeCommand(s_Ways.getOriginalWay(myWay), myWay));
      cmds.add(new ChangeCommand(s_Ways.getOriginalWay(overlapedWay), overlapedWay));

      cmds.addAll(cmds2);
      return cmds;
    }

    /**
     * Merges two nodes
     * @param myNode Node to merge
     * @param otherNode Node that will replace myNode
     * @return List of Commands.
     */
    private static List<Command> mergeNodes(Node myNode, Node otherNode){
      debugMsg("-- mergeNodes() --");

      List<Command> cmds = new LinkedList<Command>();
      Way myWay = s_Ways.get(0);

      debugMsg("   myNode: " + myNode + ", otherNode: " + otherNode);
      debugMsg("   myWay: " + myWay);

      Way tmpWay = new Way(myWay);
      // myNode is not part of myWay? Should not happen
      int j = myWay.getNodes().indexOf(myNode);
      if (j < 0) {
        return cmds;
      }

      // move otherNode to position of myNode
      cmds.add(new MoveCommand(otherNode,
                (myNode.getEastNorth().getX() - otherNode.getEastNorth().getX()),
                (myNode.getEastNorth().getY() - otherNode.getEastNorth().getY())
                ));
      otherNode.setCoor(myNode.getCoor());

     myWay.addNode(j, otherNode);
      if (j == 0) {
          // first + last point
        myWay.addNode(myWay.getNodesCount(), otherNode);
      }

      myWay.removeNode(myNode);

      cmds.add(new ChangeCommand(tmpWay, myWay));

      replaceWayInList(tmpWay, myWay);

      if (getWaysOfNode(myNode).size() == 0 && ! myNode.isOutsideDownloadArea()) {
          debugMsg("    Delete node: " + myNode);
          cmds.add(new DeleteCommand(myNode));
          s_oNodes.remove(myNode);
      }

      debugMsg("   updated myWay: " + myWay);
      return cmds;
    }

    /**
     * Copy keys from old to new way
     * @param d_way Destination way
     * @param s_way Source way
     * @param newSource value for source key
     * @param otherNode Node that will replace myNode
     * @return destination way
     */
    private static Way updateKeys(Way d_way, Way s_way, String newSource) {
      debugMsg("-- updateKeys() --");

        d_way.put("source", newSource);

        if (wayType == BUILDING) {
        // Building key
        if (s_way.hasKey("building")) {
          if (d_way.get("building").equals("yes")) {
            d_way.put("building", s_way.get("building"));
          }
        }

        // Building:levels key
        if (s_way.hasKey("building:levels") && !d_way.hasKey("building:levels")) {
            d_way.put("building:levels", s_way.get("building:levels"));
        }

        // Building:flats key
        if (s_way.hasKey("building:flats") && !d_way.hasKey("building:flats")) {
            d_way.put("building:flats", s_way.get("building:flats"));
        }

        // Start_date key
        if (s_way.hasKey("start_date") && !d_way.hasKey("start_date")) {
            d_way.put("start_date", s_way.get("start_date"));
        }

        // Ref:ruian:building key
        if (s_way.hasKey("ref:ruian:building")) {
            d_way.put("ref:ruian:building", s_way.get("ref:ruian:building"));
        }

        // Ref:ruian:building key
        if (s_way.hasKey("building:ruian:type")) {
            d_way.put("building:ruian:type", s_way.get("building:ruian:type"));
        }

        // Remove obsolete ref:ruian key
        if (s_way.hasKey("ref:ruian:building") && d_way.hasKey("ref:ruian")) {
            if (d_way.get("ref:ruian").equals(s_way.get("ref:ruian:building"))) {
            d_way.remove("ref:ruian");
            }
        }
      }

      if (wayType == LANDUSE_RUAIN) {
        // TODO
      }

      if (wayType == LANDUSE) {
        if (s_way.hasKey("landuse")) {

          // landuse
          if (s_way.hasKey("landuse")) {
              d_way.put("landuse", s_way.get("landuse"));
          }

          // crop
          if (s_way.hasKey("crop")) {
              d_way.put("crop", s_way.get("crop"));
          }

          // crop
          if (s_way.hasKey("meadow")) {
              d_way.put("meadow", s_way.get("meadow"));
          }

          // ref
          if (s_way.hasKey("ref")) {
              d_way.put("ref", s_way.get("ref"));
          }
        }
      }

        return d_way;
    }

    /**
     *  Check whether there is a building fully covered by traced building
     *  @return List of commands
     */
    private static  List<Command> mergeWithExistingNodes() {
      debugMsg("-- mergeWithExistingNodes() --");

      LinkedList<Command> cmds  = new LinkedList<Command>();
      LinkedList<Command> cmds2 = new LinkedList<Command>();
      List<Node> tmpNodesList   = new LinkedList<Node> (s_oNodes);
      List<Node> deletedNodes   = new LinkedList<Node> ();
      Way        s_oWay         = new Way(s_Ways.get(0));
      Way        tmpWay         = new Way(s_Ways.get(0));


      for (Node otherNode : tmpNodesList) {
        if (s_Ways.get(0).getNodes().indexOf(otherNode) >= 0) {
          continue;
        }

        for (int i = 0; i < tmpWay.getRealNodesCount(); i++) {
          Node myNode = tmpWay.getNode(i);
          if (deletedNodes.indexOf(myNode) < 0 && otherNode.getCoor().distance(myNode.getCoor()) <= s_dMinDistanceN2N) {
            debugMsg(    "Replace node: " + myNode + " by node: " + otherNode );
            cmds.add(new MoveCommand(otherNode,
                      (myNode.getEastNorth().getX() - otherNode.getEastNorth().getX()),
                      (myNode.getEastNorth().getY() - otherNode.getEastNorth().getY())
                      ));
            otherNode.setCoor(myNode.getCoor());
            int myNodeIndex = s_oWay.getNodes().indexOf(myNode);
            debugMsg(    "Node index: " + myNodeIndex);
            if (myNodeIndex >= 0) {
              s_oWay.addNode(myNodeIndex, otherNode);
              if (myNodeIndex == 0) {
                // First node - close the way
                s_oWay.addNode(s_oWay.getNodesCount(), otherNode);
              }
              s_oWay.removeNode(myNode);
              replaceWayInList(tmpWay, s_oWay);

              if (deletedNodes.indexOf(myNode) < 0 &&  getWaysOfNode(myNode).size() <= 1 &&
                  ! myNode.isOutsideDownloadArea()) {
                debugMsg("    Delete node: " + myNode);
                s_oNodes.remove(myNode);
                cmds2.add(new DeleteCommand(myNode));
                deletedNodes.add(myNode);
              }
            }
          }
        }
      }

      replaceWayInList(tmpWay, s_oWay);
      cmds.add(new ChangeCommand(s_Ways.getOriginalWay(s_oWay), s_oWay));
      cmds.addAll(cmds2);
      return cmds;
    }

    /**
     *  Check whether there is a building fully covered by traced building
     *  @return List of commands
     */
    private static  List<Command> removeFullyCoveredWays() {
      debugMsg("-- removeFullyCoveredWays() --");

      LinkedList<Command> cmds = new LinkedList<Command>();
      List<Way> tmpWaysList = new LinkedList<Way> (s_Ways.getWays());

      for (Way w : tmpWaysList) {
        if (!w.isUsable() || !isSameTag(w, wayType) || w.equals(s_Ways.get(0))) {
          continue;
        }

        Way tmpWay = new Way(w);
        if (isNodeInsideWay(w.getBBox().getCenter(), s_Ways.get(0)) &&
            isNodeInsideWay(s_Ways.get(0).getBBox().getCenter(), w)) {
          debugMsg("   Delete way: " + w);
          cmds.add(new DeleteCommand( w ));
          s_Ways.remove(w);
          // Remove old nodes from list of working nodes list
          for (int i = 0; i < tmpWay.getNodesCount() - 1; i++) {
            Node nd = tmpWay.getNode(i);
            if (!nd.isUsable()) {
              continue;
            }

            if ( getWaysOfNode(nd).size() == 0 && ! nd.isOutsideDownloadArea() ) {
                debugMsg("    Delete node: " + nd);
                cmds.add(new DeleteCommand(nd));
                s_oNodes.remove(nd);
            }
          }
        }
      }
      return cmds;
    }

    /**
     *  Check all nodes of the given way and merge them with close nodes
     *  @param way
     *  @return List of commands
     */
    private static  List<Command>  fixOverlapedWays () {
      debugMsg("-- fixOverlapedWays() --");

      LinkedList<Command> cmds = new LinkedList<Command>();

      for (Way w : new LinkedList<Way>(s_Ways.getWays())) {
        if (!w.equals(s_Ways.get(0)) && isSameTag(w, wayType) && areWaysOverlaped(s_Ways.get(0), w)) {
          cmds.addAll(correctOverlaping(w));
        }
      }

      debugMsg("    s_oWay: fixOverlapedWays(): " + new TracerDebug().FormatPrimitive(s_Ways.get(0).toString()));

      return cmds;
    }

    /**
     *  Remove spare nodes - nodes on straight line that are not needed anymore
     *  @return List of commands
     */
    private static  List<Command>  removeSpareNodes () {
      debugMsg("-- removeSpareNodes() --");

      LinkedList<Command> cmds  = new LinkedList<Command>();
      LinkedList<Command> cmds2 = new LinkedList<Command>();

      for (Way w : new LinkedList<Way>(s_Ways.getWays())) {
        if (!w.equals(s_Ways.get(0)) && isSameTag(w, wayType)) {
          Way bckWay = new Way(w);
          cmds2 = new LinkedList<Command>();
          boolean wayChanged = true;
          int x = 1;
          debugMsg("    Processed way: " + w);
          while (wayChanged) {
            debugMsg ("     Loop: " + x++);
            for (int i = 0; i < w.getRealNodesCount(); i++) {
              Node middleNode = w.getNode(i);
              wayChanged = false;

              if (getWaysOfNode(middleNode).size() == 1 && secondarydNodes.indexOf(middleNode) < 0) {
                Node prevNode = w.getNode(i == 0 ? w.getRealNodesCount() -1 : i - 1);
                Node nextNode = w.getNode(i == w.getNodesCount() ? 1 : i + 1);

                if ( pointIsOnLine(middleNode.getCoor(), prevNode.getCoor(), nextNode.getCoor())) {
                  debugMsg("    Way: Delete spare node: " + middleNode);
                  wayChanged = true;
                  w.removeNode(middleNode);
                  if (i == 0) {
                    // Close the way again
                    w.addNode(w.getNodesCount(), w.getNode(0));
                  }

                  replaceWayInList(bckWay, w);
                  if (getWaysOfNode(middleNode).size() == 0 && ! middleNode.isOutsideDownloadArea()) {
                    debugMsg("    -> Node: Delete command");
                    cmds2.add(new DeleteCommand(middleNode));
                    s_oNodes.remove(middleNode);
                  }
                  break;
                }
              }
            }
          }
          if (bckWay.getNodesCount() != w.getNodesCount()) {
            cmds.add(new ChangeCommand(s_Ways.getOriginalWay(w), w));
            cmds.addAll(cmds2);
            debugMsg("-------\n");
          }
        }
      }

      return cmds;
    }

    /**
     * Determines if the specified node is a part of a defined type of way (building or landuse).
     * @param n The node to be tested
     * @return True if building key is set and different from no,entrance
     */
    private static boolean isInSameTag(Node n) {
        debugMsg("-- isInSameTag() --");
        for (OsmPrimitive op : n.getReferrers()) {
            if (op instanceof Way) {
                if (isSameTag((Way) op, wayType)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Determines if the specified primitive denotes a specified key.
     * Building or landuse.
     * @param w The way to be tested
     * @param wType Type of the way - building or landuse
     * @return True if building key is set and different from no (entrance for building)
     */
    protected static final boolean isSameTag(Way w, int wType) {
      debugMsg("-- isSameTag() --");
      if (wType == LANDUSE) {
        debugMsg("-- isSameTag(): landuse");
        if ( (!w.hasKey("landuse") && !w.hasKey("natural") && !w.hasKey("leisure")) ) {
          return false;
        } else if ((w.hasKey("landuse") &&
                    w.getKeys().get("landuse").equals("no")) ||
                   (w.hasKey("natural") &&
                    w.getKeys().get("natural").equals("no")) ||
                   (w.hasKey("leisure") &&
                    w.getKeys().get("leisure").equals("no"))) {
          return false;
        } else if (w.hasKey("landuse") ||
                   (w.hasKey("natural") &&
                    (w.getKeys().get("natural").equals("scrub") ||
                     w.getKeys().get("natural").equals("wood"))) ||
                   (w.hasKey("leisure") &&
                    (w.getKeys().get("leisure").equals("garden")))
                     ) {
          return true;
        }
        return false;
      }

      if (wayType == BUILDING) {
        debugMsg("-- isSameTag(): building");
        return (w.getKeys().get("building") == null ? false : !w.getKeys().get("building").equals("no") && !w.getKeys().get("building").equals("entrance"));
      }

      return false;
    }

}
