package com.xingcloud.nba.mr.job;

import com.xingcloud.nba.mr.inputformat.MyCombineFileInputFormat;
import com.xingcloud.nba.mr.mapper.AnalyzeMapper;
import com.xingcloud.nba.mr.reducer.AnalyzeReducer;
import com.xingcloud.nba.utils.DateManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.io.compress.Lz4Codec;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;

import java.net.URI;

/**
 * 去重and统计
 * Created by wanghaixing on 14-8-1.
 */
public class AnalyzeJob implements Runnable {
    private static Log LOG = LogFactory.getLog(AnalyzeJob.class);
    private static String fixPath = "hdfs://ELEX-LA-WEB1:19000/user/hadoop/";

    private String date;
    private String specialTask;
//    private String project;
    private String inputPath;
    private String outputPath;
//    private String deleteSUCCESSPath;
//    private String deleteLogPath;

    public AnalyzeJob(String specialTask) {
        this.specialTask = specialTask;
        this.date = DateManager.getDaysBefore(1, 0);        ///ex:2014-07-29
//        this.inputPath = fixPath + "offline/uid/" + specialTask + "/all/";
        this.inputPath = fixPath + "whx/uid/" + date + "/" + specialTask + "/";
        this.outputPath = fixPath + "offline/uid/" + specialTask + "/" + date + "/";
//        this.deleteSUCCESSPath = fixPath + "whx/uid/" + date + "/" + specialTask + "/";
//        this.deleteLogPath = fixPath + "whx/uid/" + date + "/" + specialTask + "/";
    }

    public void run() {
        try {
            Configuration conf = new Configuration();
            Job job = new Job(conf, "Analyze_" + specialTask);
            conf.set("mapred.max.split.size", "157286400");
            conf.set("mapred.map.child.java.opts", "-Xmx1024m");
            conf.set("mapred.reduce.child.java.opts", "-Xmx1024m");
            conf.set("io.sort.mb", "64");
            conf.setBoolean("mapred.compress.map.output", true);
            conf.setClass("mapred.map.output.compression.codec",Lz4Codec.class, CompressionCodec.class);
            clearFiles(conf);

            FileInputFormat.addInputPaths(job, inputPath);

            job.setInputFormatClass(MyCombineFileInputFormat.class);
            job.setMapperClass(AnalyzeMapper.class);
            job.setMapOutputKeyClass(Text.class);
            job.setMapOutputValueClass(Text.class);

//            job.setCombinerClass(AnalyzeReducer.class);
            job.setReducerClass(AnalyzeReducer.class);
            job.setNumReduceTasks(5);
            job.setOutputKeyClass(Text.class);
            job.setOutputValueClass(NullWritable.class);
            FileOutputFormat.setOutputPath(job, new Path(outputPath));
            job.setOutputFormatClass(TextOutputFormat.class);

            job.setJarByClass(AnalyzeJob.class);
            job.waitForCompletion(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void clearFiles(Configuration conf) {
        try {
            FileSystem fileSystem = FileSystem.get(new URI(inputPath), conf);
            if(fileSystem.exists(new Path(outputPath))) {
                fileSystem.delete(new Path(outputPath), true);
            }
            /*if(fileSystem.exists(new Path(deleteSUCCESSPath))) {
                fileSystem.delete(new Path(deleteSUCCESSPath), true);
            }
            if(fileSystem.exists(new Path(deleteLogPath))) {
                fileSystem.delete(new Path(deleteLogPath), true);
            }*/
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
