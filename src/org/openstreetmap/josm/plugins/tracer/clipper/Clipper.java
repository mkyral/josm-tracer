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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;

enum JoinType { jtSquare, jtRound, jtMiter };
enum EndType { etClosedPolygon, etClosedLine, etOpenButt, etOpenSquare, etOpenRound };

enum EdgeSide {esLeft, esRight};
enum Direction {dRightToLeft, dLeftToRight};

class OutPt
{
    int Idx;
    final Point2d iPt = new Point2d();
    OutPt Next;
    OutPt Prev;
}

class Scanbeam {

    long Y;
    Scanbeam Next;
}

class OutRec {

    int Idx;
    boolean IsHole;
    boolean IsOpen;
    OutRec FirstLeft; //see comments in clipper.pas
    OutPt Pts;
    OutPt BottomPt;
    PolyNode PolyNode;
};

class Join {

    OutPt OutPt1;
    OutPt OutPt2;
    final Point2d iOffPt = new Point2d();
};

class IntersectNode {

    TEdge Edge1;
    TEdge Edge2;
    final Point2d iPt = new Point2d();
};

class LocalMinima {

    long Y;
    TEdge LeftBound;
    TEdge RightBound;
    LocalMinima Next;
}

class TEdge {

    final Point2d iBot = new Point2d();
    final Point2d iCurr = new Point2d();
    final Point2d iTop = new Point2d();
    final Point2d iDelta = new Point2d();
    double Dx;
    PolyType PolyTyp;
    EdgeSide Side;
    int WindDelta; //1 or -1 depending on winding direction
    int WindCnt;
    int WindCnt2; //winding count of the opposite polytype
    int OutIdx;
    TEdge Next;
    TEdge Prev;
    TEdge NextInLML;
    TEdge NextInAEL;
    TEdge PrevInAEL;
    TEdge NextInSEL;
    TEdge PrevInSEL;
}

class MyIntersectNodeSort implements Comparator<IntersectNode> {

    @Override
    public int compare(IntersectNode node1, IntersectNode node2) {
        long i = node2.iPt.Y - node1.iPt.Y;
        if (i > 0) {
            return 1;
        } else if (i < 0) {
            return -1;
        } else {
            return 0;
        }
    }
}


class ClipperBase {
    protected final double horizontal = -3.4E+38;
    protected final int Skip = -2;
    protected final int Unassigned = -1;
    protected static double tolerance = 1.0E-20;

    protected static final long loRange = 0x3FFFFFFF;
    protected static final long hiRange = 0x3FFFFFFFFFFFFFFFL;
    
    private boolean m_preserveCollinear = false;
    
    public boolean getPreserveCollinear() {
        return m_preserveCollinear;
    }
    
    public void setPreserveCollinear(boolean v) {
        m_preserveCollinear = v;
    }
    
    static boolean nearZero(double val) {
        return (val > -tolerance) && (val < tolerance);
    }
    
    LocalMinima m_MinimaList;
    LocalMinima m_CurrentLM;
    List<List<TEdge>> m_edges = new ArrayList<>();
    boolean m_UseFullRange;
    boolean m_HasOpenPaths;
    
    static boolean isHorizontal(TEdge e)
    {
        return e.iDelta.Y == 0;
    }
    
    private static BigInteger Int128Mul(long x, long y) {
        BigInteger bx = BigInteger.valueOf(x);
        BigInteger by = BigInteger.valueOf(y);
        return bx.multiply(by);
    }
    
    //------------------------------------------------------------------------------

    boolean pointIsVertex(Point2d pt, OutPt pp)
    {
        OutPt pp2 = pp;
        do
        {
            if (pp2.iPt.equals(pt))
                return true;
            pp2 = pp2.Next;
        }
        while (pp2 != pp);
        
        return false;
    }
    
    boolean pointOnLineSegment(Point2d pt, Point2d linePt1, Point2d linePt2, boolean UseFullRange)
    {
      if (UseFullRange)
        return ((pt.X == linePt1.X) && (pt.Y == linePt1.Y)) ||
          ((pt.X == linePt2.X) && (pt.Y == linePt2.Y)) ||
          (((pt.X > linePt1.X) == (pt.X < linePt2.X)) &&
          ((pt.Y > linePt1.Y) == (pt.Y < linePt2.Y)) &&
          ((Int128Mul((pt.X - linePt1.X), (linePt2.Y - linePt1.Y)).equals(
          Int128Mul((linePt2.X - linePt1.X), (pt.Y - linePt1.Y))))));
      else
        return ((pt.X == linePt1.X) && (pt.Y == linePt1.Y)) ||
            ((pt.X == linePt2.X) && (pt.Y == linePt2.Y)) ||
            (((pt.X > linePt1.X) == (pt.X < linePt2.X)) &&
            ((pt.Y > linePt1.Y) == (pt.Y < linePt2.Y)) &&
            ((pt.X - linePt1.X) * (linePt2.Y - linePt1.Y) ==
            (linePt2.X - linePt1.X) * (pt.Y - linePt1.Y)));
    }
    //------------------------------------------------------------------------------

    boolean pointOnPolygon(Point2d pt, OutPt pp, boolean UseFullRange)
    {
        OutPt pp2 = pp;
        while (true)
        {
            if (pointOnLineSegment(pt, pp2.iPt, pp2.Next.iPt, UseFullRange))
                return true;
            pp2 = pp2.Next;
            if (pp2 == pp)
                break;
        }
        return false;
    }
    
    static boolean slopesEqual(TEdge e1, TEdge e2, boolean UseFullRange)
    {
        if (UseFullRange)
          return Int128Mul(e1.iDelta.Y, e2.iDelta.X).equals(
              Int128Mul(e1.iDelta.X, e2.iDelta.Y));
        else return (long)(e1.iDelta.Y) * (e2.iDelta.X) ==
          (long)(e1.iDelta.X) * (e2.iDelta.Y);
    }
    //------------------------------------------------------------------------------

    static boolean slopesEqual(Point2d pt1, Point2d pt2, Point2d pt3, boolean UseFullRange)
    {
        if (UseFullRange)
            return Int128Mul(pt1.Y - pt2.Y, pt2.X - pt3.X).equals(
              Int128Mul(pt1.X - pt2.X, pt2.Y - pt3.Y));
        else return
          (long)(pt1.Y - pt2.Y) * (pt2.X - pt3.X) - (long)(pt1.X - pt2.X) * (pt2.Y - pt3.Y) == 0;
    }
    
    //------------------------------------------------------------------------------

    static boolean slopesEqual(Point2d pt1, Point2d pt2, Point2d pt3, Point2d pt4, boolean UseFullRange)
    {
        if (UseFullRange)
            return Int128Mul(pt1.Y - pt2.Y, pt3.X - pt4.X).equals(
              Int128Mul(pt1.X - pt2.X, pt3.Y - pt4.Y));
        else return
          (long)(pt1.Y - pt2.Y) * (pt3.X - pt4.X) - (long)(pt1.X - pt2.X) * (pt3.Y - pt4.Y) == 0;
    }
    
    ClipperBase() //constructor (nb: no external instantiation)
    {
        m_MinimaList = null;
        m_CurrentLM = null;
        m_UseFullRange = false;
        m_HasOpenPaths = false;
    }
    
    public void clear()
    {
        disposeLocalMinimaList();
        m_edges.clear();
        m_UseFullRange = false;
        m_HasOpenPaths = false;
    }
    
    private void disposeLocalMinimaList()
    {
        while (m_MinimaList != null)
        {
            LocalMinima tmpLm = m_MinimaList.Next;
            m_MinimaList = null;
            m_MinimaList = tmpLm;
        }
        m_CurrentLM = null;
    }
        
    boolean rangeTest(Point2d Pt, boolean useFullRange) throws ClipperException
    {
      if (useFullRange)
      {
        if (Pt.X > hiRange || Pt.Y > hiRange || -Pt.X > hiRange || -Pt.Y > hiRange) 
          throw new ClipperException("Coordinate outside allowed range");
      }
      else if (Pt.X > loRange || Pt.Y > loRange || -Pt.X > loRange || -Pt.Y > loRange) 
      {
        useFullRange = true;
        rangeTest(Pt, useFullRange);
      }      
      return useFullRange;
    }
    
    
    private void initEdge(TEdge e, TEdge eNext, TEdge ePrev, Point2d pt)
    {
      e.Next = eNext;
      e.Prev = ePrev;
      e.iCurr.assign(pt);
      e.OutIdx = Unassigned;    
    }
    
    //------------------------------------------------------------------------------

    private void initEdge2(TEdge e, PolyType polyType)
    {
      if (e.iCurr.Y >= e.Next.iCurr.Y)
      {
        e.iBot.assign(e.iCurr);
        e.iTop.assign(e.Next.iCurr);
      }
      else
      {
        e.iTop.assign(e.iCurr);
        e.iBot.assign(e.Next.iCurr);
      }
      setDx(e);
      e.PolyTyp = polyType;
    }
    
    private TEdge findNextLocMin(TEdge E)
    {
      TEdge E2;
      for (;;)
      {
        while (E.iBot.not_equals(E.Prev.iBot) || E.iCurr.equals(E.iTop)) E = E.Next;
        if (E.Dx != horizontal && E.Prev.Dx != horizontal) break;
        while (E.Prev.Dx == horizontal) E = E.Prev;
        E2 = E;
        while (E.Dx == horizontal) E = E.Next;
        if (E.iTop.Y == E.Prev.iBot.Y) continue; //ie just an intermediate horz.
        if (E2.Prev.iBot.X < E.iBot.X) E = E2;
        break;
      }
      return E;
    }
    
    
    
    private TEdge processBound(TEdge E, boolean LeftBoundIsForward)
    {
      TEdge EStart, Result = E;
      TEdge Horz;

      if (Result.OutIdx == Skip)
      {
        //check if there are edges beyond the skip edge in the bound and if so
        //create another LocMin and calling ProcessBound once more ...
        E = Result;
        if (LeftBoundIsForward)
        {
          while (E.iTop.Y == E.Next.iBot.Y) E = E.Next;
          while (E != Result && E.Dx == horizontal) E = E.Prev;
        }
        else
        {
          while (E.iTop.Y == E.Prev.iBot.Y) E = E.Prev;
          while (E != Result && E.Dx == horizontal) E = E.Next;
        }
        if (E == Result)
        {
          if (LeftBoundIsForward) Result = E.Next;
          else Result = E.Prev;
        }
        else
        {
          //there are more edges in the bound beyond result starting with E
          if (LeftBoundIsForward)
            E = Result.Next;
          else
            E = Result.Prev;
          LocalMinima locMin = new LocalMinima();
          locMin.Next = null;
          locMin.Y = E.iBot.Y;
          locMin.LeftBound = null;
          locMin.RightBound = E;
          E.WindDelta = 0;
          Result = processBound(E, LeftBoundIsForward);
          insertLocalMinima(locMin);
        }
        return Result;
      }

      if (E.Dx == horizontal)
      {
        //We need to be careful with open paths because this may not be a
        //true local minima (ie E may be following a skip edge).
        //Also, consecutive horz. edges may start heading left before going right.
        if (LeftBoundIsForward) EStart = E.Prev;
        else EStart = E.Next;
        if (EStart.OutIdx != Skip)
        {
          if (EStart.Dx == horizontal) //ie an adjoining horizontal skip edge
          {
            if (EStart.iBot.X != E.iBot.X && EStart.iTop.X != E.iBot.X)
              reverseHorizontal(E);
          }
          else if (EStart.iBot.X != E.iBot.X)
            reverseHorizontal(E);
        }
      }

      EStart = E;
      if (LeftBoundIsForward)
      {
        while (Result.iTop.Y == Result.Next.iBot.Y && Result.Next.OutIdx != Skip)
          Result = Result.Next;
        if (Result.Dx == horizontal && Result.Next.OutIdx != Skip)
        {
          //nb: at the top of a bound, horizontals are added to the bound
          //only when the preceding edge attaches to the horizontal's left vertex
          //unless a Skip edge is encountered when that becomes the top divide
          Horz = Result;
          while (Horz.Prev.Dx == horizontal) Horz = Horz.Prev;
          if (Horz.Prev.iTop.X == Result.Next.iTop.X)
          {
            if (!LeftBoundIsForward) Result = Horz.Prev;
          }
          else if (Horz.Prev.iTop.X > Result.Next.iTop.X) Result = Horz.Prev;
        }
        while (E != Result)
        {
          E.NextInLML = E.Next;
          if (E.Dx == horizontal && E != EStart && E.iBot.X != E.Prev.iTop.X) 
            reverseHorizontal(E);
          E = E.Next;
        }
        if (E.Dx == horizontal && E != EStart && E.iBot.X != E.Prev.iTop.X) 
          reverseHorizontal(E);
        Result = Result.Next; //move to the edge just beyond current bound
      }
      else
      {
        while (Result.iTop.Y == Result.Prev.iBot.Y && Result.Prev.OutIdx != Skip)
          Result = Result.Prev;
        if (Result.Dx == horizontal && Result.Prev.OutIdx != Skip)
        {
          Horz = Result;
          while (Horz.Next.Dx == horizontal) Horz = Horz.Next;
          if (Horz.Next.iTop.X == Result.Prev.iTop.X)
          {
            if (!LeftBoundIsForward) Result = Horz.Next;
          }
          else if (Horz.Next.iTop.X > Result.Prev.iTop.X) Result = Horz.Next;
        }

        while (E != Result)
        {
          E.NextInLML = E.Prev;
          if (E.Dx == horizontal && E != EStart && E.iBot.X != E.Next.iTop.X) 
            reverseHorizontal(E);
          E = E.Prev;
        }
        if (E.Dx == horizontal && E != EStart && E.iBot.X != E.Next.iTop.X) 
          reverseHorizontal(E);
        Result = Result.Prev; //move to the edge just beyond current bound
      }
      return Result;
    }
    //------------------------------------------------------------------------------
    
