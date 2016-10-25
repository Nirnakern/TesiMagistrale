package com.dji.videostreamdecodingsample;

import android.graphics.Point;

/**
 * Created by Simone on 25/10/2016.
 */

public class Marker_percorso {
    public Marker_percorso(){}

    public Point coordinate = new Point();
    public int distanza=0;

    public String tostring(){
        String s ="coordinate:"+Integer.toString(coordinate.x)+"-"+Integer.toString(coordinate.y)+", distanza"+Integer.toString(distanza);
        return s;
    }

}
