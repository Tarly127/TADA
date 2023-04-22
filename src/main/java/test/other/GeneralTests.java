package test.other;

import primitives.Processor;

import java.io.IOException;

import static test.other.TestAux.timeout;

public class GeneralTests
{
    public static void main(String[] args)
    {
        try
        {
            if (args.length >= 2)
            {
                Processor p = new Processor(args[0], args[1]);
            }
        }
        catch (IOException e)
        {
            System.out.println("Unable to create processor, exiting...");
        }
    }
}
