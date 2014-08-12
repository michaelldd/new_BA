package com.xingcloud.nba.mr.job;

import com.xingcloud.nba.utils.Constant;
import com.xingcloud.nba.utils.DateManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.io.compress.Lz4Codec;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.chain.ChainMapper;
import org.apache.hadoop.mapreduce.lib.chain.ChainReducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Created by Administrator on 14-8-7.
 */
public class BeUiniqJob2 implements Runnable {
    private static Log LOG = LogFactory.getLog(AnalyzeJob.class);
    private static String fixPath = "hdfs://ELEX-LA-WEB1:19000/user/hadoop/";

    private String date1;
    private String specialTask;
    private List<String> projects;
    private String inputPath;
    private String outputPath;
    private int type;   //去重类型0:当天注册用户uid去重, 1:后6天的的uid去重
    private long count;

    public BeUiniqJob2(String specialTask, List<String> projects, int type) {
        this.specialTask = specialTask;
        this.projects = projects;
        this.type = type;
        this.date1 = DateManager.getDaysBefore(6, 0);
        if(Constant.DAY_UNIQ == type) {
            this.inputPath = fixPath + "whx/transuid2/" + date1 + "/" + specialTask + "/";
            this.outputPath = fixPath + "whx/temp/day/" + specialTask + "/" + date1 + "/";
        } else if(Constant.WEEK_UNIQ == type) {
            this.inputPath = fixPath + "offline/uid/" + specialTask + "/";
            this.outputPath = fixPath + "offline/retuid/week/" + specialTask + "/" + date1 + "/";
        }
    }

    public void run() {
        try {
            Configuration conf = new Configuration();
            Job job = new Job(conf, "BeUiniq_" + specialTask);
            conf.set("mapred.map.child.java.opts", "-Xmx1024m");
            conf.set("mapred.reduce.child.java.opts", "-Xmx1024m");
            conf.set("io.sort.mb", "64");
            conf.setBoolean("mapred.compress.map.output", true);
            conf.setClass("mapred.map.output.compression.codec",Lz4Codec.class, CompressionCodec.class);

            String inPath = "";
            if(Constant.DAY_UNIQ == type) {
                for(String project : projects) {
                    inPath = inputPath + project + "/";
                    FileInputFormat.addInputPaths(job, inPath);
                }
            } else if(Constant.WEEK_UNIQ == type) {
                String date2 = "";
                for(int i = 1; i <= 6; i++) {
                    date2 = DateManager.getDaysBefore(i, 1);
                    inPath = inputPath + date2 + "/";
                    FileInputFormat.addInputPaths(job, inPath);
                }
            }

            final FileSystem fileSystem = FileSystem.get(new URI(outputPath), conf);
            if(fileSystem.exists(new Path(outputPath))) {
                fileSystem.delete(new Path(outputPath), true);
            }

            Configuration mapconf1 = new Configuration(false);
            ChainMapper.addMapper(job, BeUiniqMapper1.class, LongWritable.class, Text.class, Text.class, Text.class, mapconf1);
            Configuration reduceconf = new Configuration(false);
            ChainReducer.setReducer(job, BeUiniqReducer.class, Text.class, Text.class, Text.class, Text.class, reduceconf);
            Configuration mapconf2 = new Configuration(false);
            ChainReducer.addMapper(job, BeUiniqMapper2.class, Text.class, Text.class, Text.class, NullWritable.class, mapconf2);

            job.setInputFormatClass(TextInputFormat.class);
//            job.setMapperClass(BeUiniqMapper.class);
//            job.setMapOutputKeyClass(Text.class);
//            job.setMapOutputValueClass(Text.class);

//            job.setReducerClass(BeUiniqReducer.class);
            job.setNumReduceTasks(6);
            job.setOutputKeyClass(Text.class);
            job.setOutputValueClass(NullWritable.class);
            FileOutputFormat.setOutputPath(job, new Path(outputPath));
            job.setOutputFormatClass(TextOutputFormat.class);

            job.setJarByClass(BeUiniqJob2.class);
            job.waitForCompletion(true);

            /*Counters counters = job.getCounters();
            count = counters.findCounter("Register", "Regnum").getValue();*/
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static class BeUiniqMapper1 extends Mapper<LongWritable, Text, Text, Text> {
        protected void map(LongWritable key, Text value, Context context) throws IOException,InterruptedException {
            String[] items = value.toString().split("\t");
            context.write(new Text(items[0]), new Text(items[1]));
        }
    }

    static class BeUiniqReducer extends Reducer<Text, Text, Text, Text> {
        protected void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            Set<String> timeSet = new TreeSet<String>();
            for(Text value : values) {
                timeSet.add(value.toString());
            }
            context.write(key, new Text(timeSet.iterator().next()));
        }

    }

    static class BeUiniqMapper2 extends Mapper<Text, Text, Text, NullWritable> {
        protected void map(Text key, Text value, Context context) throws IOException,InterruptedException {
            String date2 = DateManager.getDaysBefore(6, 1);
            String items = value.toString();
            if(items.trim().startsWith(date2)) {
                context.write(key, NullWritable.get());
                context.getCounter("reg", "num").increment(1L);
            }
            if(items.startsWith("201408")){
                context.getCounter("regist",items.substring(0,8)).increment(1);
            }
        }
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }
}