    public boolean addPath(Path pg, PolyType polyType, boolean Closed) throws ClipperException
    {
      if (!Closed && polyType == PolyType.ptClip)
        throw new ClipperException("addPath: Open paths must be subject.");

      int highI = (int)pg.size() - 1;
      if (Closed) while (highI > 0 && (pg.get(highI) == pg.get(0))) --highI;
      while (highI > 0 && (pg.get(highI) == pg.get(highI - 1))) --highI;
      if ((Closed && highI < 2) || (!Closed && highI < 1)) return false;

      //create a new edge array ...
      List<TEdge> edges = new ArrayList<>(highI+1);
      for (int i = 0; i <= highI; i++) edges.add(new TEdge());
          
      boolean IsFlat = true;

      //1. Basic (first) edge initialization ...
      edges.get(1).iCurr.assign(pg.get(1));
      m_UseFullRange = rangeTest(pg.get(0), m_UseFullRange);
      m_UseFullRange = rangeTest(pg.get(highI), m_UseFullRange);
      initEdge(edges.get(0), edges.get(1), edges.get(highI), pg.get(0));
      initEdge(edges.get(highI), edges.get(0), edges.get(highI - 1), pg.get(highI));
      for (int i = highI - 1; i >= 1; --i)
      {
        m_UseFullRange = rangeTest(pg.get(i), m_UseFullRange);
        initEdge(edges.get(i), edges.get(i + 1), edges.get(i - 1), pg.get(i));
      }
      TEdge eStart = edges.get(0);

      //2. Remove duplicate vertices, and (when closed) collinear edges ...
      TEdge E = eStart, eLoopStop = eStart;
      for (;;)
      {
        //nb: allows matching start and end points when not Closed ...
        if (E.iCurr.equals(E.Next.iCurr) && (Closed || E.Next != eStart))
        {
          if (E == E.Next) break;
          if (E == eStart) eStart = E.Next;
          E = removeEdge(E);
          eLoopStop = E;
          continue;
        }
        if (E.Prev == E.Next) 
          break; //only two vertices
        else if (Closed &&
          slopesEqual(E.Prev.iCurr, E.iCurr, E.Next.iCurr, m_UseFullRange) && 
          (!getPreserveCollinear() ||
          !Pt2IsBetweenPt1AndPt3(E.Prev.iCurr, E.iCurr, E.Next.iCurr))) 
        {
          //Collinear edges are allowed for open paths but in closed paths
          //the default is to merge adjacent collinear edges into a single edge.
          //However, if the PreserveCollinear property is enabled, only overlapping
          //collinear edges (ie spikes) will be removed from closed paths.
          if (E == eStart) eStart = E.Next;
          E = removeEdge(E);
          E = E.Prev;
          eLoopStop = E;
          continue;
        }
        E = E.Next;
        if ((E == eLoopStop) || (!Closed && E.Next == eStart)) break;
      }

      if ((!Closed && (E == E.Next)) || (Closed && (E.Prev == E.Next)))
        return false;

      if (!Closed)
      {
        m_HasOpenPaths = true;
        eStart.Prev.OutIdx = Skip;
      }

      //3. Do second stage of edge initialization ...
      E = eStart;
      do
      {
        initEdge2(E, polyType);
        E = E.Next;
        if (IsFlat && E.iCurr.Y != eStart.iCurr.Y) IsFlat = false;
      }
      while (E != eStart);

      //4. Finally, add edge bounds to LocalMinima list ...

      //Totally flat paths must be handled differently when adding them
      //to LocalMinima list to avoid endless loops etc ...
      if (IsFlat) 
      {
        if (Closed) return false;
        E.Prev.OutIdx = Skip;
        if (E.Prev.iBot.X < E.Prev.iTop.X) reverseHorizontal(E.Prev);
        LocalMinima locMin = new LocalMinima();
        locMin.Next = null;
        locMin.Y = E.iBot.Y;
        locMin.LeftBound = null;
        locMin.RightBound = E;
        locMin.RightBound.Side = EdgeSide.esRight;
        locMin.RightBound.WindDelta = 0;
        while (E.Next.OutIdx != Skip)
        {
          E.NextInLML = E.Next;
          if (E.iBot.X != E.Prev.iTop.X) reverseHorizontal(E);
          E = E.Next;
        }
        insertLocalMinima(locMin);
        m_edges.add(edges);
        return true;
      }

      m_edges.add(edges);
      boolean leftBoundIsForward;
      TEdge EMin = null;

      //workaround to avoid an endless loop in the while loop below when
      //open paths have matching start and end points ...
      if (E.Prev.iBot.equals(E.Prev.iTop)) E = E.Next;

      for (;;)
      {
        E = findNextLocMin(E);
        if (E == EMin) break;
        else if (EMin == null) EMin = E;

        //E and E.Prev now share a local minima (left aligned if horizontal).
        //Compare their slopes to find which starts which bound ...
        LocalMinima locMin = new LocalMinima();
        locMin.Next = null;
        locMin.Y = E.iBot.Y;
        if (E.Dx < E.Prev.Dx) 
        {
          locMin.LeftBound = E.Prev;
          locMin.RightBound = E;
          leftBoundIsForward = false; //Q.nextInLML = Q.prev
        } else
        {
          locMin.LeftBound = E;
          locMin.RightBound = E.Prev;
          leftBoundIsForward = true; //Q.nextInLML = Q.next
        }
        locMin.LeftBound.Side = EdgeSide.esLeft;
        locMin.RightBound.Side = EdgeSide.esRight;

        if (!Closed) locMin.LeftBound.WindDelta = 0;
        else if (locMin.LeftBound.Next == locMin.RightBound)
          locMin.LeftBound.WindDelta = -1;
        else locMin.LeftBound.WindDelta = 1;
        locMin.RightBound.WindDelta = -locMin.LeftBound.WindDelta;

        E = processBound(locMin.LeftBound, leftBoundIsForward);
        if (E.OutIdx == Skip) E = processBound(E, leftBoundIsForward);

        TEdge E2 = processBound(locMin.RightBound, !leftBoundIsForward);
        if (E2.OutIdx == Skip) E2 = processBound(E2, !leftBoundIsForward);

        if (locMin.LeftBound.OutIdx == Skip)
          locMin.LeftBound = null;
        else if (locMin.RightBound.OutIdx == Skip)
          locMin.RightBound = null;
        insertLocalMinima(locMin);
        if (!leftBoundIsForward) E = E2;
      }
      return true;

    }
    //------------------------------------------------------------------------------
    
    public boolean addPaths(Paths ppg, PolyType polyType, boolean closed) throws ClipperException
    {
      boolean result = false;
      for (int i = 0; i < ppg.size(); ++i)
        if (addPath(ppg.get(i), polyType, closed)) result = true;
      return result;
    }
    //------------------------------------------------------------------------------

    boolean Pt2IsBetweenPt1AndPt3(Point2d pt1, Point2d pt2, Point2d pt3)
    {
      if ((pt1.equals(pt3)) || (pt1.equals(pt2)) || (pt3.equals(pt2))) return false;
      else if (pt1.X != pt3.X) return (pt2.X > pt1.X) == (pt2.X < pt3.X);
      else return (pt2.Y > pt1.Y) == (pt2.Y < pt3.Y);
    }
    //------------------------------------------------------------------------------

    TEdge removeEdge(TEdge e)
    {
      //removes e from double_linked_list (but without removing from memory)
      e.Prev.Next = e.Next;
      e.Next.Prev = e.Prev;
      TEdge result = e.Next;
      e.Prev = null; //flag as removed (see ClipperBase.Clear)
      return result;
    }
    //------------------------------------------------------------------------------

    private void setDx(TEdge e)
    {
      e.iDelta.X = (e.iTop.X - e.iBot.X);
      e.iDelta.Y = (e.iTop.Y - e.iBot.Y);
      if (e.iDelta.Y == 0) e.Dx = horizontal;
      else e.Dx = (double)(e.iDelta.X) / (e.iDelta.Y);
    }
    //---------------------------------------------------------------------------

    private void insertLocalMinima(LocalMinima newLm)
    {
      if( m_MinimaList == null )
      {
        m_MinimaList = newLm;
      }
      else if( newLm.Y >= m_MinimaList.Y )
      {
        newLm.Next = m_MinimaList;
        m_MinimaList = newLm;
      } else
      {
        LocalMinima tmpLm = m_MinimaList;
        while( tmpLm.Next != null  && ( newLm.Y < tmpLm.Next.Y ) )
          tmpLm = tmpLm.Next;
        newLm.Next = tmpLm.Next;
        tmpLm.Next = newLm;
      }
    }
    //------------------------------------------------------------------------------

    protected void popLocalMinima()
    {
        if (m_CurrentLM == null) return;
        m_CurrentLM = m_CurrentLM.Next;
    }
    //------------------------------------------------------------------------------

    private void reverseHorizontal(TEdge e)
    {
      //swap horizontal edges' top and bottom x's so they follow the natural
      //progression of the bounds - ie so their xbots will align with the
      //adjoining lower edge. [Helpful in the ProcessHorizontal() method.]
      long aux;

      aux = e.iTop.X;
      e.iTop.X = e.iBot.X;
      e.iBot.X = aux;      
    }
    //------------------------------------------------------------------------------
    
    protected void reset()
    {
      m_CurrentLM = m_MinimaList;
      if (m_CurrentLM == null) return; //ie nothing to process

      //reset all edges ...
      LocalMinima lm = m_MinimaList;
      while (lm != null)
      {
        TEdge e = lm.LeftBound;
        if (e != null)
        {
          e.iCurr.assign(e.iBot);
          e.Side = EdgeSide.esLeft;
          e.OutIdx = Unassigned;
        }
        e = lm.RightBound;
        if (e != null)
        {
          e.iCurr.assign(e.iBot);
          e.Side = EdgeSide.esRight;
          e.OutIdx = Unassigned;
        }
        lm = lm.Next;
      }
    }
    //------------------------------------------------------------------------------
}



public class Clipper extends ClipperBase {

    //InitOptions that can be passed to the constructor ...
    public final static int ioReverseSolution = 1;
    public final static int ioStrictlySimple = 2;
    public final static int ioPreserveCollinear = 4;

    private final List<OutRec> m_PolyOuts;
    private ClipType m_ClipType;
    private Scanbeam m_Scanbeam;
    private TEdge m_ActiveEdges;
    private TEdge m_SortedEdges;
    private final List<IntersectNode> m_IntersectList;
    Comparator<IntersectNode> m_IntersectNodeComparer;
    private boolean m_ExecuteLocked;
    private PolyFillType m_ClipFillType;
    private PolyFillType m_SubjFillType;
    private final List<Join> m_Joins;
    private final List<Join> m_GhostJoins;
    private boolean m_UsingPolyTree;

    public Clipper(int InitOptions) {
        super();
        m_Scanbeam = null;
        m_ActiveEdges = null;
        m_SortedEdges = null;
        m_IntersectList = new ArrayList<>();
        m_IntersectNodeComparer = new MyIntersectNodeSort();
        m_ExecuteLocked = false;
        m_UsingPolyTree = false;
        m_PolyOuts = new ArrayList<>();
        m_Joins = new ArrayList<>();
        m_GhostJoins = new ArrayList<>();
        setReverseSolution((ioReverseSolution & InitOptions) != 0);
        setStrictlySimple((ioStrictlySimple & InitOptions) != 0);
        setPreserveCollinear((ioPreserveCollinear & InitOptions) != 0);
    }

    void DisposeScanbeamList() {
        while (m_Scanbeam != null) {
            Scanbeam sb2 = m_Scanbeam.Next;
            m_Scanbeam = null;
            m_Scanbeam = sb2;
        }
    }
    //------------------------------------------------------------------------------

    @Override
    protected void reset() {
        super.reset();
        m_Scanbeam = null;
        m_ActiveEdges = null;
        m_SortedEdges = null;
        LocalMinima lm = m_MinimaList;
        while (lm != null) {
            insertScanbeam(lm.Y);
            lm = lm.Next;
        }
    }
      //------------------------------------------------------------------------------

    private boolean m_reverseSolution = false;
    
    public final boolean getReverseSolution() {
        return m_reverseSolution;
    }

    public final void setReverseSolution(boolean v) {
        m_reverseSolution = v;
    }
    
      //------------------------------------------------------------------------------      
    private boolean m_strictlySimple = false;
    
    public final boolean getStrictlySimple() {
        return m_strictlySimple;
    }

    public final void setStrictlySimple(boolean v) {
        m_strictlySimple = v;
    }
    
    

      //------------------------------------------------------------------------------
    private void insertScanbeam(long Y) {
        if (m_Scanbeam == null) {
            m_Scanbeam = new Scanbeam();
            m_Scanbeam.Next = null;
            m_Scanbeam.Y = Y;
        } else if (Y > m_Scanbeam.Y) {
            Scanbeam newSb = new Scanbeam();
            newSb.Y = Y;
            newSb.Next = m_Scanbeam;
            m_Scanbeam = newSb;
        } else {
            Scanbeam sb2 = m_Scanbeam;
            while (sb2.Next != null && (Y <= sb2.Next.Y)) {
                sb2 = sb2.Next;
            }
            if (Y == sb2.Y) {
                return; //ie ignores duplicates
            }
            Scanbeam newSb = new Scanbeam();
            newSb.Y = Y;
            newSb.Next = sb2.Next;
            sb2.Next = newSb;
        }
    }
      //------------------------------------------------------------------------------

    public boolean execute(ClipType clipType, Paths solution,
            PolyFillType subjFillType, PolyFillType clipFillType) throws ClipperException {
        if (m_ExecuteLocked) {
            return false;
        }
        if (m_HasOpenPaths) {
            throw new ClipperException("Error: PolyTree struct is need for open path clipping.");
        }

        m_ExecuteLocked = true;
        solution.clear();
        m_SubjFillType = subjFillType;
        m_ClipFillType = clipFillType;
        m_ClipType = clipType;
        m_UsingPolyTree = false;
        boolean succeeded;
        try {
            succeeded = executeInternal();
            //build the return polygons ...
            if (succeeded) {
                buildResult(solution);
            }
        } finally {
            disposeAllPolyPts();
            m_ExecuteLocked = false;
        }
        return succeeded;
    }

      //------------------------------------------------------------------------------
    public boolean execute(ClipType clipType, PolyTree polytree,
            PolyFillType subjFillType, PolyFillType clipFillType) throws ClipperException {
        if (m_ExecuteLocked) {
            return false;
        }
        m_ExecuteLocked = true;
        m_SubjFillType = subjFillType;
        m_ClipFillType = clipFillType;
        m_ClipType = clipType;
        m_UsingPolyTree = true;
        boolean succeeded;
        try {
            succeeded = executeInternal();
            //build the return polygons ...
            if (succeeded) {
                buildResult2(polytree);
            }
        } finally {
            disposeAllPolyPts();
            m_ExecuteLocked = false;
        }
        return succeeded;
    }
    //------------------------------------------------------------------------------

    public boolean execute(ClipType clipType, Paths solution) throws ClipperException {
        return execute(clipType, solution,
                PolyFillType.pftEvenOdd, PolyFillType.pftEvenOdd);
    }
    //------------------------------------------------------------------------------

    public boolean execute(ClipType clipType, PolyTree polytree) throws ClipperException {
        return execute(clipType, polytree,
                PolyFillType.pftEvenOdd, PolyFillType.pftEvenOdd);
    }

      //------------------------------------------------------------------------------

      void fixHoleLinkage(OutRec outRec)
      {
        //skip if an outermost polygon or
        //already already points to the correct FirstLeft ...
        if (outRec.FirstLeft == null ||
              (outRec.IsHole != outRec.FirstLeft.IsHole &&
              outRec.FirstLeft.Pts != null)) return;

        OutRec orfl = outRec.FirstLeft;
        while (orfl != null && ((orfl.IsHole == outRec.IsHole) || orfl.Pts == null))
          orfl = orfl.FirstLeft;
        outRec.FirstLeft = orfl;
      }
      //------------------------------------------------------------------------------
      
      private boolean executeInternal() throws ClipperException
      {
        try
        {
          reset();
          if (m_CurrentLM == null) return false;

          long botY = popScanbeam();
          do
          {
            insertLocalMinimaIntoAEL(botY);
            m_GhostJoins.clear();
            processHorizontals(false);
            if (m_Scanbeam == null) break;
            long topY = popScanbeam();
            if (!processIntersections(topY)) return false;
            processEdgesAtTopOfScanbeam(topY);
            botY = topY;
          } while (m_Scanbeam != null || m_CurrentLM != null);

            for (OutRec outRec : m_PolyOuts) {
                if (outRec.Pts == null || outRec.IsOpen) continue;
                if ((outRec.IsHole ^ getReverseSolution()) == (area(outRec) > 0))
                    reversePolyPtLinks(outRec.Pts);
            }

          joinCommonEdges();

            for (OutRec outRec : m_PolyOuts) {
                if (outRec.Pts != null && !outRec.IsOpen)
                    fixupOutPolygon(outRec);
            }

          if (getStrictlySimple()) doSimplePolygons();
          return true;
        }
        //catch { return false; }
        finally 
        {
          m_Joins.clear();
          m_GhostJoins.clear();
        }
      }
      //------------------------------------------------------------------------------
      
      //------------------------------------------------------------------------------

      private long popScanbeam()
      {
        long Y = m_Scanbeam.Y;
        m_Scanbeam = m_Scanbeam.Next;
        return Y;
      }
      //------------------------------------------------------------------------------

      private void disposeAllPolyPts(){
        for (int i = 0; i < m_PolyOuts.size(); ++i) disposeOutRec(i);
        m_PolyOuts.clear();
      }
      //------------------------------------------------------------------------------

      void disposeOutRec(int index)
      {
        OutRec outRec = m_PolyOuts.get(index);
        outRec.Pts = null;
        m_PolyOuts.set(index, null);
      }
      //------------------------------------------------------------------------------

      private void addJoin(OutPt Op1, OutPt Op2, final Point2d OffPt)
      {
        Join j = new Join();
        j.OutPt1 = Op1;
        j.OutPt2 = Op2;
        j.iOffPt.assign(OffPt);
        m_Joins.add(j);
      }
      //------------------------------------------------------------------------------

      private void addGhostJoin(OutPt Op, final Point2d OffPt)
      {
        Join j = new Join();
        j.OutPt1 = Op;
        j.iOffPt.assign(OffPt);
        m_GhostJoins.add(j);
      }
      //------------------------------------------------------------------------------
      
