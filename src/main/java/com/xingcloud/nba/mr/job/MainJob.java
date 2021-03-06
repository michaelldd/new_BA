package com.xingcloud.nba.mr.job;

import com.xingcloud.nba.business.StoreResult;
import com.xingcloud.nba.utils.Constant;
import com.xingcloud.nba.utils.DateManager;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.cli.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.util.*;

/**
 * Created by wanghaixing on 14-7-31.
 */
public class MainJob {
    private static Log LOG = LogFactory.getLog(MainJob.class);
    private String mode;
    private static String allPath = "hdfs://ELEX-LA-WEB1:19000/";
    private static String storeFilePath = "/home/hadoop/wanghaixing/storeData.txt";

    Options buildOptions() {
        Options options = new Options();
        Option option;

        option = OptionBuilder.withDescription("Print this help message").withLongOpt("help").create("?");
        options.addOption(option);

        option = OptionBuilder.hasArg().withDescription("Select mode to run").withLongOpt("mode").create("mode");
        options.addOption(option);

        /*option = OptionBuilder.hasArg().withDescription("Input a specific date").withLongOpt("date").create();
        options.addOption(option);*/

        return options;
    }

    public boolean parseArgs(String[] args) throws ParseException {
        Options options = buildOptions();
        CommandLine cli = new GnuParser().parse(options, args);

        if (!cli.hasOption("mode")) {
            return false;
        } else {
            mode = cli.getOptionValue("mode");
        }

        /*if (!cli.hasOption("date")) {
            return false;
        } else {
            date = cli.getOptionValue("date");
            if (date.equals("current")) {
                date = DateManager.getDate();
            }
        }*/

        return true;
    }


    public static void main(String[] args) {
        try {
            MainJob mainJob = new MainJob();
            boolean isOk = mainJob.parseArgs(args);
            if (!isOk) {
                LOG.error("Args are wrong!");
                return;
            }
            if (mainJob.getMode().equals("analytics")) {
                mainJob.doAnalyze();
            } else if (mainJob.getMode().equals("writeToRedis")) {
                /*List<String> specialList = new ArrayList<String>();
                specialList.add("internet-1");
                specialList.add("internet-2");
                specialList.add("internet");
                mainJob.writeToRedis(specialList);*/
                mainJob.writePVToRedis();
            }

        } catch (Exception e) {
            LOG.error("All analysis failed!", e);
        }

    }

