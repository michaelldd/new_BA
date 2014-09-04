package com.xingcloud.nba.business;

import com.xingcloud.maincache.MapXCache;
import com.xingcloud.maincache.XCacheException;
import com.xingcloud.maincache.XCacheOperator;
import com.xingcloud.maincache.redis.RedisXCacheOperator;
import com.xingcloud.nba.utils.Constant;
import com.xingcloud.nba.utils.DateManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;

/**
 * Created by Administrator on 14-8-4.
 */
/**
 * COMMON,internet-1,2014-08-03,2014-08-03,visit.*,TOTAL_USER,VF-ALL-0-0,PERIOD 日活跃
 * COMMON,internet-1,2014-07-27,2014-08-03,visit.*,TOTAL_USER,VF-ALL-0-0,PERIOD 周活跃
 * COMMON,internet-1,2014-07-04,2014-08-03,visit.*,TOTAL_USER,VF-ALL-0-0,PERIOD 月活跃
 *
 * COMMON,internet-1,2014-08-02,2014-08-07,visit.*,{"register_time":{"$gte":"2014-08-01","$lte":"2014-08-01"}},VF-ALL-0-0,PERIOD    一周留存
 *
 * COMMON,internet-1,2014-08-04,2014-08-04,visit.*,{"register_time":{"$gte":"2014-08-04","$lte":"2014-08-04"}},VF-ALL-0-0,PERIOD    每日新增
 */
public class StoreResult {
    private static Log LOG = LogFactory.getLog(StoreResult.class);
    private String specialTask;

    public StoreResult(String specialTask) {
        this.specialTask = specialTask;
    }

    /**
     * 保存日、周、月活跃
     * @param counts
     */
    public void storeActive(long[] counts) {
        String[] dates = new String[3];
        dates[0] = DateManager.getDaysBefore(1, 0);     //2014-08-03
        dates[1] = DateManager.getDaysBefore(8, 0);     //2014-07-27
        dates[2] = DateManager.getDaysBefore(31, 0);    //2014-07-04
        List<String> keyList = new ArrayList<String>();
        String key = "";
        for(int i = 0; i < 3; i++) {
            key = "COMMON," + specialTask + "," + dates[i] + "," + dates[0] + ",visit.*,TOTAL_USER,VF-ALL-0-0,PERIOD";
            keyList.add(key);
            System.out.println(key);
        }

        Map<String, Number[]> result = null;
        MapXCache xCache = null;
        XCacheOperator xCacheOperator = RedisXCacheOperator.getInstance();
        try {
            for(int i = 0; i < 3; i++) {
                result = new HashMap<String, Number[]>();
                result.put(keyList.get(i), new Number[]{0, 0, counts[i], 1.0});
                xCache = MapXCache.buildMapXCache(keyList.get(i), result);
                xCacheOperator.putMapCache(xCache);
            }

        } catch (XCacheException e) {
            e.printStackTrace();
        }
    }

    /*public void storeActive(long[] counts) {
        String[] dates = new String[3];
        dates[0] = DateManager.getDaysBefore(1, 0);     //2014-08-03
        dates[1] = DateManager.getDaysBefore(8, 0);     //2014-07-27
        dates[2] = DateManager.getDaysBefore(31, 0);    //2014-07-04
        List<String> keyList = new ArrayList<String>();
        keyList.add("a");
        String key = "";
        for(int i = 1; i < 3; i++) {
            key = "COMMON," + specialTask + "," + dates[i] + "," + dates[0] + ",visit.*,TOTAL_USER,VF-ALL-0-0,PERIOD";
            keyList.add(key);
        }

        Map<String, Number[]> result = null;
        MapXCache xCache = null;
        XCacheOperator xCacheOperator = RedisXCacheOperator.getInstance();
        try {
            for(int i = 0; i < 3; i++) {
                result = new HashMap<String, Number[]>();
                result.put(keyList.get(i), new Number[]{0, 0, counts[i], 1.0});
                xCache = MapXCache.buildMapXCache(keyList.get(i), result);
                xCacheOperator.putMapCache(xCache);
            }

        } catch (XCacheException e) {
            e.printStackTrace();
        }
    }*/