      private void insertLocalMinimaIntoAEL(long botY)
      {
        while(  m_CurrentLM != null  && ( m_CurrentLM.Y == botY ) )
        {
          TEdge lb = m_CurrentLM.LeftBound;
          TEdge rb = m_CurrentLM.RightBound;
          popLocalMinima();

          OutPt Op1 = null;
          if (lb == null)
          {
            insertEdgeIntoAEL(rb, null);
            setWindingCount(rb);
            if (isContributing(rb))
              Op1 = addOutPt(rb, rb.iBot);
          }
          else if (rb == null)
          {
            insertEdgeIntoAEL(lb, null);
            setWindingCount(lb);
            if (isContributing(lb))
              Op1 = addOutPt(lb, lb.iBot);
            insertScanbeam(lb.iTop.Y);
          }
          else
          {
            insertEdgeIntoAEL(lb, null);
            insertEdgeIntoAEL(rb, lb);
            setWindingCount(lb);
            rb.WindCnt = lb.WindCnt;
            rb.WindCnt2 = lb.WindCnt2;
            if (isContributing(lb))
              Op1 = addLocalMinPoly(lb, rb, lb.iBot);
            insertScanbeam(lb.iTop.Y);
          }

          if (rb != null)
          {
            if (isHorizontal(rb))
              addEdgeToSEL(rb);
            else
              insertScanbeam(rb.iTop.Y);
          }

          if (lb == null || rb == null) continue;

          //if output polygons share an Edge with a horizontal rb, they'll need joining later ...
          if (Op1 != null && isHorizontal(rb) && 
            m_GhostJoins.size() > 0 && rb.WindDelta != 0)
          {
              for (Join j : m_GhostJoins) {
                  if (horzSegmentsOverlap(j.OutPt1.iPt.X, j.iOffPt.X, rb.iBot.X, rb.iTop.X))
                      addJoin(j.OutPt1, Op1, j.iOffPt);
              }
          }

          if (lb.OutIdx >= 0 && lb.PrevInAEL != null &&
            lb.PrevInAEL.iCurr.X == lb.iBot.X &&
            lb.PrevInAEL.OutIdx >= 0 &&
            slopesEqual(lb.PrevInAEL, lb, m_UseFullRange) &&
            lb.WindDelta != 0 && lb.PrevInAEL.WindDelta != 0)
          {
            OutPt Op2 = addOutPt(lb.PrevInAEL, lb.iBot);
            addJoin(Op1, Op2, lb.iTop);
          }

          if( lb.NextInAEL != rb )
          {

            if (rb.OutIdx >= 0 && rb.PrevInAEL.OutIdx >= 0 &&
              slopesEqual(rb.PrevInAEL, rb, m_UseFullRange) &&
              rb.WindDelta != 0 && rb.PrevInAEL.WindDelta != 0)
            {
              OutPt Op2 = addOutPt(rb.PrevInAEL, rb.iBot);
              addJoin(Op1, Op2, rb.iTop);
            }

            TEdge e = lb.NextInAEL;
            if (e != null)
              while (e != rb)
              {
                //nb: For calculating winding counts etc, IntersectEdges() assumes
                //that param1 will be to the right of param2 ABOVE the intersection ...
                intersectEdges(rb, e, lb.iCurr); //order important here
                e = e.NextInAEL;
              }
          }
        }
      }
     
      //------------------------------------------------------------------------------

      private void insertEdgeIntoAEL(TEdge edge, TEdge startEdge)
      {
        if (m_ActiveEdges == null)
        {
          edge.PrevInAEL = null;
          edge.NextInAEL = null;
          m_ActiveEdges = edge;
        }
        else if (startEdge == null && E2InsertsBeforeE1(m_ActiveEdges, edge))
        {
          edge.PrevInAEL = null;
          edge.NextInAEL = m_ActiveEdges;
          m_ActiveEdges.PrevInAEL = edge;
          m_ActiveEdges = edge;
        }
        else
        {
          if (startEdge == null) startEdge = m_ActiveEdges;
          while (startEdge.NextInAEL != null &&
            !E2InsertsBeforeE1(startEdge.NextInAEL, edge))
            startEdge = startEdge.NextInAEL;
          edge.NextInAEL = startEdge.NextInAEL;
          if (startEdge.NextInAEL != null) startEdge.NextInAEL.PrevInAEL = edge;
          edge.PrevInAEL = startEdge;
          startEdge.NextInAEL = edge;
        }
      }
      //----------------------------------------------------------------------

      private boolean E2InsertsBeforeE1(TEdge e1, TEdge e2)
      {
          if (e2.iCurr.X == e1.iCurr.X)
          {
              if (e2.iTop.Y > e1.iTop.Y)
                  return e2.iTop.X < topX(e1, e2.iTop.Y);
              else return e1.iTop.X > topX(e2, e1.iTop.Y);
          }
          else return e2.iCurr.X < e1.iCurr.X;
      }
      //------------------------------------------------------------------------------

      private boolean isEvenOddFillType(TEdge edge) 
      {
        if (edge.PolyTyp == PolyType.ptSubject)
            return m_SubjFillType == PolyFillType.pftEvenOdd; 
        else
            return m_ClipFillType == PolyFillType.pftEvenOdd;
      }
      //------------------------------------------------------------------------------

      private boolean isEvenOddAltFillType(TEdge edge) 
      {
        if (edge.PolyTyp == PolyType.ptSubject)
            return m_ClipFillType == PolyFillType.pftEvenOdd; 
        else
            return m_SubjFillType == PolyFillType.pftEvenOdd;
      }
      //------------------------------------------------------------------------------

      private boolean isContributing(TEdge edge)
      {
          PolyFillType pft, pft2;
          if (edge.PolyTyp == PolyType.ptSubject)
          {
              pft = m_SubjFillType;
              pft2 = m_ClipFillType;
          }
          else
          {
              pft = m_ClipFillType;
              pft2 = m_SubjFillType;
          }

          switch (pft)
          {
              case pftEvenOdd:
                  //return false if a subj line has been flagged as inside a subj polygon
                  if (edge.WindDelta == 0 && edge.WindCnt != 1) return false;
                  break;
              case pftNonZero:
                  if (Math.abs(edge.WindCnt) != 1) return false;
                  break;
              case pftPositive:
                  if (edge.WindCnt != 1) return false;
                  break;
              default: //PolyFillType.pftNegative
                  if (edge.WindCnt != -1) return false; 
                  break;
          }

          switch (m_ClipType)
          {
            case ctIntersection:
                switch (pft2)
                {
                    case pftEvenOdd:
                    case pftNonZero:
                        return (edge.WindCnt2 != 0);
                    case pftPositive:
                        return (edge.WindCnt2 > 0);
                    default:
                        return (edge.WindCnt2 < 0);
                }
            case ctUnion:
                switch (pft2)
                {
                    case pftEvenOdd:
                    case pftNonZero:
                        return (edge.WindCnt2 == 0);
                    case pftPositive:
                        return (edge.WindCnt2 <= 0);
                    default:
                        return (edge.WindCnt2 >= 0);
                }
            case ctDifference:
                if (edge.PolyTyp == PolyType.ptSubject)
                    switch (pft2)
                    {
                        case pftEvenOdd:
                        case pftNonZero:
                            return (edge.WindCnt2 == 0);
                        case pftPositive:
                            return (edge.WindCnt2 <= 0);
                        default:
                            return (edge.WindCnt2 >= 0);
                    }
                else
                    switch (pft2)
                    {
                        case pftEvenOdd:
                        case pftNonZero:
                            return (edge.WindCnt2 != 0);
                        case pftPositive:
                            return (edge.WindCnt2 > 0);
                        default:
                            return (edge.WindCnt2 < 0);
                    }
            case ctXor:
                if (edge.WindDelta == 0) //XOr always contributing unless open
                  switch (pft2)
                  {
                    case pftEvenOdd:
                    case pftNonZero:
                      return (edge.WindCnt2 == 0);
                    case pftPositive:
                      return (edge.WindCnt2 <= 0);
                    default:
                      return (edge.WindCnt2 >= 0);
                  }
                else
                  return true;
          }
          return true;
      }
      
      //------------------------------------------------------------------------------

      private void setWindingCount(TEdge edge)
      {
        TEdge e = edge.PrevInAEL;
        //find the edge of the same polytype that immediately preceeds 'edge' in AEL
        while (e != null && ((e.PolyTyp != edge.PolyTyp) || (e.WindDelta == 0))) e = e.PrevInAEL;
        if (e == null)
        {
          edge.WindCnt = (edge.WindDelta == 0 ? 1 : edge.WindDelta);
          edge.WindCnt2 = 0;
          e = m_ActiveEdges; //ie get ready to calc WindCnt2
        }
        else if (edge.WindDelta == 0 && m_ClipType != ClipType.ctUnion)
        {
          edge.WindCnt = 1;
          edge.WindCnt2 = e.WindCnt2;
          e = e.NextInAEL; //ie get ready to calc WindCnt2
        }
        else if (isEvenOddFillType(edge))
        {
          //EvenOdd filling ...
          if (edge.WindDelta == 0)
          {
            //are we inside a subj polygon ...
            boolean Inside = true;
            TEdge e2 = e.PrevInAEL;
            while (e2 != null)
            {
              if (e2.PolyTyp == e.PolyTyp && e2.WindDelta != 0)
                Inside = !Inside;
              e2 = e2.PrevInAEL;
            }
            edge.WindCnt = (Inside ? 0 : 1);
          }
          else
          {
            edge.WindCnt = edge.WindDelta;
          }
          edge.WindCnt2 = e.WindCnt2;
          e = e.NextInAEL; //ie get ready to calc WindCnt2
        }
        else
        {
          //nonZero, Positive or Negative filling ...
          if (e.WindCnt * e.WindDelta < 0)
          {
            //prev edge is 'decreasing' WindCount (WC) toward zero
            //so we're outside the previous polygon ...
            if (Math.abs(e.WindCnt) > 1)
            {
              //outside prev poly but still inside another.
              //when reversing direction of prev poly use the same WC 
              if (e.WindDelta * edge.WindDelta < 0) edge.WindCnt = e.WindCnt;
              //otherwise continue to 'decrease' WC ...
              else edge.WindCnt = e.WindCnt + edge.WindDelta;
            }
            else
              //now outside all polys of same polytype so set own WC ...
              edge.WindCnt = (edge.WindDelta == 0 ? 1 : edge.WindDelta);
          }
          else
          {
            //prev edge is 'increasing' WindCount (WC) away from zero
            //so we're inside the previous polygon ...
            if (edge.WindDelta == 0)
              edge.WindCnt = (e.WindCnt < 0 ? e.WindCnt - 1 : e.WindCnt + 1);
            //if wind direction is reversing prev then use same WC
            else if (e.WindDelta * edge.WindDelta < 0)
              edge.WindCnt = e.WindCnt;
            //otherwise add to WC ...
            else edge.WindCnt = e.WindCnt + edge.WindDelta;
          }
          edge.WindCnt2 = e.WindCnt2;
          e = e.NextInAEL; //ie get ready to calc WindCnt2
        }

        //update WindCnt2 ...
        if (isEvenOddAltFillType(edge))
        {
          //EvenOdd filling ...
          while (e != edge)
          {
            if (e.WindDelta != 0)
              edge.WindCnt2 = (edge.WindCnt2 == 0 ? 1 : 0);
            e = e.NextInAEL;
          }
        }
        else
        {
          //nonZero, Positive or Negative filling ...
          while (e != edge)
          {
            edge.WindCnt2 += e.WindDelta;
            e = e.NextInAEL;
          }
        }
      }
      //------------------------------------------------------------------------------

      private void addEdgeToSEL(TEdge edge)
      {
          //SEL pointers in PEdge are reused to build a list of horizontal edges.
          //However, we don't need to worry about order with horizontal edge processing.
          if (m_SortedEdges == null)
          {
              m_SortedEdges = edge;
              edge.PrevInSEL = null;
              edge.NextInSEL = null;
          }
          else
          {
              edge.NextInSEL = m_SortedEdges;
              edge.PrevInSEL = null;
              m_SortedEdges.PrevInSEL = edge;
              m_SortedEdges = edge;
          }
      }
      //------------------------------------------------------------------------------

      private void copyAELToSEL()
      {
          TEdge e = m_ActiveEdges;
          m_SortedEdges = e;
          while (e != null)
          {
              e.PrevInSEL = e.PrevInAEL;
              e.NextInSEL = e.NextInAEL;
              e = e.NextInAEL;
          }
      }
      //------------------------------------------------------------------------------
      
      private void swapPositionsInAEL(TEdge edge1, TEdge edge2)
      {
        //check that one or other edge hasn't already been removed from AEL ...
          if (edge1.NextInAEL == edge1.PrevInAEL ||
            edge2.NextInAEL == edge2.PrevInAEL) return;
        
          if (edge1.NextInAEL == edge2)
          {
              TEdge next = edge2.NextInAEL;
              if (next != null)
                  next.PrevInAEL = edge1;
              TEdge prev = edge1.PrevInAEL;
              if (prev != null)
                  prev.NextInAEL = edge2;
              edge2.PrevInAEL = prev;
              edge2.NextInAEL = edge1;
              edge1.PrevInAEL = edge2;
              edge1.NextInAEL = next;
          }
          else if (edge2.NextInAEL == edge1)
          {
              TEdge next = edge1.NextInAEL;
              if (next != null)
                  next.PrevInAEL = edge2;
              TEdge prev = edge2.PrevInAEL;
              if (prev != null)
                  prev.NextInAEL = edge1;
              edge1.PrevInAEL = prev;
              edge1.NextInAEL = edge2;
              edge2.PrevInAEL = edge1;
              edge2.NextInAEL = next;
          }
          else
          {
              TEdge next = edge1.NextInAEL;
              TEdge prev = edge1.PrevInAEL;
              edge1.NextInAEL = edge2.NextInAEL;
              if (edge1.NextInAEL != null)
                  edge1.NextInAEL.PrevInAEL = edge1;
              edge1.PrevInAEL = edge2.PrevInAEL;
              if (edge1.PrevInAEL != null)
                  edge1.PrevInAEL.NextInAEL = edge1;
              edge2.NextInAEL = next;
              if (edge2.NextInAEL != null)
                  edge2.NextInAEL.PrevInAEL = edge2;
              edge2.PrevInAEL = prev;
              if (edge2.PrevInAEL != null)
                  edge2.PrevInAEL.NextInAEL = edge2;
          }

          if (edge1.PrevInAEL == null)
              m_ActiveEdges = edge1;
          else if (edge2.PrevInAEL == null)
              m_ActiveEdges = edge2;
      }
      //------------------------------------------------------------------------------

      private void swapPositionsInSEL(TEdge edge1, TEdge edge2)
      {
          if (edge1.NextInSEL == null && edge1.PrevInSEL == null)
              return;
          if (edge2.NextInSEL == null && edge2.PrevInSEL == null)
              return;

          if (edge1.NextInSEL == edge2)
          {
              TEdge next = edge2.NextInSEL;
              if (next != null)
                  next.PrevInSEL = edge1;
              TEdge prev = edge1.PrevInSEL;
              if (prev != null)
                  prev.NextInSEL = edge2;
              edge2.PrevInSEL = prev;
              edge2.NextInSEL = edge1;
              edge1.PrevInSEL = edge2;
              edge1.NextInSEL = next;
          }
          else if (edge2.NextInSEL == edge1)
          {
              TEdge next = edge1.NextInSEL;
              if (next != null)
                  next.PrevInSEL = edge2;
              TEdge prev = edge2.PrevInSEL;
              if (prev != null)
                  prev.NextInSEL = edge1;
              edge1.PrevInSEL = prev;
              edge1.NextInSEL = edge2;
              edge2.PrevInSEL = edge1;
              edge2.NextInSEL = next;
          }
          else
          {
              TEdge next = edge1.NextInSEL;
              TEdge prev = edge1.PrevInSEL;
              edge1.NextInSEL = edge2.NextInSEL;
              if (edge1.NextInSEL != null)
                  edge1.NextInSEL.PrevInSEL = edge1;
              edge1.PrevInSEL = edge2.PrevInSEL;
              if (edge1.PrevInSEL != null)
                  edge1.PrevInSEL.NextInSEL = edge1;
              edge2.NextInSEL = next;
              if (edge2.NextInSEL != null)
                  edge2.NextInSEL.PrevInSEL = edge2;
              edge2.PrevInSEL = prev;
              if (edge2.PrevInSEL != null)
                  edge2.PrevInSEL.NextInSEL = edge2;
          }

          if (edge1.PrevInSEL == null)
              m_SortedEdges = edge1;
          else if (edge2.PrevInSEL == null)
              m_SortedEdges = edge2;
      }
      //------------------------------------------------------------------------------


