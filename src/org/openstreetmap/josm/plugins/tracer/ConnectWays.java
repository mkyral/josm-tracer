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

public class ConnectWays {
    static double s_dMinDistance      = 0.000006;   // Minimal distance, for objects
    static double s_dMinDistanceN2N   = 0.000001;  // Minimal distance, when nodes are merged
    static double s_dMinDistanceN2oW  = 0.000001;   // Minimal distance, when node is connected to other way
    static double s_dMinDistanceN2tW  = 0.000001;   // Minimal distance, when other node is connected this way
    final static double MAX_ANGLE = 30;       // Minimal angle, when other node is connected this way

    static Way s_oWay;
    static Way s_oWayOld;
    static List<Way> s_oWays;
    static List<Node> s_oNodes;
    static List<Node> connectedNodes = new LinkedList<Node>();
    static List<Node> deletedNodes = new LinkedList<Node>();

//     static ServerParam s_oParam;
    static boolean s_bCtrl;
    static boolean s_bAlt;

    static boolean s_bAddNewWay;

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

    private static void getWays(Way way) {
      BBox bbox = new BBox(way);
      bbox.addPrimitive(way,s_dMinDistance);
        s_oWays = Main.main.getCurrentDataSet().searchWays(bbox);
    }

    private static List<Way> getWaysOfNode(Node node) {
      List<Way> ways;
      ways = OsmPrimitive.getFilteredList(node.getReferrers(), Way.class);
      return ways;
    }

    private static void getConnectedNodes(Way way) {
      if (way == null) {
        return;
      }

      List<Way> linkedWays;
      for (int i = 0; i < way.getNodesCount(); i++) {
        linkedWays = getWaysOfNode(way.getNode(i));
        if (linkedWays.size() > 1) {
          connectedNodes.add(way.getNode(i));
          System.out.println("Connected node: " + way.getNode(i));
        }
      }
    }

    private static  List<Command>  checkAndMergeNodes (Way way) {
      Map<Way, Way> modifiedWays = new HashMap<Way, Way>();
      LinkedList<Command> cmds = new LinkedList<Command>();
      Way newWay = new Way(way);
      System.out.println("-- checkAndMergeNodes() --");
       System.out.println("Deleted nodes: " + deletedNodes);
      for (int i = 0; i < way.getNodesCount() - 1; i++) {
        Node n = way.getNode(i);
        LatLon ll = n.getCoor();
        BBox bbox = new BBox(
                ll.getX() - s_dMinDistance,
                ll.getY() - s_dMinDistance,
                ll.getX() + s_dMinDistance,
                ll.getY() + s_dMinDistance);

        // bude se node slucovat s jinym?
        double minDistanceSq = s_dMinDistance;
        List<Node> nodes = Main.main.getCurrentDataSet().searchNodes(bbox);
        for (Node nn : nodes) {
          System.out.println("Checking node: " + nn);
          if (!nn.isUsable() || way.containsNode(nn) || newWay.containsNode(nn) || !isInSameTag(nn) || deletedNodes.indexOf(nn) >= 0 || nn.isDeleted()) {
              System.out.println("->continue");
              continue;
          }
          double dist = nn.getCoor().distance(ll);
          if (dist < minDistanceSq && nn.getId() == 0) {
            System.out.println("Merge nodes: " + n.toString() + " and " + nn.toString() + ", ID: " + nn.getId());
              cmds.addAll(mergeNodes(n, nn));
          }
        }
      }
      return cmds;
    }

    private static void getNodes(Way way) {
      BBox bbox = new BBox(way);
      bbox.addPrimitive(way,s_dMinDistance);
      s_oNodes = Main.main.getCurrentDataSet().searchNodes(bbox);
    }

    private static double calcAlpha(LatLon oP1, Node n) {
      LatLon oP2 = n.getCoor();

      double dAlpha = Math.atan((oP2.getY() - oP1.getY()) / (oP2.getX() - oP1.getX())) * 180 / Math.PI + (oP1.getX() > oP2.getX() ? 180 : 0);
      return checkAlpha(dAlpha);
    }

    private static Double checkAlpha(Double dAlpha) {
        if (dAlpha > 180) {
            return dAlpha - 360;
        }
        if (dAlpha <= -180) {
            return dAlpha + 360;
        }
        return dAlpha;
    }

