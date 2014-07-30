package com.xingcloud.nba.mr.reducer;

import com.xingcloud.nba.mr.model.JoinData;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;


/**
 * Created by wanghaixing on 14-7-29.
 */
public class ActiveReducer extends Reducer<Text, JoinData, Text, NullWritable> {
    private static Log LOG = LogFactory.getLog(ActiveReducer.class);
    private Set<String> firstTable = new HashSet<String>();
    private Set<String> secondTable = new HashSet<String>();
    private Text secondPart = null;
    private Text output = new Text();

    protected void reduce(Text key, Iterable<JoinData> values, Context context) throws IOException, InterruptedException {
        firstTable.clear();
        secondTable.clear();

        for(JoinData jd : values) {
            secondPart = jd.getSecondPart();
            if("0".equals(jd.getFlag().toString().trim())) {
                firstTable.add(secondPart.toString());
            } else if("1".equals(jd.getFlag().toString().trim())) {
                secondTable.add(secondPart.toString());
            }
        }

        if(firstTable.size() == 0) {
            return;
        }

        for(String uid : firstTable) {
            for(String orgid : secondTable) {
                System.out.println(orgid);
                output.set(orgid);
                context.write(output, NullWritable.get());
            }
        }

    }
}