      private void addLocalMaxPoly(TEdge e1, TEdge e2, final Point2d _pt)
      {
          final Point2d pt = _pt.clone();
          
          addOutPt(e1, pt);
          if (e2.WindDelta == 0) addOutPt(e2, pt);
          if (e1.OutIdx == e2.OutIdx)
          {
              e1.OutIdx = Unassigned;
              e2.OutIdx = Unassigned;
          }
          else if (e1.OutIdx < e2.OutIdx) 
              appendPolygon(e1, e2);
          else 
              appendPolygon(e2, e1);
      }
      //------------------------------------------------------------------------------

      private OutPt addLocalMinPoly(TEdge e1, TEdge e2, final Point2d _pt)
      {
        final Point2d pt = _pt;
        
        OutPt result;
        TEdge e, prevE;
        if (isHorizontal(e2) || (e1.Dx > e2.Dx))
        {
          result = addOutPt(e1, pt);
          e2.OutIdx = e1.OutIdx;
          e1.Side = EdgeSide.esLeft;
          e2.Side = EdgeSide.esRight;
          e = e1;
          if (e.PrevInAEL == e2)
            prevE = e2.PrevInAEL; 
          else
            prevE = e.PrevInAEL;
        }
        else
        {
          result = addOutPt(e2, pt);
          e1.OutIdx = e2.OutIdx;
          e1.Side = EdgeSide.esRight;
          e2.Side = EdgeSide.esLeft;
          e = e2;
          if (e.PrevInAEL == e1)
              prevE = e1.PrevInAEL;
          else
              prevE = e.PrevInAEL;
        }

        if (prevE != null && prevE.OutIdx >= 0 &&
            (topX(prevE, pt.Y) == topX(e, pt.Y)) &&
            slopesEqual(e, prevE, m_UseFullRange) &&
            (e.WindDelta != 0) && (prevE.WindDelta != 0))
        {
          OutPt outPt = addOutPt(prevE, pt);
          addJoin(result, outPt, e.iTop);
        }
        return result;
      }
      //------------------------------------------------------------------------------
      
      private OutRec createOutRec()
      {
        OutRec result = new OutRec();
        result.Idx = Unassigned;
        result.IsHole = false;
        result.IsOpen = false;
        result.FirstLeft = null;
        result.Pts = null;
        result.BottomPt = null;
        result.PolyNode = null;
        m_PolyOuts.add(result);
        result.Idx = m_PolyOuts.size() - 1;
        return result;
      }
      //------------------------------------------------------------------------------

      private OutPt addOutPt(TEdge e, Point2d _pt)
      {
        final Point2d pt = _pt;
          
        boolean ToFront = (e.Side == EdgeSide.esLeft);
        if(  e.OutIdx < 0 )
        {
          OutRec outRec = createOutRec();
          outRec.IsOpen = (e.WindDelta == 0);
          OutPt newOp = new OutPt();
          outRec.Pts = newOp;
          newOp.Idx = outRec.Idx;
          newOp.iPt.assign(pt);
          newOp.Next = newOp;
          newOp.Prev = newOp;
          if (!outRec.IsOpen)
            setHoleState(e, outRec);
          e.OutIdx = outRec.Idx; //nb: do this after SetZ !
          return newOp;
        } else
        {
          OutRec outRec = m_PolyOuts.get(e.OutIdx);
          //OutRec.Pts is the 'Left-most' point & OutRec.Pts.Prev is the 'Right-most'
          OutPt op = outRec.Pts;
          if (ToFront && pt.equals(op.iPt)) return op;
          else if (!ToFront && pt.equals(op.Prev.iPt)) return op.Prev;

          OutPt newOp = new OutPt();
          newOp.Idx = outRec.Idx;
          newOp.iPt.assign(pt);
          newOp.Next = op;
          newOp.Prev = op.Prev;
          newOp.Prev.Next = newOp;
          op.Prev = newOp;
          if (ToFront) outRec.Pts = newOp;
          return newOp;
        }
      }
      //------------------------------------------------------------------------------
      
      private boolean horzSegmentsOverlap(long seg1a, long seg1b, long seg2a, long seg2b)
      {
        if (seg1a > seg1b)
        {
            long aux = seg1a;
            seg1a = seg1b;
            seg1b = aux;
        }
        if (seg2a > seg2b)
        {
            long aux = seg2a;
            seg2a = seg2b;
            seg2b = aux;
        }
        return (seg1a < seg2b) && (seg2a < seg1b);
      }
      //------------------------------------------------------------------------------
  
      private void setHoleState(TEdge e, OutRec outRec)
      {
          boolean isHole = false;
          TEdge e2 = e.PrevInAEL;
          while (e2 != null)
          {
              if (e2.OutIdx >= 0 && e2.WindDelta != 0) 
              {
                  isHole = !isHole;
                  if (outRec.FirstLeft == null)
                      outRec.FirstLeft = m_PolyOuts.get(e2.OutIdx);
              }
              e2 = e2.PrevInAEL;
          }
          if (isHole) 
            outRec.IsHole = true;
      }
      //------------------------------------------------------------------------------

      private double getDx(Point2d pt1, Point2d pt2)
      {
          if (pt1.Y == pt2.Y) return horizontal;
          else return (double)(pt2.X - pt1.X) / (pt2.Y - pt1.Y);
      }
      //---------------------------------------------------------------------------

      private boolean firstIsBottomPt(OutPt btmPt1, OutPt btmPt2)
      {
        OutPt p = btmPt1.Prev;
        while ((p.iPt.equals(btmPt1.iPt)) && (p != btmPt1)) p = p.Prev;
        double dx1p = Math.abs(getDx(btmPt1.iPt, p.iPt));
        p = btmPt1.Next;
        while ((p.iPt.equals(btmPt1.iPt)) && (p != btmPt1)) p = p.Next;
        double dx1n = Math.abs(getDx(btmPt1.iPt, p.iPt));

        p = btmPt2.Prev;
        while ((p.iPt.equals(btmPt2.iPt)) && (p != btmPt2)) p = p.Prev;
        double dx2p = Math.abs(getDx(btmPt2.iPt, p.iPt));
        p = btmPt2.Next;
        while ((p.iPt.equals(btmPt2.iPt)) && (p != btmPt2)) p = p.Next;
        double dx2n = Math.abs(getDx(btmPt2.iPt, p.iPt));
        return (dx1p >= dx2p && dx1p >= dx2n) || (dx1n >= dx2p && dx1n >= dx2n);
      }
      //------------------------------------------------------------------------------

      private OutPt getBottomPt(OutPt pp)
      {
        OutPt dups = null;
        OutPt p = pp.Next;
        while (p != pp)
        {
          if (p.iPt.Y > pp.iPt.Y)
          {
            pp = p;
            dups = null;
          }
          else if (p.iPt.Y == pp.iPt.Y && p.iPt.X <= pp.iPt.X)
          {
            if (p.iPt.X < pp.iPt.X)
            {
                dups = null;
                pp = p;
            } else
            {
              if (p.Next != pp && p.Prev != pp) dups = p;
            }
          }
          p = p.Next;
        }
        if (dups != null)
        {
          //there appears to be at least 2 vertices at bottomPt so ...
          while (dups != p)
          {
            if (!firstIsBottomPt(p, dups)) pp = dups;
            dups = dups.Next;
            while (dups.iPt.not_equals(pp.iPt)) dups = dups.Next;
          }
        }
        return pp;
      }
      //------------------------------------------------------------------------------

      private OutRec getLowermostRec(OutRec outRec1, OutRec outRec2)
      {
          //work out which polygon fragment has the correct hole state ...
          if (outRec1.BottomPt == null) 
              outRec1.BottomPt = getBottomPt(outRec1.Pts);
          if (outRec2.BottomPt == null) 
              outRec2.BottomPt = getBottomPt(outRec2.Pts);
          OutPt bPt1 = outRec1.BottomPt;
          OutPt bPt2 = outRec2.BottomPt;
          if (bPt1.iPt.Y > bPt2.iPt.Y) return outRec1;
          else if (bPt1.iPt.Y < bPt2.iPt.Y) return outRec2;
          else if (bPt1.iPt.X < bPt2.iPt.X) return outRec1;
          else if (bPt1.iPt.X > bPt2.iPt.X) return outRec2;
          else if (bPt1.Next == bPt1) return outRec2;
          else if (bPt2.Next == bPt2) return outRec1;
          else if (firstIsBottomPt(bPt1, bPt2)) return outRec1;
          else return outRec2;
      }
      //------------------------------------------------------------------------------

      boolean param1RightOfParam2(OutRec outRec1, OutRec outRec2)
      {
          do
          {
              outRec1 = outRec1.FirstLeft;
              if (outRec1 == outRec2) return true;
          } while (outRec1 != null);
          return false;
      }
      //------------------------------------------------------------------------------

      private OutRec getOutRec(int idx)
      {
        OutRec outrec = m_PolyOuts.get(idx);
        while (outrec != m_PolyOuts.get(outrec.Idx))
          outrec = m_PolyOuts.get(outrec.Idx);
        return outrec;
      }
      //------------------------------------------------------------------------------
      
      private void appendPolygon(TEdge e1, TEdge e2)
      {
        //get the start and ends of both output polygons ...
        OutRec outRec1 = m_PolyOuts.get(e1.OutIdx);
        OutRec outRec2 = m_PolyOuts.get(e2.OutIdx);

        OutRec holeStateRec;
        if (param1RightOfParam2(outRec1, outRec2)) 
            holeStateRec = outRec2;
        else if (param1RightOfParam2(outRec2, outRec1))
            holeStateRec = outRec1;
        else
            holeStateRec = getLowermostRec(outRec1, outRec2);

        OutPt p1_lft = outRec1.Pts;
        OutPt p1_rt = p1_lft.Prev;
        OutPt p2_lft = outRec2.Pts;
        OutPt p2_rt = p2_lft.Prev;

        EdgeSide side;
        //join e2 poly onto e1 poly and delete pointers to e2 ...
        if(  e1.Side == EdgeSide.esLeft )
        {
          if (e2.Side == EdgeSide.esLeft)
          {
            //z y x a b c
            reversePolyPtLinks(p2_lft);
            p2_lft.Next = p1_lft;
            p1_lft.Prev = p2_lft;
            p1_rt.Next = p2_rt;
            p2_rt.Prev = p1_rt;
            outRec1.Pts = p2_rt;
          } else
          {
            //x y z a b c
            p2_rt.Next = p1_lft;
            p1_lft.Prev = p2_rt;
            p2_lft.Prev = p1_rt;
            p1_rt.Next = p2_lft;
            outRec1.Pts = p2_lft;
          }
          side = EdgeSide.esLeft;
        } else
        {
          if (e2.Side == EdgeSide.esRight)
          {
            //a b c z y x
            reversePolyPtLinks( p2_lft );
            p1_rt.Next = p2_rt;
            p2_rt.Prev = p1_rt;
            p2_lft.Next = p1_lft;
            p1_lft.Prev = p2_lft;
          } else
          {
            //a b c x y z
            p1_rt.Next = p2_lft;
            p2_lft.Prev = p1_rt;
            p1_lft.Prev = p2_rt;
            p2_rt.Next = p1_lft;
          }
          side = EdgeSide.esRight;
        }

        outRec1.BottomPt = null; 
        if (holeStateRec == outRec2)
        {
            if (outRec2.FirstLeft != outRec1)
                outRec1.FirstLeft = outRec2.FirstLeft;
            outRec1.IsHole = outRec2.IsHole;
        }
        outRec2.Pts = null;
        outRec2.BottomPt = null;

        outRec2.FirstLeft = outRec1;

        int OKIdx = e1.OutIdx;
        int ObsoleteIdx = e2.OutIdx;

        e1.OutIdx = Unassigned; //nb: safe because we only get here via AddLocalMaxPoly
        e2.OutIdx = Unassigned;

        TEdge e = m_ActiveEdges;
        while( e != null )
        {
          if( e.OutIdx == ObsoleteIdx )
          {
            e.OutIdx = OKIdx;
            e.Side = side;
            break;
          }
          e = e.NextInAEL;
        }
        outRec2.Idx = outRec1.Idx;
      }
      //------------------------------------------------------------------------------

      private void reversePolyPtLinks(OutPt pp)
      {
          if (pp == null) return;
          OutPt pp1;
          OutPt pp2;
          pp1 = pp;
          do
          {
              pp2 = pp1.Next;
              pp1.Next = pp1.Prev;
              pp1.Prev = pp2;
              pp1 = pp2;
          } while (pp1 != pp);
      }
      //------------------------------------------------------------------------------

      private static void swapSides(TEdge edge1, TEdge edge2)
      {
          EdgeSide side = edge1.Side;
          edge1.Side = edge2.Side;
          edge2.Side = side;
      }
      //------------------------------------------------------------------------------

      private static void swapPolyIndexes(TEdge edge1, TEdge edge2)
      {
          int outIdx = edge1.OutIdx;
          edge1.OutIdx = edge2.OutIdx;
          edge2.OutIdx = outIdx;
      }
      //------------------------------------------------------------------------------
      
