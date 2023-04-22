package AtomicInterface.consensus;

import utils.communication.message.ApproximationMessage;
import utils.consensus.snapshot.ConsensusState;
import utils.math.Functions;

// All methods receive a ConsensusState so that they may handle consensus as they see fit
// The default implementation builds SynchDLPSW86.
// An implementation of this class can be stateful, but only for a single instance of consensus

/**
 * Handler for performing the necessary steps at certain points in an approximate consensus algorithm's execution. An
 * implementation of this interface may be stateful, but only for a single instance of consensus. State is not kept
 * from instance to instance. Each method has a default implementation that implements the corresponding steps in the
 * SynchDLPSW86 algorithm.
 * @param <ConsensusAttachment> Type of the attachment that can be fed to the handler to provide additional
 *                             information needed for the algorithm's exectution.
 */
public interface ApproximateConsensusHandler<ConsensusAttachment>
{
    /**
     * Handler to perform a transformation on the previously cast vote when new consensus is called by another process.
     * @param cs Object containing the state of the consensus algorithm's execution.
     * @param latestVote The last vote the process has cast.
     * @param ca The object attached to the instance of consensus when it was initiated.
     * @return The vote that will be used in this instance of consensus.
     */
    default Double onNewConsensus(final ConsensusState cs, Double latestVote, final ConsensusAttachment ca)
    {
        return latestVote;
    }

    /**
     * Handler to determine the number of rounds necessary to achieve approximate consensus based on the initial
     * votes collected in the first round.
     * @param cs Object containing the state of the consensus algorithm's execution.
     * @param V0 The multiset of votes at the initialization round, as a list.
     * @param ca The object attached to the instance of consensus when it was initiated.
     * @return The number of rounds necessary to achieve consensus.
     */
    default int rounds(final ConsensusState cs, double[] V0, final ConsensusAttachment ca)
    {
        // H = ceil(log_c(delta(V)/epsilon))
        return Math.max(0, Functions.SynchH(V0, cs.epsilon, cs.n, cs.t));
    }

    /**
     * Handler to perform a transformation on a vote received from another process.
     * @param cs Object containing the state of the consensus algorithm's execution.
     * @param msg The message received from another process, containing their vote.
     * @param ca The object attached to the instance of consensus when it was initiated.
     * @return The process's vote, transformed.
     */
    default Double onReceive(final ConsensusState cs, ApproximationMessage msg, final ConsensusAttachment ca)
    {
        return msg.v;
    }

    /**
     * Handler to perform the reduction on the multiset of votes received on a certain round, corresponding to the
     * MSR phase in many approximate consensus algorithms.
     * @param cs Object containing the state of the consensus algorithm's execution.
     * @param V The multiset of votes received in this round, including the process's own.
     * @param v The process's own approximation from the previous round.
     * @param round The round of approximation.
     * @param ca The object attached to the instance of consensus when it was initiated.
     * @return The next approximation.
     */
    default double approximationRound(final ConsensusState cs, double[] V, double v, int round, final ConsensusAttachment ca)
    {
        // INITIALIZATION ROUND / APPROXIMATION ROUNDS
        if(cs.H == null || round <= cs.H)
            // v <- f_t,t(V)
            return Functions.f(V, cs.t, cs.t);

        else return v;
    }

    /**
     * Handler to check if enough messages have been received from other processes on a given round.
     * @param cs Object containing the state of the consensus algorithm's execution.
     * @param multiset The multiset of votes received thus far, excluding the calling process's own, as a list.
     * @param round The relevant round.
     * @param ca The object attached to the instance of consensus when it was initiated.
     * @return True if no more messages are needed for this round, false otherwise.
     */
    default boolean endExchangeCondition (final ConsensusState cs, double[] multiset, int round, final ConsensusAttachment ca)
    {
        return multiset.length >= cs.n - 1;
    }

}
