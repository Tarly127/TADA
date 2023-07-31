package utils.math;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ApproximationFunctions
{
    // Reduce^k
    public static double[] reduce(double[] V, int k)
    {
        double[] reduced = new double[V.length - 2 * k];
        System.arraycopy(V, k, reduced, 0, V.length - 2 * k);

        return reduced;
    }

    // Select_k
    public static double[] select(double[] V, int k)
    {
        int selectSize = c(V.length, k);

        double[] selected = new double[selectSize];

        for(int i = 0; i < selected.length; i++)
            selected[i] = V[i * k];

        return selected;
    }

    // mean
    public static double mean(double[] V)
    {
        return Arrays.stream(V).average().orElse(Double.NaN);
    }

    // f = mean(select_k(reduce^t(V)))
    public static double f(double[] V, int k, int t)
    {
        return mean(select(reduce(V, t), k));
    }

    // median
    public static double median(double[] V)
    {
        if (V.length == 0)
            return 0.0;

        var sortedList = Arrays.stream(V).sorted().toArray();

        return sortedList.length % 2 == 0 ?
               (sortedList[sortedList.length / 2 - 1] + sortedList[sortedList.length / 2]) / 2.0 :
               sortedList[sortedList.length / 2];
    }

    // Calculate Number of Rounds (assumes V comes sorted in ascending order) for SynchDLPSW86
    public static int SynchH(double[] V, double epsilon, int n, int t)
    {
        double delta = V[n - 1] - V[0];

        int c = c(n - 2 * t, t);

        return delta == 0 ? 1 : Math.max((int)Math.ceil( Math.log(delta/epsilon) / Math.log(c) ), 1);
    }

    // Calculate Number of Rounds (assumes V comes sorted in ascending order) for AsyncDLPSW86
    public static int AsyncH(double[] V, double epsilon, int n, int t)
    {
        int c = c(n - 3 * t, 2 * t);

        double delta = V[V.length - 1] - V[0];

        return delta == 0 ? 1 : Math.max((int)Math.ceil( Math.log(delta/epsilon) / Math.log(c) ), 1);
    }

    // Calculate Number of Rounds (assumes V comes sorted in ascending order) for FCA and CCA
    public static int InexactH(double delta, double epsilon)
    {
        return delta == 0 ? 0 : (int) Math.ceil(Math.log( epsilon / delta ) / Math.log( 2.0/3.0 ));
    }

    public static double InexactDelta(double[] V)
    {
        var sortedList = Arrays.stream(V).sorted().toArray();

        return sortedList.length > 1 ? Math.abs(sortedList[sortedList.length - 1] - sortedList[0]) : 0;
    }

    public static double sortedDelta(double[] V)
    {
        return V.length > 1 ? Math.abs(V[V.length - 1] - V[0]) : 0.0;
    }

    public static double[] Acceptable(double[] V, double delta, int n, int t)
    {
        List<Double> acceptable = new ArrayList<>();

        for(double v : V)
            if(Arrays.stream(V).filter(other_v -> Math.abs(other_v - v) <= delta).count() >= n - t)
                acceptable.add(v);

        return acceptable.stream().mapToDouble(v -> v).toArray();
    }

    public static List<Double> Acceptable(List<Double> V, double delta, int n, int t)
    {
        List<Double> acceptable = new ArrayList<>();

        for(double v : V)
            if(V.stream().filter(other_v -> Math.abs(other_v - v) <= delta).count() >= n - t)
                acceptable.add(v);

        return acceptable;
    }

    public static double estimator(double[] V)
    {
        return mean(V);
    }

    public static double estimator(List<Double> V)
    {
        return V.stream().mapToDouble(v -> v).average().orElse(0.0);
    }

    public static int c(int m, int k) // c(m,k) = floor((m-1)/k) + 1
    {
        return (int) Math.floor((double) (m - 1) / (double) k) + 1;
    }
}