      private void intersectEdges(TEdge e1, TEdge e2, final Point2d _pt)
      {
        final Point2d pt = _pt.clone();
          
          //e1 will be to the left of e2 BELOW the intersection. Therefore e1 is before
          //e2 in AEL except when e1 is being inserted at the intersection point ...

        boolean e1Contributing = (e1.OutIdx >= 0);
        boolean e2Contributing = (e2.OutIdx >= 0);

          //if either edge is on an OPEN path ...
          if (e1.WindDelta == 0 || e2.WindDelta == 0)
          {
            //ignore subject-subject open path intersections UNLESS they
            //are both open paths, AND they are both 'contributing maximas' ...
            if (e1.WindDelta == 0 && e2.WindDelta == 0) return;
            //if intersecting a subj line with a subj poly ...
            else if (e1.PolyTyp == e2.PolyTyp && 
              e1.WindDelta != e2.WindDelta && m_ClipType == ClipType.ctUnion)
            {
              if (e1.WindDelta == 0)
              {
                if (e2Contributing)
                {
                  addOutPt(e1, pt);
                  if (e1Contributing) e1.OutIdx = Unassigned;
                }
              }
              else
              {
                if (e1Contributing)
                {
                  addOutPt(e2, pt);
                  if (e2Contributing) e2.OutIdx = Unassigned;
                }
              }
            }
            else if (e1.PolyTyp != e2.PolyTyp)
            {
              if ((e1.WindDelta == 0) && Math.abs(e2.WindCnt) == 1 && 
                (m_ClipType != ClipType.ctUnion || e2.WindCnt2 == 0))
              {
                addOutPt(e1, pt);
                if (e1Contributing) e1.OutIdx = Unassigned;
              }
              else if ((e2.WindDelta == 0) && (Math.abs(e1.WindCnt) == 1) && 
                (m_ClipType != ClipType.ctUnion || e1.WindCnt2 == 0))
              {
                addOutPt(e2, pt);
                if (e2Contributing) e2.OutIdx = Unassigned;
              }
            }
            return;
          }

          //update winding counts...
  //assumes that e1 will be to the Right of e2 ABOVE the intersection
          if (e1.PolyTyp == e2.PolyTyp)
          {
              if (isEvenOddFillType(e1))
              {
                  int oldE1WindCnt = e1.WindCnt;
                  e1.WindCnt = e2.WindCnt;
                  e2.WindCnt = oldE1WindCnt;
              }
              else
              {
                  if (e1.WindCnt + e2.WindDelta == 0) e1.WindCnt = -e1.WindCnt;
                  else e1.WindCnt += e2.WindDelta;
                  if (e2.WindCnt - e1.WindDelta == 0) e2.WindCnt = -e2.WindCnt;
                  else e2.WindCnt -= e1.WindDelta;
              }
          }
          else
          {
              if (!isEvenOddFillType(e2)) e1.WindCnt2 += e2.WindDelta;
              else e1.WindCnt2 = (e1.WindCnt2 == 0) ? 1 : 0;
              if (!isEvenOddFillType(e1)) e2.WindCnt2 -= e1.WindDelta;
              else e2.WindCnt2 = (e2.WindCnt2 == 0) ? 1 : 0;
          }

          PolyFillType e1FillType, e2FillType, e1FillType2, e2FillType2;
          if (e1.PolyTyp == PolyType.ptSubject)
          {
              e1FillType = m_SubjFillType;
              e1FillType2 = m_ClipFillType;
          }
          else
          {
              e1FillType = m_ClipFillType;
              e1FillType2 = m_SubjFillType;
          }
          if (e2.PolyTyp == PolyType.ptSubject)
          {
              e2FillType = m_SubjFillType;
              e2FillType2 = m_ClipFillType;
          }
          else
          {
              e2FillType = m_ClipFillType;
              e2FillType2 = m_SubjFillType;
          }

          int e1Wc, e2Wc;
          switch (e1FillType)
          {
              case pftPositive: e1Wc = e1.WindCnt; break;
              case pftNegative: e1Wc = -e1.WindCnt; break;
              default: e1Wc = Math.abs(e1.WindCnt); break;
          }
          switch (e2FillType)
          {
              case pftPositive: e2Wc = e2.WindCnt; break;
              case pftNegative: e2Wc = -e2.WindCnt; break;
              default: e2Wc = Math.abs(e2.WindCnt); break;
          }

          if (e1Contributing && e2Contributing)
          {
            if ((e1Wc != 0 && e1Wc != 1) || (e2Wc != 0 && e2Wc != 1) ||
              (e1.PolyTyp != e2.PolyTyp && m_ClipType != ClipType.ctXor))
            {
              addLocalMaxPoly(e1, e2, pt);
            }
            else
            {
              addOutPt(e1, pt);
              addOutPt(e2, pt);
              swapSides(e1, e2);
              swapPolyIndexes(e1, e2);
            }
          }
          else if (e1Contributing)
          {
              if (e2Wc == 0 || e2Wc == 1)
              {
                addOutPt(e1, pt);
                swapSides(e1, e2);
                swapPolyIndexes(e1, e2);
              }

          }
          else if (e2Contributing)
          {
              if (e1Wc == 0 || e1Wc == 1)
              {
                addOutPt(e2, pt);
                swapSides(e1, e2);
                swapPolyIndexes(e1, e2);
              }
          }
          else if ( (e1Wc == 0 || e1Wc == 1) && (e2Wc == 0 || e2Wc == 1))
          {
              //neither edge is currently contributing ...
              int e1Wc2, e2Wc2;
              switch (e1FillType2)
              {
                  case pftPositive: e1Wc2 = e1.WindCnt2; break;
                  case pftNegative: e1Wc2 = -e1.WindCnt2; break;
                  default: e1Wc2 = Math.abs(e1.WindCnt2); break;
              }
              switch (e2FillType2)
              {
                  case pftPositive: e2Wc2 = e2.WindCnt2; break;
                  case pftNegative: e2Wc2 = -e2.WindCnt2; break;
                  default: e2Wc2 = Math.abs(e2.WindCnt2); break;
              }

              if (e1.PolyTyp != e2.PolyTyp)
              {
                addLocalMinPoly(e1, e2, pt);
              }
              else if (e1Wc == 1 && e2Wc == 1)
                switch (m_ClipType)
                {
                  case ctIntersection:
                    if (e1Wc2 > 0 && e2Wc2 > 0)
                      addLocalMinPoly(e1, e2, pt);
                    break;
                  case ctUnion:
                    if (e1Wc2 <= 0 && e2Wc2 <= 0)
                      addLocalMinPoly(e1, e2, pt);
                    break;
                  case ctDifference:
                    if (((e1.PolyTyp == PolyType.ptClip) && (e1Wc2 > 0) && (e2Wc2 > 0)) ||
                        ((e1.PolyTyp == PolyType.ptSubject) && (e1Wc2 <= 0) && (e2Wc2 <= 0)))
                          addLocalMinPoly(e1, e2, pt);
                    break;
                  case ctXor:
                    addLocalMinPoly(e1, e2, pt);
                    break;
                }
              else
                swapSides(e1, e2);
          }
      }
      //------------------------------------------------------------------------------

      private void deleteFromAEL(TEdge e)
      {
          TEdge AelPrev = e.PrevInAEL;
          TEdge AelNext = e.NextInAEL;
          if (AelPrev == null && AelNext == null && (e != m_ActiveEdges))
              return; //already deleted
          if (AelPrev != null)
              AelPrev.NextInAEL = AelNext;
          else m_ActiveEdges = AelNext;
          if (AelNext != null)
              AelNext.PrevInAEL = AelPrev;
          e.NextInAEL = null;
          e.PrevInAEL = null;
      }
      //------------------------------------------------------------------------------

      private void deleteFromSEL(TEdge e)
      {
          TEdge SelPrev = e.PrevInSEL;
          TEdge SelNext = e.NextInSEL;
          if (SelPrev == null && SelNext == null && (e != m_SortedEdges))
              return; //already deleted
          if (SelPrev != null)
              SelPrev.NextInSEL = SelNext;
          else m_SortedEdges = SelNext;
          if (SelNext != null)
              SelNext.PrevInSEL = SelPrev;
          e.NextInSEL = null;
          e.PrevInSEL = null;
      }
      //------------------------------------------------------------------------------

      private TEdge updateEdgeIntoAEL_REFreturned(TEdge e) throws ClipperException
      {
          if (e.NextInLML == null)
              throw new ClipperException("UpdateEdgeIntoAEL: invalid call");
          TEdge AelPrev = e.PrevInAEL;
          TEdge AelNext = e.NextInAEL;
          e.NextInLML.OutIdx = e.OutIdx;
          if (AelPrev != null)
              AelPrev.NextInAEL = e.NextInLML;
          else m_ActiveEdges = e.NextInLML;
          if (AelNext != null)
              AelNext.PrevInAEL = e.NextInLML;
          e.NextInLML.Side = e.Side;
          e.NextInLML.WindDelta = e.WindDelta;
          e.NextInLML.WindCnt = e.WindCnt;
          e.NextInLML.WindCnt2 = e.WindCnt2;
          e = e.NextInLML;
          e.iCurr.assign(e.iBot);
          e.PrevInAEL = AelPrev;
          e.NextInAEL = AelNext;
          if (!isHorizontal(e)) insertScanbeam(e.iTop.Y);
          return e;
      }
      //------------------------------------------------------------------------------

      private void processHorizontals(boolean isTopOfScanbeam) throws ClipperException
      {
          TEdge horzEdge = m_SortedEdges;
          while (horzEdge != null)
          {
              deleteFromSEL(horzEdge);
              processHorizontal(horzEdge, isTopOfScanbeam);
              horzEdge = m_SortedEdges;
          }
      }
      //------------------------------------------------------------------------------

      class HorzDir 
      {
          Direction dir;
          long horzLeft;
          long horzRight;
      }
      
      void getHorzDirection(TEdge HorzEdge, HorzDir hd)
      {
        if (HorzEdge.iBot.X < HorzEdge.iTop.X)
        {
          hd.horzLeft = HorzEdge.iBot.X;
          hd.horzRight = HorzEdge.iTop.X;
          hd.dir = Direction.dLeftToRight;
        } else
        {
          hd.horzLeft = HorzEdge.iTop.X;
          hd.horzRight = HorzEdge.iBot.X;
          hd.dir = Direction.dRightToLeft;
        }
      }
      
      //------------------------------------------------------------------------

      private void processHorizontal(TEdge horzEdge, boolean isTopOfScanbeam) throws ClipperException
      {
          final HorzDir hd = new HorzDir();

        getHorzDirection(horzEdge, hd);

        TEdge eLastHorz = horzEdge, eMaxPair = null;
        while (eLastHorz.NextInLML != null && isHorizontal(eLastHorz.NextInLML)) 
          eLastHorz = eLastHorz.NextInLML;
        if (eLastHorz.NextInLML == null)
          eMaxPair = getMaximaPair(eLastHorz);

        for (;;)
        {
          boolean IsLastHorz = (horzEdge == eLastHorz);
          TEdge e = getNextInAEL(horzEdge, hd.dir);
          while(e != null)
          {
            //Break if we've got to the end of an intermediate horizontal edge ...
            //nb: Smaller Dx's are to the right of larger Dx's ABOVE the horizontal.
            if (e.iCurr.X == horzEdge.iTop.X && horzEdge.NextInLML != null && 
              e.Dx < horzEdge.NextInLML.Dx) break;

            TEdge eNext = getNextInAEL(e, hd.dir); //saves eNext for later

            if ((hd.dir == Direction.dLeftToRight && e.iCurr.X <= hd.horzRight) ||
              (hd.dir == Direction.dRightToLeft && e.iCurr.X >= hd.horzLeft))
            {
              //so far we're still in range of the horizontal Edge  but make sure
              //we're at the last of consec. horizontals when matching with eMaxPair
              if(e == eMaxPair && IsLastHorz)
              {
                if (horzEdge.OutIdx >= 0)
                {
                  OutPt op1 = addOutPt(horzEdge, horzEdge.iTop);
                  TEdge eNextHorz = m_SortedEdges;
                  while (eNextHorz != null)
                  {
                    if (eNextHorz.OutIdx >= 0 &&
                      horzSegmentsOverlap(horzEdge.iBot.X,
                      horzEdge.iTop.X, eNextHorz.iBot.X, eNextHorz.iTop.X))
                    {
                      OutPt op2 = addOutPt(eNextHorz, eNextHorz.iBot);
                      addJoin(op2, op1, eNextHorz.iTop);
                    }
                    eNextHorz = eNextHorz.NextInSEL;
                  }
                  addGhostJoin(op1, horzEdge.iBot);
                  addLocalMaxPoly(horzEdge, eMaxPair, horzEdge.iTop);
                }
                deleteFromAEL(horzEdge);
                deleteFromAEL(eMaxPair);
                return;
              }
              else if(hd.dir == Direction.dLeftToRight)
              {
                final Point2d Pt = new Point2d(e.iCurr.X, horzEdge.iCurr.Y);
                intersectEdges(horzEdge, e, Pt);
              }
              else
              {
                final Point2d Pt = new Point2d(e.iCurr.X, horzEdge.iCurr.Y);
                intersectEdges(e, horzEdge, Pt);
              }
              swapPositionsInAEL(horzEdge, e);
            }
            else if ((hd.dir == Direction.dLeftToRight && e.iCurr.X >= hd.horzRight) ||
              (hd.dir == Direction.dRightToLeft && e.iCurr.X <= hd.horzLeft)) break;
            e = eNext;
          } //end while

          if (horzEdge.NextInLML != null && isHorizontal(horzEdge.NextInLML))
          {
            horzEdge = updateEdgeIntoAEL_REFreturned(horzEdge);
            if (horzEdge.OutIdx >= 0) addOutPt(horzEdge, horzEdge.iBot);
            getHorzDirection(horzEdge, hd);
          } else
            break;
        } //end for (;;)

        if(horzEdge.NextInLML != null)
        {
          if(horzEdge.OutIdx >= 0)
          {
            OutPt op1 = addOutPt( horzEdge, horzEdge.iTop);
            if (isTopOfScanbeam) addGhostJoin(op1, horzEdge.iBot);

            horzEdge = updateEdgeIntoAEL_REFreturned(horzEdge);
            if (horzEdge.WindDelta == 0) return;
            //nb: HorzEdge is no longer horizontal here
            TEdge ePrev = horzEdge.PrevInAEL;
            TEdge eNext = horzEdge.NextInAEL;
            if (ePrev != null && ePrev.iCurr.X == horzEdge.iBot.X &&
              ePrev.iCurr.Y == horzEdge.iBot.Y && ePrev.WindDelta != 0 &&
              (ePrev.OutIdx >= 0 && ePrev.iCurr.Y > ePrev.iTop.Y &&
              slopesEqual(horzEdge, ePrev, m_UseFullRange)))
            {
              OutPt op2 = addOutPt(ePrev, horzEdge.iBot);
              addJoin(op1, op2, horzEdge.iTop);
            }
            else if (eNext != null && eNext.iCurr.X == horzEdge.iBot.X &&
              eNext.iCurr.Y == horzEdge.iBot.Y && eNext.WindDelta != 0 &&
              eNext.OutIdx >= 0 && eNext.iCurr.Y > eNext.iTop.Y &&
              slopesEqual(horzEdge, eNext, m_UseFullRange))
            {
              OutPt op2 = addOutPt(eNext, horzEdge.iBot);
              addJoin(op1, op2, horzEdge.iTop);
            }
          }
          else
          {
            horzEdge = updateEdgeIntoAEL_REFreturned(horzEdge); 
          }
        }
        else
        {
          if (horzEdge.OutIdx >= 0) addOutPt(horzEdge, horzEdge.iTop);
          deleteFromAEL(horzEdge);
        }
      }
      
      //------------------------------------------------------------------------------

      private TEdge getNextInAEL(TEdge e, Direction direction)
      {
          return direction == Direction.dLeftToRight ? e.NextInAEL: e.PrevInAEL;
      }
      
      //------------------------------------------------------------------------------

      private boolean isMinima(TEdge e)
      {
          return e != null && (e.Prev.NextInLML != e) && (e.Next.NextInLML != e);
      }
      //------------------------------------------------------------------------------

      private boolean isMaxima(TEdge e, double Y)
      {
          return (e != null && e.iTop.Y == Y && e.NextInLML == null);
      }
      //------------------------------------------------------------------------------

      private boolean isIntermediate(TEdge e, double Y)
      {
          return (e.iTop.Y == Y && e.NextInLML != null);
      }
      //------------------------------------------------------------------------------

      private TEdge getMaximaPair(TEdge e)
      {
        TEdge result = null;
        if ((e.Next.iTop.equals(e.iTop)) && e.Next.NextInLML == null)
          result = e.Next;
        else if ((e.Prev.iTop.equals(e.iTop)) && e.Prev.NextInLML == null)
          result = e.Prev;
        if (result != null && (result.OutIdx == Skip ||
          (result.NextInAEL == result.PrevInAEL && !isHorizontal(result))))
          return null;
        return result;
      }
      //------------------------------------------------------------------------------

      private boolean processIntersections(long topY) throws ClipperException
      {
        if( m_ActiveEdges == null ) return true;
        try {
          buildIntersectList(topY);
          if (m_IntersectList.isEmpty()) return true;
          if (m_IntersectList.size() == 1 || fixupIntersectionOrder()) 
              processIntersectList();
          else 
              return false;
        }
        catch (Exception e) {
          m_SortedEdges = null;
          m_IntersectList.clear();
          throw new ClipperException("ProcessIntersections error");
        }
        m_SortedEdges = null;
        return true;
      }
      //------------------------------------------------------------------------------

      private void buildIntersectList(long topY)
      {
        if (m_ActiveEdges == null) return;

        //prepare for sorting ...
        TEdge e = m_ActiveEdges;
        m_SortedEdges = e;
        while (e != null)
        {
          e.PrevInSEL = e.PrevInAEL;
          e.NextInSEL = e.NextInAEL;
          e.iCurr.X = topX(e, topY);
          e = e.NextInAEL;
        }

        //bubblesort ...
        boolean isModified = true;
        while( isModified && m_SortedEdges != null )
        {
          isModified = false;
          e = m_SortedEdges;
          while( e.NextInSEL != null )
          {
            TEdge eNext = e.NextInSEL;
            if (e.iCurr.X > eNext.iCurr.X)
            {
                final Point2d pt = intersectPoint(e, eNext);
                IntersectNode newNode = new IntersectNode();
                newNode.Edge1 = e;
                newNode.Edge2 = eNext;
                newNode.iPt.assign(pt);
                m_IntersectList.add(newNode);

                swapPositionsInSEL(e, eNext);
                isModified = true;
            }
            else
              e = eNext;
          }
          if( e.PrevInSEL != null ) e.PrevInSEL.NextInSEL = null;
          else break;
        }
        m_SortedEdges = null;
      }      
      
      //------------------------------------------------------------------------------
      
      private boolean edgesAdjacent(IntersectNode inode)
      {
        return (inode.Edge1.NextInSEL == inode.Edge2) ||
          (inode.Edge1.PrevInSEL == inode.Edge2);
      }
      //------------------------------------------------------------------------------

