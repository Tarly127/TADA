package utils.consensus.types.faultDescriptors;

public enum FaultClass
{
    OMISSIVE_SYMMETRIC,
    OMISSIVE_ASYMMETRIC,
    SEMANTIC_SYMMETRIC,
    SEMANTIC_ASYMMETRIC,
    FULLY_BYZANTINE;


    public static String toString(FaultClass faultClass)
    {
        return switch (faultClass)
                {
                    case FULLY_BYZANTINE     -> "FullyByzantine";
                    case SEMANTIC_SYMMETRIC  -> "SemanticSymmetric";
                    case OMISSIVE_ASYMMETRIC -> "OmissiveSymmetric";
                    case OMISSIVE_SYMMETRIC  -> "OmissiveAsymmetric";
                    case SEMANTIC_ASYMMETRIC -> "SemanticAsymmetric";
                };
    }
}
