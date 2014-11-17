/*******************************************************************************
*                                                                              *
* Author    :  Angus Johnson                                                   *
* Version   :  6.2.1                                                           *
* Date      :  31 October 2014                                                 *
* Website   :  http://www.angusj.com                                           *
* Copyright :  Angus Johnson 2010-2014                                         *
*                                                                              *
* Java port :  Copyright (c) Martin Svec 2014                                  *
*                                                                              *
* License:                                                                     *
* Use, modification & distribution is subject to Boost Software License Ver 1. *
* http://www.boost.org/LICENSE_1_0.txt                                         *
*                                                                              *
* Attributions:                                                                *
* The code in this library is an extension of Bala Vatti's clipping algorithm: *
* "A generic solution to polygon clipping"                                     *
* Communications of the ACM, Vol 35, Issue 7 (July 1992) pp 56-63.             *
* http://portal.acm.org/citation.cfm?id=129906                                 *
*                                                                              *
* Computer graphics and geometric modeling: implementation and algorithms      *
* By Max K. Agoston                                                            *
* Springer; 1 edition (January 4, 2005)                                        *
* http://books.google.com/books?q=vatti+clipping+agoston                       *
*                                                                              *
* See also:                                                                    *
* "Polygon Offsetting by Computing Winding Numbers"                            *
* Paper no. DETC2005-85513 pp. 565-575                                         *
* ASME 2005 International Design Engineering Technical Conferences             *
* and Computers and Information in Engineering Conference (IDETC/CIE2005)      *
* September 24-28, 2005 , Long Beach, California, USA                          *
* http://www.me.berkeley.edu/~mcmains/pubs/DAC05OffsetPolygon.pdf              *
*                                                                              *
*******************************************************************************/


package org.openstreetmap.josm.plugins.tracer.clipper;

import java.util.ArrayList;
import java.util.List;

public class PolyNode {
      PolyNode m_Parent;
      Path m_polygon = new Path();
      int m_Index;
      JoinType m_jointype;
      EndType m_endtype;
      List<PolyNode> m_Childs = new ArrayList<>();
      boolean m_isOpen;

      private boolean isHoleNode()
      {
          boolean result = true;
          PolyNode node = m_Parent;
          while (node != null)
          {
              result = !result;
              node = node.m_Parent;
          }
          return result;
      }

      public int getChildCount()
      {
          return m_Childs.size();
      }

      public Path getContour()
      {
          return m_polygon;
      }

      void addChild(PolyNode Child)
      {
          int cnt = m_Childs.size();
          m_Childs.add(Child);
          Child.m_Parent = this;
          Child.m_Index = cnt;
      }

      public PolyNode getNext()
      {
          if (m_Childs.size() > 0)
              return m_Childs.get(0);
          else
              return getNextSiblingUp();
      }

      PolyNode getNextSiblingUp()
      {
          if (m_Parent == null)
              return null;
          else if (m_Index == m_Parent.m_Childs.size() - 1)
              return m_Parent.getNextSiblingUp();
          else
              return m_Parent.m_Childs.get(m_Index + 1);
      }

      public List<PolyNode> getChilds()
      {
          return m_Childs;
      }

      public PolyNode getParent()
      {
          return m_Parent;
      }

      public boolean isHole()
      {
          return isHoleNode();
      }

      public boolean isOpen()
      {
          return m_isOpen;
      }
}
