package utils.math;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class BSOFunctions extends Functions
{
    public static double delta(double[] V, boolean isSorted)
    {
        if(!isSorted)
            return V != null && V.length > 0 ?
                 Math.abs( Arrays.stream(V).max().getAsDouble() - Arrays.stream(V).min().getAsDouble() ) : 0;
        else
            return V != null && V.length > 0 ?
                   Math.abs( V[V.length - 1 ] - V[0] ) : 0;
    }

    // fill arrays with NULL, up to n, AND ALSO WITH OUR OWN VOTE!!!!
    public static Double[] fillWithNull(double[] V, double v, int n)
    {
        Double[] V1 = new Double[n];

        // add values for timed out processes
        for (int i = 0; i < n; i++)
        {
            if (i < V.length)
                V1[i] = V[i];
            else if (i == V.length)
                V1[i] = v;
            else
                V1[i] = null;
        }

        return V1;
    }

    // If there are more than o instances of NULL in V, replaces them with 0.0, returning the modified array, without
    // modifying the original
    public static Double[] replaceExcess(Double[] V, int o)
    {
        Double[] replaced = Arrays.copyOf(V, V.length);

        if (Arrays.stream(V).filter(Objects::isNull).count() > o)
        {
            var nonExcess = o - Arrays.stream(V).filter(Objects::isNull).count();

            for (int i = 0; i < V.length; i++)
            {
                if (nonExcess > 0 && V[i] == null)
                {
                    replaced[i] = 0.0;
                    nonExcess--;
                }
            }
        }

        return replaced;
    }

    // V_Result = V + V
    public static Double[] doubleArray(Double[] V)
    {
        if(V.length != 0)
        {
            Double[] doubled = new Double[V.length * 2];

            for(int i = 0; i < V.length; i++)
            {
                doubled[i * 2] = V[i]; doubled[i * 2 + 1] = V[i];
            }

            return doubled;
        }
        else return V;
    }

    public static Double[] reduce(Double[] V, long k)
    {
        if(k < 0)
            return V;

        if(V.length > 2 * (int)k + 1)
        {
            Double[] reduced = new Double[V.length - 2 * (int) k];
            System.arraycopy(V, (int) k, reduced, 0, V.length - 2 * (int) k);
            return reduced;
        }
        else
            return new Double[0];
    }

    // Select_k
    public static Double[] select(Double[] V, int k)
    {
        int selectSize = c(V.length, k);

        Double[] selected = new Double[Math.max(selectSize,0)];

        for(int i = 0; i < selected.length; i++)
            selected[i] = V[i * k];

        return selected;
    }

    // mean
    public static Double mean(Double[] V)
    {
        return Arrays.stream(V).filter(Objects::nonNull).reduce(0.0, Double::sum)
                / Arrays.stream(V).filter(Objects::nonNull).count();
    }

    // count the number of NULL instances in input array
    public static <T> long nillInstances(T[] V)
    {
        return Arrays.stream(V).filter(Objects::isNull).count();
    }

    // remove the first k instances of NULL from input array
    public static Double[] removeFirstKNill(Double[] V, long k)
    {
        List<Double> filtered = new ArrayList<>(V.length);
        int occurrencesRemoved = 0;

        for(var v : V)
            if(occurrencesRemoved < k && v == null)
                occurrencesRemoved++;
            else filtered.add(v);

        Double[] filteredArr = new Double[filtered.size()];

        for(int i = 0; i < filteredArr.length; i++)
            filteredArr[i] = filtered.get(i);

        return filteredArr;
    }

    // Calculate Number of Rounds (assumes V comes sorted in ascending order) for BSO
    public static int BSO_H(double[] V, int n, int b, int s, int o, double epsilon, boolean isSorted)
    {
        // initial precision
        double delta = delta(V, isSorted);

        // convergence rate (inverted)
        double k_inverted = Math.ceil( (n - 2.0 * b - 2.0 * s - o) / (b + o / 2.0) );

        return delta != 0 ?
               (int) Math.ceil( Math.log(epsilon / delta) / - Math.log(k_inverted) ) : 1;
    }
}
