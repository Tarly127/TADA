package test.other;

import java.util.concurrent.TimeUnit;

public class TestConsts
{
    protected static final double BASE   = 5.0;
    public static final double EPSILON   = 0.001 * BASE;
    public static final long   TIMEOUT   = 200;
    public static final TimeUnit UNIT    = TimeUnit.MILLISECONDS;
    public static final long MAX_ITER    = 300;
    public static final long MAX_CS_ITER = 300;

    public static final String PROJECT_DIR        = System.getProperty("user.dir");
    public static final String OUTPUT_DIR         = "output/F1/";
    public static final String FAULTY_OUTPUT_DIR  = "output/F2/";
    public static final String CS_OUTPUT_DIR      = "output/F3/";
    public static final String CPU_SCRIPT_DIR     = PROJECT_DIR + "/scripts/cpu_usage.sh";

    public static final int S = 0;
    public static final int O = 0;
}
