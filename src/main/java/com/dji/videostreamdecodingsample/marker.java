package com.dji.videostreamdecodingsample;

/**
 * Created by Simone on 19/10/2016.
 */

public class marker {

    public marker(){}

    long x_sum=0;
    long y_sum=0;
    long num_sum=0;
    boolean max_set=false;
    int max=0;
    int min=0;
    boolean active_this_column =false;

    public void reset(){
        max_set=false;
        active_this_column =false;
    }

    public int compute_x(){

        return (int) (x_sum/num_sum);
    }

    public int compute_y(){

        return (int) (y_sum/num_sum);
    }


}
