package com.dji.videostreamdecodingsample;

import java.util.ArrayList;

/**
 * Created by Simone on 28/11/2016.
 */
public class Mosse {

    ArrayList<String> mosse = new ArrayList<>();

    public Mosse(){

    }

    public void add(String mossa){
        mosse.add(mossa);

    }

    public boolean is_ok(){
        if (mosse.size()<3){
            return true;
        }else{
            if((mosse.get(mosse.size()-1).equals("left") && mosse.get(mosse.size()-2).equals("move")) || (mosse.get(mosse.size()-1).equals("left") && mosse.get(mosse.size()-2).equals("right"))){
                return false;
            }else{
                return true;
            }
        }
    }

}
