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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
// import org.openstreetmap.josm.plugins.tracer2.preferences.ServerParam;
import org.openstreetmap.josm.tools.Pair;

import org.openstreetmap.josm.plugins.tracer.TracerDebug;
public class ConnectWays {
    static double s_dMinDistance      = 0.000006;   // Minimal distance, for objects
    static double s_dMinDistanceN2N   = 0.000001;  // Minimal distance, when nodes are merged
    static double s_dMinDistanceN2oW  = 0.000001;   // Minimal distance, when node is connected to other way
    static double s_dMinDistanceN2tW  = 0.000001;   // Minimal distance, when other node is connected this way

    static Way s_oWay; // New or updated way
    static Way s_oWayOld; // The original way
    static List<Way>  s_oWays; // List of all ways we will work with
    static List<Node> s_oNodes; // List of all nodes we will work with
    static List<Node> connectedNodes; // List of nodes that are also connected other way

    static List<Node> s_overlapNodes; // List of nodes inside other ways (overlaped ways)

//     static ServerParam s_oParam;
    static boolean s_bCtrl;
    static boolean s_bAlt;

    static boolean s_bAddNewWay;

    /**
     *  Print debug messages
     *  @param msg mesage
     */
    private static void debugMsg(String msg) {
      System.out.println(msg);
    }

    /**
     *  Calculate minimal distances
     */
    private static void calcDistance()
    {
//        double dTileSize = Double.parseDouble(s_oParam.getTileSize());
//        double dResolution = Double.parseDouble(s_oParam.getResolution());
//        double dMin = dTileSize / dResolution;

    double dMin = (double) 0.0004 / (double) 2048;

      s_dMinDistance = dMin * 30;
      s_dMinDistanceN2N = dMin * 2.5;
      s_dMinDistanceN2oW = dMin * 5;
      s_dMinDistanceN2tW = dMin * 5;
    }