    /**
     * 保存一周留存率
     * COMMON,internet-1,2014-08-08,2014-08-13,visit.*,{"register_time":{"$gte":"2014-08-07","$lte":"2014-08-07"}},VF-ALL-0-0,PERIOD
     * COMMON,internet-1,2014-08-07,2014-08-13,visit.*,{"register_time":{"$gte":"2014-08-06","$lte":"2014-08-06"}},VF-ALL-0-0,PERIOD
     * COMMON,internet-1,2014-08-06,2014-08-12,visit.*,{"register_time":{"$gte":"2014-08-05","$lte":"2014-08-05"}},VF-ALL-0-0,PERIOD
     * @param ret
     */
    public void storeRetention(long ret) {
        /*Map<String, Number[]> result = new HashMap<String, Number[]>();
        String key = "COMMON,internet-1,2014-08-07,2014-08-13,visit.*,{\"register_time\":{\"$gte\":\"2014-08-08\",\"$lte\":\"2014-08-08\"}},VF-ALL-0-0,PERIOD";
        result.put(key, new Number[]{0, 0, ret, 1.0});
        XCacheOperator xCacheOperator = RedisXCacheOperator.getInstance();
        try {
            MapXCache xCache = MapXCache.buildMapXCache(key, result);
            xCacheOperator.putMapCache(xCache);
        } catch (XCacheException e) {
            e.printStackTrace();
        }*/

        String date = DateManager.getDaysBefore(8, 0);     //该天的一周留存
        String beginDate = DateManager.getDaysBefore(7, 0);
        String endDate = DateManager.getDaysBefore(1, 0);
        Map<String, Number[]> result = null;
        MapXCache xCache = null;
        XCacheOperator xCacheOperator = RedisXCacheOperator.getInstance();
        String key = "";
        try {
            key = "COMMON," + specialTask + "," + beginDate + "," + endDate + ",visit.*,{\"register_time\":{\"$gte\":\"" + date + "\",\"$lte\":\"" + date + "\"}},VF-ALL-0-0,PERIOD";
            result = new HashMap<String, Number[]>();
            result.put(key, new Number[]{0, 0, ret, 1.0});
            xCache = MapXCache.buildMapXCache(key, result);
            xCacheOperator.putMapCache(xCache);
            System.out.println(key);
        } catch (XCacheException e) {
            e.printStackTrace();
        }

    }

    /**
     * 保存每日新增用户数
     * @param count
     */
    public void storeNewUserNum(long count) {
        String date = DateManager.getDaysBefore(1, 0);
        Map<String, Number[]> result = null;
        MapXCache xCache = null;
        XCacheOperator xCacheOperator = RedisXCacheOperator.getInstance();
        String key = "";
        try {
            key = "COMMON," + specialTask + "," + date + "," + date + ",visit.*,{\"register_time\":{\"$gte\":\"" + date + "\",\"$lte\":\"" + date + "\"}},VF-ALL-0-0,PERIOD";
            result = new HashMap<String, Number[]>();
            result.put(key, new Number[]{0, 0, count, 1.0});
            xCache = MapXCache.buildMapXCache(key, result);
            xCacheOperator.putMapCache(xCache);
            System.out.println(key);
        } catch (XCacheException e) {
            e.printStackTrace();
        }

    }

    /**
     * 保存2日留存
     * COMMON,internet-2,2014-08-21,2014-08-21,visit.*,{"register_time":{"$gte":"2014-08-20","$lte":"2014-08-20"}},VF-ALL-0-0,PERIOD
     * 保存7日留存
     * COMMON,internet-2,2014-08-21,2014-08-21,visit.*,{"register_time":{"$gte":"2014-08-15","$lte":"2014-08-15"}},VF-ALL-0-0,PERIOD
     */
    public void storeOneDayRetention(long[] counts) {
        String visitDate = DateManager.getDaysBefore(1, 0);
        String[] dates = new String[2];
        dates[0] = DateManager.getDaysBefore(2, 0);
        dates[1] = DateManager.getDaysBefore(7, 0);

        List<String> keyList = new ArrayList<String>();
        String key = "";
        for(int i = 0; i < 2; i++) {
            key = "COMMON," + specialTask + "," + visitDate + "," + visitDate + ",visit.*,{\"register_time\":{\"$gte\":\"" + dates[i] + "\",\"$lte\":\"" + dates[i] + "\"}},VF-ALL-0-0,PERIOD";
            keyList.add(key);
            System.out.println(key);
        }

        Map<String, Number[]> result = null;
        MapXCache xCache = null;
        XCacheOperator xCacheOperator = RedisXCacheOperator.getInstance();
        try {
            for(int i = 0; i < 2; i++) {
                result = new HashMap<String, Number[]>();
                result.put(keyList.get(i), new Number[]{0, 0, counts[i], 1.0});
                xCache = MapXCache.buildMapXCache(keyList.get(i), result);
                xCacheOperator.putMapCache(xCache);
            }

        } catch (XCacheException e) {
            e.printStackTrace();
        }
    }

    /**
     * test
     * @param counts
     */
    public static void testStore(long counts) {
        /*String date1 = DateManager.getDaysBefore(1, 0);
        String date2 = DateManager.getDaysBefore(8, 0);
        String key = "COMMON," + specialTask + "," + date1 + "," + date1 + ",visit.*,TOTAL_USER,VF-ALL-0-0,PERIOD";*/

        Map<String, Number[]> result = null;
        MapXCache xCache = null;
        String key = "COMMON,internet,2014-09-01,2014-09-01,visit.*,{\"register_time\":{\"$gte\":\"2014-09-01\",\"$lte\":\"2014-09-01\"}},VF-ALL-0-0,PERIOD";
        XCacheOperator xCacheOperator = RedisXCacheOperator.getInstance();
        try {
                result = new HashMap<String, Number[]>();
                result.put(key, new Number[]{0, 0, counts, 1.0});
                xCache = MapXCache.buildMapXCache(key, result);
                xCacheOperator.putMapCache(xCache);
        } catch (XCacheException e) {
            e.printStackTrace();
        }
    }

}