    private static boolean isNodeInsideWay(LatLon pos, Way way) {
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

    private static Way getOldWay(LatLon pos) {
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

    private static Way updateKeys(Way d_way, Way s_way, String newSource) {

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

    /**
     * Try connect way to other buildings.
     * @param way Way to connect.
     * @return Commands.
     */
    public static Command connect(Way newWay, LatLon pos, boolean ctrl, boolean alt, String source) {
        LinkedList<Command> cmds = new LinkedList<Command>();
        LinkedList<Command> cmds2 = new LinkedList<Command>();

        s_bCtrl = ctrl;
        s_bAlt = alt;

//         calcDistance();
        getNodes(newWay);
        getWays(newWay);

        s_oWayOld = getOldWay(pos);
        getConnectedNodes(s_oWayOld);

        if (s_oWayOld == null) {
          s_bAddNewWay = true;
          cmds.add(new AddCommand(newWay));
          s_oWayOld = newWay;
          s_oWay = new Way( newWay );
        } else {

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
              System.out.println("Old and New ways have " + s_oWayOld.getNodesCount() + " nodes");
              // 2) All nodes have the same coordination
              outer: for (n = 0; n < nodesCount; n++) {
                Node newNode = newWay.getNode(n);
                System.out.println("New.Node(" + n + ") = " + newNode.getCoor().toDisplayString());
                inner: for (o = 0; o < nodesCount; o++) {
                  Node oldNode = s_oWayOld.getNode(o);
                  System.out.println(" -> Old.Node(" + o + ") = " + oldNode.getCoor().toDisplayString());
                  if (oldNode.getCoor().equalsEpsilon(newNode.getCoor())) {
                    System.out.println("Nodes: New(" + n + ") and Old(" + o + ") are equal.");
                    nodesFound += 1;
                    continue outer;
                  }
                }
              }

              System.out.println("nodesCount = " + nodesCount + "; nodesFound = " + nodesFound);
              if (nodesCount == nodesFound) {
                System.out.println("Ways are equal!");
                return new SequenceCommand("Nothing", null);
              }
            }

            // Ways are different - merging
            System.out.println("Ways are NOT equal!");
            Way tempWay;
            s_bAddNewWay = false;

            // Create a working copy of the oldWay
            tempWay = new Way(s_oWayOld);

            // Add New nodes
            for (int i = 0; i < newWay.getNodesCount(); i++) {
              tempWay.addNode(tempWay.getNodesCount(), newWay.getNode(i));
            }

            // Remove Old nodes
            for (int i = 0; i < s_oWayOld.getNodesCount() - 1; i++) {
              tempWay.removeNode( s_oWayOld.getNode(i) );
            }

            // Remove old nodes from list of working nodes list
            for (int i = 0; i < s_oWayOld.getNodesCount() - 1; i++) {
              Node nd = s_oWayOld.getNode(i);
              List<Way> ways = getWaysOfNode(nd);
              if (ways.size()<=1) {
                  cmds2.add(new DeleteCommand( s_oWayOld.getNode(i) ));
                  deletedNodes.add(s_oWayOld.getNode(i));
                  s_oNodes.remove(s_oWayOld.getNode(i));
              }
//               s_oNodes.remove(s_oWayOld.getNode(i));
            }
            s_oWay = tempWay;
            s_oWay = updateKeys(s_oWay, newWay, source);
        }

        cmds2.addAll(connectTo());

        // Modify the way
        cmds.add(new ChangeCommand(s_oWayOld, trySplitWayByAnyNodes(s_oWay)));

        cmds2.addAll(checkAndMergeNodes(s_oWay));

        cmds.addAll(cmds2);


//         TracerDebug oTracerDebug = new TracerDebug();
//         oTracerDebug.OutputCommands(cmds);

        Command cmd = new SequenceCommand(tr("Merge objects nodes"), cmds);

        return cmd;
    }


    /**
     * Try connect way to other buildings.
     * @param way Way to connect.
     * @return Commands.
     */
    public static List<Command> connectTo() {
        Map<Way, Way> modifiedWays = new HashMap<Way, Way>();
        LinkedList<Command> cmds = new LinkedList<Command>();
        Way way = new Way(s_oWay);
        for (int i = 0; i < way.getNodesCount() - 1; i++) {
            Node n = way.getNode(i);
            System.out.println("-------");
            System.out.println("Node: " + n);
            LatLon ll = n.getCoor();

            // Will merge with something else?
            double minDistanceSq = s_dMinDistanceN2N;
            Node nearestNode = null;
            for (Node nn : s_oNodes) {
              System.out.println("Node: " + nn);
                if (!nn.isUsable() || way.containsNode(nn) || s_oWay.containsNode(nn) || !isInSameTag(nn) || deletedNodes.indexOf(nn) >= 0 || nn.isDeleted()) {
                    System.out.println("deletedNodes.indexOf(nn): " + deletedNodes.indexOf(nn));
                    System.out.println("nn.isDeleted(): " + nn.isDeleted());
                    System.out.println("-> Continue");
                    continue;
                }
                double dist = nn.getCoor().distance(ll);
                System.out.println("Dist: "+ dist+"; minDistanceSq: "+ minDistanceSq);
                if (dist <= minDistanceSq) {
                    minDistanceSq = dist;
                    nearestNode = nn;
                }
            }

            System.out.println("Nearest: " + nearestNode + " distance: " + minDistanceSq);
            if (nearestNode == null) {
                cmds.addAll(tryConnectNodeToAnyWay(n, modifiedWays));
            } else {
                System.out.println("+add Node distance: " + minDistanceSq);
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
     * Merges two nodes
     * @param n1 First node
     * @param n2 Second node
     * @return List of Commands.
     */
    private static List<Command> mergeNodes(Node n1, Node n2){
        List<Command> cmds = new LinkedList<Command>();
        Node n1x = n1;

        cmds.add(new MoveCommand(n2,
                 (n1.getEastNorth().getX() - n2.getEastNorth().getX())/2,
                 (n1.getEastNorth().getY() - n2.getEastNorth().getY())/2
                 ));

        System.out.println("-- mergeNodes --");
        System.out.println("-- n1: " + n1.toString() + ", n2: " + n2.toString());
        System.out.println("-- Way: " + s_oWay.toString());
        Way newWay = new Way(s_oWay);

        int j = s_oWay.getNodes().indexOf(n1);
        System.out.println("-- j: " + j);
        System.out.println("--  ----  ----  ----  ----  ----  ----  ----  ----  --");

        if (j < 0) {
//           // Node not found, try lower precision
//           for (int i = 0; i < s_oWay.getNodesCount(); i++) {
//             if (s_oWay.getNode(i).getCoor().distance(n1.getCoor()) <= s_dMinDistanceN2N) {
//               j = i;
//               n1x = s_oWay.getNode(i);
//               break;
//             }
//           }
        return cmds;

        }
        newWay.addNode(j, n2);
        if (j == 0) {
            // first + last point
          newWay.addNode(newWay.getNodesCount(), n2);
        }

        newWay.removeNode(n1x);

        s_oWay = new Way(newWay);

        cmds.add(new DeleteCommand(n1x));
        if (n1x.getDataSet() != null){
          List<Way> ways = getWaysOfNode(n1x);
          if (ways.size()<=1) {
              deletedNodes.add(n1x);
          }
        }

        return cmds;
    }

    /**
     * Try connect node "node" to way of other building.
     *
     * Zkusi zjistit, zda node neni tak blizko nejake usecky existujici budovy,
     * ze by mel byt zacnenen do teto usecky. Pokud ano, provede to.
     *
     * @param node Node to connect.
     * @throws IllegalStateException
     * @throws IndexOutOfBoundsException
     * @return List of Commands.
     */
  private static List<Command>  tryConnectNodeToAnyWay(Node node, Map<Way, Way> m)
            throws IllegalStateException, IndexOutOfBoundsException {

        LatLon ll = node.getCoor();
        List<Command> cmds = new LinkedList<Command>();

        // node nebyl slouceny s jinym
        // hledani pripadne blizke usecky, kam bod pridat
        double minDist = Double.MAX_VALUE;
        Way nearestWay = null;
        int nearestNodeIndex = 0;
        for (Way ww : s_oWays) {
//           System.out.println("Way: " + ww);
            if (!ww.isUsable() || ww.containsNode(node) || !isSameTag(ww)) {
//                 System.out.println("!ww.isUsable(): "+!ww.isUsable()+"; ww.containsNode(node): "+ww.containsNode(node)+"; !isSameTag(ww): "+!isSameTag(ww));
                continue;
            }

            if (m.get(ww) != null) {
                ww = m.get(ww);
            }

            for (Pair<Node, Node> np : ww.getNodePairs(false)) {
                //double dist1 = TracerGeometry.distanceFromSegment(ll, np.a.getCoor(), np.b.getCoor());
                double dist = distanceFromSegment2(ll, np.a.getCoor(), np.b.getCoor());
                //System.out.println(" distance: " + dist1 + "  " + dist);

                if (dist < minDist) {
                    minDist = dist;
                    nearestWay = ww;
                    nearestNodeIndex = ww.getNodes().indexOf(np.a);
                }
            }
        }
        System.out.println("Nearest way: " + nearestWay + " distance: " + minDist);
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

            System.out.println("New way:" + newNWay);
            System.out.println("+add WayOld.Node distance: " + minDist);
            m.put(nearestWay, newNWay);
            s_oWays.remove(newNWay);
            s_oWays.add(nearestWay);
            System.out.println("Updated nearest way: " + nearestWay.toString());
            System.out.println("=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=");
        }
      return cmds;
    }

    private static double distanceFromSegment2(LatLon c, LatLon a, LatLon b) {
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

        // projdi kazdou novou usecku a zjisti, zda by nemela vest pres existujici body
        int i = 0;
        while (i < way.getNodesCount()) {
            // usecka n1, n2
            LatLon n1 = way.getNodes().get(i).getCoor();
            LatLon n2 = way.getNodes().get((i + 1) % way.getNodesCount()).getCoor();
            System.out.println(way.getNodes().get(i) + "-----" + way.getNodes().get((i + 1) % way.getNodesCount()));
            double minDistanceSq = Double.MAX_VALUE;
//            double maxAngle = MAX_ANGLE;
            //List<Node> nodes = Main.main.getCurrentDataSet().searchNodes(new BBox(
            //    Math.min(n1.getX(), n2.getX()) - minDistanceSq,
            //    Math.min(n1.getY(), n2.getY()) - minDistanceSq,
            //    Math.max(n1.getX(), n2.getX()) + minDistanceSq,
            //    Math.max(n1.getY(), n2.getY()) + minDistanceSq
            //));

            Node nearestNode = null;
            for (Node nod : s_oNodes) {
                if (!nod.isUsable() || way.containsNode(nod) || !isInSameTag(nod) || deletedNodes.indexOf(nod) >= 0 || nod.isDeleted()) {
                    System.out.println("deletedNodes.indexOf(nod): " + deletedNodes.indexOf(nod));
                    System.out.println("nod.isDeleted(): " + nod.isDeleted());
                    System.out.println("->continue");
                    continue;
                }
                LatLon nn = nod.getCoor();
                //double dist = TracerGeometry.distanceFromSegment(nn, n1, n2);
                double dist = distanceFromSegment2(nn, n1, n2);
//                double angle = TracerGeometry.angleOfLines(n1, nn, nn, n2);
                //System.out.println("Angle: " + angle + " distance: " + dist + " Node: " + nod);
                if (!n1.equalsEpsilon(nn) && !n2.equalsEpsilon(nn) && dist < minDistanceSq){ // && Math.abs(angle) < maxAngle) {
                  minDistanceSq = dist;
//                  maxAngle = angle;
                    nearestNode = nod;
                }
            }
            System.out.println("Nearest_: " + nearestNode + " distance: " + minDistanceSq);
            if (nearestNode == null || minDistanceSq >= s_dMinDistanceN2tW) {
                // tato usecka se nerozdeli
                i++;
                System.out.println("");
                continue;
            } else {
                // rozdeleni usecky
                way.addNode(i + 1, nearestNode);
                i++;
                System.out.println("+add Way.Node distance: " + minDistanceSq);
                System.out.println("");
                //i++;
                continue; // i nezvetsuji, treba bude treba rozdelit usecku znovu
            }
        }
        return way;
    }

    private static boolean isInSameTag(Node n) {
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
//         String v = p.get(s_oParam.getTag());
//         if (s_bCtrl || s_oParam.getTag().equals("")) {
//           return  v == null || v.equals("no");
//         }
//         if (s_oParam.getTag().equals("building")) {
//           return v != null && !v.equals("no") && !v.equals("entrance");
//         }
//         return v != null && !v.equals("no");
//       System.out.println(w.toString()+" |Tag: "+w.getKeys().get("building"));
      return (w.getKeys().get("building") == null ? false : !w.getKeys().get("building").equals("no") && !w.getKeys().get("building").equals("entrance"));
    }

}
