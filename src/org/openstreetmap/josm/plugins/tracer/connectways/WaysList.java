/**
 *  Tracer - plugin for JOSM
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

package org.openstreetmap.josm.plugins.tracer.connectways;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;

import java.util.*;

/**
 * Private class to store ways we will working with
 *
 * Note: the first position is special - it holds master Way
 */

public class WaysList {
  private ArrayList <Way>  m_orig_ways;   // Stores original ways
  private ArrayList <Way>  m_updated_ways; // Stores working ways
  private ArrayList <Boolean>  m_has_shared_nodes; // Way was connected to the master (first) way
  private ArrayList <ArrayList<Node>> m_shared_nodes;  // Nodes shared with master way

  public WaysList () {
    init();
  }

  private void init () {
    m_orig_ways = new ArrayList <Way> ();
    m_updated_ways = new  ArrayList <Way> ();
    m_has_shared_nodes  = new  ArrayList <Boolean> ();
    m_shared_nodes = new  ArrayList <ArrayList<Node>> ();
  }

// ----------------------------------------------------
 /**
  *  Add way to the end of list
  *  @param way way
  */
  public void add (Way w) {
    if (m_orig_ways.indexOf(w) == -1 &&
        m_updated_ways.indexOf(w) == -1)
    {
      m_orig_ways.add(w);
      m_updated_ways.add(w);
      m_has_shared_nodes.add(false);
      m_shared_nodes.add(new ArrayList<Node>());
      checkSharedNodes(m_orig_ways.indexOf(w));
    }
  }

 /**
  *  Remove way from the end of list
  *  @param way way
  */
  public void remove (Way w) {
    remove(m_updated_ways.indexOf(w));
  }

 /**
  *  Remove way on the position
  *  @param int index
  */
  public void remove (int i) {
    if (i >= 0) {
      m_orig_ways.remove(i);
      m_updated_ways.remove(i);
      m_has_shared_nodes.remove(i);
      m_shared_nodes.remove(i);
    }
  }

 /**
  *  Set given was as the Master way
  *  @param way way
  */
  public void setAsMasterWay(Way w) {
    int i = m_updated_ways.indexOf(w);
    if ( i > 0) {
      m_orig_ways.set(0, m_orig_ways.get(i));
      m_updated_ways.set(0, m_updated_ways.get(i));
      m_has_shared_nodes.set(0, false);
      m_shared_nodes.set(0, new ArrayList<Node>());
      remove(i);
      for (int idx = 1; idx < m_updated_ways.size(); idx++) {
        checkSharedNodes(idx);
      }
    }
  }

 /**
  *  Replace working copy
  *  @param way Old Way
  *  @param way New Way
  */
  public void updateWay(Way ow, Way nw) {
    int i = m_updated_ways.indexOf(ow);
    if ( i >= 0)
      m_updated_ways.set(i, nw);
  }

 /**
  *  Returns size of the list
  *  @return int Size of the list
  */
  public int size () {
    return m_updated_ways.size();
  }

 /**
  *  Returns position on the list for given way
  *  @param  way way
  *  @return int position in the list
  */
  public int indexOf (Way w) {
    return m_updated_ways.indexOf(w);
  }

 /**
  *  Returns the original (initial) way for given updated way
  *  @param  way way
  *  @return way Initial way
  */
  public Way getOriginalWay(Way w) {
    if (m_orig_ways.size() > 0 && m_updated_ways.indexOf(w) >= 0) {
      return m_orig_ways.get(m_updated_ways.indexOf(w));
    }
    return new Way();
  }

 /**
  *  Returns working way on position i
  *  @param  int index
  *  @return way working way
  */
  public Way get(int i) {
    return m_updated_ways.get(i);
  }

 /**
  *  Returns master way
  *  @return master way
  */
  public Way getMasterWay() {
    return m_updated_ways.get(0);
  }

 /**
  *  Returns list of ways
  *  @return list List of ways
  */
  public ArrayList <Way> getWays () {
    return m_updated_ways;
  }

 /**
  *  Return list of ways connected to the Initial way
  *  @return list List of ways
  */
  public ArrayList <Way> getConnectedWays () {
    ArrayList <Way> r = new ArrayList <Way> ();
    for (int i = 0; i < m_updated_ways.size(); i++) {
      if (m_has_shared_nodes.get(i)) {
        r.add(m_updated_ways.get(i));
      }
    }
    return r;
  }

 /**
  *  Returns list of nodes originaly shared between given way and master way
  *  @param  Updated way
  *  @return List of shared nodes
  */
  public ArrayList <Node> getSharedNodes (Way w) {
    ArrayList <Node> sharedNodes = new ArrayList <Node> ();
    int idx = m_updated_ways.indexOf(w);

    if (idx >= 0) {
      for (Node n: m_shared_nodes.get(idx)) {
        if (m_updated_ways.get(idx).getNodes().indexOf(n) >= 0) {
          sharedNodes.add(n);
        }
      }
    }
    return sharedNodes;
  }

 /**
  *  Check whether way shares nodes with master way
  *  @param index of way to check
  */
  private void checkSharedNodes (int idx) {
    if (idx == 0) {
      return;
    }

    ArrayList<Node> sharedNodes = new ArrayList<Node>();
    for (Node n: m_orig_ways.get(idx).getNodes()) {
      if (m_orig_ways.get(0).getNodes().indexOf(n) >= 0) {
        sharedNodes.add(n);
      }
    }

    if (sharedNodes.size() > 0) {
      m_has_shared_nodes.set(idx, true);
      m_shared_nodes.set(idx, sharedNodes);
    }
  }

}