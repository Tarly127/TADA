package utils.io;

public class PrettyPrintColours
{
    public static final String RED    = "\u001B[31m";
    public static final String BLUE   = "\u001B[34m";
    public static final String GREEN  = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String RESET  = "\u001B[0m";

    public static void printRed(String arg)
    {
        System.out.println(RED + arg + RESET);
    }

    public static void printRed(String arg, String end)
    {
        System.out.print(RED + arg + RESET + end);
    }

    public static void printBlue(String arg)
    {
        System.out.println(BLUE + arg + RESET);
    }

    public static void printBlue(String arg, String end)
    {
        System.out.print(BLUE + arg + RESET + end);
    }

    public static void printGreen(String arg)
    {
        System.out.println(GREEN + arg + RESET);
    }

    public static void printYellow(String arg, String end)
    {
        System.out.print(YELLOW + arg + RESET + end);
    }

    public static void printYellow(String arg)
    {
        System.out.println(YELLOW + arg + RESET);
    }

    public static void printGreen(String arg, String end)
    {
        System.out.print(GREEN + arg + RESET + end);
    }
}
