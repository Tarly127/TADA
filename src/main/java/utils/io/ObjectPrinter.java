package utils.io;

import java.util.List;
import java.util.Set;

public class ObjectPrinter
{

    public static void printArray(double[] arr)
    {
        if(arr.length > 0)
        {

            System.out.print("[");

            for (int i = 0; i < arr.length - 1; i++)
                System.out.print(arr[i] + ",");

            System.out.println(arr[arr.length - 1] + "];");
        }
        else System.out.println("[];");
    }

    public static void printArray(byte[] arr)
    {
        if(arr.length > 0)
        {
            System.out.print("[");

            for (int i = 0; i < arr.length - 1; i++)
                System.out.print(arr[i] + ",");

            System.out.println(arr[arr.length - 1] + "];");
        }
        else System.out.println("[];");
    }

    public static <T> void printArray(T[] arr)
    {
        if(arr.length > 0)
        {
            System.out.print("[");

            for (int i = 0; i < arr.length - 1; i++)
                System.out.print(arr[i] + ",");

            System.out.println(arr[arr.length - 1] + "];");
        }
        else System.out.println("[];");
    }

    public static <T> void printList(List<T> lst)
    {
        if(lst == null || lst.size() == 0)
        {
            System.out.println("[];");
        }
        else
        {
            System.out.print("[");

            for (int i = 0; i < lst.size() - 1; i++)
            {
                if(lst.get(i) == null) System.out.print("NULL,");
                else System.out.print(lst.get(i) + ",");
            }

            if(lst.get(lst.size() - 1) == null) System.out.println("NULL];");
            else System.out.print(lst.get(lst.size() - 1) + "];");
        }
    }

    public static <T> void printSet(Set<T> set)
    {
        if(set == null || set.size() == 0)
        {
            System.out.println("{};");
        }
        else
        {
            System.out.print("{");

            int i = set.size() - 1;

            for (T elem : set)
            {
                if (i == 0)
                {
                    if(elem == null) System.out.println("NULL};");
                    else System.out.println(elem + "};");
                }
                else
                {
                    if(elem == null) System.out.println("NULL,");
                    else System.out.print(elem + ",");
                }
                i--;
            }
        }
    }
}