      private boolean fixupIntersectionOrder()
      {
        //pre-condition: intersections are sorted bottom-most first.
        //Now it's crucial that intersections are made only between adjacent edges,
        //so to ensure this the order of intersections may need adjusting ...
        Collections.sort(m_IntersectList, m_IntersectNodeComparer);

        copyAELToSEL();
        int cnt = m_IntersectList.size();
        for (int i = 0; i < cnt; i++)
        {
          if (!edgesAdjacent(m_IntersectList.get(i)))
          {
            int j = i + 1;
            while (j < cnt && !edgesAdjacent(m_IntersectList.get(j))) j++;
            if (j == cnt) return false;

            IntersectNode tmp = m_IntersectList.get(i);
            m_IntersectList.set(i, m_IntersectList.get(j));
            m_IntersectList.set(j, tmp);

          }
          swapPositionsInSEL(m_IntersectList.get(i).Edge1, m_IntersectList.get(i).Edge2);
        }
          return true;
      }
      //------------------------------------------------------------------------------

      private void processIntersectList()
      {
        for (IntersectNode iNode : m_IntersectList) {
            intersectEdges(iNode.Edge1, iNode.Edge2, iNode.iPt);
            swapPositionsInAEL(iNode.Edge1, iNode.Edge2);
        }
        m_IntersectList.clear();
      }
      //------------------------------------------------------------------------------

      private static long Round(double value)
      {
          return value < 0 ? (long)(value - 0.5) : (long)(value + 0.5);
      }
      //------------------------------------------------------------------------------

      private static long topX(TEdge edge, long currentY)
      {
          if (currentY == edge.iTop.Y)
              return edge.iTop.X;
          return edge.iBot.X + Round(edge.Dx *(currentY - edge.iBot.Y));
      }
      //------------------------------------------------------------------------------

      private Point2d intersectPoint(TEdge edge1, TEdge edge2)
      {
        final Point2d ip = new Point2d();
        double b1, b2;
        //nb: with very large coordinate values, it's possible for SlopesEqual() to 
        //return false but for the edge.Dx value be equal due to double precision rounding.
        if (edge1.Dx == edge2.Dx)
        {
          ip.Y = edge1.iCurr.Y;
          ip.X = topX(edge1, ip.Y);
          System.out.println("IntersectionPoint(1): " + Long.toString(ip.X) + ", " + Long.toString(ip.Y));
          return ip;
        }

        if (edge1.iDelta.X == 0)
        {
            ip.X = edge1.iBot.X;
            if (isHorizontal(edge2))
            {
                ip.Y = edge2.iBot.Y;
            }
            else
            {
                b2 = edge2.iBot.Y - (edge2.iBot.X / edge2.Dx);
                ip.Y = Round(ip.X / edge2.Dx + b2);
            }
        }
        else if (edge2.iDelta.X == 0)
        {
            ip.X = edge2.iBot.X;
            if (isHorizontal(edge1))
            {
                ip.Y = edge1.iBot.Y;
            }
            else
            {
                b1 = edge1.iBot.Y - (edge1.iBot.X / edge1.Dx);
                ip.Y = Round(ip.X / edge1.Dx + b1);
            }
        }
        else
        {
            b1 = edge1.iBot.X - edge1.iBot.Y * edge1.Dx;
            b2 = edge2.iBot.X - edge2.iBot.Y * edge2.Dx;
            double q = (b2 - b1) / (edge1.Dx - edge2.Dx);
            ip.Y = Round(q);
            if (Math.abs(edge1.Dx) < Math.abs(edge2.Dx))
                ip.X = Round(edge1.Dx * q + b1);
            else
                ip.X = Round(edge2.Dx * q + b2);
        }

        if (ip.Y < edge1.iTop.Y || ip.Y < edge2.iTop.Y)
        {
          if (edge1.iTop.Y > edge2.iTop.Y)
            ip.Y = edge1.iTop.Y;
          else
            ip.Y = edge2.iTop.Y;
          if (Math.abs(edge1.Dx) < Math.abs(edge2.Dx))
            ip.X = topX(edge1, ip.Y);
          else
            ip.X = topX(edge2, ip.Y);
        }
        //finally, don't allow 'ip' to be BELOW curr.Y (ie bottom of scanbeam) ...
        if (ip.Y > edge1.iCurr.Y)
        {
          ip.Y = edge1.iCurr.Y;
          //better to use the more vertical edge to derive X ...
          if (Math.abs(edge1.Dx) > Math.abs(edge2.Dx)) 
            ip.X = topX(edge2, ip.Y);
          else 
            ip.X = topX(edge1, ip.Y);
        }
        
        System.out.println("IntersectionPoint(2): " + Long.toString(ip.X) + ", " + Long.toString(ip.Y));
        return ip;
      }
      
      
      //------------------------------------------------------------------------------
      
      private void processEdgesAtTopOfScanbeam(long topY) throws ClipperException
      {
        TEdge e = m_ActiveEdges;
        while(e != null)
        {
          //1. process maxima, treating them as if they're 'bent' horizontal edges,
          //   but exclude maxima with horizontal edges. nb: e can't be a horizontal.
          boolean IsMaximaEdge = isMaxima(e, topY);

          if(IsMaximaEdge)
          {
            TEdge eMaxPair = getMaximaPair(e);
            IsMaximaEdge = (eMaxPair == null || !isHorizontal(eMaxPair));
          }

          if(IsMaximaEdge)
          {
            TEdge ePrev = e.PrevInAEL;
            doMaxima(e);
            if( ePrev == null) e = m_ActiveEdges;
            else e = ePrev.NextInAEL;
          }
          else
          {
            //2. promote horizontal edges, otherwise update Curr.X and Curr.Y ...
            if (isIntermediate(e, topY) && isHorizontal(e.NextInLML))
            {
              e = updateEdgeIntoAEL_REFreturned(e);
              if (e.OutIdx >= 0)
                addOutPt(e, e.iBot);
              addEdgeToSEL(e);
            } 
            else
            {
              e.iCurr.X = topX( e, topY );
              e.iCurr.Y = topY;
            }

            /* #### this piece of code breaks collinear side edges into unwanted segments
            if (getStrictlySimple())
            {
              TEdge ePrev = e.PrevInAEL;
              if ((e.OutIdx >= 0) && (e.WindDelta != 0) && ePrev != null &&
                (ePrev.OutIdx >= 0) && (ePrev.iCurr.X == e.iCurr.X) &&
                (ePrev.WindDelta != 0))
              {
                final Point2d ip = new Point2d(e.iCurr);
                setZ(ip, ePrev, e);
                OutPt op = addOutPt(ePrev, ip);
                OutPt op2 = addOutPt(e, ip);
                addJoin(op, op2, ip); //StrictlySimple (type-3) join
              }
            } */

            e = e.NextInAEL;
          }
        }

        //3. Process horizontals at the Top of the scanbeam ...
        processHorizontals(true);

        //4. Promote intermediate vertices ...
        e = m_ActiveEdges;
        while (e != null)
        {
          if(isIntermediate(e, topY))
          {
            OutPt op = null;
            if( e.OutIdx >= 0 ) 
              op = addOutPt(e, e.iTop);
            e = updateEdgeIntoAEL_REFreturned(e);

            //if output polygons share an edge, they'll need joining later ...
            TEdge ePrev = e.PrevInAEL;
            TEdge eNext = e.NextInAEL;
            if (ePrev != null && ePrev.iCurr.X == e.iBot.X &&
              ePrev.iCurr.Y == e.iBot.Y && op != null &&
              ePrev.OutIdx >= 0 && ePrev.iCurr.Y > ePrev.iTop.Y &&
              slopesEqual(e, ePrev,m_UseFullRange) &&
              (e.WindDelta != 0) && (ePrev.WindDelta != 0))
            {
              OutPt op2 = addOutPt(ePrev, e.iBot);
              addJoin(op, op2, e.iTop);
            }
            else if (eNext != null && eNext.iCurr.X == e.iBot.X &&
              eNext.iCurr.Y == e.iBot.Y && op != null &&
              eNext.OutIdx >= 0 && eNext.iCurr.Y > eNext.iTop.Y &&
              slopesEqual(e, eNext,m_UseFullRange) &&
              (e.WindDelta != 0) && (eNext.WindDelta != 0))
            {
              OutPt op2 = addOutPt(eNext, e.iBot);
              addJoin(op, op2, e.iTop);
            }
          }
          e = e.NextInAEL;
        }
      }
      //------------------------------------------------------------------------------

      private void doMaxima(TEdge e) throws ClipperException
      {
        TEdge eMaxPair = getMaximaPair(e);
        if (eMaxPair == null)
        {
          if (e.OutIdx >= 0)
            addOutPt(e, e.iTop);
          deleteFromAEL(e);
          return;
        }

        TEdge eNext = e.NextInAEL;
        while(eNext != null && eNext != eMaxPair)
        {
          intersectEdges(e, eNext, e.iTop);
          swapPositionsInAEL(e, eNext);
          eNext = e.NextInAEL;
        }

        if(e.OutIdx == Unassigned && eMaxPair.OutIdx == Unassigned)
        {
          deleteFromAEL(e);
          deleteFromAEL(eMaxPair);
        }
        else if( e.OutIdx >= 0 && eMaxPair.OutIdx >= 0 )
        {
          if (e.OutIdx >= 0) addLocalMaxPoly(e, eMaxPair, e.iTop);
          deleteFromAEL(e);
          deleteFromAEL(eMaxPair);
        }
        else if (e.WindDelta == 0)
        {
          if (e.OutIdx >= 0) 
          {
            addOutPt(e, e.iTop);
            e.OutIdx = Unassigned;
          }
          deleteFromAEL(e);

          if (eMaxPair.OutIdx >= 0)
          {
            addOutPt(eMaxPair, e.iTop);
            eMaxPair.OutIdx = Unassigned;
          }
          deleteFromAEL(eMaxPair);
        } 
        else throw new ClipperException("DoMaxima error");
      }
      //------------------------------------------------------------------------------

      public static void reversePaths(Paths polys)
      {
        for (Path poly: polys) { poly.reverse(); }
      }
      //------------------------------------------------------------------------------

      public static boolean orientation(Path poly)
      {
          return area(poly) >= 0;
      }
      //------------------------------------------------------------------------------

      private int pointCount(OutPt pts)
      {
          if (pts == null) return 0;
          int result = 0;
          OutPt p = pts;
          do
          {
              result++;
              p = p.Next;
          }
          while (p != pts);
          return result;
      }
    //------------------------------------------------------------------------------

    private void buildResult(Paths polyg) {
        polyg.clear();
        for (OutRec outRec : m_PolyOuts) {
            if (outRec.Pts == null) {
                continue;
            }
            OutPt p = outRec.Pts.Prev;
            int cnt = pointCount(p);
            if (cnt < 2) {
                continue;
            }
            Path pg = new Path();
            for (int j = 0; j < cnt; j++) {
                pg.add(p.iPt);
                p = p.Prev;
            }
            polyg.add(pg);
        }
    }
    
    //------------------------------------------------------------------------------

    private void buildResult2(PolyTree polytree) {
        polytree.clear();

        for (OutRec outRec : m_PolyOuts) {
            int cnt = pointCount(outRec.Pts);
            if ((outRec.IsOpen && cnt < 2)
                    || (!outRec.IsOpen && cnt < 3)) {
                continue;
            }
            fixHoleLinkage(outRec);
            PolyNode pn = new PolyNode();
            polytree.m_AllPolys.add(pn);
            outRec.PolyNode = pn;
            OutPt op = outRec.Pts.Prev;
            for (int j = 0; j < cnt; j++) {
                pn.m_polygon.add(op.iPt);
                op = op.Prev;
            }
        }

        for (OutRec outRec : m_PolyOuts) {
            if (outRec.PolyNode == null) {
                continue;
            } else if (outRec.IsOpen) {
                outRec.PolyNode.m_isOpen = true;
                polytree.addChild(outRec.PolyNode);
            } else if (outRec.FirstLeft != null
                    && outRec.FirstLeft.PolyNode != null) {
                outRec.FirstLeft.PolyNode.addChild(outRec.PolyNode);
            } else {
                polytree.addChild(outRec.PolyNode);
            }
        }
    }
    
    //------------------------------------------------------------------------------

      private void fixupOutPolygon(OutRec outRec)
      {
          //FixupOutPolygon() - removes duplicate points and simplifies consecutive
          //parallel edges by removing the middle vertex.
          OutPt lastOK = null;
          outRec.BottomPt = null;
          OutPt pp = outRec.Pts;
          for (;;)
          {
              if (pp.Prev == pp || pp.Prev == pp.Next)
              {
                  outRec.Pts = null;
                  return;
              }
              //test for duplicate points and collinear edges ...
              if ((pp.iPt.equals(pp.Next.iPt)) || (pp.iPt.equals(pp.Prev.iPt)) ||
                (slopesEqual(pp.Prev.iPt, pp.iPt, pp.Next.iPt, m_UseFullRange) &&
                (!getPreserveCollinear() || !Pt2IsBetweenPt1AndPt3(pp.Prev.iPt, pp.iPt, pp.Next.iPt))))
              {
                  lastOK = null;
                  pp.Prev.Next = pp.Next;
                  pp.Next.Prev = pp.Prev;
                  pp = pp.Prev;
              }
              else if (pp == lastOK) break;
              else
              {
                  if (lastOK == null) lastOK = pp;
                  pp = pp.Next;
              }
          }
          outRec.Pts = pp;
      }
      //------------------------------------------------------------------------------

      OutPt dupOutPt(OutPt outPt, boolean InsertAfter)
      {
        OutPt result = new OutPt();
        result.iPt.assign(outPt.iPt);
        result.Idx = outPt.Idx;
        if (InsertAfter)
        {
          result.Next = outPt.Next;
          result.Prev = outPt;
          outPt.Next.Prev = result;
          outPt.Next = result;
        } 
        else
        {
          result.Prev = outPt.Prev;
          result.Next = outPt;
          outPt.Prev.Next = result;
          outPt.Prev = result;
        }
        return result;
      }
      //------------------------------------------------------------------------------
      
      class OverlapResult
      {
          long Left;
          long Right;
      }
      
      private boolean getOverlap(long a1, long a2, long b1, long b2, OverlapResult or)
      {
        if (a1 < a2)
        {
          if (b1 < b2) {or.Left = Math.max(a1,b1); or.Right = Math.min(a2,b2);}
          else {or.Left = Math.max(a1,b2); or.Right = Math.min(a2,b1);}
        } 
        else
        {
          if (b1 < b2) {or.Left = Math.max(a2,b1); or.Right = Math.min(a1,b2);}
          else { or.Left = Math.max(a2, b2); or.Right = Math.min(a1, b1); }
        }
        return or.Left < or.Right;
      }
      //------------------------------------------------------------------------------

      boolean joinHorz(OutPt op1, OutPt op1b, OutPt op2, OutPt op2b, 
        final Point2d _Pt, boolean DiscardLeft)
      {
        final Point2d Pt = _Pt.clone();
          
        Direction Dir1 = (op1.iPt.X > op1b.iPt.X ? 
          Direction.dRightToLeft : Direction.dLeftToRight);
        Direction Dir2 = (op2.iPt.X > op2b.iPt.X ?
          Direction.dRightToLeft : Direction.dLeftToRight);
        if (Dir1 == Dir2) return false;

        //When DiscardLeft, we want Op1b to be on the Left of Op1, otherwise we
        //want Op1b to be on the Right. (And likewise with Op2 and Op2b.)
        //So, to facilitate this while inserting Op1b and Op2b ...
        //when DiscardLeft, make sure we're AT or RIGHT of Pt before adding Op1b,
        //otherwise make sure we're AT or LEFT of Pt. (Likewise with Op2b.)
        if (Dir1 == Direction.dLeftToRight) 
        {
          while (op1.Next.iPt.X <= Pt.X && 
            op1.Next.iPt.X >= op1.iPt.X && op1.Next.iPt.Y == Pt.Y)  
              op1 = op1.Next;
          if (DiscardLeft && (op1.iPt.X != Pt.X)) op1 = op1.Next;
          op1b = dupOutPt(op1, !DiscardLeft);
          if (op1b.iPt.not_equals(Pt)) 
          {
            op1 = op1b;
            op1.iPt.assign(Pt);
            op1b = dupOutPt(op1, !DiscardLeft);
          }
        } 
        else
        {
          while (op1.Next.iPt.X >= Pt.X && 
            op1.Next.iPt.X <= op1.iPt.X && op1.Next.iPt.Y == Pt.Y) 
              op1 = op1.Next;
          if (!DiscardLeft && (op1.iPt.X != Pt.X)) op1 = op1.Next;
          op1b = dupOutPt(op1, DiscardLeft);
          if (op1b.iPt.not_equals(Pt))
          {
            op1 = op1b;
            op1.iPt.assign(Pt);
            op1b = dupOutPt(op1, DiscardLeft);
          }
        }

        if (Dir2 == Direction.dLeftToRight)
        {
          while (op2.Next.iPt.X <= Pt.X && 
            op2.Next.iPt.X >= op2.iPt.X && op2.Next.iPt.Y == Pt.Y)
              op2 = op2.Next;
          if (DiscardLeft && (op2.iPt.X != Pt.X)) op2 = op2.Next;
          op2b = dupOutPt(op2, !DiscardLeft);
          if (op2b.iPt.not_equals(Pt))
          {
            op2 = op2b;
            op2.iPt.assign(Pt);
            op2b = dupOutPt(op2, !DiscardLeft);
          }
        } else
        {
          while (op2.Next.iPt.X >= Pt.X && 
            op2.Next.iPt.X <= op2.iPt.X && op2.Next.iPt.Y == Pt.Y) 
              op2 = op2.Next;
          if (!DiscardLeft && (op2.iPt.X != Pt.X)) op2 = op2.Next;
          op2b = dupOutPt(op2, DiscardLeft);
          if (op2b.iPt.not_equals(Pt))
          {
            op2 = op2b;
            op2.iPt.assign(Pt);
            op2b = dupOutPt(op2, DiscardLeft);
          }
        }

        if ((Dir1 == Direction.dLeftToRight) == DiscardLeft)
        {
          op1.Prev = op2;
          op2.Next = op1;
          op1b.Next = op2b;
          op2b.Prev = op1b;
        }
        else
        {
          op1.Next = op2;
          op2.Prev = op1;
          op1b.Prev = op2b;
          op2b.Next = op1b;
        }
        return true;
      }
      //------------------------------------------------------------------------------