    /**
     *  Get ways close to the way
     *  @param way Way
     */
    private static void getWays(Way way) {
      debugMsg("-- getWays() --");
      s_oWays = new LinkedList<Way>();
      BBox bbox = new BBox(way);
      bbox.addPrimitive(way,s_dMinDistance);

      for (Way w : Main.main.getCurrentDataSet().searchWays(bbox)) {
        if (w.isUsable()) {
          s_oWays.add(w);
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
        if (nd.isUsable()) {
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
      debugMsg("   param: Node = " + node);

      List<Way> ways = new LinkedList<Way>();

      if (node.getDataSet() == null) {
        return ways;
      }

      for (Way way : s_oWays) {
        if (way.isUsable() && way.containsNode(node)) {
          debugMsg("    Use way:" + way);
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
    private static void getConnectedNodes(Way way) {
      debugMsg("-- getConnectedNodes() --");
      if (way == null) {
        return;
      }

      connectedNodes = new LinkedList<Node>();

      for (int i = 0; i < way.getNodesCount(); i++) {
        if (getWaysOfNode(way.getNode(i)).size() > 1) {
          connectedNodes.add(way.getNode(i));
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
      debugMsg("-- calcAlpha() --");
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
      debugMsg("-- checkAlpha() --");
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

      return dSumAlpha > 359 && dSumAlpha < 361;
    }

    /**
     *  Get already existing way
     *  @param pos position
     *  @return way
     */
    private static Way getOldWay(LatLon pos) {
      debugMsg("-- getOldWay() --");
      int i;

      for (i = 0; i < s_oWays.size(); i++) {
        Way way = s_oWays.get(i);
        if (!isSameTag(way)) {
          continue;
        }
        if (isNodeInsideWay(pos, way)) {
          s_oWays.remove(way);
          return way;
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
    private static Boolean waysAreOverlaped(Way w1, Way w2) {

      debugMsg("-- waysAreOverlaped() --");
      s_overlapNodes = new LinkedList<Node>();
      for (int i; i < w1.getNodesCount() - 1; i++) {
        Node n = way.getNode(i);
        if (isNodeInsideWay(n.getCoor(), w2)) {
          s_overlapNodes.add(n);
        }

      for (int i; i < w2.getNodesCount() - 1; i++) {
        Node n = way.getNode(i);
        if (isNodeInsideWay(n.getCoor(), w1)) {
          s_overlapNodes.add(n);
        }

      if (s_overlapNodes.size() == 0 ) {
        return false;
      }

      return true;
    }


    /**
     * Correct overlaping of ways
     *  @param way overlaped way
     * @return List of Commands.
     */
    private static  List<Command> correctOverlaping(Way way) {

      debugMsg("-- correctOverlaping() --");
      debugMsg("Overlaped way" + way);

      List<Command> cmds = new LinkedList<Command>();

      private static final int LEFT = 1;
      private static final int RIGHT = 2;
      private static final int UP = 3;
      private static final int DOWN = 4;

      Way myWay = new Way(s_oWays);
      Way otherWay = new Way(way);

      LatLon myCenterPoint = myWay.getBBox().center();
      LatLon otherCenterPoint = otherWay.getBBox().center();

      for (Node n : s_overlapNodes) {
        Node myNode = new(n);
        int myNodeIndex = s_oWays.indexOf(myNode);
        if (myNodeIndex >= 0) {
          // My node is inside another way
          //  1) Find segment of other way that is between my node and my way center
          //  2) Move nodes of such way segment to my border
          //  3) Split my border and include these nodes to my way

          debugMsg("    myNode inside otherWay: " + myNode);

          // Define line between my Center point and my Node
          StraightLine myLine = new StraightLine(
              new Point2D.Double(myCenterPoint.getX(), myCenterPoint.getY()),
              new Point2D.Double(myNode.getCoor().getX(), myNode.getCoor().getY()));

          // Test each segment of otherWay whether it intersect myLine
          // It means that it overlaps myWay and needs to be fixed
          for (Pair<Node, Node> np : otherWay.getNodePairs(false)) {
            Node owFirstNode = new Node(np.a);
            Node owLastNode = new Node(np.b);

            debugMsg("    Testing way segment: " + owFirstNode + " and " + owLastNode);
            StraightLine otherWaySegment = new StraightLine(
                new Point2D.Double(owFirstNode.getCoor().getX(), owFirstNode.getCoor().getY()),
                new Point2D.Double(nowLastNode.getCoor().getX(), nowLastNode.getCoor().getY()));

            Point2D.Double iPoint = myLine.GetIntersectionPoint(otherWaySegment);
            if (iPoint.getX().isNaN() || iPoint.getY().isNaN()) {
              // Not an intersection
              debugMsg("    No intersecion");
              continue;
            }

            debugMsg("    Intersection found at: " + iPoint);

            // Prepare data
            // Get left and right node for my node
            Node leftNode = getNode( (myNodeIndex - 1) < 0 ? s_oWay.getNodesCount() : (myNodeIndex - 1) );
            Node rightNode = getNode( (myNodeIndex + 1) > s_oWay.getNodesCount() ? 0 : (myNodeIndex + 1) );

            debugMsg("    Left  node: " + leftNode);
            debugMsg("    Right node: " + rightNode);

            StraightLine leftSegment = new StraightLine(
                new Point2D.Double(leftNode.getCoor().getX(), LeftNode.getCoor().getY()),
                new Point2D.Double(myNode.getCoor().getX(), myNode.getCoor().getY()));

            StraightLine rightSegment = new StraightLine(
                new Point2D.Double(myNode.getCoor().getX(), myNode.getCoor().getY()),
                new Point2D.Double(leftNode.getCoor().getX(), LeftNode.getCoor().getY()));

            // Get direction where nodes will be moved
            int moveTo;
            double distanceX, distanceY

            distanceX = otherCenterPoint.getX() - iPoint.getX();
            distanceY = otherCenterPoint.getY() - iPoint.getY();

            if (distanceX > 0 && abs(distanceX) < abs(distanceY)) {
              moveTo = RIGHT;
            } else if (distanceX < 0 && abs(distanceX) < abs(distanceY)) {
              moveTo = LEFT;
            } else if (distanceY > 0 && abs(distanceX) > abs(distanceY)) {
              moveTo = UP;
            } else if (distanceY < 0 && abs(distanceX) > abs(distanceY)) {
              moveTo = DOWN;
            }

          }

        } else {
          // other node is inside my way
          //
        }

      }

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

        debugMsg("   myNode: " + myNode + ", otherNode: " + otherNode);
        debugMsg("   myWay: " + s_oWay);

        Way tmpWay = new Way(s_oWay);
        // myNode is not part of myWay? Should not happen
        int j = s_oWay.getNodes().indexOf(myNode);
        if (j < 0) {
          return cmds;
        }

        // move otherNode to position of myNode
        cmds.add(new MoveCommand(otherNode,
                  (myNode.getEastNorth().getX() - otherNode.getEastNorth().getX()),
                  (myNode.getEastNorth().getY() - otherNode.getEastNorth().getY())
                  ));

        s_oWay.addNode(j, otherNode);
        if (j == 0) {
            // first + last point
          s_oWay.addNode(s_oWay.getNodesCount(), otherNode);
        }

        s_oWay.removeNode(myNode);

        cmds.add(new ChangeCommand(tmpWay, s_oWay));

        s_oWays.remove(tmpWay);
        s_oWays.add(s_oWay);

        if (getWaysOfNode(myNode).size() == 0) {
            debugMsg("    Delete node: " + myNode);
            cmds.add(new DeleteCommand(myNode));
            s_oNodes.remove(myNode);
        }

        debugMsg("   updated myWay: " + s_oWay);
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

        return d_way;
    }

// -----------------------------------------------------------------------------------------------------
    /**
     * Try connect way to other buildings.
     * @param way Way to connect.
     * @return Commands.
     */
    public static Command connect(Way newWay, LatLon pos, boolean ctrl, boolean alt, String source) {
        debugMsg("-- connect() --");

        LinkedList<Command> cmds = new LinkedList<Command>();
        LinkedList<Command> cmds2 = new LinkedList<Command>();

        s_bCtrl = ctrl;
        s_bAlt = alt;

//         calcDistance();
        getNodes(newWay);
        getWays(newWay);

        s_oWayOld = getOldWay(pos);
//         getConnectedNodes(s_oWayOld);

        if (s_oWayOld == null) {
          s_bAddNewWay = true;
          cmds.add(new AddCommand(newWay));
          s_oWayOld = newWay;
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
                return new SequenceCommand("Nothing", null);
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

            // Remove old nodes from list of working nodes list
            for (int i = 0; i < s_oWayOld.getNodesCount() - 1; i++) {
              Node nd = s_oWayOld.getNode(i);
              List<Way> ways = getWaysOfNode(nd);
              if (ways.size() == 0) {
                  debugMsg("    Delete node: " + nd);
                  cmds2.add(new DeleteCommand(nd));
                  s_oNodes.remove(nd);
              }

            }
            s_oWay = tempWay;
            s_oWay = updateKeys(s_oWay, newWay, source);
            s_oWays.remove(s_oWayOld);
            s_oWays.add(s_oWay);
            debugMsg("updatedWay: " + s_oWay);
        }

        cmds.add(new ChangeCommand(s_oWayOld, s_oWay));

        // Modify the way
        debugMsg("");
        debugMsg("-----------------------------------------");
        cmds.addAll(removeFullyCoveredWays());

        debugMsg("");
        debugMsg("-----------------------------------------");
        cmds.addAll(connectTo());

        debugMsg("");
        debugMsg("-----------------------------------------");
        cmds.addAll(fixOverlapedWays());

        debugMsg("");
        debugMsg("-----------------------------------------");
//          cmds.add(new ChangeCommand(s_oWayOld, trySplitWayByAnyNodes(s_oWay)));

        cmds.addAll(cmds2);


        TracerDebug oTracerDebug = new TracerDebug();
        debugMsg("  List of ways: ");
        for (Way w : s_oWays) {
          debugMsg(new TracerDebug().FormatPrimitive(w.toString()));
          for(Map.Entry<String, String> entry : w.getKeys().entrySet()) {
            debugMsg(entry.getKey() + " = " + entry.getValue());
          }
        }
        debugMsg("-----------------------------------------");
        oTracerDebug.OutputCommands(cmds);

         Command cmd = new SequenceCommand(tr("Merge objects nodes"), cmds);

        return cmd;
    }


    /**
     * Try connect way to other buildings.
     * @param way Way to connect.
     * @return Commands.
     */
    public static List<Command> connectTo() {
        debugMsg("-- connectTo() --");

        Map<Way, Way> modifiedWays = new HashMap<Way, Way>();
        LinkedList<Command> cmds = new LinkedList<Command>();

        Way way = new Way(s_oWay);

        for (int i = 0; i < way.getNodesCount() - 1; i++) {
            Node n = way.getNode(i);
            debugMsg("   Node: " + n);
            LatLon ll = n.getCoor();

            // Will merge with something else?
            double minDistanceSq = s_dMinDistanceN2N;
            Node nearestNode = null;
            for (Node nn : new LinkedList<Node>(s_oNodes)) {
              debugMsg("    Node: " + nn);
                if (!nn.isUsable() || way.containsNode(nn) || s_oWay.containsNode(nn) || !isInSameTag(nn)) {
                    debugMsg("    -> Continue");
                    continue;
                }
                double dist = nn.getCoor().distance(ll);
                debugMsg("    Dist: "+ dist+"; minDistanceSq: "+ minDistanceSq);
                if (dist <= minDistanceSq) {
                    minDistanceSq = dist;
                    nearestNode = nn;
                }
            }

            debugMsg("   Nearest: " + nearestNode + " distance: " + minDistanceSq);
            if (nearestNode == null) {
//                  cmds.addAll(tryConnectNodeToAnyWay(n, modifiedWays));
            } else {
                debugMsg("+add Node distance: " + minDistanceSq);
                cmds.addAll(mergeNodes(n, nearestNode));
            }
        }

        for (Map.Entry<Way, Way> e : modifiedWays.entrySet()) {
            cmds.add(new ChangeCommand(e.getKey(), e.getValue()));
        }

        List<Command> cmd = cmds;
        return cmd;
    }

    /**
     *  Check all nodes of the given way and merge them with close nodes
     *  @param way
     *  @return List of commands
     */
    private static  List<Command>  fixOverlapedWays () {
      debugMsg("-- fixOverlapedWays() --");

      LinkedList<Command> cmds = new LinkedList<Command>();

      for (Way w : new LinkedList<Way>(s_oWays)) {
        if (waysAreOverlaped(s_oWay, w) {
          cmds.addAll(correctOverlaping(w));
        }
      }

      return cmds;
    }

    /**
     *  Check whether there is a building fully covered by traced building
     *  @return List of commands
     */
    private static  List<Command> removeFullyCoveredWays() {
      debugMsg("-- removeFullyCoveredWays() --");

      LinkedList<Command> cmds = new LinkedList<Command>();
      List<Way> tmpWaysList = new LinkedList<Way> (s_oWays);

      for (Way w : tmpWaysList) {
        if (!w.isUsable() || !isSameTag(w) || w.equals(s_oWay)) {
          continue;
        }

        Way tmpWay = new Way(w);
        if (isNodeInsideWay(w.getBBox().getCenter(), s_oWay)) {
          debugMsg("   Delete way: " + w);
          cmds.add(new DeleteCommand( w ));
          s_oWays.remove(w);
          // Remove old nodes from list of working nodes list
          for (int i = 0; i < tmpWay.getNodesCount() - 1; i++) {
            Node nd = tmpWay.getNode(i);
            if (!nd.isUsable()) {
              continue;
            }

            if ( getWaysOfNode(nd).size() == 0) {
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
     * Try connect node "node" to ways of other buildings.
     *
     * Zkusi zjistit, zda node neni tak blizko nejake usecky existujici budovy,
     * ze by mel byt zacnenen do teto usecky. Pokud ano, provede to.
     *
     * @param node Node to connect.
     * @param m map of ways.
     * @throws IllegalStateException
     * @throws IndexOutOfBoundsException
     * @return List of Commands.
     */
  private static List<Command>  tryConnectNodeToAnyWay(Node node, Map<Way, Way> m)
            throws IllegalStateException, IndexOutOfBoundsException {

        debugMsg("-- tryConnectNodeToAnyWay() --");

        LatLon ll = node.getCoor();
        List<Command> cmds = new LinkedList<Command>();

        // node nebyl slouceny s jinym
        // hledani pripadne blizke usecky, kam bod pridat
        double minDist = Double.MAX_VALUE;
        Way nearestWay = null;
        int nearestNodeIndex = 0;
        for (Way ww : s_oWays) {
            if (!ww.isUsable() || ww.containsNode(node) || !isSameTag(ww)) {
                continue;
            }

            if (m.get(ww) != null) {
                ww = m.get(ww);
            }

            for (Pair<Node, Node> np : ww.getNodePairs(false)) {
                //double dist1 = TracerGeometry.distanceFromSegment(ll, np.a.getCoor(), np.b.getCoor());
                double dist = distanceFromSegment2(ll, np.a.getCoor(), np.b.getCoor());
                //debugMsg(" distance: " + dist1 + "  " + dist);

                if (dist < minDist) {
                    minDist = dist;
                    nearestWay = ww;
                    nearestNodeIndex = ww.getNodes().indexOf(np.a);
                }
            }
        }
        debugMsg("   Nearest way: " + nearestWay + " distance: " + minDist);
        if (minDist < s_dMinDistanceN2oW) {
            Way newNWay = new Way(nearestWay);

            boolean duplicateNodeFound = false;
            for ( int i = 0; i < newNWay.getNodesCount(); i++) {
              if (newNWay.getNode(i).getCoor().distance(node.getCoor()) <= s_dMinDistanceN2N ) {
                // Do not put duplicated node, merge nodes instead
                cmds.addAll(mergeNodes(node, newNWay.getNode(i)));
                duplicateNodeFound = true;
              }
            }

            if (!duplicateNodeFound) {
              newNWay.addNode(nearestNodeIndex + 1, node);
            }

            debugMsg("   New way:" + newNWay);
            debugMsg("   +add WayOld.Node distance: " + minDist);
            m.put(nearestWay, newNWay);
            s_oWays.remove(newNWay);
            s_oWays.add(nearestWay);
            debugMsg("   Updated nearest way: " + nearestWay);
            debugMsg("   =-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=");
        }
      return cmds;
    }

    private static double distanceFromSegment2(LatLon c, LatLon a, LatLon b) {
      debugMsg("-- distanceFromSegment2() --");

      double x;
      double y;

        StraightLine oStraightLine1 = new StraightLine(
            new Point2D.Double(a.getX(),a.getY()),
            new Point2D.Double(b.getX(),b.getY()));
        StraightLine oStraightLine2 = new StraightLine(
            new Point2D.Double(c.getX(),c.getY()),
            new Point2D.Double(c.getX() + (a.getY()-b.getY()),c.getY() - (a.getX()-b.getX())));
        Point2D.Double oPoint = oStraightLine1.GetIntersectionPoint(oStraightLine2);

        if ((oPoint.x > a.getX() && oPoint.x > b.getX()) || (oPoint.x < a.getX() && oPoint.x < b.getX()) ||
            (oPoint.y > a.getY() && oPoint.y > b.getY()) || (oPoint.y < a.getY() && oPoint.y < b.getY())) {
          return 100000;
        }

        x=c.getX()-oPoint.getX();
        y=c.getY()-oPoint.getY();

        return Math.sqrt((x*x)+(y*y));
    }

    /**
     * Try split way by any existing building nodes.
     *
     * Zkusi zjistit zda nejake usecka z way by nemela prochazet nejakym existujicim bodem,
     * ktery je ji velmi blizko. Pokud ano, tak puvodni usecku rozdeli na dve tak, aby
     * prochazela takovym bodem.
     *
     * @param way Way to split.
     * @throws IndexOutOfBoundsException
     * @throws IllegalStateException
     * @return Modified way
     */
    private static Way trySplitWayByAnyNodes(Way way)
            throws IndexOutOfBoundsException, IllegalStateException {

        debugMsg("-- trySplitWayByAnyNodes() --");
        // projdi kazdou novou usecku a zjisti, zda by nemela vest pres existujici body
        int i = 0;
        while (i < way.getNodesCount()) {
            // usecka n1, n2
            LatLon n1 = way.getNodes().get(i).getCoor();
            LatLon n2 = way.getNodes().get((i + 1) % way.getNodesCount()).getCoor();
            debugMsg(way.getNodes().get(i) + "-----" + way.getNodes().get((i + 1) % way.getNodesCount()));
            double minDistanceSq = Double.MAX_VALUE;


            Node nearestNode = null;
            for (Node nod : s_oNodes) {
                if (!nod.isUsable() || way.containsNode(nod) || !isInSameTag(nod)) {
                    continue;
                }
                LatLon nn = nod.getCoor();
                //double dist = TracerGeometry.distanceFromSegment(nn, n1, n2);
                double dist = distanceFromSegment2(nn, n1, n2);
//                double angle = TracerGeometry.angleOfLines(n1, nn, nn, n2);
                //debugMsg("Angle: " + angle + " distance: " + dist + " Node: " + nod);
                if (!n1.equalsEpsilon(nn) && !n2.equalsEpsilon(nn) && dist < minDistanceSq){ // && Math.abs(angle) < maxAngle) {
                  minDistanceSq = dist;
//                  maxAngle = angle;
                    nearestNode = nod;
                }
            }
            debugMsg("   Nearest: " + nearestNode + " distance: " + minDistanceSq);
            if (nearestNode == null || minDistanceSq >= s_dMinDistanceN2tW) {
                // tato usecka se nerozdeli
                i++;
                debugMsg("");
                continue;
            } else {
                // rozdeleni usecky
                way.addNode(i + 1, nearestNode);
                i++;
                debugMsg("   +add Way.Node distance: " + minDistanceSq);
                debugMsg("");
                //i++;
                continue; // i nezvetsuji, treba bude treba rozdelit usecku znovu
            }
        }
        return way;
    }

    /**
     * Determines if the specified node is a part of a building.
     * @param n The node to be tested
     * @return True if building key is set and different from no,entrance
     */
    private static boolean isInSameTag(Node n) {
        debugMsg("-- isInSameTag() --");
        for (OsmPrimitive op : n.getReferrers()) {
            if (op instanceof Way) {
                if (isSameTag((Way) op)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Determines if the specified primitive denotes a building.
     * @param p The primitive to be tested
     * @return True if building key is set and different from no,entrance
     */
    protected static final boolean isSameTag(Way w) {
      debugMsg("-- isSameTag() --");
      return (w.getKeys().get("building") == null ? false : !w.getKeys().get("building").equals("no") && !w.getKeys().get("building").equals("entrance"));
    }

}
