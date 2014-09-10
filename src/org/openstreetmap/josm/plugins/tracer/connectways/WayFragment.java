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
 * Class to store Way fragment
 */
public class WayFragment {

  private ArrayList <Node> m_fragment; // list of nodes in fragment
  private Way m_way;
  private int m_start_idx = -1;
  private int m_end_idx = -1;
  private int m_current_idx = -1;
  private int m_current_inner_idx;

  /**
   *  Constructor
   *  @param way - the way of the fragment
   *  @param first - index of the first node of the fragment
   *  @param last - index of the last node in the fragment
   */
  public WayFragment (Way way, int first, int last) {
    m_fragment = new ArrayList <Node> ();
    m_way = way;
    m_start_idx = first;
    m_end_idx = last;
    m_current_idx = -1;
    m_current_inner_idx = 0;

    System.out.println("   -- WayFragment() --");
    System.out.println("   WF: start_index: " + m_start_idx + "; end_Index: " + m_end_idx);

    int i = m_start_idx;
    while (true) {
      if (i >= 0 && i <= way.getRealNodesCount() && m_fragment.indexOf(way.getNode(i)) == -1) {
        m_fragment.add(way.getNode(i));
      }

      if (i == m_end_idx)
        break;

      i++;
      if (i > way.getRealNodesCount()) {
        i = 0;
      }
    }
    System.out.println("   WF: " + toString());
  }

  /**
   *  Reset internal nodes index.
   */
  public void resetIndex() {
    m_current_idx = 0;
  }

  /**
   *  Reset internal index of inner nodes.
   */
  public void resetInnerIndex() {
    m_current_inner_idx = 0;
  }

  /**
   *  Reset internal index of inner nodes.
   */
  public void setMaxInnerIndex() {
    m_current_inner_idx = m_fragment.size() - 1;
  }

  /**
   *  Return count of nodes in the fragment.
   *  @return count of nodes in fragment
   */
  public int getNodesCount() {
    return m_fragment.size();
  }

  /**
   *  Return count of inner nodes in the fragment.
   *  @return count of inner nodes in fragment
   */
  public int getInnerNodesCount() {
    if (m_fragment.size() < 3)
      return 0;

    return m_fragment.size() - 2;
  }

  /**
   *  Return whether fragment contains inner nodes (has at least 3 nodes).
   *  @return true/false
   */
  public boolean hasInnerNodes() {
    if (m_fragment.size() < 3)
      return false;

    return true;
  }

  /**
   *  Return first node of the fragment.
   *  @return first node of the fragment
   */
  public Node getFirstNode() {
    return m_fragment.get(0);
  }

  /**
   *  Return last node of the fragment.
   *  @return last node of the fragment
   */
  public Node getLastNode() {
    return m_fragment.get(m_fragment.size()-1);
  }

  /**
   *  Return next node of the fragment
   *  @return next node of the fragment
   */
  public Node getCurrentNode() {
    if (m_current_idx < m_fragment.size()) {
      return m_fragment.get(m_current_idx);
    }

    return null;
  }

  /**
   *  Return next node of the fragment
   *  @return next node of the fragment
   */
  public Node getNextNode() {
    m_current_idx++;
    return getCurrentNode();
  }

  /**
   *  Return current inner node of the fragment.
   *  @return current inner node of the fragment
   */
  public Node getCurrentInnerNode() {
    if (m_current_inner_idx < m_fragment.size() - 1 &&
        m_current_inner_idx > 0) {
      return m_fragment.get(m_current_inner_idx);
    }

    return null;
  }

  /**
   *  Return next inner node of the fragment
   *  @return next inner node of the fragment
   */
  public Node getNextInnerNode() {
    m_current_inner_idx++;
    return getCurrentInnerNode();
  }

  /**
   *  Return next inner node of the fragment
   *  @return next inner node of the fragment
   */
  public Node getPreviousInnerNode() {
    m_current_inner_idx--;
    return getCurrentInnerNode();
  }

  /**
   *  Return node at the position of index.
   *  @param idx node position in the fragment
   *  @return Return node at the position of index
   */
  public Node getNode(int idx) {
    if (idx >= 0 && idx < m_fragment.size()) {
      return m_fragment.get(idx);
    }

    return null;
  }

  public String toString() {
    String ret = "";
    if (m_way == null)
      return ret;

    ret = ret + "Way id= " + m_way.getUniqueId();
    ret = ret + " {Nodes: ";
    for (Node n: m_fragment) {
      ret = ret + "[" + n.getUniqueId() + "]";
    }
    ret = ret + "}";
    return ret;
  }
}