      private boolean joinPoints(Join j, OutRec outRec1, OutRec outRec2)
      {
        OutPt op1 = j.OutPt1, op1b;
        OutPt op2 = j.OutPt2, op2b;

        //There are 3 kinds of joins for output polygons ...
        //1. Horizontal joins where Join.OutPt1 & Join.OutPt2 are a vertices anywhere
        //along (horizontal) collinear edges (& Join.OffPt is on the same horizontal).
        //2. Non-horizontal joins where Join.OutPt1 & Join.OutPt2 are at the same
        //location at the Bottom of the overlapping segment (& Join.OffPt is above).
        //3. StrictlySimple joins where edges touch but are not collinear and where
        //Join.OutPt1, Join.OutPt2 & Join.OffPt all share the same point.
        boolean isHorizontal = (j.OutPt1.iPt.Y == j.iOffPt.Y);

        if (isHorizontal && (j.iOffPt.equals(j.OutPt1.iPt)) && (j.iOffPt.equals(j.OutPt2.iPt)))
        {          
          //Strictly Simple join ...
          if (outRec1 != outRec2) return false;
          op1b = j.OutPt1.Next;
          while (op1b != op1 && (op1b.iPt.equals(j.iOffPt))) 
            op1b = op1b.Next;
          boolean reverse1 = (op1b.iPt.Y > j.iOffPt.Y);
          op2b = j.OutPt2.Next;
          while (op2b != op2 && (op2b.iPt.equals(j.iOffPt))) 
            op2b = op2b.Next;
          boolean reverse2 = (op2b.iPt.Y > j.iOffPt.Y);
          if (reverse1 == reverse2) return false;
          if (reverse1)
          {
            op1b = dupOutPt(op1, false);
            op2b = dupOutPt(op2, true);
            op1.Prev = op2;
            op2.Next = op1;
            op1b.Next = op2b;
            op2b.Prev = op1b;
            j.OutPt1 = op1;
            j.OutPt2 = op1b;
            return true;
          } else
          {
            op1b = dupOutPt(op1, true);
            op2b = dupOutPt(op2, false);
            op1.Next = op2;
            op2.Prev = op1;
            op1b.Prev = op2b;
            op2b.Next = op1b;
            j.OutPt1 = op1;
            j.OutPt2 = op1b;
            return true;
          }
        } 
        else if (isHorizontal)
        {
          //treat horizontal joins differently to non-horizontal joins since with
          //them we're not yet sure where the overlapping is. OutPt1.Pt & OutPt2.Pt
          //may be anywhere along the horizontal edge.
          op1b = op1;
          while (op1.Prev.iPt.Y == op1.iPt.Y && op1.Prev != op1b && op1.Prev != op2)
            op1 = op1.Prev;
          while (op1b.Next.iPt.Y == op1b.iPt.Y && op1b.Next != op1 && op1b.Next != op2)
            op1b = op1b.Next;
          if (op1b.Next == op1 || op1b.Next == op2) return false; //a flat 'polygon'

          op2b = op2;
          while (op2.Prev.iPt.Y == op2.iPt.Y && op2.Prev != op2b && op2.Prev != op1b)
            op2 = op2.Prev;
          while (op2b.Next.iPt.Y == op2b.iPt.Y && op2b.Next != op2 && op2b.Next != op1)
            op2b = op2b.Next;
          if (op2b.Next == op2 || op2b.Next == op1) return false; //a flat 'polygon'

          final OverlapResult or = new OverlapResult();
          //Op1 -. Op1b & Op2 -. Op2b are the extremites of the horizontal edges
          if (!getOverlap(op1.iPt.X, op1b.iPt.X, op2.iPt.X, op2b.iPt.X, or))
            return false;

          //DiscardLeftSide: when overlapping edges are joined, a spike will created
          //which needs to be cleaned up. However, we don't want Op1 or Op2 caught up
          //on the discard Side as either may still be needed for other joins ...
          final Point2d Pt = new Point2d();
          boolean DiscardLeftSide;
          if (op1.iPt.X >= or.Left && op1.iPt.X <= or.Right) 
          {
            Pt.assign(op1.iPt); DiscardLeftSide = (op1.iPt.X > op1b.iPt.X);
          } 
          else if (op2.iPt.X >= or.Left && op2.iPt.X <= or.Right) 
          {
            Pt.assign(op2.iPt); DiscardLeftSide = (op2.iPt.X > op2b.iPt.X);
          } 
          else if (op1b.iPt.X >= or.Left && op1b.iPt.X <= or.Right)
          {
            Pt.assign(op1b.iPt); DiscardLeftSide = op1b.iPt.X > op1.iPt.X;
          } 
          else
          {
            Pt.assign(op2b.iPt); DiscardLeftSide = (op2b.iPt.X > op2.iPt.X);
          }
          j.OutPt1 = op1;
          j.OutPt2 = op2;
          return joinHorz(op1, op1b, op2, op2b, Pt, DiscardLeftSide);
        } else
        {
          //nb: For non-horizontal joins ...
          //    1. Jr.OutPt1.Pt.Y == Jr.OutPt2.Pt.Y
          //    2. Jr.OutPt1.Pt > Jr.OffPt.Y

          //make sure the polygons are correctly oriented ...
          op1b = op1.Next;
          while ((op1b.iPt.equals(op1.iPt)) && (op1b != op1)) op1b = op1b.Next;
          boolean Reverse1 = ((op1b.iPt.Y > op1.iPt.Y) ||
            !slopesEqual(op1.iPt, op1b.iPt, j.iOffPt, m_UseFullRange));
          if (Reverse1)
          {
            op1b = op1.Prev;
            while ((op1b.iPt.equals(op1.iPt)) && (op1b != op1)) op1b = op1b.Prev;
            if ((op1b.iPt.Y > op1.iPt.Y) ||
              !slopesEqual(op1.iPt, op1b.iPt, j.iOffPt, m_UseFullRange)) return false;
          }
          op2b = op2.Next;
          while ((op2b.iPt.equals(op2.iPt)) && (op2b != op2)) op2b = op2b.Next;
          boolean Reverse2 = ((op2b.iPt.Y > op2.iPt.Y) ||
            !slopesEqual(op2.iPt, op2b.iPt, j.iOffPt, m_UseFullRange));
          if (Reverse2)
          {
            op2b = op2.Prev;
            while ((op2b.iPt.equals(op2.iPt)) && (op2b != op2)) op2b = op2b.Prev;
            if ((op2b.iPt.Y > op2.iPt.Y) ||
              !slopesEqual(op2.iPt, op2b.iPt, j.iOffPt, m_UseFullRange)) return false;
          }

          if ((op1b == op1) || (op2b == op2) || (op1b == op2b) ||
            ((outRec1 == outRec2) && (Reverse1 == Reverse2))) return false;

          if (Reverse1)
          {
            op1b = dupOutPt(op1, false);
            op2b = dupOutPt(op2, true);
            op1.Prev = op2;
            op2.Next = op1;
            op1b.Next = op2b;
            op2b.Prev = op1b;
            j.OutPt1 = op1;
            j.OutPt2 = op1b;
            return true;
          } else
          {
            op1b = dupOutPt(op1, true);
            op2b = dupOutPt(op2, false);
            op1.Next = op2;
            op2.Prev = op1;
            op1b.Prev = op2b;
            op2b.Next = op1b;
            j.OutPt1 = op1;
            j.OutPt2 = op1b;
            return true;
          }
        }
      }
      //----------------------------------------------------------------------
      
      public static int pointInPolygon(final Point2d _pt, Path path)
      {
        final Point2d pt = _pt;
          
        //returns 0 if false, +1 if true, -1 if pt ON polygon boundary
        //See "The Point in Polygon Problem for Arbitrary Polygons" by Hormann & Agathos
        //http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.88.5498&rep=rep1&type=pdf
        int result = 0, cnt = path.size();
        if (cnt < 3) return 0;
        final Point2d ip = new Point2d();
        ip.assign (path.get(0));
        for (int i = 1; i <= cnt; ++i)
        {
          final Point2d ipNext = (i == cnt ? path.get(0) : path.get(i)).clone();
          if (ipNext.Y == pt.Y)
          {
            if ((ipNext.X == pt.X) || (ip.Y == pt.Y &&
              ((ipNext.X > pt.X) == (ip.X < pt.X)))) return -1;
          }
          if ((ip.Y < pt.Y) != (ipNext.Y < pt.Y))
          {
            if (ip.X >= pt.X)
            {
              if (ipNext.X > pt.X) result = 1 - result;
              else
              {
                double d = (double)(ip.X - pt.X) * (ipNext.Y - pt.Y) -
                  (double)(ipNext.X - pt.X) * (ip.Y - pt.Y);
                if (d == 0) return -1;
                else if ((d > 0) == (ipNext.Y > ip.Y)) result = 1 - result;
              }
            }
            else
            {
              if (ipNext.X > pt.X)
              {
                double d = (double)(ip.X - pt.X) * (ipNext.Y - pt.Y) -
                  (double)(ipNext.X - pt.X) * (ip.Y - pt.Y);
                if (d == 0) return -1;
                else if ((d > 0) == (ipNext.Y > ip.Y)) result = 1 - result;
              }
            }
          }
          ip.assign(ipNext);
        }
        return result;
      }
      //------------------------------------------------------------------------------

      private static int pointInPolygon(final Point2d _pt, OutPt op)
      {
        final Point2d pt = _pt.clone();
          
        //returns 0 if false, +1 if true, -1 if pt ON polygon boundary
        //See "The Point in Polygon Problem for Arbitrary Polygons" by Hormann & Agathos
        //http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.88.5498&rep=rep1&type=pdf
        int result = 0;
        OutPt startOp = op;
        long ptx = pt.X, pty = pt.Y;
        long poly0x = op.iPt.X, poly0y = op.iPt.Y;
        do
        {
          op = op.Next;
          long poly1x = op.iPt.X, poly1y = op.iPt.Y;

          if (poly1y == pty)
          {
            if ((poly1x == ptx) || (poly0y == pty &&
              ((poly1x > ptx) == (poly0x < ptx)))) return -1;
          }
          if ((poly0y < pty) != (poly1y < pty))
          {
            if (poly0x >= ptx)
            {
              if (poly1x > ptx) result = 1 - result;
              else
              {
                double d = (double)(poly0x - ptx) * (poly1y - pty) -
                  (double)(poly1x - ptx) * (poly0y - pty);
                if (d == 0) return -1;
                if ((d > 0) == (poly1y > poly0y)) result = 1 - result;
              }
            }
            else
            {
              if (poly1x > ptx)
              {
                double d = (double)(poly0x - ptx) * (poly1y - pty) -
                  (double)(poly1x - ptx) * (poly0y - pty);
                if (d == 0) return -1;
                if ((d > 0) == (poly1y > poly0y)) result = 1 - result;
              }
            }
          }
          poly0x = poly1x; poly0y = poly1y;
        } while (startOp != op);
        return result;
      }
      //------------------------------------------------------------------------------
      
      private static boolean poly2ContainsPoly1(OutPt outPt1, OutPt outPt2)
      {
        OutPt op = outPt1;
        do
        {
          //nb: PointInPolygon returns 0 if false, +1 if true, -1 if pt on polygon
          int res = pointInPolygon(op.iPt, outPt2);
          if (res >= 0) return res > 0;
          op = op.Next;
        }
        while (op != outPt1);
        return true;
      }
      //----------------------------------------------------------------------

      private void fixupFirstLefts1(OutRec OldOutRec, OutRec NewOutRec)
      { 
        for (OutRec outRec : m_PolyOuts) {
            if (outRec.Pts == null || outRec.FirstLeft == null) continue;
            OutRec firstLeft = parseFirstLeft(outRec.FirstLeft);
            if (firstLeft == OldOutRec)
            {
                if (poly2ContainsPoly1(outRec.Pts, NewOutRec.Pts))
                    outRec.FirstLeft = NewOutRec;
            }
        }
      }
      //----------------------------------------------------------------------

      private void fixupFirstLefts2(OutRec OldOutRec, OutRec NewOutRec)
      { 
          for (OutRec outRec: m_PolyOuts)
              if (outRec.FirstLeft == OldOutRec) outRec.FirstLeft = NewOutRec;
      }
      //----------------------------------------------------------------------

      private static OutRec parseFirstLeft(OutRec FirstLeft)
      {
        while (FirstLeft != null && FirstLeft.Pts == null) 
          FirstLeft = FirstLeft.FirstLeft;
        return FirstLeft;
      }
      
    //------------------------------------------------------------------------------
    private void joinCommonEdges() {
        for (Join join : m_Joins) {
            OutRec outRec1 = getOutRec(join.OutPt1.Idx);
            OutRec outRec2 = getOutRec(join.OutPt2.Idx);

            if (outRec1.Pts == null || outRec2.Pts == null) {
                continue;
            }

            //get the polygon fragment with the correct hole state (FirstLeft)
            //before calling JoinPoints() ...
            OutRec holeStateRec;
            if (outRec1 == outRec2) {
                holeStateRec = outRec1;
            } else if (param1RightOfParam2(outRec1, outRec2)) {
                holeStateRec = outRec2;
            } else if (param1RightOfParam2(outRec2, outRec1)) {
                holeStateRec = outRec1;
            } else {
                holeStateRec = getLowermostRec(outRec1, outRec2);
            }

            if (!joinPoints(join, outRec1, outRec2)) {
                continue;
            }

            if (outRec1 == outRec2) {
                //instead of joining two polygons, we've just created a new one by
                //splitting one polygon into two.
                outRec1.Pts = join.OutPt1;
                outRec1.BottomPt = null;
                outRec2 = createOutRec();
                outRec2.Pts = join.OutPt2;

                //update all OutRec2.Pts Idx's ...
                updateOutPtIdxs(outRec2);

                //We now need to check every OutRec.FirstLeft pointer. If it points
                //to OutRec1 it may need to point to OutRec2 instead ...
                if (m_UsingPolyTree) {
                    for (int j = 0; j < m_PolyOuts.size() - 1; j++) {
                        OutRec oRec = m_PolyOuts.get(j);
                        if (oRec.Pts == null || parseFirstLeft(oRec.FirstLeft) != outRec1
                                || oRec.IsHole == outRec1.IsHole) {
                            continue;
                        }
                        if (poly2ContainsPoly1(oRec.Pts, join.OutPt2)) {
                            oRec.FirstLeft = outRec2;
                        }
                    }
                }

                if (poly2ContainsPoly1(outRec2.Pts, outRec1.Pts)) {
                    //outRec2 is contained by outRec1 ...
                    outRec2.IsHole = !outRec1.IsHole;
                    outRec2.FirstLeft = outRec1;

                    //fixup FirstLeft pointers that may need reassigning to OutRec1
                    if (m_UsingPolyTree) {
                        fixupFirstLefts2(outRec2, outRec1);
                    }

                    if ((outRec2.IsHole ^ getReverseSolution()) == (area(outRec2) > 0)) {
                        reversePolyPtLinks(outRec2.Pts);
                    }

                } else if (poly2ContainsPoly1(outRec1.Pts, outRec2.Pts)) {
                    //outRec1 is contained by outRec2 ...
                    outRec2.IsHole = outRec1.IsHole;
                    outRec1.IsHole = !outRec2.IsHole;
                    outRec2.FirstLeft = outRec1.FirstLeft;
                    outRec1.FirstLeft = outRec2;

                    //fixup FirstLeft pointers that may need reassigning to OutRec1
                    if (m_UsingPolyTree) {
                        fixupFirstLefts2(outRec1, outRec2);
                    }

                    if ((outRec1.IsHole ^ getReverseSolution()) == (area(outRec1) > 0)) {
                        reversePolyPtLinks(outRec1.Pts);
                    }
                } else {
                    //the 2 polygons are completely separate ...
                    outRec2.IsHole = outRec1.IsHole;
                    outRec2.FirstLeft = outRec1.FirstLeft;

                    //fixup FirstLeft pointers that may need reassigning to OutRec2
                    if (m_UsingPolyTree) {
                        fixupFirstLefts1(outRec1, outRec2);
                    }
                }

            } else {
                //joined 2 polygons together ...

                outRec2.Pts = null;
                outRec2.BottomPt = null;
                outRec2.Idx = outRec1.Idx;

                outRec1.IsHole = holeStateRec.IsHole;
                if (holeStateRec == outRec2) {
                    outRec1.FirstLeft = outRec2.FirstLeft;
                }
                outRec2.FirstLeft = outRec1;

                //fixup FirstLeft pointers that may need reassigning to OutRec1
                if (m_UsingPolyTree) {
                    fixupFirstLefts2(outRec2, outRec1);
                }
            }
        }
    }
    
