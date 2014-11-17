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

public class Point2d {
    public long X;
    public long Y;

    public Point2d() {
        X = 0;
        Y = 0;
    }

    public Point2d(long x, long y) {
        X = x;
        Y = y;
    }

    public Point2d(Point2d p) {
        X = p.X;
        Y = p.Y;
    }

    public boolean equals(Point2d p) {
        return p != null &&
                p.X == this.X &&
                p.Y == this.Y;
    }

    public boolean not_equals(Point2d p) {
        return !this.equals(p);
    }

    public void assign(Point2d p) {
        X = p.X;
        Y = p.Y;
    }

    @Override
    public Point2d clone() {
        return new Point2d(this);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        Point2d other = (Point2d) obj;
        if (X != other.X)
            return false;
        if (Y != other.Y)
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 83 * hash + (int) (this.X ^ (this.X >>> 32));
        hash = 83 * hash + (int) (this.Y ^ (this.Y >>> 32));
        return hash;
    }

}
