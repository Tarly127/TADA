package utils.io;

import java.util.InputMismatchException;
import java.util.Scanner;

public class Input
{
    public static String nextLine()
    {
        Scanner sc = new Scanner(System.in);

        try
        {
            return sc.nextLine();
        }
        catch (InputMismatchException e)
        {
            return nextLine();
        }
    }

    public static double nextDouble()
    {
        Scanner sc = new Scanner(System.in);

        try
        {
            System.out.print("Please insert a double: ");
            return sc.nextDouble();
        }
        catch (InputMismatchException e)
        {
            return nextDouble();
        }
    }

    public static double nextDouble(String reqMsg)
    {
        Scanner sc = new Scanner(System.in);

        try
        {
            System.out.print(reqMsg + " ");
            return sc.nextDouble();
        }
        catch (InputMismatchException e)
        {
            return nextDouble();
        }
    }
}