    //------------------------------------------------------------------------------
      
    
      private void updateOutPtIdxs(OutRec outrec)
      {  
        OutPt op = outrec.Pts;
        do
        {
          op.Idx = outrec.Idx;
          op = op.Prev;
        }
        while(op != outrec.Pts);
      }
      //------------------------------------------------------------------------------

      private void doSimplePolygons()
      {
        int i = 0;
        while (i < m_PolyOuts.size()) 
        {
          OutRec outrec = m_PolyOuts.get(i++);
          OutPt op = outrec.Pts;
          if (op == null || outrec.IsOpen) continue;
          do //for each Pt in Polygon until duplicate found do ...
          {
            OutPt op2 = op.Next;
            while (op2 != outrec.Pts) 
            {
              if ((op.iPt.equals(op2.iPt)) && op2.Next != op && op2.Prev != op) 
              {
                //split the polygon into two ...
                OutPt op3 = op.Prev;
                OutPt op4 = op2.Prev;
                op.Prev = op4;
                op4.Next = op;
                op2.Prev = op3;
                op3.Next = op2;

                outrec.Pts = op;
                OutRec outrec2 = createOutRec();
                outrec2.Pts = op2;
                updateOutPtIdxs(outrec2);
                if (poly2ContainsPoly1(outrec2.Pts, outrec.Pts))
                {
                  //OutRec2 is contained by OutRec1 ...
                  outrec2.IsHole = !outrec.IsHole;
                  outrec2.FirstLeft = outrec;
                  if (m_UsingPolyTree) fixupFirstLefts2(outrec2, outrec);
                }
                else
                  if (poly2ContainsPoly1(outrec.Pts, outrec2.Pts))
                {
                  //OutRec1 is contained by OutRec2 ...
                  outrec2.IsHole = outrec.IsHole;
                  outrec.IsHole = !outrec2.IsHole;
                  outrec2.FirstLeft = outrec.FirstLeft;
                  outrec.FirstLeft = outrec2;
                  if (m_UsingPolyTree) fixupFirstLefts2(outrec, outrec2);
                }
                  else
                {
                  //the 2 polygons are separate ...
                  outrec2.IsHole = outrec.IsHole;
                  outrec2.FirstLeft = outrec.FirstLeft;
                  if (m_UsingPolyTree) fixupFirstLefts1(outrec, outrec2);
                }
                op2 = op; //ie get ready for the next iteration
              }
              op2 = op2.Next;
            }
            op = op.Next;
          }
          while (op != outrec.Pts);
        }
      }
      //------------------------------------------------------------------------------

      public static double area(Path poly)
      {
        int cnt = (int)poly.size();
        if (cnt < 3) return 0;
        double a = 0;
        for (int i = 0, j = cnt - 1; i < cnt; ++i)
        {
          a += (poly.get(j).X + poly.get(i).X) * (poly.get(j).Y - poly.get(i).Y);
          j = i;
        }
        return -a * 0.5;
      }
      //------------------------------------------------------------------------------

      double area(OutRec outRec)
      {
        OutPt op = outRec.Pts;
        if (op == null) return 0;
        double a = 0;
        do {
          a = a + (double)(op.Prev.iPt.X + op.iPt.X) * (double)(op.Prev.iPt.Y - op.iPt.Y);
          op = op.Next;
        } while (op != outRec.Pts);
        return a * 0.5;
      }

      //------------------------------------------------------------------------------
      // SimplifyPolygon functions ...
      // Convert self-intersecting polygons into simple polygons
      //------------------------------------------------------------------------------

      public static Paths simplifyPolygon(Path poly) throws ClipperException
      {
          return simplifyPolygon(poly, PolyFillType.pftEvenOdd);
      }

      
      public static Paths simplifyPolygon(Path poly, 
            PolyFillType fillType) throws ClipperException
      {
          Paths result = new Paths();
          Clipper c = new Clipper(0);
          c.setStrictlySimple(true);
          c.addPath(poly, PolyType.ptSubject, true);
          c.execute(ClipType.ctUnion, result, fillType, fillType);
          return result;
      }
      //------------------------------------------------------------------------------

      public static Paths simplifyPolygons(Paths polys) throws ClipperException
      {
          return simplifyPolygons(polys, PolyFillType.pftEvenOdd);
      }      
      
      public static Paths simplifyPolygons(Paths polys,
          PolyFillType fillType) throws ClipperException
      {
          Paths result = new Paths();
          Clipper c = new Clipper(0);
          c.setStrictlySimple(true);
          c.addPaths(polys, PolyType.ptSubject, true);
          c.execute(ClipType.ctUnion, result, fillType, fillType);
          return result;
      }
      
      //------------------------------------------------------------------------------
    
      private static double distanceFromLineSqrd(Point2d pt, Point2d ln1, Point2d ln2)
      {
        //The equation of a line in general form (Ax + By + C = 0)
        //given 2 points (x¹,y¹) & (x²,y²) is ...
        //(y¹ - y²)x + (x² - x¹)y + (y² - y¹)x¹ - (x² - x¹)y¹ = 0
        //A = (y¹ - y²); B = (x² - x¹); C = (y² - y¹)x¹ - (x² - x¹)y¹
        //perpendicular distance of point (x³,y³) = (Ax³ + By³ + C)/Sqrt(A² + B²)
        //see http://en.wikipedia.org/wiki/Perpendicular_distance
        double A = ln1.Y - ln2.Y;
        double B = ln2.X - ln1.X;
        double C = A * ln1.X  + B * ln1.Y;
        C = A * pt.X + B * pt.Y - C;
        return (C * C) / (A * A + B * B);
      }
      //---------------------------------------------------------------------------

      private static boolean slopesNearCollinear(Point2d pt1, 
          Point2d pt2, Point2d pt3, double distSqrd)
      {
        //this function is more accurate when the point that's GEOMETRICALLY 
        //between the other 2 points is the one that's tested for distance.  
        //nb: with 'spikes', either pt1 or pt3 is geometrically between the other pts                    
        if (Math.abs(pt1.X - pt2.X) > Math.abs(pt1.Y - pt2.Y))
	      {
          if ((pt1.X > pt2.X) == (pt1.X < pt3.X))
            return distanceFromLineSqrd(pt1, pt2, pt3) < distSqrd;
          else if ((pt2.X > pt1.X) == (pt2.X < pt3.X))
            return distanceFromLineSqrd(pt2, pt1, pt3) < distSqrd;
		      else
	          return distanceFromLineSqrd(pt3, pt1, pt2) < distSqrd;
	      }
	      else
	      {
          if ((pt1.Y > pt2.Y) == (pt1.Y < pt3.Y))
            return distanceFromLineSqrd(pt1, pt2, pt3) < distSqrd;
          else if ((pt2.Y > pt1.Y) == (pt2.Y < pt3.Y))
            return distanceFromLineSqrd(pt2, pt1, pt3) < distSqrd;
		      else
            return distanceFromLineSqrd(pt3, pt1, pt2) < distSqrd;
	      }
      }
      //------------------------------------------------------------------------------

      private static boolean pointsAreClose(Point2d pt1, Point2d pt2, double distSqrd)
      {
          double dx = (double)pt1.X - pt2.X;
          double dy = (double)pt1.Y - pt2.Y;
          return ((dx * dx) + (dy * dy) <= distSqrd);
      }
      //------------------------------------------------------------------------------

      private static OutPt excludeOp(OutPt op)
      {
        OutPt result = op.Prev;
        result.Next = op.Next;
        op.Next.Prev = result;
        result.Idx = 0;
        return result;
      }
      //------------------------------------------------------------------------------

      public static Path cleanPolygon(Path path)
      {
          return cleanPolygon (path,  1.415);
      }
            
      public static Path cleanPolygon(Path path, double distance)
      {
        //distance = proximity in units/pixels below which vertices will be stripped. 
        //Default ~= sqrt(2) so when adjacent vertices or semi-adjacent vertices have 
        //both x & y coords within 1 unit, then the second vertex will be stripped.

        int cnt = path.size();

        if (cnt == 0) return new Path();

        OutPt [] outPts = new OutPt[cnt];
        for (int i = 0; i < cnt; ++i) outPts[i] = new OutPt();

        for (int i = 0; i < cnt; ++i)
        {
          outPts[i].iPt.assign(path.get(i));
          outPts[i].Next = outPts[(i + 1) % cnt];
          outPts[i].Next.Prev = outPts[i];
          outPts[i].Idx = 0;
        }

        double distSqrd = distance * distance;
        OutPt op = outPts[0];
        while (op.Idx == 0 && op.Next != op.Prev)
        {
          if (pointsAreClose(op.iPt, op.Prev.iPt, distSqrd))
          {
            op = excludeOp(op);
            cnt--;
          }
          else if (pointsAreClose(op.Prev.iPt, op.Next.iPt, distSqrd))
          {
            excludeOp(op.Next);
            op = excludeOp(op);
            cnt -= 2;
          }
          else if (slopesNearCollinear(op.Prev.iPt, op.iPt, op.Next.iPt, distSqrd))
          {
            op = excludeOp(op);
            cnt--;
          }
          else
          {
            op.Idx = 1;
            op = op.Next;
          }
        }

        if (cnt < 3) cnt = 0;
        Path result = new Path();
        for (int i = 0; i < cnt; ++i)
        {
          result.add(op.iPt);
          op = op.Next;
        }
        return result;
      }
      //------------------------------------------------------------------------------

      public static Paths cleanPolygons(Paths polys)
      {
          return cleanPolygons(polys, 1.415);
      }
            
      public static Paths cleanPolygons(Paths polys,
          double distance)
      {
        Paths result = new Paths();
        for (Path poly : polys) {
            result.add(cleanPolygon(poly, distance));
        }
        return result;
      }
      //------------------------------------------------------------------------------

      static Paths minkowski(Path pattern, Path path, boolean IsSum, boolean IsClosed)
      {
        int delta = (IsClosed ? 1 : 0);
        int polyCnt = pattern.size();
        int pathCnt = path.size();
        Paths result = new Paths();
        if (IsSum)
          for (int i = 0; i < pathCnt; i++)
          {
            Path p = new Path();
            for (final Point2d ip: pattern)
              p.add(new Point2d(path.get(i).X + ip.X, path.get(i).Y + ip.Y));
            result.add(p);
          }
        else
          for (int i = 0; i < pathCnt; i++)
          {
            Path p = new Path();
            for (final Point2d ip: pattern)
              p.add(new Point2d(path.get(i).X - ip.X, path.get(i).Y - ip.Y));
            result.add(p);
          }

        Paths quads = new Paths();
        for (int i = 0; i < pathCnt - 1 + delta; i++)
          for (int j = 0; j < polyCnt; j++)
          {
            Path quad = new Path();
            quad.add(result.get(i % pathCnt).get(j % polyCnt));
            quad.add(result.get((i + 1) % pathCnt).get(j % polyCnt));
            quad.add(result.get((i + 1) % pathCnt).get((j + 1) % polyCnt));
            quad.add(result.get(i % pathCnt).get((j + 1) % polyCnt));
            if (!orientation(quad)) quad.reverse();
            quads.add(quad);
          }
        return quads;
      }
      //------------------------------------------------------------------------------

      public static Paths minkowskiSum(Path pattern, Path path, boolean pathIsClosed) throws ClipperException
      {
        Paths paths = minkowski(pattern, path, true, pathIsClosed);
        Clipper c = new Clipper(0);
        c.addPaths(paths, PolyType.ptSubject, true);
        c.execute(ClipType.ctUnion, paths, PolyFillType.pftNonZero, PolyFillType.pftNonZero);
        return paths;
      }
      //------------------------------------------------------------------------------

      private static Path translatePath(Path path, Point2d delta) 
      {
        Path outPath = new Path();
        for (final Point2d pt : path) {
            outPath.add(new Point2d(pt.X + delta.X, pt.Y + delta.Y));
        }
        return outPath;
      }
      //------------------------------------------------------------------------------

      public static Paths minkowskiSum(Path pattern, Paths paths, boolean pathIsClosed) throws ClipperException
      {
        Paths solution = new Paths();
        Clipper c = new Clipper(0);
        for (int i = 0; i < paths.size(); ++i)
        {
          Paths tmp = minkowski(pattern, paths.get(i), true, pathIsClosed);
          c.addPaths(tmp, PolyType.ptSubject, true);
          if (pathIsClosed)
          {
            Path path = translatePath(paths.get(i), pattern.get(0));
            c.addPath(path, PolyType.ptClip, true);
          }
        }
        c.execute(ClipType.ctUnion, solution, 
          PolyFillType.pftNonZero, PolyFillType.pftNonZero);
        return solution;
      }
      //------------------------------------------------------------------------------

      public static Paths minkowskiDiff(Path poly1, Path poly2) throws ClipperException
      {
        Paths paths = minkowski(poly1, poly2, false, true);
        Clipper c = new Clipper(0);
        c.addPaths(paths, PolyType.ptSubject, true);
        c.execute(ClipType.ctUnion, paths, PolyFillType.pftNonZero, PolyFillType.pftNonZero);
        return paths;
      }
      //------------------------------------------------------------------------------

      enum NodeType { ntAny, ntOpen, ntClosed };

      public static Paths polyTreeToPaths(PolyTree polytree)
      {
        Paths result = new Paths();
        addPolyNodeToPaths(polytree, NodeType.ntAny, result);
        return result;
      }
      //------------------------------------------------------------------------------

      static void addPolyNodeToPaths(PolyNode polynode, NodeType nt, Paths paths)
      {
        boolean match = true;
        switch (nt)
        {
          case ntOpen: return;
          case ntClosed: match = !polynode.isOpen(); break;
          default: break;
        }

        if (polynode.m_polygon.size() > 0 && match)
          paths.add(polynode.m_polygon);
        for (PolyNode pn: polynode.getChilds())
          addPolyNodeToPaths(pn, nt, paths);
      }
      //------------------------------------------------------------------------------

      public static Paths openPathsFromPolyTree(PolyTree polytree)
      {
        Paths result = new Paths();
        for (int i = 0; i < polytree.getChildCount(); i++)
          if (polytree.getChilds().get(i).isOpen())
            result.add(polytree.getChilds().get(i).m_polygon);
        return result;
      }
      //------------------------------------------------------------------------------

      public static Paths closedPathsFromPolyTree(PolyTree polytree)
      {
        Paths result = new Paths();
        addPolyNodeToPaths(polytree, NodeType.ntClosed, result);
        return result;
      }
      //------------------------------------------------------------------------------
      
}
