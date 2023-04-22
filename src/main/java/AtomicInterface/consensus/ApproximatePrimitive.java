package AtomicInterface.consensus;

/**
 * Interface defining some properties of an approximate variable, namely the precision of the value stored therein,
 * the size of the group it exists in, etc.
 */
public interface ApproximatePrimitive {

    /**
     * Get the precision of the stored value (epsilon)
     * @return Epsilon
     */
    double getPrecision();

    /**
     * Get the number of processes in the communication group
     * @return Number of processes
     */
    int getCommunicationGroupSize();

    /**
     * Get the maximum number of faulty processes in group, depending on algorithm implementation and group size
     * @return Maximum number of faulty processes in group
     */
    int getMaxFaults();

}
