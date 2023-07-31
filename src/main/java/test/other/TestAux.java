package test.other;

import utils.prof.ConsensusMetrics;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

public class TestAux
{
    // sketchy estimate of how long the timeout should be
    // [(log10(epsilon/base) - 1) * log2(n) * 100] ms
    public static long timeout(int n)
    {
        return (long)( Math.max(1, - Math.log10(TestConsts.EPSILON / TestConsts.BASE) - 1) )
                * 100L * ((long)(Math.log(n)/Math.log(2)) - 1);
    }

    public static boolean byzantine(String myPort, String leaderPort, String totProcs, int maxFaultyProcs)
    {
        int _myPort = Integer.parseInt(myPort);
        int _leaderPort = Integer.parseInt(leaderPort);
        int _totProcs = Integer.parseInt(totProcs);

        return (_myPort - _leaderPort) >= (_totProcs - maxFaultyProcs);
    }



    public static void store(List<Double>        execTimes,
                             List<LocalDateTime> execWallTimes,
                             List<Double>        realTimestamp,
                             List<ConsensusMetrics> metrics,
                             String path)
    {


        if(execTimes.size() == metrics.size())
        {
            try
            {
                BufferedWriter bw = new BufferedWriter(new FileWriter(path));

                bw.write("Id;StartingWallTime;Texec;expectedH;realH;timedOutRounds;neededMessages;processedMessages;" +
                        "initialV;endingV;realTimeAtEnding\n");

                for (int i = 0; i < execTimes.size(); i++)
                {
                    bw.write(i + ";");
                    bw.write(execWallTimes.get(i).format(DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSSSSS")) + ";");
                    bw.write(execTimes.get(i) + ";");
                    bw.write((metrics.get(i).expectedH + 2)+ ";");
                    bw.write((metrics.get(i).realH + 2) + ";");
                    bw.write(metrics.get(i).timedOutRounds + ";");
                    bw.write(metrics.get(i).neededMsgs.get() + ";");
                    bw.write(metrics.get(i).processedMsgs.get() + ";");
                    bw.write(metrics.get(i).startingV + ";");
                    bw.write(metrics.get(i).finalV + ";");
                    bw.write(realTimestamp.get(i) + "");
                    bw.write("\n");
                }
                bw.flush();
                bw.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    public static void store(List<Double> execTimes,
                             List<LocalDateTime> execWallTimes,
                             List<ConsensusMetrics> metrics,
                             String path)
    {


        if(execTimes.size() == metrics.size())
        {
            try
            {
                BufferedWriter bw = new BufferedWriter(new FileWriter(path));

                bw.write("Id;StartingWallTime;Texec;expectedH;realH;timedOutRounds;neededMessages;processedMessages;" +
                        "initialV;endingV\n");

                for (int i = 0; i < execTimes.size(); i++)
                {
                    bw.write(i + ";");
                    bw.write(execWallTimes.get(i).format(DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSSSSS")) + ";");
                    bw.write(execTimes.get(i) + ";");
                    bw.write((metrics.get(i).expectedH + 2)+ ";");
                    bw.write((metrics.get(i).realH + 2) + ";");
                    bw.write(metrics.get(i).timedOutRounds + ";");
                    bw.write(metrics.get(i).neededMsgs.get() + ";");
                    bw.write(metrics.get(i).processedMsgs.get() + ";");
                    bw.write(metrics.get(i).startingV + ";");
                    bw.write(metrics.get(i).finalV + "");
                    bw.write("\n");
                }
                bw.flush();
                bw.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    public static void store(List<ConsensusMetrics> metrics,
                             String path)
    {
        try
        {
            BufferedWriter bw = new BufferedWriter(new FileWriter(path));

            bw.write("Id;Texec;expectedH;realH;timedOutRounds;neededMessages;processedMessages;initialV;endingV\n");

            var sortedMetrics = metrics.stream().sorted(Comparator.comparingInt(m -> m.reqID.internalID)).toList();

            for (var metric : sortedMetrics)
            {
                bw.write(metric.reqID.internalID + ";");
                bw.write((metric.texecNanos / 1000000.0) + ";");
                bw.write((metric.expectedH + 2) + ";");
                bw.write((metric.realH + 2) + ";");
                bw.write((metric.timedOutRounds) + ";");
                bw.write(metric.neededMsgs.get() + ";");
                bw.write(metric.processedMsgs.get() + ";");
                bw.write(metric.startingV + ";");
                bw.write(metric.finalV + "");
                bw.write("\n");
            }
            bw.flush();
            bw.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public static double getUptime()
    {
        try
        {
            Process proc = (new ProcessBuilder("cat", "/proc/uptime")).start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String unparsed_uptime = reader.readLine();

            if(unparsed_uptime != null)
                return Double.parseDouble(unparsed_uptime.split(" ")[1]);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        return 0.0;
    }

    public static void outputCpuUsage(double starttime, final String outputFilename)
    {
        try
        {
            // the script only works in linux systems
            if (System.getProperty("user.name").compareTo("gsd") == 0)
                (new ProcessBuilder(TestConsts.CPU_SCRIPT_DIR, "" + starttime))
                        .directory(new File(TestConsts.PROJECT_DIR))
                        .redirectOutput(new File(outputFilename))
                        .start()
                        .waitFor();
        }
        catch (IOException | InterruptedException e)
        {
            e.printStackTrace();
        }
    }
}