    private void doAnalyze() {
        try {
            List<String> specialList = new ArrayList<String>();
            specialList.add("internet-1");
            specialList.add("internet-2");
            Map<String, List<String>> specialProjectList = getSpecialProjectList();

            int ret1 = runProjectJob(specialList, specialProjectList);
            if(ret1 == 0) {
                runAnalyzeJob(specialList, specialProjectList);
            }
            runInternetJob(Constant.ACT_UNIQ);
            //所有的数据都生成完毕
            LOG.info("the raw uids all generated to /user/hadoop/offline/uid/................");

            long[][] activeCounts = new long[3][3]; //日、周、月活跃
            specialList.add("internet");
            for(int i = 0; i < 3; i++) {
                runActiveJob(specialList.get(i), activeCounts[i]);
                //将统计好的活跃量放入redis中
                new StoreResult(specialList.get(i)).storeActive(activeCounts[i]);
            }

    //------------------------------------------------------------------------------------------------------

            specialList.remove(2);
            if((runTransUidJob(specialList, specialProjectList) == 0)) {
                runRegUidJob(specialList, specialProjectList);
                LOG.info("the regist uids registerd have generated......");
            }
            specialList.add("internet");
            long[] newCounts = new long[3]; //新增用户量
            newCounts = runCalNewUserJob(specialList);
            for(int i = 0; i < 3; i++) {
                new StoreResult(specialList.get(i)).storeNewUserNum(newCounts[i]);
            }

            long[] retCounts = new long[3]; //周留存
            runBeUiniqJob(specialList);
            retCounts = runRetentionJob(specialList);
            for(int i = 0; i < 3; i++) {
                new StoreResult(specialList.get(i)).storeRetention(retCounts[i]);
            }

            long[] results = new long[6];
            results = runOneDayRetJob(specialList);

            long[][] retOneCounts = new long[3][2]; //2日和7日留存
            retOneCounts[0][0] = results[0];
            retOneCounts[0][1] = results[1];
            retOneCounts[1][0] = results[2];
            retOneCounts[1][1] = results[3];
            retOneCounts[2][0] = results[4];
            retOneCounts[2][1] = results[5];
            for(int i = 0; i < 3; i++) {
                new StoreResult(specialList.get(i)).storeOneDayRetention(retOneCounts[i]);
            }
            /*for(long l : retOneCounts) {
                System.out.print(l + "    ");
            }
            System.out.println();*/



    //------------------------------------------------------------------------------------------------------

            String date = DateManager.getDaysBefore(1, 0);
            String data = date + "\t" + activeCounts[0][0] + "\t" + activeCounts[0][1] + "\t" + activeCounts[0][2] + "\t" + activeCounts[1][0] + "\t" + activeCounts[1][1] + "\t" + activeCounts[1][2] + "\t"
                    + activeCounts[2][0] + "\t" + activeCounts[2][1] + "\t" + activeCounts[2][2] + "\t" + newCounts[0] + "\t" + newCounts[1] + "\t" + newCounts[2] + "\t" + retCounts[0] + "\t" + retCounts[1] + "\t"
                    + retCounts[2] + "\t" + retOneCounts[0][0] + "\t" + retOneCounts[0][1] + "\t" + retOneCounts[1][0] + "\t" + retOneCounts[1][1] + "\t" + retOneCounts[2][0] + "\t" + retOneCounts[2][1] +  "\r\n";
            storeToFile(data);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 分别将internet-1、internet-2中每个项目前一天的stream_log和对应的mysqlidmap进行连接操作获得原始uid
     * 每个项目一个job，多线程运行
     * @param specials internet-1等
     * @param specialProjectList 项目列表
     * @return
     */
    public int runProjectJob(List<String> specials, Map<String, List<String>> specialProjectList) {
        try {
            int projectNum = 0;
            for(String specialTask : specials) {
                List<String> projects = specialProjectList.get(specialTask);
                projectNum += projects.size();
            }
            Thread[] task = new Thread[projectNum];

            for(String specialTask : specials) {
                List<String> projects = specialProjectList.get(specialTask);
                int i = 0;
                for(String project : projects) {
                    Runnable r = new ProjectJob(specialTask, project);
                    task[i] = new Thread(r);
                    task[i].start();
                    i += 1;
                }
            }
            for(Thread t : task) {
                if(t != null) {
                    t.join();       //等待这些job运行完成，进行后续操作
                }
            }
            return 0;
        } catch (Exception e) {
            e.printStackTrace();
            LOG.error("runProjectJob job got exception!", e);
            return -1;
        }
    }

    /**
     * 分别对internet-1、internet-2中的前一天的所有项目的原始uid进行去重，结果放到/user/hadoop/offline/uid/中
     * @param specials internet-1等
     * @param specialProjectList 项目列表
     * @return
     */
    public int runAnalyzeJob(List<String> specials, Map<String, List<String>> specialProjectList) {
        try {
            int len = specials.size();
            Thread[] task = new Thread[len];
            int i = 0;
            for(String specialTask : specials) {
                List<String> projects = specialProjectList.get(specialTask);
                Runnable r = new AnalyzeJob(specialTask, projects);
                task[i] = new Thread(r);
                task[i].start();
                i += 1;
            }
            for(Thread t : task) {
                if(t != null) {
                    t.join();
                }
            }
            return 0;
        } catch (Exception e) {
            e.printStackTrace();
            LOG.error("runAnalyzeJob job got exception!", e);
            return -1;
        }
    }

    /**
     * 对internet-1和internet-2中的前一天的所有项目原始uid一起去重，生成internet里的原始uid,
     * 因为internet-1和internet-2中的项目加起来是internet中的项目
     */
    public long runInternetJob(int type) {
        try {
            Runnable r = new InternetJob(type);
            Thread t = new Thread(r);
            t.start();
            t.join();
            return ((InternetJob)r).getCount();
        } catch (InterruptedException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * 分别统计internet、internet-1和internet-2中的日活跃量、周活跃量、月活跃量
     * @param specialTask
     * @param counts
     */
    public void runActiveJob(String specialTask, long[] counts) {
        Thread[] task = new Thread[3];
        Runnable[] aj = new Runnable[3];
        try {
            for(int i = 0; i < 3; i++) {
                aj[i] = new ActiveJob(specialTask, i + 1);
                task[i] = new Thread(aj[i]);
                task[i].start();
            }
            for(int i = 0; i < 3; i++) {
                if(task[i] != null) {
                    task[i].join();
                    counts[i] = ((ActiveJob)aj[i]).getCount();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            LOG.error("runActiveJob job got exception!", e);
        }
    }

    public static int runTransUidJob(List<String> specials, Map<String, List<String>> specialProjectList) {
        try {
            int projectNum = 0;
            for(String specialTask : specials) {
                List<String> projects = specialProjectList.get(specialTask);
                projectNum += projects.size();
            }
            Thread[] task = new Thread[projectNum];
            for(String specialTask : specials) {
                List<String> projects = specialProjectList.get(specialTask);
                int i = 0;
                for(String project : projects) {
                    Runnable r = new TransUidJob(specialTask, project);
                    task[i] = new Thread(r);
                    task[i].start();
                    i += 1;
                }
            }
            for(Thread t : task) {
                if(t != null) {
                    t.join();       //等待这些job运行完成，进行后续操作
                }
            }
            return 0;
        } catch (Exception e) {
            e.printStackTrace();
            LOG.error("runTransUidJob job got exception!", e);
            return -1;
        }
    }

    /**
     * 分别将internet-1、internet-2中每个项目前一天的stream_log和对应的mysqlidmap进行连接操作获得原始uid
     * 每个项目一个job，多线程运行
     * @param specials
     * @param specialProjectList
     * @return
     */
    public void runRegUidJob(List<String> specials, Map<String, List<String>> specialProjectList) {
        int len = specials.size();
        Thread[] task = new Thread[len];
        Runnable[] bj = new Runnable[len];
        try {
            for(int i = 0; i < len; i++) {
                String specialTask = specials.get(i);
                List<String> projects = specialProjectList.get(specialTask);
                bj[i] = new RegUidJob(specialTask, projects);
                task[i] = new Thread(bj[i]);
                task[i].start();
            }
            for(int i = 0; i < len; i++) {
                if(task[i] != null) {
                    task[i].join();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            LOG.error("runRegUidJob job got exception!", e);
        }
    }

    public long[] runCalNewUserJob(List<String> specials) {
        long[] newCounts = new long[3];
        int len = specials.size();
        Thread[] task = new Thread[len];
        Runnable[] cnu = new Runnable[len];
        try {
            for(int i = 0; i < len; i++) {
                String specialTask = specials.get(i);
                if(specialTask.equals("internet")) {
                    cnu[i] = new CalcInternetJob();
                    task[i] = new Thread(cnu[i]);
                    task[i].start();
                } else {
                    cnu[i] = new CalcNewUserJob(specialTask);
                    task[i] = new Thread(cnu[i]);
                    task[i].start();
                }
            }
            for(int i = 0; i < len; i++) {
                if(task[i] != null) {
                    task[i].join();
                    if(i == 2) {
                        newCounts[i] = ((CalcInternetJob)cnu[i]).getCount();
                    } else {
                        newCounts[i] = ((CalcNewUserJob)cnu[i]).getCount();
                    }
                }
            }
            return  newCounts;
        } catch (Exception e) {
            e.printStackTrace();
            LOG.error("runCalNewUserJob job got exception!", e);
            return null;
        }
    }

    public void runBeUiniqJob(List<String> specials) {
        int len = specials.size();
        Thread[] task = new Thread[len];
        Runnable[] bj = new Runnable[len];
        try {
            for(int i = 0; i < len; i++) {
                String specialTask = specials.get(i);
                bj[i] = new BeUniqJob(specialTask);
                task[i] = new Thread(bj[i]);
                task[i].start();
            }
            for(int i = 0; i < len; i++) {
                if(task[i] != null) {
                    task[i].join();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            LOG.error("runBeUiniqJob job got exception!", e);
        }

    }

    public long[] runRetentionJob(List<String> specials) {
        long[] retCounts = new long[3];
        int len = specials.size();
        Thread[] task = new Thread[len];
        Runnable[] rj = new Runnable[len];
        try {
            for(int i = 0; i < len; i++) {
                String specialTask = specials.get(i);
                rj[i] = new RetentionJob(specialTask);
                task[i] = new Thread(rj[i]);
                task[i].start();
            }
            for(int i = 0; i < len; i++) {
                if(task[i] != null) {
                    task[i].join();
                    retCounts[i] = ((RetentionJob)rj[i]).getCount();
                }
            }
            return  retCounts;
        } catch (Exception e) {
            e.printStackTrace();
            LOG.error("runRetentionJob job got exception!", e);
            return null;
        }
    }

    public long[] runOneDayRetJob(List<String> specials) {
        long[] retCounts = new long[6];
        int[] types = {Constant.TWO_RET, Constant.SEVEN_RET};
        int len = specials.size();
        Thread[] task = new Thread[len * 2];
        Runnable[] rj = new Runnable[len * 2];
        try {
            for(int i = 0; i < len; i++) {
                String specialTask = specials.get(i);
                for(int j = 0; j < 2; j++) {
                    rj[2*i + j] = new OneDayRetJob(specialTask, types[j]);
                    task[2*i + j] = new Thread(rj[2*i + j]);
                    task[2*i + j].start();
                }
            }
            for(int i = 0; i < len*2; i++) {
                if(task[i] != null) {
                    task[i].join();
                    retCounts[i] = ((OneDayRetJob)rj[i]).getCount();
                }
            }
            return  retCounts;
        } catch (Exception e) {
            e.printStackTrace();
            LOG.error("runOneDayRetJob job got exception!", e);
            return null;
        }
    }

    public static Map<String, List<String>> getSpecialProjectList() throws Exception {
        Map<String, List<String>> projectList = new HashMap<String, List<String>>();
        File file = new File("/home/hadoop/ba/BA/conf/specialtask");
        String json = "";
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line = null;
            while ((line = reader.readLine()) != null) {
                json += line;
            }
            JSONArray jsonArray = JSONArray.fromObject(json);
            for (Object object : jsonArray) {
                JSONObject jsonObj = (JSONObject) object;
                String project = jsonObj.getString("project");
                String[] projects = jsonObj.getString("members").split(",");
                List<String> memberList = new ArrayList<String>();
                for (String member : projects) {
                    String kv[] = member.split(":");
                    memberList.add(kv[0]);
                }
                projectList.put(project, memberList);
            }
        } catch (Exception e) {
            throw new Exception("parse the json(/home/hadoop/ba/BA/conf/specialtask) " + json + " get exception "  + e.getMessage());
        }
        return projectList;
    }

    /**
     * 将每天计算的结果保存到本地
     * 数据包括日、周、月活跃量；每日新增用户数；(周留存)
     */
    public void storeToFile(String data) {
        /*Configuration conf = new Configuration();
        FileSystem fileSystem = FileSystem.get(new URI(allPath), conf);
        FSDataInputStream in = fileSystem.open(new Path(storeFilePath));
        InputStreamReader isReader = new InputStreamReader(in);
        BufferedReader fr = new BufferedReader(isReader);
        StringBuffer sb=new StringBuffer();
        sb.append("2014-08-12" + "\t" +	"14669314" + "\t" +	"28976068" + "\t" +	"49192976" + "\t" +	"19514712" + "\t" +	"30998487" + "\t" + "45183872" + "\t" + "27966971" + "\t" + "45636142" + "\t" + "66915509" + "\r\n");
        String temp = "";
        while((temp = fr.readLine()) != null) {
            sb.append(temp);
            sb.append("\r\n");
        }
        in.close();
        if(fileSystem.exists(new Path(storeFilePath))) {
            fileSystem.delete(new Path(storeFilePath), true);
        }
        FSDataOutputStream out = fileSystem.create(new Path("/user/whx/storeData1.txt"));
        out.write(sb.toString().getBytes("utf-8"));
        out.close();*/


        FileInputStream in = null;
        FileOutputStream out = null;
        try {
            in = new FileInputStream(new File(storeFilePath));
            InputStreamReader isReader = new InputStreamReader(in);
            BufferedReader fr = new BufferedReader(isReader);
            StringBuffer sb=new StringBuffer(data);
//            sb.append("2014-08-14" + "\t" +	"15380085" + "\t" +	"29118482" + "\t" +	"49172388" + "\t" +	"19846028" + "\t" +	"30768834" + "\t" + "45398978" + "\t" + "28805295" + "\t" + "45703594" + "\t" + "67068383" + "\r\n");
            String temp = "";
            int n = 0;
            while((temp = fr.readLine()) != null) {
                sb.append(temp);
                sb.append("\r\n");
                if(n++ == 89) { //保留30天的数据
                    break;
                }
            }
            out = new FileOutputStream(new File(storeFilePath));
            out.write(sb.toString().getBytes("utf-8"));
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                in.close();
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void writePVToRedis() {
        new StoreResult().storePV();
    }

    /**
     * 准备从脚本中加参数执行
     */
    public void writeToRedis(List<String> specialList) {
        FileInputStream in = null;
        try {
            in = new FileInputStream(new File(storeFilePath));
            InputStreamReader isReader = new InputStreamReader(in);
            BufferedReader fr = new BufferedReader(isReader);
            String today = fr.readLine();
            String[] datas = today.split("\t");

            long[][] activeCounts = new long[3][3];
            activeCounts[0][0] = Long.parseLong(datas[1]);
            activeCounts[0][1] = Long.parseLong(datas[2]);
            activeCounts[0][2] = Long.parseLong(datas[3]);
            activeCounts[1][0] = Long.parseLong(datas[4]);
            activeCounts[1][1] = Long.parseLong(datas[5]);
            activeCounts[1][2] = Long.parseLong(datas[6]);
            activeCounts[2][0] = Long.parseLong(datas[7]);
            activeCounts[2][1] = Long.parseLong(datas[8]);
            activeCounts[2][2] = Long.parseLong(datas[9]);
            for(int i = 0; i < 3; i++) {
                //将统计好的活跃量放入redis中
                new StoreResult(specialList.get(i)).storeActive(activeCounts[i]);
            }

            long[] newCounts = new long[3];
            newCounts[0] = Long.parseLong(datas[10]);
            newCounts[1] = Long.parseLong(datas[11]);
            newCounts[2] = Long.parseLong(datas[12]);
            for(int i = 0; i < 3; i++) {
                new StoreResult(specialList.get(i)).storeNewUserNum(newCounts[i]);
            }

            long[] retCounts = new long[3];
            retCounts[0] = Long.parseLong(datas[13]);
            retCounts[1] = Long.parseLong(datas[14]);
            retCounts[2] = Long.parseLong(datas[15]);
            for(int i = 0; i < 3; i++) {
                new StoreResult(specialList.get(i)).storeRetention(retCounts[i]);
            }

            long[][] retOneCounts = new long[3][2]; //2日和7日留存
            retOneCounts[0][0] = Long.parseLong(datas[16]);
            retOneCounts[0][1] = Long.parseLong(datas[17]);
            retOneCounts[1][0] = Long.parseLong(datas[18]);
            retOneCounts[1][1] = Long.parseLong(datas[19]);
            retOneCounts[2][0] = Long.parseLong(datas[20]);
            retOneCounts[2][1] = Long.parseLong(datas[21]);
            for(int i = 0; i < 3; i++) {
                new StoreResult(specialList.get(i)).storeOneDayRetention(retOneCounts[i]);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public String getMode() { return mode; }

    public void setMode(String mode) {
        this.mode = mode;
    }

}
