package com.xingcloud.nba.mr.mapper;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;

import java.io.IOException;

/**
 * Created by wanghaixing on 14-7-29.
 */
public class ActiveMapper extends Mapper<LongWritable, Text, Text, Text> {

    protected void map(LongWritable key, Text value, Context context) throws IOException,InterruptedException {
//        String pathName = ((FileSplit)context.getInputSplit()).getPath().toString();
//        if(pathName.contains("-r-")) {
            context.write(value, new Text(""));
//        }
    }

}

