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

  public class PolyTree extends PolyNode
  {
      List<PolyNode> m_AllPolys = new ArrayList<>();

      public void clear()
      {
          m_AllPolys.clear();
          m_Childs.clear();
      }

      public PolyNode getFirst()
      {
          if (m_Childs.size() > 0)
              return m_Childs.get(0);
          else
              return null;
      }

      public int getTotal()
      {
            int result = m_AllPolys.size();
            //with negative offsets, ignore the hidden outer polygon ...
            if (result > 0 && m_Childs.get(0) != m_AllPolys.get(0)) result--;
            return result;
      }

  }
