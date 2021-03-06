package com.xingcloud.nba.model;

import java.math.BigDecimal;

/**
 * Author: liqiang
 * Date: 14-12-1
 * Time: 下午3:47
 */
public class CacheModel {

    private int userNum = 0;
    private int userTime = 0;
    private BigDecimal value = new BigDecimal(0);

    public CacheModel(){}

    public CacheModel(CacheModel cm){
        this.userTime = cm.userTime;
        this.userNum = cm.userNum;
        this.value = cm.value;
    }

    public int getUserNum() {
        return userNum;
    }

    public void setUserNum(int userNum) {
        this.userNum = userNum;
    }

    public int getUserTime() {
        return userTime;
    }

    public void setUserTime(int userTime) {
        this.userTime = userTime;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public void incrTime(){
        userTime++;
    }

    public void incrValue(BigDecimal v){
        value = value.add(v);
    }

    public void incrSameUser(BigDecimal v){
        userTime++;
        value = value.add(v);
    }

    public void incrSameUserInDifPro(CacheModel cm){
        this.userTime += cm.getUserTime();
        value = value.add(cm.getValue());
    }

    public void incrDiffUser(CacheModel cm){
        this.userTime += cm.getUserTime();
        this.userNum += cm.getUserNum();
        incrValue(cm.getValue());
    }

    public String toString() {
        return userNum + "#" + userTime + "#" + value;
    }
}
